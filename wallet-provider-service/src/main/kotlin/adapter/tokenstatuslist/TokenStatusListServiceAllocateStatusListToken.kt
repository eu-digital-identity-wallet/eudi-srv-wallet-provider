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
@file:Suppress("ktlint:standard:max-line-length")

package eu.europa.ec.eudi.walletprovider.adapter.tokenstatuslist

import com.eygraber.uri.Url
import eu.europa.ec.eudi.walletprovider.domain.NonBlankString
import eu.europa.ec.eudi.walletprovider.domain.specification.AttestationBasedClientAuthentication
import eu.europa.ec.eudi.walletprovider.domain.specification.OpenId4VCI
import eu.europa.ec.eudi.walletprovider.domain.time.Clock
import eu.europa.ec.eudi.walletprovider.domain.tokenstatuslist.Status
import eu.europa.ec.eudi.walletprovider.domain.tokenstatuslist.StatusListToken
import eu.europa.ec.eudi.walletprovider.port.output.tokenstatuslist.AllocateStatusListToken
import eu.europa.ec.eudi.walletprovider.port.output.tokenstatuslist.StatusList
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.request.forms.*
import io.ktor.http.*
import java.time.format.DateTimeFormatter
import kotlin.time.Instant

typealias ApiKey = NonBlankString

class TokenStatusListServiceAllocateStatusListToken(
    private val httpClient: HttpClient,
    private val serviceUrl: Url,
    private val apiKey: ApiKey,
    private val clock: Clock,
) : AllocateStatusListToken {
    override suspend fun invoke(
        statusList: StatusList,
        expiresAt: Instant,
    ): StatusListToken =
        httpClient
            .submitForm(
                serviceUrl.toString(),
                Parameters.build {
                    append("country", "FC")
                    append(
                        "doctype",
                        when (statusList) {
                            StatusList.WalletInstanceAttestation -> AttestationBasedClientAuthentication.CLIENT_ATTESTATION_JWT_TYPE
                            StatusList.KeyAttestation -> OpenId4VCI.KEY_ATTESTATION_JWT_TYPE
                        },
                    )
                    append(
                        "expiry_date",
                        with(clock) { expiresAt.toZonedDateTime().format(DateTimeFormatter.ISO_LOCAL_DATE) },
                    )
                },
            ) {
                expectSuccess = true
                headers.apply {
                    append(HttpHeaders.ContentType, ContentType.Application.FormUrlEncoded.toString())
                    append(HttpHeaders.Accept, ContentType.Application.Json.toString())
                    append("X-API-Key", apiKey.value)
                }
            }.body<Status>()
            .statusList
}
