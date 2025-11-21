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
package eu.europa.ec.eudi.walletprovider

import arrow.continuations.SuspendApp
import arrow.continuations.ktor.server
import arrow.fx.coroutines.resourceScope
import com.sksamuel.hoplite.ConfigLoader
import eu.europa.ec.eudi.walletprovider.config.*
import io.ktor.server.cio.*
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json

fun main() =
    SuspendApp {
        System.setProperty("io.ktor.server.engine.ShutdownHook", "false")

        val config =
            ConfigLoader
                .builder()
                .addDecoder(SignatureAlgorithmDecoder())
                .addDecoder(Base64UrlSafeByteArrayDecoder())
                .addDecoder(CertificationInformationDecoder())
                .build()
                .loadConfigOrThrow<WalletProviderConfiguration>()

        resourceScope {
            server(
                CIO,
                port =
                    config.server.port.value
                        .toInt(),
                preWait = config.server.preWait.value,
                grace = config.server.grace.value,
                timeout = config.server.timeout.value,
            ) {
                runBlocking {
                    configureWalletProviderApplication(config)
                }
            }
            awaitCancellation()
        }
    }
