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
package eu.europa.ec.eudi.walletprovider.adapter.jose

import arrow.core.NonEmptyList
import at.asitplus.signum.indispensable.josef.JwsAlgorithm
import at.asitplus.signum.indispensable.josef.JwsCompactTyped
import at.asitplus.signum.indispensable.josef.JwsHeader
import at.asitplus.signum.indispensable.josef.toJwsAlgorithm
import at.asitplus.signum.indispensable.pki.X509Certificate
import at.asitplus.signum.supreme.sign.Signer
import at.asitplus.signum.supreme.signature
import eu.europa.ec.eudi.walletprovider.domain.JwtType
import eu.europa.ec.eudi.walletprovider.port.output.jose.SignJwt

class SignumSignJwt<T : Any> private constructor(
    override val signingAlgorithm: JwsAlgorithm,
    private val certificateChain: NonEmptyList<X509Certificate>,
    private val type: JwtType,
    private val sign: suspend (JwsHeader, T) -> JwsCompactTyped<T>,
) : SignJwt<T> {
    override suspend fun invoke(claims: T): JwsCompactTyped<T> {
        val header =
            JwsHeader(
                algorithm = signingAlgorithm,
                certificateChain = certificateChain,
                type = type.value,
            )

        return sign(header, claims)
    }

    companion object {
        internal inline operator fun <reified T : Any> invoke(
            signer: Signer,
            certificateChain: NonEmptyList<X509Certificate>,
            type: JwtType,
        ): SignumSignJwt<T> =
            SignumSignJwt(
                signer.signatureAlgorithm.toJwsAlgorithm().getOrElse {
                    throw IllegalArgumentException("signer is not using a JwsAlgorithm", it)
                },
                certificateChain,
                type,
            ) { header, claims ->
                JwsCompactTyped<T>(protectedHeader = header, payload = claims) {
                    signer.sign(it).signature.rawByteArray
                }
            }
    }
}
