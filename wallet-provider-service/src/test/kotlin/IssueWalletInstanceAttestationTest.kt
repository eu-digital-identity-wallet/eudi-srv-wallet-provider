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

import at.asitplus.signum.indispensable.CryptoPublicKey
import at.asitplus.signum.indispensable.ECCurve
import at.asitplus.signum.indispensable.josef.JwsCompactTyped
import at.asitplus.signum.supreme.sign.EphemeralKey
import eu.europa.ec.eudi.walletprovider.config.WalletProviderConfiguration
import eu.europa.ec.eudi.walletprovider.domain.SecondsDuration
import eu.europa.ec.eudi.walletprovider.domain.time.Clock
import eu.europa.ec.eudi.walletprovider.domain.walletinstanceattestation.WalletInstanceAttestation
import eu.europa.ec.eudi.walletprovider.domain.walletinstanceattestation.WalletInstanceAttestationClaims
import eu.europa.ec.eudi.walletprovider.domain.walletinstanceattestation.WalletMetadata
import eu.europa.ec.eudi.walletprovider.port.input.walletinstanceattestation.WalletInstanceAttestationIssuanceRequest.Jwk
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.test.dispatcher.*
import kotlinx.coroutines.test.TestResult
import kotlinx.serialization.json.*
import kotlin.test.*
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days

class IssueWalletInstanceAttestationTest : WalletProviderTest() {
    @Test
    fun `wallet instance attestation includes wallet metadata when provided as object`(httpClient: HttpClient) {
        val walletMetadata =
            buildJsonObject {
                put("device_id", "ABC123")
                put("app_version", "1.2.3")
            }

        httpClient.runWalletInstanceAttestationTestCase(walletMetadata = walletMetadata) {
            val signedWalletMetadata = assertNotNull(it.payload.walletMetadata)
            assertEquals(walletMetadata, signedWalletMetadata)
        }
    }

    @Test
    fun `wallet instance attestation includes wallet metadata when provided as array`(httpClient: HttpClient) {
        val walletMetadata =
            buildJsonArray {
                add("tag1")
                add("tag2")
                add("tag3")
            }

        httpClient.runWalletInstanceAttestationTestCase(walletMetadata = walletMetadata) {
            val signedWalletMetadata = assertNotNull(it.payload.walletMetadata)
            assertEquals(walletMetadata, signedWalletMetadata)
        }
    }

    @Test
    fun `wallet instance attestation includes wallet metadata when provided as primitive`(httpClient: HttpClient) {
        val walletMetadata = JsonPrimitive("simple-string-value")

        httpClient.runWalletInstanceAttestationTestCase(walletMetadata = walletMetadata) {
            val signedWalletMetadata = assertNotNull(it.payload.walletMetadata)
            assertEquals(walletMetadata, signedWalletMetadata)
        }
    }

    @Test
    fun `wallet instance attestation works without wallet metadata for backward compatibility`(httpClient: HttpClient) =
        httpClient.runWalletInstanceAttestationTestCase(walletMetadata = null) {
            assertNull(it.payload.walletMetadata)
        }

    @Test
    fun `wallet instance attestation uses configured client status validity when no preference is provided`(
        httpClient: HttpClient,
        clock: Clock,
        config: WalletProviderConfiguration,
    ) = httpClient.runWalletInstanceAttestationTestCase {
        val now = clock.now()
        val clientStatusValidity: Duration = it.payload.clientStatus.expiresAt - now
        assertTrue(clientStatusValidity <= config.walletInstanceAttestation.clientStatusValidity.value)
    }

    @Test
    fun `wallet instance attestation uses configured client status validity when preference is less than configured`(
        httpClient: HttpClient,
        clock: Clock,
        config: WalletProviderConfiguration,
    ) {
        val preferredClientStatusPeriod = config.walletInstanceAttestation.clientStatusValidity.value - 5.days
        check(preferredClientStatusPeriod.isPositive()) { "preferredClientStatusPeriod can't be negative" }

        httpClient.runWalletInstanceAttestationTestCase(preferredClientStatusPeriod = preferredClientStatusPeriod) {
            val now = clock.now()
            val clientStatusValidity: Duration = it.payload.clientStatus.expiresAt - now
            assertTrue(clientStatusValidity > preferredClientStatusPeriod)
            assertTrue(clientStatusValidity <= config.walletInstanceAttestation.clientStatusValidity.value)
        }
    }

    @Test
    fun `wallet instance attestation uses preferred client status validity when preference is more than configured`(
        httpClient: HttpClient,
        clock: Clock,
        config: WalletProviderConfiguration,
    ) {
        val preferredClientStatusPeriod = config.walletInstanceAttestation.clientStatusValidity.value + 5.days

        httpClient.runWalletInstanceAttestationTestCase(preferredClientStatusPeriod = preferredClientStatusPeriod) {
            val now = clock.now()
            val clientStatusValidity: Duration = it.payload.clientStatus.expiresAt - now
            assertTrue(clientStatusValidity <= preferredClientStatusPeriod)
            assertTrue(clientStatusValidity > config.walletInstanceAttestation.clientStatusValidity.value)
        }
    }
}

private fun HttpClient.runWalletInstanceAttestationTestCase(
    walletMetadata: WalletMetadata? = null,
    preferredClientStatusPeriod: SecondsDuration? = null,
    assertions: suspend (WalletInstanceAttestation) -> Unit,
): TestResult =
    runTestWithRealTime {
        val request =
            Jwk(
                jwk =
                    EphemeralKey {
                        ec {
                            curve = ECCurve.SECP_256_R_1
                        }
                    }.getOrThrow()
                        .publicKey as CryptoPublicKey.EC,
                walletMetadata = walletMetadata,
                preferredClientStatusPeriod = preferredClientStatusPeriod,
            )

        val response =
            post("/wallet-instance-attestation/jwk") {
                expectSuccess = true

                contentType(ContentType.Application.Json)
                accept(ContentType.Application.Json)
                setBody(request)
            }.body<JsonObject>()

        val serializedWalletInstanceAttestation = assertIs<JsonPrimitive>(response["walletInstanceAttestation"]).content
        val walletInstanceAttestation = JwsCompactTyped<WalletInstanceAttestationClaims>(serializedWalletInstanceAttestation)
        assertions(walletInstanceAttestation)
    }
