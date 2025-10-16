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

import eu.europa.ec.eudi.walletprovider.domain.walletapplicationattestation.WalletApplicationAttestation
import eu.europa.ec.eudi.walletprovider.port.input.walletapplicationattestation.IssueWalletApplicationAttestation
import eu.europa.ec.eudi.walletprovider.port.input.walletapplicationattestation.WalletApplicationAttestationIssuanceFailure
import eu.europa.ec.eudi.walletprovider.port.input.walletapplicationattestation.WalletApplicationAttestationIssuanceRequest
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

private val logger = LoggerFactory.getLogger("WalletApplicationAttestationRoutes")

fun Application.configureWalletApplicationAttestationRoutes(issueWalletApplicationAttestation: IssueWalletApplicationAttestation) {
    routing {
        route("/wallet-application-attestation") {
            suspend fun <T : WalletApplicationAttestationIssuanceRequest> RoutingContext.issueWalletApplicationAttestation(
                requestType: KClass<T>,
            ) {
                val request = call.receive(requestType)
                logger.info("Received WalletApplicationAttestationIssuanceRequest: {}", request)

                issueWalletApplicationAttestation(request)
                    .fold(
                        ifRight = { walletApplicationAttestation ->
                            logger.info("Successfully issued WalletApplicationAttestation: {}", walletApplicationAttestation)
                            call.respond(HttpStatusCode.OK, walletApplicationAttestation.toWalletApplicationAttestationResponse())
                        },
                        ifLeft = { failure ->
                            logger.warn(failure)
                            call.respond(HttpStatusCode.BadRequest, failure.toWalletApplicationAttestationErrorResponse())
                        },
                    )
            }

            route("/platform-key-attestation/android") {
                post {
                    issueWalletApplicationAttestation(WalletApplicationAttestationIssuanceRequest.PlatformKeyAttestation.Android::class)
                }
            }
            route("/platform-key-attestation/ios") {
                post {
                    issueWalletApplicationAttestation(WalletApplicationAttestationIssuanceRequest.PlatformKeyAttestation.Ios::class)
                }
            }
            route("/jwk") {
                post {
                    issueWalletApplicationAttestation(WalletApplicationAttestationIssuanceRequest.Jwk::class)
                }
            }
        }
    }
}

private fun Logger.warn(failure: WalletApplicationAttestationIssuanceFailure) {
    val (error, cause) =
        when (failure) {
            is WalletApplicationAttestationIssuanceFailure.InvalidChallenge ->
                "WalletApplicationAttestationIssuanceRequest validation failed, " +
                    "Challenge is not valid: ${failure.error}" to
                    failure.cause

            is WalletApplicationAttestationIssuanceFailure.InvalidKeyAttestation -> {
                when (val keyAttestationFailure = failure.error) {
                    is KeyAttestationValidationFailure.InvalidKeyAttestation ->
                        "WalletApplicationAttestationIssuanceRequest validation failed, " +
                            "Key Attestation is not valid: ${keyAttestationFailure.error}" to
                            keyAttestationFailure.cause

                    is KeyAttestationValidationFailure.UnsupportedAttestedKey ->
                        "WalletApplicationAttestationIssuanceRequest validation failed, " +
                            "Key Attestation contains an unsupported PublicKey: ${keyAttestationFailure.error}" to
                            keyAttestationFailure.cause
                }
            }
        }

    warn(error, cause)
}

@Serializable
private data class WalletApplicationAttestationResponse(
    @Required val walletApplicationAttestation: String,
) {
    constructor(walletApplicationAttestation: WalletApplicationAttestation) : this(walletApplicationAttestation.serialize())
}

private fun WalletApplicationAttestation.toWalletApplicationAttestationResponse(): WalletApplicationAttestationResponse =
    WalletApplicationAttestationResponse(this)

@Serializable
private enum class WalletApplicationAttestationError {
    @SerialName("invalid_challenge")
    InvalidChallenge,

    @SerialName("invalid_key_attestation")
    InvalidKeyAttestation,

    @SerialName("unsupported_attested_key")
    UnsupportedAttestedKey,
}

@Serializable
private data class WalletApplicationAttestationErrorResponse(
    @Required val error: WalletApplicationAttestationError,
)

private fun WalletApplicationAttestationIssuanceFailure.toWalletApplicationAttestationErrorResponse():
    WalletApplicationAttestationErrorResponse =
    when (this) {
        is WalletApplicationAttestationIssuanceFailure.InvalidChallenge -> WalletApplicationAttestationError.InvalidChallenge
        is WalletApplicationAttestationIssuanceFailure.InvalidKeyAttestation ->
            when (error) {
                is KeyAttestationValidationFailure.InvalidKeyAttestation -> WalletApplicationAttestationError.InvalidKeyAttestation
                is KeyAttestationValidationFailure.UnsupportedAttestedKey -> WalletApplicationAttestationError.UnsupportedAttestedKey
            }
    }.let { WalletApplicationAttestationErrorResponse(it) }
