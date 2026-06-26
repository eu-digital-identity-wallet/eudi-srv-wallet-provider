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

import arrow.core.nonEmptyListOf
import at.asitplus.signum.indispensable.CryptoPublicKey
import at.asitplus.signum.indispensable.ECCurve
import at.asitplus.signum.indispensable.josef.JwsCompactTyped
import at.asitplus.signum.supreme.sign.EphemeralKey
import eu.europa.ec.eudi.walletprovider.config.WalletProviderConfiguration
import eu.europa.ec.eudi.walletprovider.domain.JsonWebKeySet
import eu.europa.ec.eudi.walletprovider.domain.SecondsDuration
import eu.europa.ec.eudi.walletprovider.domain.keyattestation.KeyAttestation
import eu.europa.ec.eudi.walletprovider.domain.keyattestation.KeyAttestationClaims
import eu.europa.ec.eudi.walletprovider.domain.keyattestation.Nonce
import eu.europa.ec.eudi.walletprovider.domain.time.Clock
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
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
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

    @Test
    fun `key attestation uses configured key storage status validity when no preference is provided`(
        httpClient: HttpClient,
        clock: Clock,
        config: WalletProviderConfiguration,
    ) = httpClient.runKeyAttestationTestCase {
        val now = clock.now()
        val keyStorageStatusValidity: Duration = it.payload.keyStorageStatus.exp - now

        assertTrue(keyStorageStatusValidity <= config.keyAttestation.keyStorageStatusValidity.value)
    }

    @Test
    fun `key attestation uses configured key storage status validity when preference is less than configured`(
        httpClient: HttpClient,
        clock: Clock,
        config: WalletProviderConfiguration,
    ) {
        val preferredKeyStorageStatusPeriod = config.keyAttestation.keyStorageStatusValidity.value - 5.days
        check(preferredKeyStorageStatusPeriod.isPositive()) { "preferredKeyStorageStatusPeriod can't be negative" }

        httpClient.runKeyAttestationTestCase(preferredKeyStorageStatusPeriod = preferredKeyStorageStatusPeriod) {
            val now = clock.now()
            val keyStorageStatusValidity: Duration = it.payload.keyStorageStatus.exp - now

            assertTrue(keyStorageStatusValidity > preferredKeyStorageStatusPeriod)
            assertTrue(keyStorageStatusValidity <= config.keyAttestation.keyStorageStatusValidity.value)
        }
    }

    @Test
    fun `key attestation uses preferred key storage status validity when preference is more than configured`(
        httpClient: HttpClient,
        clock: Clock,
        config: WalletProviderConfiguration,
    ) {
        val preferredKeyStorageStatusPeriod = config.keyAttestation.keyStorageStatusValidity.value + 5.days

        httpClient.runKeyAttestationTestCase(preferredKeyStorageStatusPeriod = preferredKeyStorageStatusPeriod) {
            val now = clock.now()
            val keyStorageStatusValidity: Duration = it.payload.keyStorageStatus.exp - now

            assertTrue(keyStorageStatusValidity <= preferredKeyStorageStatusPeriod)
            assertTrue(keyStorageStatusValidity > config.keyAttestation.keyStorageStatusValidity.value)
        }
    }
}

private fun HttpClient.runKeyAttestationTestCase(
    nonce: Nonce? = null,
    preferredKeyStorageStatusPeriod: SecondsDuration? = null,
    assertions: suspend (KeyAttestation) -> Unit,
): TestResult =
    runTestWithRealTime {
        val request =
            JwkSet(
                nonce = nonce,
                jwkSet =
                    JsonWebKeySet(
                        keys =
                            nonEmptyListOf(
                                EphemeralKey {
                                    ec {
                                        curve = ECCurve.SECP_256_R_1
                                    }
                                }.getOrThrow()
                                    .publicKey as CryptoPublicKey.EC,
                            ),
                    ),
                preferredKeyStorageStatusPeriod = preferredKeyStorageStatusPeriod,
            )

        val response =
            post("/key-attestation/jwk-set") {
                expectSuccess = true

                contentType(ContentType.Application.Json)
                accept(ContentType.Application.Json)
                setBody(request)
            }.body<JsonObject>()

        val serializedKeyAttestation = assertIs<JsonPrimitive>(response["keyAttestation"]).content
        val keyAttestation = JwsCompactTyped<KeyAttestationClaims>(serializedKeyAttestation)
        assertions(keyAttestation)
    }
