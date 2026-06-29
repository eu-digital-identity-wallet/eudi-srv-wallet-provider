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
import arrow.core.raise.context.result
import arrow.fx.coroutines.ExitCase
import arrow.fx.coroutines.ResourceScope
import com.eygraber.uri.Uri
import com.eygraber.uri.Url
import com.sksamuel.hoplite.Secret
import eu.europa.ec.eudi.walletprovider.adapter.persistence.challenge.Challenges
import eu.europa.ec.eudi.walletprovider.config.*
import eu.europa.ec.eudi.walletprovider.domain.specification.AttestationBasedClientAuthentication
import eu.europa.ec.eudi.walletprovider.domain.specification.OpenId4VCI
import eu.europa.ec.eudi.walletprovider.domain.time.Clock
import eu.europa.ec.eudi.walletprovider.domain.toNonBlankString
import eu.europa.ec.eudi.walletprovider.domain.tokenstatuslist.Status
import eu.europa.ec.eudi.walletprovider.domain.tokenstatuslist.StatusListToken
import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.request.forms.*
import io.ktor.http.*
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
import kotlin.test.*
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation as ClientContentNegotiation

private val database by lazy {
    MySQLContainer("mysql:8.4.10")
        .withInitScripts(
            "schema.sql",
        ).also {
            it.start()
        }
}

private val MySQLContainer.r2dbcUrl: String
    get() = "r2dbc:pool:mysql://$host:$firstMappedPort/$databaseName"

@OptIn(AutoCloseImplementation::class)
private class WalletProviderExtension :
    BeforeAllCallback,
    BeforeEachCallback,
    AfterAllCallback,
    ParameterResolver,
    ResourceScope {
    private val finalizers: Atomic<List<suspend (ExitCase) -> Unit>> = Atomic(emptyList())

    private val clock = Clock.System

    private val json =
        Json {
            ignoreUnknownKeys = true
            prettyPrint = true
        }

    private val config =
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
            walletInstanceAttestation =
                WalletInstanceAttestationConfiguration(
                    walletName = "EUDI Wallet".toNonBlankString(),
                    walletVersion = "1.0.0".toNonBlankString(),
                    walletSolutionCertificationInformation = JsonPrimitive("https://github.com/eu-digital-identity-wallet"),
                ),
            keyAttestation =
                KeyAttestationConfiguration(
                    certification = Url.parse("https://example.org/certification"),
                ),
            tokenStatusListService =
                TokenStatusListServiceConfiguration(
                    serviceUrl = Url.parse("https://status.example.com/create"),
                    apiKey = Secret("API-KEY"),
                ),
        )

    private val testApplication: TestApplication =
        runBlocking {
            val database = config.database.connect()
            val (signer, certificateChain) = config.signingKey.load()
            val httpClient = createMockHttpClient(config, json)

            TestApplication {
                application {
                    configureWalletProviderModule(
                        config,
                        clock,
                        json,
                        database,
                        signer,
                        certificateChain,
                        httpClient,
                    )
                }
            }
        }

    private lateinit var httpClient: HttpClient

    override fun beforeAll(context: ExtensionContext) {
        log.info("Starting TestApplication and creating HttpClient")
        runBlocking {
            testApplication.start()
            httpClient =
                install(
                    testApplication.createClient {
                        install(ClientContentNegotiation) {
                            json(json)
                        }
                    },
                )
        }
    }

    override fun beforeEach(context: ExtensionContext) {
        log.info("Cleaning up database")
        runBlocking {
            withContext(NonCancellable) {
                suspendTransaction {
                    Challenges.deleteAll()
                }
            }
        }
    }

    override fun afterAll(context: ExtensionContext) {
        log.info("Stopping TestApplication and clearing ResourceScope")
        runBlocking {
            withContext(NonCancellable) {
                testApplication.stop()
                releaseAll()
            }
        }
    }

    override fun supportsParameter(
        parameterContext: ParameterContext,
        extensionContext: ExtensionContext,
    ): Boolean =
        ResourceScope::class.java.isAssignableFrom(parameterContext.parameter.type) ||
            HttpClient::class.java.isAssignableFrom(parameterContext.parameter.type) ||
            WalletProviderConfiguration::class.java.isAssignableFrom(parameterContext.parameter.type) ||
            Clock::class.java.isAssignableFrom(parameterContext.parameter.type)

    override fun resolveParameter(
        parameterContext: ParameterContext,
        extensionContext: ExtensionContext,
    ): Any =
        when {
            ResourceScope::class.java.isAssignableFrom(parameterContext.parameter.type) -> this
            HttpClient::class.java.isAssignableFrom(parameterContext.parameter.type) -> httpClient
            WalletProviderConfiguration::class.java.isAssignableFrom(parameterContext.parameter.type) -> config
            Clock::class.java.isAssignableFrom(parameterContext.parameter.type) -> clock
            else -> throw ParameterResolutionException("Unsupported parameter type: ${parameterContext.parameter.type}")
        }

    override fun onRelease(release: suspend (ExitCase) -> Unit) {
        finalizers.update(release::prependTo)
    }

    suspend fun releaseAll() {
        withContext(NonCancellable) {
            finalizers
                .getAndSet(emptyList())
                .fold(null as Throwable?) { acc, finalizer ->
                    acc mergeSuppressed result { finalizer(ExitCase.Completed) }.exceptionOrNull()
                }?.let { throw it }
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(WalletProviderExtension::class.java)
    }
}

context(resources: ResourceScope)
private fun createMockHttpClient(
    config: WalletProviderConfiguration,
    json: Json,
): HttpClient {
    val engine =
        MockEngine { request ->
            when (request.url.toString()) {
                config.tokenStatusListService.serviceUrl.toString() -> {
                    assertEquals(HttpMethod.Post, request.method)
                    assertEquals(ContentType.Application.Json.toString(), request.headers[HttpHeaders.Accept])
                    assertEquals(config.tokenStatusListService.apiKey.value, request.headers["X-API-Key"])

                    val form = assertIs<FormDataContent>(request.body).formData
                    assertEquals("FC", form["country"])
                    assertTrue {
                        form["doctype"] in
                            setOf(
                                AttestationBasedClientAuthentication.CLIENT_ATTESTATION_JWT_TYPE,
                                OpenId4VCI.KEY_ATTESTATION_JWT_TYPE,
                            )
                    }
                    assertNotNull(form["expiry_date"])

                    respond(
                        content =
                            json.encodeToString(
                                Status(StatusListToken(5u, Uri.parse("https://status.example.com/lists/10"))),
                            ),
                        status = HttpStatusCode.OK,
                        headers =
                            headersOf(
                                HttpHeaders.ContentType to listOf(ContentType.Application.Json.toString()),
                            ),
                    )
                }

                else -> {
                    fail("Unexpected request: ${request.url}")
                }
            }
        }

    return resources.install(
        HttpClient(engine) {
            install(ClientContentNegotiation) {
                json(json)
            }
        },
    )
}

@ExtendWith(WalletProviderExtension::class)
abstract class WalletProviderTest
