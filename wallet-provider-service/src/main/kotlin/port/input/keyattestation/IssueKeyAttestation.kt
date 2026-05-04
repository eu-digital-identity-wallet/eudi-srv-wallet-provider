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

import arrow.core.Either
import arrow.core.NonEmptyList
import arrow.core.nonEmptyListOf
import arrow.core.raise.either
import arrow.core.raise.ensure
import arrow.core.raise.ensureNotNull
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
import eu.europa.ec.eudi.walletprovider.domain.keyattestation.AttackPotentialResistance
import eu.europa.ec.eudi.walletprovider.domain.keyattestation.KeyAttestation
import eu.europa.ec.eudi.walletprovider.domain.keyattestation.KeyAttestationClaims
import eu.europa.ec.eudi.walletprovider.domain.keyattestation.KeyStorageStatus
import eu.europa.ec.eudi.walletprovider.domain.keyattestation.Nonce
import eu.europa.ec.eudi.walletprovider.domain.time.Clock
import eu.europa.ec.eudi.walletprovider.domain.tokenstatuslist.Status
import eu.europa.ec.eudi.walletprovider.port.output.challenge.ValidateChallenge
import eu.europa.ec.eudi.walletprovider.port.output.jose.SignJwt
import eu.europa.ec.eudi.walletprovider.port.output.platformkeyattestation.PlatformKeyAttestationValidationFailure
import eu.europa.ec.eudi.walletprovider.port.output.platformkeyattestation.ValidatePlatformKeyAttestation
import eu.europa.ec.eudi.walletprovider.port.output.tokenstatuslist.GenerateStatusListToken
import eu.europa.ec.eudi.walletprovider.port.output.tokenstatuslist.StatusListTokenGenerationFailure
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.Required
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Duration

fun interface IssueKeyAttestation {
    suspend operator fun invoke(request: KeyAttestationIssuanceRequest): Either<KeyAttestationIssuanceFailure, KeyAttestation>
}

sealed interface KeyAttestationIssuanceRequest {
    val nonce: Nonce?
    val supportedSigningAlgorithms: NonEmptyList<JsonWebAlgorithm>?
    val preferredTtl: SecondsDuration?

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
            @SerialName(ARF.PREFERRED_TTL) override val preferredTtl: SecondsDuration? = null,
        ) : PlatformKeyAttestation<AndroidKeystoreAttestation> {
            override fun equals(other: Any?): Boolean =
                other is Android &&
                    other.nonce == nonce &&
                    other.platformKeyAttestations == platformKeyAttestations &&
                    other.challenge.contentEquals(challenge) &&
                    other.supportedSigningAlgorithms == supportedSigningAlgorithms &&
                    other.preferredTtl == preferredTtl

            override fun hashCode(): Int {
                var result = nonce?.hashCode() ?: 0
                result = 31 * result + platformKeyAttestations.hashCode()
                result = 31 * result + challenge.contentHashCode()
                result = 31 * result + (supportedSigningAlgorithms?.hashCode() ?: 0)
                result = 31 * result + (preferredTtl?.hashCode() ?: 0)
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
            @SerialName(ARF.PREFERRED_TTL) override val preferredTtl: SecondsDuration? = null,
        ) : PlatformKeyAttestation<IosHomebrewAttestation> {
            override fun equals(other: Any?): Boolean =
                other is Ios &&
                    other.nonce == nonce &&
                    other.platformKeyAttestations == platformKeyAttestations &&
                    other.challenge.contentEquals(challenge) &&
                    other.supportedSigningAlgorithms == supportedSigningAlgorithms &&
                    other.preferredTtl == preferredTtl

            override fun hashCode(): Int {
                var result = nonce?.hashCode() ?: 0
                result = 31 * result + platformKeyAttestations.hashCode()
                result = 31 * result + challenge.contentHashCode()
                result = 31 * result + (supportedSigningAlgorithms?.hashCode() ?: 0)
                result = 31 * result + (preferredTtl?.hashCode() ?: 0)
                return result
            }
        }
    }

    @Serializable
    data class JwkSet(
        override val nonce: Nonce? = null,
        val jwkSet: JsonWebKeySet,
        @Serializable(with = NonEmptyListSerializer::class) override val supportedSigningAlgorithms: NonEmptyList<JsonWebAlgorithm>? = null,
        @SerialName(ARF.PREFERRED_TTL) override val preferredTtl: SecondsDuration? = null,
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

    data class InvalidPreferredTtl(
        val requested: Duration,
        val minimumAllowed: Duration,
        val maximumAllowed: Duration,
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

    class KeyStorageStatusGenerationFailure(
        val error: StatusListTokenGenerationFailure,
    ) : KeyAttestationIssuanceFailure
}

@JvmInline
value class KeyAttestationValidity(
    val value: ClosedRange<Duration>,
) {
    init {
        require(value.start.isPositive() && value.start >= ARF.MIN_KEY_ATTESTATION_VALIDITY) {
            "minimum value must be greater than ${ARF.MIN_KEY_ATTESTATION_VALIDITY}"
        }
        require(value.start < value.endInclusive) {
            "maximum value must be greater than minimum value"
        }
    }

    override fun toString(): String = value.toString()
}

class IssueKeyAttestationLive(
    private val clock: Clock,
    private val validateChallenge: ValidateChallenge,
    private val validatePlatformKeyAttestation: ValidatePlatformKeyAttestation,
    private val validity: KeyAttestationValidity,
    private val generateStatusListToken: GenerateStatusListToken,
    private val certification: StringUrl,
    private val signJwt: SignJwt<KeyAttestationClaims>,
) : IssueKeyAttestation {
    override suspend fun invoke(request: KeyAttestationIssuanceRequest): Either<KeyAttestationIssuanceFailure, KeyAttestation> =
        either {
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
                        validateChallenge(request.challenge, clock.now())
                            .mapLeft { KeyAttestationIssuanceFailure.InvalidChallenge(it.error, it.cause) }
                            .bind()

                        request.platformKeyAttestations
                            .parMapOrAccumulate(Dispatchers.Default, 4) { validatePlatformKeyAttestation(it, request.challenge).bind() }
                            .mapLeft { errors -> KeyAttestationIssuanceFailure.InvalidPlatformKeyAttestations(errors) }
                            .bind()
                            .map { it.publicKey.toJsonWebKey() }
                            .toNonEmptyListOrNull()
                    }

                    is KeyAttestationIssuanceRequest.JwkSet -> {
                        request.jwkSet.keys.toNonEmptyListOrNull()
                    }
                }

            ensureNotNull(platformAttestedKeys) { KeyAttestationIssuanceFailure.NoPlatformAttestedKeys }
            ensure(platformAttestedKeys.distinct().size == platformAttestedKeys.size) {
                KeyAttestationIssuanceFailure.NonUniquePlatformAttestedKeys
            }

            val validity =
                request.preferredTtl?.let {
                    ensure(it in validity.value) {
                        KeyAttestationIssuanceFailure.InvalidPreferredTtl(
                            requested = it,
                            minimumAllowed = validity.value.start,
                            maximumAllowed = validity.value.endInclusive,
                        )
                    }
                    it
                } ?: validity.value.start

            val issuedAt = clock.now()
            val expiresAt = issuedAt + validity
            val keyStorageStatus =
                run {
                    val statusListToken =
                        generateStatusListToken(expiresAt)
                            .mapLeft { error -> KeyAttestationIssuanceFailure.KeyStorageStatusGenerationFailure(error) }
                            .bind()
                    val status = Status(statusListToken)
                    KeyStorageStatus(status, expiresAt)
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

            signJwt(keyAttestation)
        }
}
