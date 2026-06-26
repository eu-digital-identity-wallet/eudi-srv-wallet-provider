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

import arrow.core.NonEmptyList
import arrow.core.serialization.NonEmptyListSerializer
import at.asitplus.signum.indispensable.CryptoPublicKey
import at.asitplus.signum.indispensable.io.ByteArrayBase64UrlSerializer
import at.asitplus.signum.indispensable.io.InstantLongSerializer
import com.eygraber.uri.Url
import eu.europa.ec.eudi.walletprovider.adapter.serialization.DurationSecondsSerializer
import eu.europa.ec.eudi.walletprovider.adapter.serialization.ECCryptoPublicKeyJsonWebKeySerializer
import eu.europa.ec.eudi.walletprovider.domain.specification.RFC7517
import eu.europa.ec.eudi.walletprovider.domain.specification.RFC7800
import kotlinx.serialization.Required
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
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

@JvmInline
@Serializable
value class Issuer(
    val value: Url,
) {
    init {
        require(value.toString().substringAfter(delimiter = "#", missingDelimiterValue = "").isEmpty())
    }

    companion object {
        fun create(value: String): Issuer = Issuer(Url.parse(value))
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

typealias CertificationInformation = JsonElement

typealias JsonWebKeyECCryptoPublicKey =
    @Serializable(with = ECCryptoPublicKeyJsonWebKeySerializer::class)
    CryptoPublicKey.EC

@Serializable
data class JsonWebKeySet(
    @Required
    @SerialName(RFC7517.KEYS)
    @Serializable(with = NonEmptyListSerializer::class)
    val keys: NonEmptyList<JsonWebKeyECCryptoPublicKey>,
)

@Serializable
data class Confirmation(
    @Required @SerialName(RFC7800.JWK) val jwk: JsonWebKeyECCryptoPublicKey,
)
