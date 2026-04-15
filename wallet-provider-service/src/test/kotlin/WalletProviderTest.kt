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

import arrow.AutoCloseImplementation
import arrow.atomic.Atomic
import arrow.atomic.update
import arrow.core.mergeSuppressed
import arrow.core.prependTo
import arrow.fx.coroutines.ExitCase
import com.sksamuel.hoplite.Secret
import eu.europa.ec.eudi.walletprovider.adapter.persistence.challenge.Challenges
import eu.europa.ec.eudi.walletprovider.config.*
import eu.europa.ec.eudi.walletprovider.domain.toNonBlankString
import eu.europa.ec.eudi.walletprovider.domain.walletinformation.*
import io.ktor.client.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.testing.*
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import org.jetbrains.exposed.v1.r2dbc.deleteAll
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction
import org.junit.jupiter.api.extension.*
import org.slf4j.LoggerFactory
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

private val MySQLContainer.r2dbcUrl: String
    get() = "r2dbc:pool:mysql://$host:$firstMappedPort/$databaseName"

private val log = LoggerFactory.getLogger(WalletProviderExtension::class.java)

private class WalletProviderExtension :
    BeforeAllCallback,
    BeforeEachCallback,
    AfterAllCallback,
    ParameterResolver {
    private val resourceScope = ResourceScope()

    private val testApplication =
        with(resourceScope) {
            TestApplication {
                val config =
                    WalletProviderConfiguration(
                        database =
                            DatabaseConfiguration(
                                url = database.r2dbcUrl.toNonBlankString(),
                                username = database.username,
                                password = Secret(database.password),
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
            }
        }

    private lateinit var httpClient: HttpClient

    override fun beforeAll(context: ExtensionContext) {
        log.info("Starting TestApplication and creating HttpClient")
        runBlocking {
            testApplication.start()
            httpClient =
                resourceScope.install(
                    testApplication.createClient {
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
        }
    }

    override fun beforeEach(context: ExtensionContext) {
        log.info("Cleaning up database")
        runBlocking {
            suspendTransaction {
                Challenges.deleteAll()
            }
        }
    }

    override fun afterAll(context: ExtensionContext) {
        log.info("Stopping TestApplication and clearing ResourceScope")
        runBlocking {
            withContext(NonCancellable) {
                testApplication.stop()
                resourceScope.releaseAll()
            }
        }
    }

    override fun supportsParameter(
        parameterContext: ParameterContext,
        extensionContext: ExtensionContext,
    ): Boolean =
        arrow.fx.coroutines.ResourceScope::class.java.isAssignableFrom(parameterContext.parameter.type) ||
            HttpClient::class.java.isAssignableFrom(parameterContext.parameter.type)

    override fun resolveParameter(
        parameterContext: ParameterContext,
        extensionContext: ExtensionContext,
    ): Any =
        when {
            arrow.fx.coroutines.ResourceScope::class.java.isAssignableFrom(parameterContext.parameter.type) -> resourceScope
            HttpClient::class.java.isAssignableFrom(parameterContext.parameter.type) -> httpClient
            else -> throw ParameterResolutionException("Unsupported parameter type: ${parameterContext.parameter.type}")
        }
}

@OptIn(AutoCloseImplementation::class)
private class ResourceScope : arrow.fx.coroutines.ResourceScope {
    private val finalizers: Atomic<List<suspend (ExitCase) -> Unit>> = Atomic(emptyList())

    override fun onRelease(release: suspend (ExitCase) -> Unit) {
        finalizers.update(release::prependTo)
    }

    suspend fun releaseAll() {
        withContext(NonCancellable) {
            finalizers
                .getAndSet(emptyList())
                .fold(null as Throwable?) { acc, finalizer ->
                    acc mergeSuppressed runCatching { finalizer(ExitCase.Completed) }.exceptionOrNull()
                }?.let { throw it }
        }
    }
}

@ExtendWith(WalletProviderExtension::class)
abstract class WalletProviderTest
