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
package eu.europa.ec.eudi.walletprovider.domain

import at.asitplus.signum.indispensable.io.ByteArrayBase64UrlSerializer
import at.asitplus.signum.indispensable.io.InstantLongSerializer
import eu.europa.ec.eudi.walletprovider.adapter.serialization.DurationSecondsSerializer
import eu.europa.ec.eudi.walletprovider.adapter.serialization.UriStringSerializer
import eu.europa.ec.eudi.walletprovider.adapter.serialization.UrlStringSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.net.URI
import java.net.URL
import kotlin.time.Duration
import kotlin.time.Instant

typealias Base64UrlSafeByteArray =
    @Serializable(with = ByteArrayBase64UrlSerializer::class)
    ByteArray

typealias EpochSecondsInstant =
    @Serializable(with = InstantLongSerializer::class)
    Instant

@JvmInline
@Serializable
value class NonBlankString(
    val value: String,
) {
    init {
        require(value.isNotBlank()) { "value must not be blank" }
    }

    override fun toString(): String = value
}

fun String.toNonBlankString() = NonBlankString(this)

typealias ClientId = NonBlankString

typealias StringUri =
    @Serializable(with = UriStringSerializer::class)
    URI

typealias StringUrl =
    @Serializable(with = UrlStringSerializer::class)
    URL

@JvmInline
@Serializable
value class Issuer(
    val value: StringUrl,
) {
    init {
        require(value.toExternalForm().substringAfter(delimiter = "#", missingDelimiterValue = "").isEmpty())
    }

    companion object {
        fun create(value: String): Issuer = Issuer(URI.create(value).toURL())
    }
}

typealias Name = NonBlankString

typealias JwtType = NonBlankString

typealias SecondsDuration =
    @Serializable(with = DurationSecondsSerializer::class)
    Duration

@JvmInline
value class PositiveDuration(
    val value: Duration,
) {
    init {
        require(value.isPositive()) { "value must be positive" }
    }

    override fun toString(): String = value.toString()
}

@JvmInline
@Serializable
value class CertificationInformation(
    val value: JsonElement,
) {
    init {
        require((value is JsonPrimitive && value.isString && value.content.isNotBlank()) || value is JsonObject)
    }

    override fun toString(): String = value.toString()
}
