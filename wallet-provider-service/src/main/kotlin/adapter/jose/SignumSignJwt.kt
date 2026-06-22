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

import at.asitplus.signum.indispensable.josef.*
import at.asitplus.signum.indispensable.pki.CertificateChain
import at.asitplus.signum.supreme.sign.Signer
import at.asitplus.signum.supreme.signature
import eu.europa.ec.eudi.walletprovider.domain.JwtType
import eu.europa.ec.eudi.walletprovider.port.output.jose.SignJwt

inline fun <reified T : Any> SignJwt(
    signer: Signer,
    certificateChain: CertificateChain?,
    type: JwtType,
): SignJwt<T> =
    object : SignJwt<T> {
        override val signingAlgorithm: JwsAlgorithm
            get() = signer.signatureAlgorithm.toJwsAlgorithm().getOrThrow()

        override suspend fun invoke(claims: T): JwsCompactTyped<T> {
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

            return JwsCompactTyped<T>(protectedHeader = header, payload = claims) {
                signer.sign(it).signature.rawByteArray
            }
        }
    }
