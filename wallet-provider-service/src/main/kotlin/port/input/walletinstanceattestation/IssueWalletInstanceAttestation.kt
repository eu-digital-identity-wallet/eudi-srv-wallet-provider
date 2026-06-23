/*
 * Copyright (c) 2025-2026 European Commission
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package eu.europa.ec.eudi.walletprovider.port.input.walletinstanceattestation

import arrow.core.NonEmptyList
import arrow.core.raise.context.Raise
import arrow.core.raise.context.ensure
import arrow.core.raise.context.withError
import arrow.core.serialization.NonEmptyListSerializer
import at.asitplus.signum.indispensable.AndroidKeystoreAttestation
import at.asitplus.signum.indispensable.Attestation
import at.asitplus.signum.indispensable.ECCurve
import at.asitplus.signum.indispensable.IosHomebrewAttestation
import at.asitplus.signum.indispensable.josef.*
import eu.europa.ec.eudi.walletprovider.domain.*
import eu.europa.ec.eudi.walletprovider.domain.time.Clock
import eu.europa.ec.eudi.walletprovider.domain.tokenstatuslist.Status
import eu.europa.ec.eudi.walletprovider.domain.walletinstanceattestation.*
import eu.europa.ec.eudi.walletprovider.domain.walletinstanceattestation.ClientStatus
import eu.europa.ec.eudi.walletprovider.port.output.challenge.ValidateChallenge
import eu.europa.ec.eudi.walletprovider.port.output.jose.SignJwt
import eu.europa.ec.eudi.walletprovider.port.output.platformkeyattestation.PlatformKeyAttestationValidationFailure
import eu.europa.ec.eudi.walletprovider.port.output.platformkeyattestation.ValidatePlatformKeyAttestation
import eu.europa.ec.eudi.walletprovider.port.output.tokenstatuslist.AllocateStatusListToken
import eu.europa.ec.eudi.walletprovider.port.output.tokenstatuslist.StatusList
import kotlinx.serialization.Required
import kotlinx.serialization.Serializable
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

fun interface IssueWalletInstanceAttestation {
    context(_: Raise<WalletInstanceAttestationIssuanceFailure>)
    suspend operator fun invoke(request: WalletInstanceAttestationIssuanceRequest): WalletInstanceAttestation
}

sealed interface WalletInstanceAttestationIssuanceRequest {
    val supportedSigningAlgorithms: NonEmptyList<JsonWebAlgorithm>?
    val walletMetadata: WalletMetadata?
    val preferredClientStatusPeriod: SecondsDuration?

    sealed interface PlatformKeyAttestation<out PlatformKeyAttestation : Attestation> : WalletInstanceAttestationIssuanceRequest {
        val platformKeyAttestation: PlatformKeyAttestation
        val challenge: Base64UrlSafeByteArray

        @Serializable
        data class Android(
            @Required override val platformKeyAttestation: AndroidKeystoreAttestation,
            @Required override val challenge: Base64UrlSafeByteArray,
            @Serializable(
                with = NonEmptyListSerializer::class,
            ) override val supportedSigningAlgorithms: NonEmptyList<JsonWebAlgorithm>? = null,
            override val walletMetadata: WalletMetadata? = null,
            override val preferredClientStatusPeriod: SecondsDuration? = null,
        ) : PlatformKeyAttestation<AndroidKeystoreAttestation> {
            override fun equals(other: Any?): Boolean =
                other is Android &&
                    other.platformKeyAttestation == platformKeyAttestation &&
                    other.challenge.contentEquals(challenge) &&
                    other.supportedSigningAlgorithms == supportedSigningAlgorithms &&
                    other.walletMetadata == walletMetadata &&
                    other.preferredClientStatusPeriod == preferredClientStatusPeriod

            override fun hashCode(): Int {
                var result = platformKeyAttestation.hashCode()
                result = 31 * result + challenge.contentHashCode()
                result = 31 * result + (supportedSigningAlgorithms?.hashCode() ?: 0)
                result = 31 * result + (walletMetadata?.hashCode() ?: 0)
                result = 31 * result + (preferredClientStatusPeriod?.hashCode() ?: 0)
                return result
            }
        }

        @Serializable
        data class Ios(
            @Required override val platformKeyAttestation: IosHomebrewAttestation,
            @Required override val challenge: Base64UrlSafeByteArray,
            @Serializable(
                with = NonEmptyListSerializer::class,
            ) override val supportedSigningAlgorithms: NonEmptyList<JsonWebAlgorithm>? = null,
            override val walletMetadata: WalletMetadata? = null,
            override val preferredClientStatusPeriod: SecondsDuration? = null,
        ) : PlatformKeyAttestation<IosHomebrewAttestation> {
            override fun equals(other: Any?): Boolean =
                other is Ios &&
                    other.platformKeyAttestation == platformKeyAttestation &&
                    other.challenge.contentEquals(challenge) &&
                    other.supportedSigningAlgorithms == supportedSigningAlgorithms &&
                    other.walletMetadata == walletMetadata &&
                    other.preferredClientStatusPeriod == preferredClientStatusPeriod

            override fun hashCode(): Int {
                var result = platformKeyAttestation.hashCode()
                result = 31 * result + challenge.contentHashCode()
                result = 31 * result + (supportedSigningAlgorithms?.hashCode() ?: 0)
                result = 31 * result + (walletMetadata?.hashCode() ?: 0)
                result = 31 * result + (preferredClientStatusPeriod?.hashCode() ?: 0)
                return result
            }
        }
    }

    @Serializable
    data class Jwk(
        val jwk: JsonWebKey,
        @Serializable(with = NonEmptyListSerializer::class) override val supportedSigningAlgorithms: NonEmptyList<JsonWebAlgorithm>? = null,
        override val walletMetadata: WalletMetadata? = null,
        override val preferredClientStatusPeriod: SecondsDuration? = null,
    ) : WalletInstanceAttestationIssuanceRequest
}

sealed interface WalletInstanceAttestationIssuanceFailure {
    class UnsupportedSigningAlgorithms(
        val supportedSigningAlgorithm: JwsAlgorithm,
        val requestedSigningAlgorithms: NonEmptyList<JsonWebAlgorithm>,
    ) : WalletInstanceAttestationIssuanceFailure

    class InvalidChallenge(
        val error: NonBlankString,
        val cause: Throwable? = null,
    ) : WalletInstanceAttestationIssuanceFailure

    class InvalidPlatformKeyAttestation(
        val error: PlatformKeyAttestationValidationFailure,
    ) : WalletInstanceAttestationIssuanceFailure

    data class UnsupportedPlatformAttestedKeyType(
        val type: JwkType,
    ) : WalletInstanceAttestationIssuanceFailure

    data class UnsupportedPlatformAttestedKeyCurve(
        val curve: ECCurve,
    ) : WalletInstanceAttestationIssuanceFailure
}

@JvmInline
value class WalletInstanceAttestationValidity(
    val value: Duration,
) {
    init {
        require(value.isPositive())
        require(value < ARF.MAX_WALLET_INSTANCE_ATTESTATION_VALIDITY) {
            "Wallet Instance Attestation validity must be less than ${ARF.MAX_WALLET_INSTANCE_ATTESTATION_VALIDITY}"
        }
    }

    override fun toString(): String = value.toString()

    companion object {
        val Default: WalletInstanceAttestationValidity = WalletInstanceAttestationValidity(5.minutes)
    }
}

class IssueWalletInstanceAttestationLive(
    private val clock: Clock,
    private val validateChallenge: ValidateChallenge,
    private val validatePlatformKeyAttestation: ValidatePlatformKeyAttestation,
    private val validity: WalletInstanceAttestationValidity,
    private val issuer: Issuer,
    private val clientId: ClientId,
    private val walletName: WalletName,
    private val walletLink: WalletLink?,
    private val walletVersion: WalletVersion,
    private val walletSolutionCertificationInformation: CertificationInformation,
    private val clientStatusValidity: PositiveDuration,
    private val allocateStatusListToken: AllocateStatusListToken,
    private val signJwt: SignJwt<WalletInstanceAttestationClaims>,
) : IssueWalletInstanceAttestation {
    init {
        require(signJwt.signingAlgorithm in TS3.ALLOWED_SIGNATURE_ALGORITHMS) {
            "Wallet Instance Attestations must be signed using one of the following JWS Algorithms: " +
                TS3.ALLOWED_SIGNATURE_ALGORITHMS.joinToString { it.identifier }
        }
    }

    context(_: Raise<WalletInstanceAttestationIssuanceFailure>)
    override suspend fun invoke(request: WalletInstanceAttestationIssuanceRequest): WalletInstanceAttestation {
        val supportedSigningAlgorithm = signJwt.signingAlgorithm
        val requestedSigningAlgorithms = request.supportedSigningAlgorithms
        if (null != requestedSigningAlgorithms) {
            ensure(supportedSigningAlgorithm in requestedSigningAlgorithms) {
                WalletInstanceAttestationIssuanceFailure.UnsupportedSigningAlgorithms(
                    supportedSigningAlgorithm,
                    requestedSigningAlgorithms,
                )
            }
        }

        val platformAttestedKey =
            when (request) {
                is WalletInstanceAttestationIssuanceRequest.PlatformKeyAttestation<*> -> {
                    withError({ WalletInstanceAttestationIssuanceFailure.InvalidChallenge(it.error, it.cause) }) {
                        validateChallenge(request.challenge, clock.now())
                    }

                    withError({ WalletInstanceAttestationIssuanceFailure.InvalidPlatformKeyAttestation(it) }) {
                        validatePlatformKeyAttestation(request.platformKeyAttestation, request.challenge)
                            .publicKey
                            .toJsonWebKey()
                    }
                }

                is WalletInstanceAttestationIssuanceRequest.Jwk -> {
                    request.jwk
                }
            }

        val platformAttestedKeyType = checkNotNull(platformAttestedKey.type) { "Platform Attested Key is missing `kty` claim" }
        ensure(JwkType.EC == platformAttestedKeyType) {
            WalletInstanceAttestationIssuanceFailure.UnsupportedPlatformAttestedKeyType(platformAttestedKeyType)
        }

        val platformAttestedKeyCurve = checkNotNull(platformAttestedKey.curve) { "Platform Attested Key is missing `crv` claim" }
        ensure(platformAttestedKeyCurve in TS3.ALLOWED_SIGNATURE_ALGORITHMS.map { it.ecCurve }) {
            WalletInstanceAttestationIssuanceFailure.UnsupportedPlatformAttestedKeyCurve(platformAttestedKeyCurve)
        }

        val issuedAt = clock.now()
        val expiresAt = issuedAt + validity.value
        val clientStatus =
            run {
                val clientStatusPeriod = maxOf(request.preferredClientStatusPeriod ?: Duration.ZERO, clientStatusValidity.value)
                val clientStatusExpiresAt = issuedAt + clientStatusPeriod
                val statusListToken = allocateStatusListToken(StatusList.WalletInstanceAttestation, clientStatusExpiresAt)
                ClientStatus(Status(statusListToken), clientStatusExpiresAt)
            }

        val walletInstanceAttestation =
            WalletInstanceAttestationClaims(
                issuer,
                clientId,
                expiresAt = expiresAt,
                ConfirmationClaim(jsonWebKey = platformAttestedKey),
                issuedAt = issuedAt,
                notBefore = issuedAt,
                walletName = walletName,
                walletLink,
                null,
                walletVersion = walletVersion,
                walletSolutionCertificationInformation,
                clientStatus,
                request.walletMetadata,
            )

        return signJwt(walletInstanceAttestation)
    }
}
