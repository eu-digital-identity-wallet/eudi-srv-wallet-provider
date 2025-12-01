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
import at.asitplus.signum.indispensable.josef.JsonWebKeySet
import at.asitplus.signum.indispensable.josef.JwsAlgorithm
import at.asitplus.signum.indispensable.josef.toJsonWebKey
import at.asitplus.signum.indispensable.josef.toJwsAlgorithm
import at.asitplus.signum.indispensable.pki.CertificateChain
import at.asitplus.signum.supreme.sign.Signer
import eu.europa.ec.eudi.walletprovider.domain.AttestationBasedClientAuthenticationSpec
import eu.europa.ec.eudi.walletprovider.domain.Issuer
import eu.europa.ec.eudi.walletprovider.domain.OpenId4VCISpec
import eu.europa.ec.eudi.walletprovider.domain.RFC9728
import eu.europa.ec.eudi.walletprovider.domain.StringUrl
import io.ktor.http.HttpStatusCode
import io.ktor.http.URLBuilder
import io.ktor.http.appendPathSegments
import io.ktor.http.toURI
import io.ktor.server.application.Application
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import kotlinx.serialization.Required
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("MetadataRoutes")

fun Application.configureMetadataRoutes(
    issuer: Issuer,
    signer: Signer,
    certificateChain: CertificateChain?,
) {
    routing {
        route(RFC9728.WELL_KNOWN_URI_SUFFIX) {
            get {
                logger.info("Getting Protected Resource Metadata")
                val metadata =
                    run {
                        val jwksUri =
                            URLBuilder(issuer.value.toExternalForm())
                                .appendPathSegments("/jwks")
                                .build()
                                .toURI()
                                .toURL()
                        val signingAlgorithm = signer.signatureAlgorithm.toJwsAlgorithm().getOrThrow()
                        ProtectedResourceMetadataResponse(
                            issuer,
                            jwksUri,
                            resourceSigningAlgorithmsSupported = nonEmptyListOf(signingAlgorithm),
                            clientAttestationSigningAlgorithmsSupported = nonEmptyListOf(signingAlgorithm),
                            proofSigningAlgorithmsSupported = nonEmptyListOf(signingAlgorithm),
                        )
                    }
                call.respond(HttpStatusCode.OK, metadata)
            }
        }

        route("/jwks") {
            get {
                logger.info("Getting JWKS")
                val jwk = signer.publicKey.toJsonWebKey().copy(certificateChain = certificateChain)
                val jwkSet = JsonWebKeySet(nonEmptyListOf(jwk))
                call.respond(HttpStatusCode.OK, jwkSet)
            }
        }
    }
}

@Serializable
private data class ProtectedResourceMetadataResponse(
    @Required @SerialName(RFC9728.RESOURCE) val resource: Issuer,
    @Required @SerialName(RFC9728.JWKS_URI) val jwksUri: StringUrl,
    @Required @SerialName(RFC9728.RESOURCE_SIGNING_ALGORITHMS_SUPPORTED) @Serializable(with = NonEmptyListSerializer::class)
    val resourceSigningAlgorithmsSupported: NonEmptyList<JwsAlgorithm>,
    @Required @SerialName(
        AttestationBasedClientAuthenticationSpec.CLIENT_ATTESTATION_SIGNING_ALGORITHMS_SUPPORTED,
    ) @Serializable(with = NonEmptyListSerializer::class)
    val clientAttestationSigningAlgorithmsSupported: NonEmptyList<JwsAlgorithm>,
    @Required @SerialName(OpenId4VCISpec.PROOF_SIGNING_ALGORITHMS_SUPPORTED) @Serializable(with = NonEmptyListSerializer::class)
    val proofSigningAlgorithmsSupported: NonEmptyList<JwsAlgorithm>,
)
