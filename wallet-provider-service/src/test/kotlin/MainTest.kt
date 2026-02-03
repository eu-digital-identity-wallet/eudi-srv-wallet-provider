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
package eu.europa.ec.eudi.walletprovider

import at.asitplus.signum.indispensable.josef.JwsSigned
import eu.europa.ec.eudi.walletprovider.config.*
import eu.europa.ec.eudi.walletprovider.domain.toNonBlankString
import eu.europa.ec.eudi.walletprovider.domain.walletinformation.*
import eu.europa.ec.eudi.walletprovider.domain.walletinstanceattestation.WalletInstanceAttestationClaims
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.json.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class MainTest {
    @Test
    fun `verify wallet provider application loads`() =
        testResourceScopedApplication {
            val config =
                WalletProviderConfiguration(
                    walletInformation =
                        WalletInformationConfiguration(
                            GeneralInformationConfiguration(
                                provider = WalletProviderName("Wallet Provider"),
                                id = SolutionId("EUDI Wallet"),
                                version = SolutionVersion("1.0.0"),
                                certification = CertificationInformation(JsonPrimitive("ARF")),
                            ),
                            WalletSecureCryptographicDeviceInformationConfiguration(
                                WalletSecureCryptographicDeviceType.LocalNative,
                                CertificationInformation(JsonPrimitive("ARF")),
                            ),
                        ),
                    swaggerUi = SwaggerUiConfiguration.Enabled(swaggerFile = "../openapi/openapi.json".toNonBlankString()),
                )

            application {
                configureWalletProviderApplication(config)
            }
        }

    @Test
    fun `wallet instance attestation includes wallet metadata when provided as object`() =
        testResourceScopedApplication {
            val config = createTestConfig()

            application {
                configureWalletProviderApplication(config)
            }

            val requestBody =
                buildJsonObject {
                    put("jwk", createTestJwk())
                    put(
                        "walletMetadata",
                        buildJsonObject {
                            put("device_id", "ABC123")
                            put("app_version", "1.2.3")
                        },
                    )
                }

            val response =
                client.post("/wallet-instance-attestation/jwk") {
                    contentType(ContentType.Application.Json)
                    setBody(requestBody.toString())
                }

            assertEquals(HttpStatusCode.OK, response.status)
            val jwt = response.bodyAsText()
            val claims = parseJwtClaims(jwt)
            println("JWT Claims: $claims")
            val metadata = claims["wallet_metadata"]
            assertNotNull(metadata, "wallet_metadata should be present in JWT. Full claims: $claims")
            assertEquals("ABC123", (metadata as JsonObject)["device_id"]?.jsonPrimitive?.content)
            assertEquals("1.2.3", metadata["app_version"]?.jsonPrimitive?.content)
        }

    @Test
    fun `wallet instance attestation includes wallet metadata when provided as array`() =
        testResourceScopedApplication {
            val config = createTestConfig()

            application {
                configureWalletProviderApplication(config)
            }

            val requestBody =
                buildJsonObject {
                    put("jwk", createTestJwk())
                    put(
                        "walletMetadata",
                        buildJsonArray {
                            add("tag1")
                            add("tag2")
                            add("tag3")
                        },
                    )
                }

            val response =
                client.post("/wallet-instance-attestation/jwk") {
                    contentType(ContentType.Application.Json)
                    setBody(requestBody.toString())
                }

            assertEquals(HttpStatusCode.OK, response.status)
            val jwt = response.bodyAsText()
            val claims = parseJwtClaims(jwt)
            val metadata = claims["wallet_metadata"]
            assertNotNull(metadata, "wallet_metadata should be present in JWT")
            val array = metadata as JsonArray
            assertEquals(3, array.size)
            assertEquals("tag1", array[0].jsonPrimitive.content)
        }

    @Test
    fun `wallet instance attestation includes wallet metadata when provided as primitive`() =
        testResourceScopedApplication {
            val config = createTestConfig()

            application {
                configureWalletProviderApplication(config)
            }

            val requestBody =
                buildJsonObject {
                    put("jwk", createTestJwk())
                    put("walletMetadata", "simple-string-value")
                }

            val response =
                client.post("/wallet-instance-attestation/jwk") {
                    contentType(ContentType.Application.Json)
                    setBody(requestBody.toString())
                }

            assertEquals(HttpStatusCode.OK, response.status)
            val jwt = response.bodyAsText()
            val claims = parseJwtClaims(jwt)
            val metadata = claims["wallet_metadata"]
            assertNotNull(metadata, "wallet_metadata should be present in JWT")
            assertEquals("simple-string-value", (metadata as JsonPrimitive).content)
        }

    @Test
    fun `wallet instance attestation works without wallet metadata for backward compatibility`() =
        testResourceScopedApplication {
            val config = createTestConfig()

            application {
                configureWalletProviderApplication(config)
            }

            val requestBody =
                buildJsonObject {
                    put("jwk", createTestJwk())
                }

            val response =
                client.post("/wallet-instance-attestation/jwk") {
                    contentType(ContentType.Application.Json)
                    setBody(requestBody.toString())
                }

            assertEquals(HttpStatusCode.OK, response.status)
            val jwt = response.bodyAsText()
            val claims = parseJwtClaims(jwt)
            assertNull(claims["wallet_metadata"], "wallet_metadata should not be present when not provided")
        }

    private fun createTestConfig() =
        WalletProviderConfiguration(
            walletInformation =
                WalletInformationConfiguration(
                    GeneralInformationConfiguration(
                        provider = WalletProviderName("Wallet Provider"),
                        id = SolutionId("EUDI Wallet"),
                        version = SolutionVersion("1.0.0"),
                        certification = CertificationInformation(JsonPrimitive("ARF")),
                    ),
                    WalletSecureCryptographicDeviceInformationConfiguration(
                        WalletSecureCryptographicDeviceType.LocalNative,
                        CertificationInformation(JsonPrimitive("ARF")),
                    ),
                ),
            swaggerUi = SwaggerUiConfiguration.Enabled(swaggerFile = "../openapi/openapi.json".toNonBlankString()),
        )

    private fun createTestJwk() =
        buildJsonObject {
            put("kty", "EC")
            put("crv", "P-256")
            put("x", "WKn-ZIGevcwGIyyrzFoZNBdaq9_TsqzGl96oc0CWuis")
            put("y", "y77t-RvAHRKTsSGdIYUfweuOvwrvDD-Q3Hv5J0fSKbE")
        }

    private fun parseJwtClaims(jwt: String): JsonObject {
        val parts = jwt.split(".")
        val payload = parts[1]
        val decodedBytes =
            java.util.Base64
                .getUrlDecoder()
                .decode(payload)
        val decodedString = decodedBytes.decodeToString()
        return Json.parseToJsonElement(decodedString).jsonObject
    }
}
