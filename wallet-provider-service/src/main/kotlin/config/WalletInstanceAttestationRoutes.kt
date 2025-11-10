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

import eu.europa.ec.eudi.walletprovider.domain.walletinstanceattestation.WalletInstanceAttestation
import eu.europa.ec.eudi.walletprovider.port.input.walletinstanceattestation.IssueWalletInstanceAttestation
import eu.europa.ec.eudi.walletprovider.port.input.walletinstanceattestation.WalletInstanceAttestationIssuanceFailure
import eu.europa.ec.eudi.walletprovider.port.input.walletinstanceattestation.WalletInstanceAttestationIssuanceRequest
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

private val logger = LoggerFactory.getLogger("WalletInstanceAttestationRoutes")

fun Application.configureWalletInstanceAttestationRoutes(issueWalletInstanceAttestation: IssueWalletInstanceAttestation) {
    routing {
        route("/wallet-instance-attestation") {
            suspend fun <T : WalletInstanceAttestationIssuanceRequest> RoutingContext.issueWalletInstanceAttestation(
                requestType: KClass<T>,
            ) {
                val request = call.receive(requestType)
                logger.info("Received WalletInstanceAttestationIssuanceRequest: {}", request)

                issueWalletInstanceAttestation(request)
                    .fold(
                        ifRight = { walletInstanceAttestation ->
                            logger.info("Successfully issued WalletInstanceAttestation: {}", walletInstanceAttestation)
                            call.respond(HttpStatusCode.OK, walletInstanceAttestation.toWalletInstanceAttestationResponse())
                        },
                        ifLeft = { failure ->
                            logger.warn(failure)
                            call.respond(HttpStatusCode.BadRequest, failure.toWalletInstanceAttestationErrorResponse())
                        },
                    )
            }

            route("/platform-key-attestation/android") {
                post {
                    issueWalletInstanceAttestation(WalletInstanceAttestationIssuanceRequest.PlatformKeyAttestation.Android::class)
                }
            }
            route("/platform-key-attestation/ios") {
                post {
                    issueWalletInstanceAttestation(WalletInstanceAttestationIssuanceRequest.PlatformKeyAttestation.Ios::class)
                }
            }
            route("/jwk") {
                post {
                    issueWalletInstanceAttestation(WalletInstanceAttestationIssuanceRequest.Jwk::class)
                }
            }
        }
    }
}

private fun Logger.warn(failure: WalletInstanceAttestationIssuanceFailure) {
    val (error, cause) =
        when (failure) {
            is WalletInstanceAttestationIssuanceFailure.InvalidChallenge ->
                "WalletInstanceAttestationIssuanceRequest validation failed, " +
                    "Challenge is not valid: ${failure.error}" to
                    failure.cause

            is WalletInstanceAttestationIssuanceFailure.InvalidKeyAttestation -> {
                when (val keyAttestationFailure = failure.error) {
                    is KeyAttestationValidationFailure.InvalidKeyAttestation ->
                        "WalletInstanceAttestationIssuanceRequest validation failed, " +
                            "Key Attestation is not valid: ${keyAttestationFailure.error}" to
                            keyAttestationFailure.cause

                    is KeyAttestationValidationFailure.UnsupportedAttestedKey ->
                        "WalletInstanceAttestationIssuanceRequest validation failed, " +
                            "Key Attestation contains an unsupported PublicKey: ${keyAttestationFailure.error}" to
                            keyAttestationFailure.cause
                }
            }
        }

    warn(error, cause)
}

@Serializable
private data class WalletInstanceAttestationResponse(
    @Required val walletInstanceAttestation: String,
) {
    constructor(walletInstanceAttestation: WalletInstanceAttestation) : this(walletInstanceAttestation.serialize())
}

private fun WalletInstanceAttestation.toWalletInstanceAttestationResponse(): WalletInstanceAttestationResponse =
    WalletInstanceAttestationResponse(this)

@Serializable
private enum class WalletInstanceAttestationError {
    @SerialName("invalid_challenge")
    InvalidChallenge,

    @SerialName("invalid_key_attestation")
    InvalidKeyAttestation,

    @SerialName("unsupported_attested_key")
    UnsupportedAttestedKey,
}

@Serializable
private data class WalletInstanceAttestationErrorResponse(
    @Required val error: WalletInstanceAttestationError,
)

private fun WalletInstanceAttestationIssuanceFailure.toWalletInstanceAttestationErrorResponse(): WalletInstanceAttestationErrorResponse =
    when (this) {
        is WalletInstanceAttestationIssuanceFailure.InvalidChallenge -> WalletInstanceAttestationError.InvalidChallenge
        is WalletInstanceAttestationIssuanceFailure.InvalidKeyAttestation ->
            when (error) {
                is KeyAttestationValidationFailure.InvalidKeyAttestation -> WalletInstanceAttestationError.InvalidKeyAttestation
                is KeyAttestationValidationFailure.UnsupportedAttestedKey -> WalletInstanceAttestationError.UnsupportedAttestedKey
            }
    }.let { WalletInstanceAttestationErrorResponse(it) }
