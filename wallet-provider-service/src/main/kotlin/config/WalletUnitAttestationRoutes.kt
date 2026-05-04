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
package eu.europa.ec.eudi.walletprovider.config

import arrow.core.NonEmptyList
import arrow.core.nonEmptyListOf
import arrow.core.serialization.NonEmptyListSerializer
import eu.europa.ec.eudi.walletprovider.domain.walletunitattestation.WalletUnitAttestation
import eu.europa.ec.eudi.walletprovider.port.input.walletunitattestation.IssueWalletUnitAttestation
import eu.europa.ec.eudi.walletprovider.port.input.walletunitattestation.WalletUnitAttestationIssuanceFailure
import eu.europa.ec.eudi.walletprovider.port.input.walletunitattestation.WalletUnitAttestationIssuanceRequest
import eu.europa.ec.eudi.walletprovider.port.output.platformkeyattestation.PlatformKeyAttestationValidationFailure
import eu.europa.ec.eudi.walletprovider.port.output.tokenstatuslist.StatusListTokenGenerationFailure
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

private val logger = LoggerFactory.getLogger("WalletUnitAttestationRoutes")

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
        is WalletUnitAttestationIssuanceFailure.UnsupportedSigningAlgorithms -> {
            warn(
                "WalletUnitAttestationIssuanceRequest validation failed, " +
                    "unsupported signing algorithms. Supported: ${failure.supportedSigningAlgorithm.identifier}, " +
                    "requested: ${failure.requestedSigningAlgorithms.joinToString { it.identifier }}",
            )
        }

        is WalletUnitAttestationIssuanceFailure.InvalidPreferredTtl -> {
            warn(
                "WalletUnitAttestationIssuanceRequest validation failed, " +
                    "preferred ttl is not valid. Requested: ${failure.requested}, " +
                    "minimum allowed: ${failure.minimumAllowed}, maximum allowed: ${failure.maximumAllowed}",
            )
        }

        is WalletUnitAttestationIssuanceFailure.InvalidChallenge -> {
            warn(
                "WalletUnitAttestationIssuanceRequest validation failed, Challenge is not valid: ${failure.error}",
                failure.cause,
            )
        }

        is WalletUnitAttestationIssuanceFailure.InvalidPlatformKeyAttestations -> {
            failure.errors.forEach {
                when (it) {
                    is PlatformKeyAttestationValidationFailure.InvalidPlatformKeyAttestation -> {
                        warn(
                            "WalletUnitAttestationIssuanceRequest validation failed, Platform Key Attestation is not valid: ${it.error}",
                            it.cause,
                        )
                    }

                    is PlatformKeyAttestationValidationFailure.UnsupportedPlatformAttestedKey -> {
                        warn(
                            "WalletUnitAttestationIssuanceRequest validation failed, Platform Key Attestation contains an unsupported PublicKey: ${it.error}",
                            it.cause,
                        )
                    }
                }
            }
        }

        WalletUnitAttestationIssuanceFailure.NoPlatformAttestedKeys -> {
            warn("WalletUnitAttestationIssuanceRequest validation failed, contains no Platform Attested Keys")
        }

        WalletUnitAttestationIssuanceFailure.NonUniquePlatformAttestedKeys -> {
            warn("WalletUnitAttestationIssuanceRequest validation failed, contains non-unique Platform Attested Keys")
        }

        is WalletUnitAttestationIssuanceFailure.KeyStorageStatusGenerationFailure -> {
            when (failure.error) {
                is StatusListTokenGenerationFailure.Unexpected -> {
                    warn("WalletUnitAttestationIssuance failed, unable to generate Key Storage Status", failure.error.cause)
                }
            }
        }
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
    @SerialName("unsupported_signing_algorithms")
    UnsupportedSigningAlgorithms,

    @SerialName("invalid_preferred_ttl")
    InvalidPreferredTtl,

    @SerialName("invalid_challenge")
    InvalidChallenge,

    @SerialName("invalid_platform_key_attestation")
    InvalidPlatformKeyAttestation,

    @SerialName("unsupported_platform_attested_key")
    UnsupportedPlatformAttestedKey,

    @SerialName("no_platform_attested_keys")
    NoPlatformAttestedKeys,

    @SerialName("non_unique_platform_attested_keys")
    NonUniquePlatformAttestedKeys,

    @SerialName("key_storage_status_generation_failure")
    KeyStorageStatusGenerationFailure,
}

@Serializable
private data class WalletUnitAttestationErrorResponse(
    @Required @Serializable(with = NonEmptyListSerializer::class) val errors: NonEmptyList<WalletUnitAttestationError>,
)

private fun WalletUnitAttestationIssuanceFailure.toWalletUnitAttestationErrorResponse(): WalletUnitAttestationErrorResponse =
    when (this) {
        is WalletUnitAttestationIssuanceFailure.UnsupportedSigningAlgorithms -> {
            nonEmptyListOf(WalletUnitAttestationError.UnsupportedSigningAlgorithms)
        }

        is WalletUnitAttestationIssuanceFailure.InvalidPreferredTtl -> {
            nonEmptyListOf(WalletUnitAttestationError.InvalidPreferredTtl)
        }

        is WalletUnitAttestationIssuanceFailure.InvalidChallenge -> {
            nonEmptyListOf(WalletUnitAttestationError.InvalidChallenge)
        }

        is WalletUnitAttestationIssuanceFailure.InvalidPlatformKeyAttestations -> {
            errors
                .map {
                    when (it) {
                        is PlatformKeyAttestationValidationFailure.InvalidPlatformKeyAttestation -> {
                            WalletUnitAttestationError.InvalidPlatformKeyAttestation
                        }

                        is PlatformKeyAttestationValidationFailure.UnsupportedPlatformAttestedKey -> {
                            WalletUnitAttestationError.UnsupportedPlatformAttestedKey
                        }
                    }
                }.distinct()
        }

        WalletUnitAttestationIssuanceFailure.NoPlatformAttestedKeys -> {
            nonEmptyListOf(WalletUnitAttestationError.NoPlatformAttestedKeys)
        }

        WalletUnitAttestationIssuanceFailure.NonUniquePlatformAttestedKeys -> {
            nonEmptyListOf(WalletUnitAttestationError.NonUniquePlatformAttestedKeys)
        }

        is WalletUnitAttestationIssuanceFailure.KeyStorageStatusGenerationFailure -> {
            nonEmptyListOf(WalletUnitAttestationError.KeyStorageStatusGenerationFailure)
        }
    }.let { WalletUnitAttestationErrorResponse(it) }
