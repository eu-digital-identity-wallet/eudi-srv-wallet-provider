/*
 * Copyright (c) 2023 European Commission
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
package eu.europa.ec.eudi.walletprovider.port.input.walletunitattestation

import arrow.core.Either
import arrow.core.NonEmptyList
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
import eu.europa.ec.eudi.walletprovider.domain.time.Clock
import eu.europa.ec.eudi.walletprovider.domain.tokenstatuslist.Status
import eu.europa.ec.eudi.walletprovider.domain.walletinformation.GeneralInformation
import eu.europa.ec.eudi.walletprovider.domain.walletinformation.WalletSecureCryptographicDeviceInformation
import eu.europa.ec.eudi.walletprovider.domain.walletunitattestation.AttackPotentialResistance
import eu.europa.ec.eudi.walletprovider.domain.walletunitattestation.Nonce
import eu.europa.ec.eudi.walletprovider.domain.walletunitattestation.WalletUnitAttestation
import eu.europa.ec.eudi.walletprovider.domain.walletunitattestation.WalletUnitAttestationClaims
import eu.europa.ec.eudi.walletprovider.port.output.challenge.ValidateChallenge
import eu.europa.ec.eudi.walletprovider.port.output.jose.SignJwt
import eu.europa.ec.eudi.walletprovider.port.output.keyattestation.KeyAttestationValidationFailure
import eu.europa.ec.eudi.walletprovider.port.output.keyattestation.ValidateKeyAttestation
import eu.europa.ec.eudi.walletprovider.port.output.tokenstatuslist.GenerateStatusListToken
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.Required
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Duration

fun interface IssueWalletUnitAttestation {
    suspend operator fun invoke(
        request: WalletUnitAttestationIssuanceRequest,
    ): Either<WalletUnitAttestationIssuanceFailure, WalletUnitAttestation>
}

sealed interface WalletUnitAttestationIssuanceRequest {
    val nonce: Nonce?
    val supportedSigningAlgorithms: NonEmptyList<JsonWebAlgorithm>?
    val preferredTtl: SecondsDuration?

    sealed interface PlatformKeyAttestation<out KeyAttestation : Attestation> : WalletUnitAttestationIssuanceRequest {
        val keyAttestations: NonEmptyList<KeyAttestation>
        val challenge: Challenge

        @Serializable
        data class Android(
            override val nonce: Nonce? = null,
            @Required @Serializable(with = NonEmptyListSerializer::class)
            override val keyAttestations: NonEmptyList<AndroidKeystoreAttestation>,
            @Required override val challenge: Challenge,
            @Serializable(
                with = NonEmptyListSerializer::class,
            ) override val supportedSigningAlgorithms: NonEmptyList<JsonWebAlgorithm>? = null,
            @SerialName(ARF.PREFERRED_TTL) override val preferredTtl: SecondsDuration? = null,
        ) : PlatformKeyAttestation<AndroidKeystoreAttestation>

        @Serializable
        data class Ios(
            override val nonce: Nonce? = null,
            @Required @Serializable(with = NonEmptyListSerializer::class)
            override val keyAttestations: NonEmptyList<IosHomebrewAttestation>,
            @Required override val challenge: Challenge,
            @Serializable(
                with = NonEmptyListSerializer::class,
            ) override val supportedSigningAlgorithms: NonEmptyList<JsonWebAlgorithm>? = null,
            @SerialName(ARF.PREFERRED_TTL) override val preferredTtl: SecondsDuration? = null,
        ) : PlatformKeyAttestation<IosHomebrewAttestation>
    }

    @Serializable
    data class JwkSet(
        override val nonce: Nonce? = null,
        val jwkSet: JsonWebKeySet,
        @Serializable(with = NonEmptyListSerializer::class) override val supportedSigningAlgorithms: NonEmptyList<JsonWebAlgorithm>? = null,
        @SerialName(ARF.PREFERRED_TTL) override val preferredTtl: SecondsDuration? = null,
    ) : WalletUnitAttestationIssuanceRequest {
        init {
            require(jwkSet.keys.isNotEmpty()) { "jwkSet must not be empty" }
        }
    }
}

sealed interface WalletUnitAttestationIssuanceFailure {
    class UnsupportedSigningAlgorithms(
        val supportedSigningAlgorithm: JwsAlgorithm,
        val requestedSigningAlgorithms: NonEmptyList<JsonWebAlgorithm>,
    ) : WalletUnitAttestationIssuanceFailure

    data class InvalidPreferredTtl(
        val requested: Duration,
        val minimumAllowed: Duration,
        val maximumAllowed: Duration,
    ) : WalletUnitAttestationIssuanceFailure

    class InvalidChallenge(
        val error: NonBlankString,
        val cause: Throwable? = null,
    ) : WalletUnitAttestationIssuanceFailure

    class InvalidKeyAttestations(
        val errors: NonEmptyList<KeyAttestationValidationFailure>,
    ) : WalletUnitAttestationIssuanceFailure

    data object NoAttestedKeys : WalletUnitAttestationIssuanceFailure

    data object NonUniqueAttestedKeys : WalletUnitAttestationIssuanceFailure

    class StatusListTokenGenerationFailure(
        val error: eu.europa.ec.eudi.walletprovider.port.output.tokenstatuslist.StatusListTokenGenerationFailure,
    ) : WalletUnitAttestationIssuanceFailure
}

@JvmInline
value class WalletUnitAttestationValidity(
    val value: ClosedRange<Duration>,
) {
    init {
        require(value.start.isPositive() && value.start >= ARF.MIN_WALLET_UNIT_ATTESTATION_VALIDITY) {
            "minimum value must be greater than ${ARF.MIN_WALLET_UNIT_ATTESTATION_VALIDITY}"
        }
        require(value.start < value.endInclusive) {
            "maximum value must be greater than minimum value"
        }
    }

    override fun toString(): String = value.toString()
}

class IssueWalletUnitAttestationLive(
    private val clock: Clock,
    private val validateChallenge: ValidateChallenge,
    private val validateKeyAttestation: ValidateKeyAttestation,
    private val validity: WalletUnitAttestationValidity,
    private val generateStatusListToken: GenerateStatusListToken?,
    private val issuer: Issuer,
    private val clientId: ClientId,
    private val keyStorage: NonEmptyList<AttackPotentialResistance>?,
    private val userAuthentication: NonEmptyList<AttackPotentialResistance>?,
    private val certification: StringUrl?,
    private val generalInformation: GeneralInformation,
    private val walletSecureCryptographicDeviceInformation: WalletSecureCryptographicDeviceInformation,
    private val signJwt: SignJwt<WalletUnitAttestationClaims>,
) : IssueWalletUnitAttestation {
    override suspend fun invoke(
        request: WalletUnitAttestationIssuanceRequest,
    ): Either<WalletUnitAttestationIssuanceFailure, WalletUnitAttestation> =
        either {
            val supportedSigningAlgorithm = signJwt.signingAlgorithm
            val requestedSigningAlgorithms = request.supportedSigningAlgorithms
            if (null != requestedSigningAlgorithms) {
                ensure(supportedSigningAlgorithm in requestedSigningAlgorithms) {
                    WalletUnitAttestationIssuanceFailure.UnsupportedSigningAlgorithms(supportedSigningAlgorithm, requestedSigningAlgorithms)
                }
            }

            val attestedKeys =
                when (request) {
                    is WalletUnitAttestationIssuanceRequest.PlatformKeyAttestation<*> -> {
                        validateChallenge(request.challenge, clock.now())
                            .mapLeft { WalletUnitAttestationIssuanceFailure.InvalidChallenge(it.error, it.cause) }
                            .bind()

                        request.keyAttestations
                            .parMapOrAccumulate(Dispatchers.Default, 4) { validateKeyAttestation(it, request.challenge).bind() }
                            .mapLeft { errors -> WalletUnitAttestationIssuanceFailure.InvalidKeyAttestations(errors) }
                            .bind()
                            .map { it.publicKey.toJsonWebKey() }
                            .toNonEmptyListOrNull()
                    }

                    is WalletUnitAttestationIssuanceRequest.JwkSet -> {
                        request.jwkSet.keys.toNonEmptyListOrNull()
                    }
                }

            ensureNotNull(attestedKeys) { WalletUnitAttestationIssuanceFailure.NoAttestedKeys }
            ensure(attestedKeys.distinct().size == attestedKeys.size) { WalletUnitAttestationIssuanceFailure.NonUniqueAttestedKeys }

            val validity =
                request.preferredTtl?.let {
                    ensure(it in validity.value) {
                        WalletUnitAttestationIssuanceFailure.InvalidPreferredTtl(
                            requested = it,
                            minimumAllowed = validity.value.start,
                            maximumAllowed = validity.value.endInclusive,
                        )
                    }
                    it
                } ?: validity.value.start

            val issuedAt = clock.now()
            val expiresAt = issuedAt + validity
            val statusListToken =
                generateStatusListToken
                    ?.invoke(expiresAt)
                    ?.mapLeft { error -> WalletUnitAttestationIssuanceFailure.StatusListTokenGenerationFailure(error) }
                    ?.bind()

            val walletUnitAttestation =
                WalletUnitAttestationClaims(
                    issuer,
                    clientId,
                    issuedAt = issuedAt,
                    expiresAt = expiresAt,
                    attestedKeys,
                    keyStorage = keyStorage,
                    userAuthentication = userAuthentication,
                    certification,
                    request.nonce,
                    statusListToken?.let { Status(it) },
                    WalletUnitAttestationClaims.WalletInformation(
                        generalInformation,
                        walletSecureCryptographicDeviceInformation,
                    ),
                )

            signJwt(walletUnitAttestation)
        }
}
