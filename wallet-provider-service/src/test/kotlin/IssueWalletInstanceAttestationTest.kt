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

import at.asitplus.signum.indispensable.ECCurve
import at.asitplus.signum.indispensable.josef.toJsonWebKey
import at.asitplus.signum.supreme.sign.EphemeralKey
import eu.europa.ec.eudi.walletprovider.domain.walletinstanceattestation.WalletInstanceAttestation
import eu.europa.ec.eudi.walletprovider.domain.walletinstanceattestation.WalletInstanceAttestationClaims
import eu.europa.ec.eudi.walletprovider.domain.walletinstanceattestation.WalletMetadata
import eu.europa.ec.eudi.walletprovider.port.input.walletinstanceattestation.WalletInstanceAttestationIssuanceRequest.Jwk
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.test.TestResult
import kotlinx.serialization.json.*
import kotlin.test.*

class IssueWalletInstanceAttestationTest {
    @Test
    fun `wallet instance attestation includes wallet metadata when provided as object`() {
        val walletMetadata =
            buildJsonObject {
                put("device_id", "ABC123")
                put("app_version", "1.2.3")
            }

        runWalletInstanceAttestationTestCase(walletMetadata) {
            val signedWalletMetadata = assertNotNull(it.payload.walletMetadata)
            assertEquals(walletMetadata, signedWalletMetadata)
        }
    }

    @Test
    fun `wallet instance attestation includes wallet metadata when provided as array`() {
        val walletMetadata =
            buildJsonArray {
                add("tag1")
                add("tag2")
                add("tag3")
            }

        runWalletInstanceAttestationTestCase(walletMetadata) {
            val signedWalletMetadata = assertNotNull(it.payload.walletMetadata)
            assertEquals(walletMetadata, signedWalletMetadata)
        }
    }

    @Test
    fun `wallet instance attestation includes wallet metadata when provided as primitive`() {
        val walletMetadata = JsonPrimitive("simple-string-value")

        runWalletInstanceAttestationTestCase(walletMetadata) {
            val signedWalletMetadata = assertNotNull(it.payload.walletMetadata)
            assertEquals(walletMetadata, signedWalletMetadata)
        }
    }

    @Test
    fun `wallet instance attestation works without wallet metadata for backward compatibility`() {
        runWalletInstanceAttestationTestCase(null) {
            assertNull(it.payload.walletMetadata)
        }
    }
}

private fun runWalletInstanceAttestationTestCase(
    walletMetadata: WalletMetadata?,
    assertions: suspend (WalletInstanceAttestation) -> Unit,
): TestResult =
    runWalletProviderTestCase {
        val request =
            Jwk(
                jwk =
                    EphemeralKey {
                        ec {
                            curve = ECCurve.SECP_256_R_1
                        }
                    }.getOrThrow().publicKey.toJsonWebKey(),
                walletMetadata = walletMetadata,
            )

        val response =
            client
                .post("/wallet-instance-attestation/jwk") {
                    expectSuccess = true

                    contentType(ContentType.Application.Json)
                    accept(ContentType.Application.Json)
                    setBody(request)
                }.body<JsonObject>()

        val serializedWalletInstanceAttestation = assertIs<JsonPrimitive>(response["walletInstanceAttestation"]).content
        val walletInstanceAttestation =
            WalletInstanceAttestation
                .deserialize(
                    WalletInstanceAttestationClaims.serializer(),
                    serializedWalletInstanceAttestation,
                ).getOrThrow()
        assertions(walletInstanceAttestation)
    }
