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
package eu.europa.ec.eudi.walletprovider.config

import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.plugins.cachingheaders.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.di.*
import io.ktor.server.plugins.forwardedheaders.*
import io.ktor.server.plugins.swagger.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.Json

fun Application.configureServer() {
    configureContentNegotiation()
    configureCachingHeaders()
    configureForwardedHeader()
    configureSwaggerUi()
}

private fun Application.configureContentNegotiation() {
    install(ContentNegotiation) {
        val json: Json by this@configureContentNegotiation.dependencies
        json(json)
    }
}

private fun Application.configureCachingHeaders() {
    install(CachingHeaders) {
        options { _, _ ->
            CachingOptions(CacheControl.NoStore(CacheControl.Visibility.Private))
        }
    }
}

private fun Application.configureForwardedHeader() {
    install(XForwardedHeaders)
    install(ForwardedHeaders)
}

private fun Application.configureSwaggerUi() {
    routing {
        swaggerUI(path = "/swagger", swaggerFile = "openapi/openapi.json")
    }
}
