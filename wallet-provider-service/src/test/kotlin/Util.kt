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

import arrow.fx.coroutines.resourceScope
import com.sksamuel.hoplite.Secret
import eu.europa.ec.eudi.walletprovider.adapter.persistence.challenge.Challenges
import eu.europa.ec.eudi.walletprovider.config.*
import eu.europa.ec.eudi.walletprovider.domain.toNonBlankString
import eu.europa.ec.eudi.walletprovider.domain.walletinformation.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.testing.*
import kotlinx.coroutines.test.TestResult
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import org.jetbrains.exposed.v1.r2dbc.deleteAll
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction
import org.testcontainers.mysql.MySQLContainer
import java.nio.file.Path

private val database by lazy {
    MySQLContainer("mysql:8.4.8")
        .withInitScripts(
            "schema.sql",
        ).also {
            it.start()
        }
}

fun runWalletProviderTestCase(testCase: suspend ApplicationTestBuilder.() -> Unit): TestResult =
    testApplication {
        resourceScope {
            val config =
                WalletProviderConfiguration(
                    database =
                        DatabaseConfiguration(
                            url = "r2dbc:mysql://${database.host}:${database.firstMappedPort}/${database.databaseName}".toNonBlankString(),
                            user = database.username,
                            password = Secret(database.password),
                            driver = DatabaseConfiguration.Driver.MySQL,
                        ),
                    signingKey =
                        SigningKeyConfiguration(
                            keystoreFile = Path.of("src/test/resources/keystore.jks"),
                            keystorePassword = Secret("testKeystore"),
                            keyAlias = "test-key".toNonBlankString(),
                            keyPassword = Secret("testKeystore"),
                            algorithm = SigningAlgorithm.ES256,
                        ),
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
                check(database.isRunning) { "Database is not running" }
                configureWalletProviderApplication(config)
            }

            client =
                install(
                    createClient {
                        install(ContentNegotiation) {
                            json(
                                Json {
                                    ignoreUnknownKeys = true
                                    prettyPrint = true
                                },
                            )
                        }
                    },
                )

            testCase()

            suspendTransaction {
                Challenges.deleteAll()
            }
        }
    }
