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
package eu.europa.ec.eudi.walletprovider.adapter.jose

import at.asitplus.signum.indispensable.josef.*
import at.asitplus.signum.indispensable.pki.CertificateChain
import at.asitplus.signum.supreme.sign.Signer
import at.asitplus.signum.supreme.signature
import eu.europa.ec.eudi.walletprovider.domain.JwtType
import eu.europa.ec.eudi.walletprovider.port.output.jose.SignJwt
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer

class SignumSignJwt<T : Any>(
    private val signer: Signer,
    private val certificateChain: CertificateChain?,
    private val type: JwtType,
    private val serializer: SerializationStrategy<T>,
    private val json: Json,
) : SignJwt<T> {
    override val signingAlgorithm: JwsAlgorithm = signer.signatureAlgorithm.toJwsAlgorithm().getOrThrow()

    override suspend fun invoke(claims: T): JwsSigned<T> {
        val header =
            JwsHeader(
                algorithm = signer.signatureAlgorithm.toJwsAlgorithm().getOrThrow(),
                certificateChain = certificateChain?.takeIf { it.isNotEmpty() },
                jsonWebKey =
                    if (certificateChain.isNullOrEmpty())
                        signer.publicKey.toJsonWebKey()
                    else
                        null,
                type = type.value,
            )
        val plainSignatureInput =
            JwsSigned.prepareJwsSignatureInput(
                header = header,
                payload = claims,
                serializer = serializer,
                json = json,
            )
        val signature = signer.sign(plainSignatureInput).signature
        return JwsSigned(header, claims, signature, plainSignatureInput)
    }

    companion object {
        inline operator fun <reified T : Any> invoke(
            signer: Signer,
            certificateChain: CertificateChain?,
            type: JwtType,
            json: Json,
        ): SignumSignJwt<T> = SignumSignJwt(signer, certificateChain, type, serializer<T>(), json)
    }
}
