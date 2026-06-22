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
package eu.europa.ec.eudi.walletprovider.port.input.keyattestation

import arrow.core.NonEmptyList
import arrow.core.nonEmptyListOf
import arrow.core.raise.context.*
import arrow.core.serialization.NonEmptyListSerializer
import arrow.core.toNonEmptyListOrNull
import arrow.fx.coroutines.parMapOrAccumulate
import at.asitplus.signum.indispensable.AndroidKeystoreAttestation
import at.asitplus.signum.indispensable.Attestation
import at.asitplus.signum.indispensable.IosHomebrewAttestation
import at.asitplus.signum.indispensable.josef.JsonWebAlgorithm
import at.asitplus.signum.indispensable.josef.JsonWebKeySet
import at.asitplus.signum.indispensable.josef.JwsAlgorithm
import at.asitplus.signum.indispensable.josef.toJsonWebKey
import eu.europa.ec.eudi.walletprovider.domain.*
import eu.europa.ec.eudi.walletprovider.domain.keyattestation.*
import eu.europa.ec.eudi.walletprovider.domain.time.Clock
import eu.europa.ec.eudi.walletprovider.domain.tokenstatuslist.Status
import eu.europa.ec.eudi.walletprovider.port.output.challenge.ValidateChallenge
import eu.europa.ec.eudi.walletprovider.port.output.jose.SignJwt
import eu.europa.ec.eudi.walletprovider.port.output.platformkeyattestation.PlatformKeyAttestationValidationFailure
import eu.europa.ec.eudi.walletprovider.port.output.platformkeyattestation.ValidatePlatformKeyAttestation
import eu.europa.ec.eudi.walletprovider.port.output.tokenstatuslist.AllocateStatusListToken
import eu.europa.ec.eudi.walletprovider.port.output.tokenstatuslist.StatusList
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.Required
import kotlinx.serialization.Serializable
import kotlin.time.Duration

fun interface IssueKeyAttestation {
    context(_: Raise<KeyAttestationIssuanceFailure>)
    suspend operator fun invoke(request: KeyAttestationIssuanceRequest): KeyAttestation
}

sealed interface KeyAttestationIssuanceRequest {
    val nonce: Nonce?
    val supportedSigningAlgorithms: NonEmptyList<JsonWebAlgorithm>?
    val preferredKeyStorageStatusPeriod: SecondsDuration?

    sealed interface PlatformKeyAttestation<out PlatformKeyAttestation : Attestation> : KeyAttestationIssuanceRequest {
        val platformKeyAttestations: NonEmptyList<PlatformKeyAttestation>
        val challenge: Base64UrlSafeByteArray

        @Serializable
        data class Android(
            override val nonce: Nonce? = null,
            @Required @Serializable(with = NonEmptyListSerializer::class)
            override val platformKeyAttestations: NonEmptyList<AndroidKeystoreAttestation>,
            @Required override val challenge: Base64UrlSafeByteArray,
            @Serializable(
                with = NonEmptyListSerializer::class,
            ) override val supportedSigningAlgorithms: NonEmptyList<JsonWebAlgorithm>? = null,
            override val preferredKeyStorageStatusPeriod: SecondsDuration? = null,
        ) : PlatformKeyAttestation<AndroidKeystoreAttestation> {
            override fun equals(other: Any?): Boolean =
                other is Android &&
                    other.nonce == nonce &&
                    other.platformKeyAttestations == platformKeyAttestations &&
                    other.challenge.contentEquals(challenge) &&
                    other.supportedSigningAlgorithms == supportedSigningAlgorithms &&
                    other.preferredKeyStorageStatusPeriod == preferredKeyStorageStatusPeriod

            override fun hashCode(): Int {
                var result = nonce?.hashCode() ?: 0
                result = 31 * result + platformKeyAttestations.hashCode()
                result = 31 * result + challenge.contentHashCode()
                result = 31 * result + (supportedSigningAlgorithms?.hashCode() ?: 0)
                result = 31 * result + (preferredKeyStorageStatusPeriod?.hashCode() ?: 0)
                return result
            }
        }

        @Serializable
        data class Ios(
            override val nonce: Nonce? = null,
            @Required @Serializable(with = NonEmptyListSerializer::class)
            override val platformKeyAttestations: NonEmptyList<IosHomebrewAttestation>,
            @Required override val challenge: Base64UrlSafeByteArray,
            @Serializable(
                with = NonEmptyListSerializer::class,
            ) override val supportedSigningAlgorithms: NonEmptyList<JsonWebAlgorithm>? = null,
            override val preferredKeyStorageStatusPeriod: SecondsDuration? = null,
        ) : PlatformKeyAttestation<IosHomebrewAttestation> {
            override fun equals(other: Any?): Boolean =
                other is Ios &&
                    other.nonce == nonce &&
                    other.platformKeyAttestations == platformKeyAttestations &&
                    other.challenge.contentEquals(challenge) &&
                    other.supportedSigningAlgorithms == supportedSigningAlgorithms &&
                    other.preferredKeyStorageStatusPeriod == preferredKeyStorageStatusPeriod

            override fun hashCode(): Int {
                var result = nonce?.hashCode() ?: 0
                result = 31 * result + platformKeyAttestations.hashCode()
                result = 31 * result + challenge.contentHashCode()
                result = 31 * result + (supportedSigningAlgorithms?.hashCode() ?: 0)
                result = 31 * result + (preferredKeyStorageStatusPeriod?.hashCode() ?: 0)
                return result
            }
        }
    }

    @Serializable
    data class JwkSet(
        override val nonce: Nonce? = null,
        val jwkSet: JsonWebKeySet,
        @Serializable(with = NonEmptyListSerializer::class) override val supportedSigningAlgorithms: NonEmptyList<JsonWebAlgorithm>? = null,
        override val preferredKeyStorageStatusPeriod: SecondsDuration? = null,
    ) : KeyAttestationIssuanceRequest {
        init {
            require(jwkSet.keys.isNotEmpty()) { "jwkSet must not be empty" }
        }
    }
}

sealed interface KeyAttestationIssuanceFailure {
    class UnsupportedSigningAlgorithms(
        val supportedSigningAlgorithm: JwsAlgorithm,
        val requestedSigningAlgorithms: NonEmptyList<JsonWebAlgorithm>,
    ) : KeyAttestationIssuanceFailure

    class InvalidChallenge(
        val error: NonBlankString,
        val cause: Throwable? = null,
    ) : KeyAttestationIssuanceFailure

    class InvalidPlatformKeyAttestations(
        val errors: NonEmptyList<PlatformKeyAttestationValidationFailure>,
    ) : KeyAttestationIssuanceFailure

    data object NoPlatformAttestedKeys : KeyAttestationIssuanceFailure

    data object NonUniquePlatformAttestedKeys : KeyAttestationIssuanceFailure
}

@JvmInline
value class KeyAttestationValidity(
    val value: Duration,
) {
    init {
        require(value.isPositive()) {
            "value must be positive"
        }
        require(value >= ARF.MIN_KEY_ATTESTATION_VALIDITY) {
            "minimum value must be equal or greater than ${ARF.MIN_KEY_ATTESTATION_VALIDITY}"
        }
    }

    companion object {
        val Default: KeyAttestationValidity = KeyAttestationValidity(ARF.MIN_KEY_ATTESTATION_VALIDITY)
    }

    override fun toString(): String = value.toString()
}

class IssueKeyAttestationLive(
    private val clock: Clock,
    private val validateChallenge: ValidateChallenge,
    private val validatePlatformKeyAttestation: ValidatePlatformKeyAttestation,
    private val validity: KeyAttestationValidity,
    private val allocateStatusListToken: AllocateStatusListToken,
    private val certification: StringUrl,
    private val signJwt: SignJwt<KeyAttestationClaims>,
    private val preferredKeyStorageStatusPeriod: PositiveDuration,
) : IssueKeyAttestation {
    context(_: Raise<KeyAttestationIssuanceFailure>)
    override suspend fun invoke(request: KeyAttestationIssuanceRequest): KeyAttestation {
        val supportedSigningAlgorithm = signJwt.signingAlgorithm
        val requestedSigningAlgorithms = request.supportedSigningAlgorithms
        if (null != requestedSigningAlgorithms) {
            ensure(supportedSigningAlgorithm in requestedSigningAlgorithms) {
                KeyAttestationIssuanceFailure.UnsupportedSigningAlgorithms(supportedSigningAlgorithm, requestedSigningAlgorithms)
            }
        }

        val platformAttestedKeys =
            when (request) {
                is KeyAttestationIssuanceRequest.PlatformKeyAttestation<*> -> {
                    withError({ KeyAttestationIssuanceFailure.InvalidChallenge(it.error, it.cause) }) {
                        validateChallenge(request.challenge, clock.now())
                    }

                    withError({ errors -> KeyAttestationIssuanceFailure.InvalidPlatformKeyAttestations(errors) }) {
                        request.platformKeyAttestations
                            .parMapOrAccumulate(Dispatchers.Default, 4) { validatePlatformKeyAttestation(it, request.challenge) }
                            .bind()
                            .map { it.publicKey.toJsonWebKey() }
                            .toNonEmptyListOrNull()
                    }
                }

                is KeyAttestationIssuanceRequest.JwkSet -> {
                    request.jwkSet.keys.toNonEmptyListOrNull()
                }
            }

        ensureNotNull(platformAttestedKeys) { KeyAttestationIssuanceFailure.NoPlatformAttestedKeys }
        ensure(platformAttestedKeys.distinct().size == platformAttestedKeys.size) {
            KeyAttestationIssuanceFailure.NonUniquePlatformAttestedKeys
        }

        val issuedAt = clock.now()
        val expiresAt = issuedAt + validity.value
        val keyStorageStatus =
            run {
                val keyStatusPeriod = maxOf(request.preferredKeyStorageStatusPeriod ?: Duration.ZERO, preferredKeyStorageStatusPeriod.value)
                val keyStorageExpiresAt = issuedAt + keyStatusPeriod

                val statusListToken = allocateStatusListToken(StatusList.KeyAttestation, keyStorageExpiresAt)
                val status = Status(statusListToken)

                KeyStorageStatus(status, keyStorageExpiresAt)
            }

        val keyAttestation =
            KeyAttestationClaims(
                issuedAt = issuedAt,
                expiresAt = expiresAt,
                platformAttestedKeys,
                keyStorage = nonEmptyListOf(AttackPotentialResistance.Iso18045High),
                userAuthentication = nonEmptyListOf(AttackPotentialResistance.Iso18045High),
                certification,
                request.nonce,
                keyStorageStatus = keyStorageStatus,
            )

        return signJwt(keyAttestation)
    }
}
