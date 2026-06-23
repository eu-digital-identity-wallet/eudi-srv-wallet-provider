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

import arrow.continuations.SuspendApp
import arrow.continuations.ktor.server
import arrow.fx.coroutines.resourceScope
import com.sksamuel.hoplite.ConfigLoader
import eu.europa.ec.eudi.walletprovider.domain.time.Clock
import io.ktor.client.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.awaitCancellation
import kotlinx.serialization.json.Json
import io.ktor.client.engine.cio.CIO as CIOClientEngine
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation as ClientContentNegotiation
import io.ktor.server.cio.CIO as CIOServerEngine

object WalletProviderApplication {
    fun run(): Unit =
        SuspendApp {
            System.setProperty("io.ktor.server.engine.ShutdownHook", "false")

            val config =
                ConfigLoader
                    .builder()
                    .addDecoder(Base64UrlSafeByteArrayDecoder())
                    .addDecoder(CertificationInformationDecoder())
                    .withExplicitSealedTypes()
                    .build()
                    .loadConfigOrThrow<WalletProviderConfiguration>()

            resourceScope {
                val clock = Clock.System
                val json =
                    Json {
                        ignoreUnknownKeys = true
                        prettyPrint = true
                    }
                val database = config.database.connect()
                val (signer, certificateChain) = config.signingKey.load()
                val httpClient =
                    install(
                        HttpClient(CIOClientEngine) {
                            install(ClientContentNegotiation) {
                                json(json)
                            }
                        },
                    )

                server(
                    CIOServerEngine,
                    port =
                        config.server.port.value
                            .toInt(),
                    preWait = config.server.preWait.value,
                    grace = config.server.grace.value,
                    timeout = config.server.timeout.value,
                ) {
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
                awaitCancellation()
            }
        }
}
