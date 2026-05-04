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
import at.asitplus.signum.indispensable.josef.JsonWebKeySet
import at.asitplus.signum.indispensable.josef.toJsonWebKey
import at.asitplus.signum.supreme.sign.EphemeralKey
import eu.europa.ec.eudi.walletprovider.domain.keyattestation.KeyAttestation
import eu.europa.ec.eudi.walletprovider.domain.keyattestation.KeyAttestationClaims
import eu.europa.ec.eudi.walletprovider.domain.keyattestation.Nonce
import eu.europa.ec.eudi.walletprovider.port.input.keyattestation.KeyAttestationIssuanceRequest.JwkSet
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.test.dispatcher.*
import kotlinx.coroutines.test.TestResult
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.*
import kotlin.uuid.Uuid

class IssueKeyAttestationTest : WalletProviderTest() {
    @Test
    fun `key attestation contains nonce when provided`(httpClient: HttpClient) {
        httpClient.runKeyAttestationTestCase {
            assertNull(it.payload.nonce)
        }

        val nonce = Uuid.random().toString()
        httpClient.runKeyAttestationTestCase(nonce) {
            assertEquals(nonce, it.payload.nonce)
        }
    }
}

private fun HttpClient.runKeyAttestationTestCase(
    nonce: Nonce? = null,
    assertions: suspend (KeyAttestation) -> Unit,
): TestResult =
    runTestWithRealTime {
        val request =
            JwkSet(
                nonce = nonce,
                jwkSet =
                    JsonWebKeySet(
                        keys =
                            listOf(
                                EphemeralKey {
                                    ec {
                                        curve = ECCurve.SECP_256_R_1
                                    }
                                }.getOrThrow().publicKey.toJsonWebKey(),
                            ),
                    ),
            )

        val response =
            post("/key-attestation/jwk-set") {
                expectSuccess = true

                contentType(ContentType.Application.Json)
                accept(ContentType.Application.Json)
                setBody(request)
            }.body<JsonObject>()

        val serializedKeyAttestation = assertIs<JsonPrimitive>(response["keyAttestation"]).content
        val keyAttestation =
            KeyAttestation
                .deserialize(
                    KeyAttestationClaims.serializer(),
                    serializedKeyAttestation,
                ).getOrThrow()
        assertions(keyAttestation)
    }
