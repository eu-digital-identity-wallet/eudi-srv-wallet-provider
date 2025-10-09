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
package eu.europa.ec.eudi.walletprovider.adapter.serialization

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.net.URI
import java.net.URL

class UriStringSerializer : KSerializer<URI> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("UriStringSerializer", PrimitiveKind.STRING)

    override fun serialize(
        encoder: Encoder,
        value: URI,
    ) {
        encoder.encodeString(value.toString())
    }

    override fun deserialize(decoder: Decoder): URI = URI.create(decoder.decodeString())
}

class UrlStringSerializer : KSerializer<URL> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("UrlStringSerializer", PrimitiveKind.STRING)

    override fun serialize(
        encoder: Encoder,
        value: URL,
    ) {
        encoder.encodeString(value.toString())
    }

    override fun deserialize(decoder: Decoder): URL = URI.create(decoder.decodeString()).toURL()
}
