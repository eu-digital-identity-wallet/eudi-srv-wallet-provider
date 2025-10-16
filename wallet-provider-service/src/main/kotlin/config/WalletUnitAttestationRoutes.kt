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
package eu.europa.ec.eudi.walletprovider.config

import arrow.core.NonEmptyList
import arrow.core.nonEmptyListOf
import arrow.core.serialization.NonEmptyListSerializer
import eu.europa.ec.eudi.walletprovider.domain.walletunitattestation.WalletUnitAttestation
import eu.europa.ec.eudi.walletprovider.port.input.walletunitattestation.IssueWalletUnitAttestation
import eu.europa.ec.eudi.walletprovider.port.input.walletunitattestation.WalletUnitAttestationIssuanceFailure
import eu.europa.ec.eudi.walletprovider.port.input.walletunitattestation.WalletUnitAttestationIssuanceRequest
import eu.europa.ec.eudi.walletprovider.port.output.keyattestation.KeyAttestationValidationFailure
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Required
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import kotlin.reflect.KClass

private val logger = LoggerFactory.getLogger("WalletApplicationUnitRoutes")

fun Application.configureWalletUnitAttestationRoutes(issueWalletUnitAttestation: IssueWalletUnitAttestation) {
    routing {
        route("/wallet-unit-attestation") {
            suspend fun <T : WalletUnitAttestationIssuanceRequest> RoutingContext.issueWalletUnitAttestation(requestType: KClass<T>) {
                val request = call.receive(requestType)
                logger.info("Received WalletUnitAttestationIssuanceRequest: {}", request)

                issueWalletUnitAttestation(request)
                    .fold(
                        ifRight = { walletUnitAttestation ->
                            logger.info("Successfully issued WalletUnitAttestation: {}", walletUnitAttestation)
                            call.respond(HttpStatusCode.OK, walletUnitAttestation.toWalletUnitAttestationResponse())
                        },
                        ifLeft = { failure ->
                            logger.warn(failure)
                            call.respond(HttpStatusCode.BadRequest, failure.toWalletUnitAttestationErrorResponse())
                        },
                    )
            }

            route("/platform-key-attestation/android") {
                post {
                    issueWalletUnitAttestation(WalletUnitAttestationIssuanceRequest.PlatformKeyAttestation.Android::class)
                }
            }
            route("/platform-key-attestation/ios") {
                post {
                    issueWalletUnitAttestation(WalletUnitAttestationIssuanceRequest.PlatformKeyAttestation.Ios::class)
                }
            }
            route("/jwk-set") {
                post {
                    issueWalletUnitAttestation(WalletUnitAttestationIssuanceRequest.JwkSet::class)
                }
            }
        }
    }
}

private fun Logger.warn(failure: WalletUnitAttestationIssuanceFailure) {
    when (failure) {
        is WalletUnitAttestationIssuanceFailure.InvalidChallenge ->
            warn(
                "WalletUnitAttestationIssuanceRequest validation failed Challenge is not valid: ${failure.error}",
                failure.cause,
            )

        is WalletUnitAttestationIssuanceFailure.InvalidKeyAttestations -> {
            failure.errors.forEach {
                when (it) {
                    is KeyAttestationValidationFailure.InvalidKeyAttestation ->
                        warn(
                            "WalletApplicationAttestationIssuanceRequest validation failed Key Attestation is not valid: ${it.error}",
                            it.cause,
                        )

                    is KeyAttestationValidationFailure.UnsupportedAttestedKey ->
                        warn(
                            "WalletApplicationAttestationIssuanceRequest validation failed, Key Attestation contains an unsupported PublicKey: ${it.error}",
                            it.cause,
                        )
                }
            }
        }

        WalletUnitAttestationIssuanceFailure.NoAttestedKeys ->
            warn("WalletApplicationAttestationIssuanceRequest validation failed, contains no Attested Keys")

        WalletUnitAttestationIssuanceFailure.NonUniqueAttestedKeys ->
            warn("WalletApplicationAttestationIssuanceRequest validation failed, contains non-unique Attested Keys")
    }
}

@Serializable
private data class WalletUnitAttestationResponse(
    @Required val walletUnitAttestation: String,
) {
    constructor(walletUnitAttestation: WalletUnitAttestation) : this(walletUnitAttestation.serialize())
}

private fun WalletUnitAttestation.toWalletUnitAttestationResponse(): WalletUnitAttestationResponse = WalletUnitAttestationResponse(this)

@Serializable
private enum class WalletUnitAttestationError {
    @SerialName("invalid_challenge")
    InvalidChallenge,

    @SerialName("invalid_key_attestation")
    InvalidKeyAttestation,

    @SerialName("unsupported_attested_key")
    UnsupportedAttestedKey,

    @SerialName("no_attested_keys")
    NoAttestedKeys,

    @SerialName("non_unique_attested_keys")
    NonUniqueAttestedKeys,
}

@Serializable
private data class WalletUnitAttestationErrorResponse(
    @Required @Serializable(with = NonEmptyListSerializer::class) val errors: NonEmptyList<WalletUnitAttestationError>,
)

private fun WalletUnitAttestationIssuanceFailure.toWalletUnitAttestationErrorResponse(): WalletUnitAttestationErrorResponse =
    when (this) {
        is WalletUnitAttestationIssuanceFailure.InvalidChallenge -> nonEmptyListOf(WalletUnitAttestationError.InvalidChallenge)
        is WalletUnitAttestationIssuanceFailure.InvalidKeyAttestations ->
            errors
                .map {
                    when (it) {
                        is KeyAttestationValidationFailure.InvalidKeyAttestation -> WalletUnitAttestationError.InvalidKeyAttestation
                        is KeyAttestationValidationFailure.UnsupportedAttestedKey -> WalletUnitAttestationError.UnsupportedAttestedKey
                    }
                }.distinct()
        WalletUnitAttestationIssuanceFailure.NoAttestedKeys -> nonEmptyListOf(WalletUnitAttestationError.NoAttestedKeys)
        WalletUnitAttestationIssuanceFailure.NonUniqueAttestedKeys -> nonEmptyListOf(WalletUnitAttestationError.NonUniqueAttestedKeys)
    }.let { WalletUnitAttestationErrorResponse(it) }
