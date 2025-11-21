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
@file:Suppress("ktlint:standard:max-line-length")

package eu.europa.ec.eudi.walletprovider.adapter.tokenstatuslist

import arrow.core.Either
import eu.europa.ec.eudi.walletprovider.domain.NonBlankString
import eu.europa.ec.eudi.walletprovider.domain.OpenId4VCISpec
import eu.europa.ec.eudi.walletprovider.domain.time.Clock
import eu.europa.ec.eudi.walletprovider.domain.toNonBlankString
import eu.europa.ec.eudi.walletprovider.domain.tokenstatuslist.Status
import eu.europa.ec.eudi.walletprovider.domain.tokenstatuslist.StatusListToken
import eu.europa.ec.eudi.walletprovider.port.output.tokenstatuslist.GenerateStatusListToken
import eu.europa.ec.eudi.walletprovider.port.output.tokenstatuslist.StatusListTokenGenerationFailure
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.request.forms.*
import io.ktor.client.request.url
import io.ktor.http.*
import io.ktor.http.headers
import java.time.format.DateTimeFormatter
import kotlin.time.Instant

typealias ApiKey = NonBlankString

class TokenStatusListServiceGenerateStatusListToken(
    private val httpClient: HttpClient,
    private val serviceUrl: Url,
    private val apiKey: ApiKey,
    private val clock: Clock,
) : GenerateStatusListToken {
    override suspend fun invoke(expiresAt: Instant): Either<StatusListTokenGenerationFailure, StatusListToken> =
        Either
            .catch {
                httpClient
                    .submitForm(
                        Parameters.build {
                            append("country", "FC")
                            append("doctype", OpenId4VCISpec.KEY_ATTESTATION_JWT_TYPE)
                            append(
                                "expiry_date",
                                with(clock) { expiresAt.toZonedDateTime().format(DateTimeFormatter.ISO_LOCAL_DATE) },
                            )
                        },
                    ) {
                        expectSuccess = true
                        url(serviceUrl)
                        headers.apply {
                            append(HttpHeaders.ContentType, ContentType.Application.FormUrlEncoded.toString())
                            append(HttpHeaders.Accept, ContentType.Application.Json.toString())
                            append("X-API-Key", apiKey.value)
                        }
                    }.body<Status>()
                    .statusList
            }.mapLeft { error ->
                StatusListTokenGenerationFailure.Unexpected("Unable to generate StatusListToken".toNonBlankString(), error)
            }
}
