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
import arrow.core.raise.effect
import arrow.core.raise.getOrElse
import arrow.core.serialization.NonEmptyListSerializer
import eu.europa.ec.eudi.walletprovider.domain.keyattestation.KeyAttestation
import eu.europa.ec.eudi.walletprovider.port.input.keyattestation.IssueKeyAttestation
import eu.europa.ec.eudi.walletprovider.port.input.keyattestation.KeyAttestationIssuanceFailure
import eu.europa.ec.eudi.walletprovider.port.input.keyattestation.KeyAttestationIssuanceRequest
import eu.europa.ec.eudi.walletprovider.port.output.platformkeyattestation.PlatformKeyAttestationValidationFailure
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

private val logger = LoggerFactory.getLogger("KeyAttestationRoutes")

fun Application.configureKeyAttestationRoutes(issueKeyAttestation: IssueKeyAttestation) {
    routing {
        route("/key-attestation") {
            suspend fun <T : KeyAttestationIssuanceRequest> RoutingContext.issueKeyAttestation(requestType: KClass<T>) {
                val request = call.receive(requestType)
                logger.info("Received KeyAttestationIssuanceRequest: {}", request)

                effect {
                    val keyAttestation = issueKeyAttestation(request)
                    logger.info("Successfully issued KeyAttestation: {}", keyAttestation)
                    call.respond(HttpStatusCode.OK, keyAttestation.toKeyAttestationResponse())
                }.getOrElse { failure ->
                    logger.warn(failure)
                    call.respond(HttpStatusCode.BadRequest, failure.toKeyAttestationErrorResponse())
                }
            }

            route("/platform-key-attestation/android") {
                post {
                    issueKeyAttestation(KeyAttestationIssuanceRequest.PlatformKeyAttestation.Android::class)
                }
            }
            route("/platform-key-attestation/ios") {
                post {
                    issueKeyAttestation(KeyAttestationIssuanceRequest.PlatformKeyAttestation.Ios::class)
                }
            }
            route("/jwk-set") {
                post {
                    issueKeyAttestation(KeyAttestationIssuanceRequest.JwkSet::class)
                }
            }
        }
    }
}

private fun Logger.warn(failure: KeyAttestationIssuanceFailure) {
    when (failure) {
        is KeyAttestationIssuanceFailure.UnsupportedSigningAlgorithms -> {
            warn(
                "KeyAttestationIssuanceRequest validation failed, " +
                    "unsupported signing algorithms. Supported: ${failure.supportedSigningAlgorithm.identifier}, " +
                    "requested: ${failure.requestedSigningAlgorithms.joinToString { it.identifier }}",
            )
        }

        is KeyAttestationIssuanceFailure.InvalidChallenge -> {
            warn(
                "KeyAttestationIssuanceRequest validation failed, Challenge is not valid: ${failure.error}",
                failure.cause,
            )
        }

        is KeyAttestationIssuanceFailure.InvalidPlatformKeyAttestations -> {
            failure.errors.forEach {
                when (it) {
                    is PlatformKeyAttestationValidationFailure.InvalidPlatformKeyAttestation -> {
                        warn(
                            "KeyAttestationIssuanceRequest validation failed, Platform Key Attestation is not valid: ${it.error}",
                            it.cause,
                        )
                    }

                    is PlatformKeyAttestationValidationFailure.UnsupportedPlatformAttestedKey -> {
                        warn(
                            "KeyAttestationIssuanceRequest validation failed, Platform Key Attestation contains an unsupported PublicKey: ${it.error}",
                            it.cause,
                        )
                    }
                }
            }
        }

        KeyAttestationIssuanceFailure.NonUniquePlatformAttestedKeys -> {
            warn("KeyAttestationIssuanceRequest validation failed, contains non-unique Platform Attested Keys")
        }

        is KeyAttestationIssuanceFailure.UnsupportedPlatformAttestedKeyCurve -> {
            warn("KeyAttestationIssuanceRequest validation failed, Platform Attested Key Curve is not supported: ${failure.curve.name}")
        }
    }
}

@Serializable
private data class KeyAttestationResponse(
    @Required val keyAttestation: String,
) {
    constructor(keyAttestation: KeyAttestation) : this(keyAttestation.toString())
}

private fun KeyAttestation.toKeyAttestationResponse(): KeyAttestationResponse = KeyAttestationResponse(this)

@Serializable
private enum class KeyAttestationError {
    @SerialName("unsupported_signing_algorithms")
    UnsupportedSigningAlgorithms,

    @SerialName("invalid_challenge")
    InvalidChallenge,

    @SerialName("invalid_platform_key_attestation")
    InvalidPlatformKeyAttestation,

    @SerialName("unsupported_platform_attested_key")
    UnsupportedPlatformAttestedKey,

    @SerialName("non_unique_platform_attested_keys")
    NonUniquePlatformAttestedKeys,

    @SerialName("unsupported_platform_attested_key_curve")
    UnsupportedPlatformAttestedKeyCurve,
}

@Serializable
private data class KeyAttestationErrorResponse(
    @Required @Serializable(with = NonEmptyListSerializer::class) val errors: NonEmptyList<KeyAttestationError>,
)

private fun KeyAttestationIssuanceFailure.toKeyAttestationErrorResponse(): KeyAttestationErrorResponse =
    when (this) {
        is KeyAttestationIssuanceFailure.UnsupportedSigningAlgorithms -> {
            nonEmptyListOf(KeyAttestationError.UnsupportedSigningAlgorithms)
        }

        is KeyAttestationIssuanceFailure.InvalidChallenge -> {
            nonEmptyListOf(KeyAttestationError.InvalidChallenge)
        }

        is KeyAttestationIssuanceFailure.InvalidPlatformKeyAttestations -> {
            errors
                .map {
                    when (it) {
                        is PlatformKeyAttestationValidationFailure.InvalidPlatformKeyAttestation -> {
                            KeyAttestationError.InvalidPlatformKeyAttestation
                        }

                        is PlatformKeyAttestationValidationFailure.UnsupportedPlatformAttestedKey -> {
                            KeyAttestationError.UnsupportedPlatformAttestedKey
                        }
                    }
                }.distinct()
        }

        KeyAttestationIssuanceFailure.NonUniquePlatformAttestedKeys -> {
            nonEmptyListOf(KeyAttestationError.NonUniquePlatformAttestedKeys)
        }

        is KeyAttestationIssuanceFailure.UnsupportedPlatformAttestedKeyCurve -> {
            nonEmptyListOf(KeyAttestationError.UnsupportedPlatformAttestedKeyCurve)
        }
    }.let { KeyAttestationErrorResponse(it) }
