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

import arrow.core.raise.catch
import arrow.core.raise.either
import at.asitplus.signum.indispensable.josef.JwsCompactTyped
import at.asitplus.signum.supreme.sign.Signer
import at.asitplus.signum.supreme.sign.Verifier
import at.asitplus.signum.supreme.sign.verifierFor
import at.asitplus.signum.supreme.sign.verify
import eu.europa.ec.eudi.walletprovider.domain.toNonBlankString
import eu.europa.ec.eudi.walletprovider.port.output.jose.JwtSignatureValidationFailure
import eu.europa.ec.eudi.walletprovider.port.output.jose.ValidateJwtSignature

inline fun <reified T : Any> ValidateJwtSignature(verifier: Verifier): ValidateJwtSignature<T> =
    ValidateJwtSignature { unvalidated ->
        either {
            val parsed =
                catch({
                    JwsCompactTyped<T>(unvalidated)
                }) {
                    raise(
                        JwtSignatureValidationFailure.UnparsableJwt(
                            "Jwt cannot be parsed".toNonBlankString(),
                            it,
                        ),
                    )
                }

            verifier
                .verify(parsed.jws.signatureInput, parsed.jws.signature)
                .getOrElse {
                    raise(
                        JwtSignatureValidationFailure.InvalidSignature(
                            "Jwt signature is invalid".toNonBlankString(),
                            it,
                        ),
                    )
                }

            parsed
        }
    }

inline fun <reified T : Any> ValidateJwtSignature(singer: Signer): ValidateJwtSignature<T> =
    ValidateJwtSignature(singer.signatureAlgorithm.verifierFor(singer.publicKey).getOrThrow())
