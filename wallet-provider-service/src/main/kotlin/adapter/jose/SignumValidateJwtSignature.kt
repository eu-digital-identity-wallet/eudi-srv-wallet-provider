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

import arrow.core.Either
import arrow.core.raise.catch
import arrow.core.raise.either
import at.asitplus.signum.indispensable.josef.JwsSigned
import at.asitplus.signum.supreme.sign.Signer
import at.asitplus.signum.supreme.sign.Verifier
import at.asitplus.signum.supreme.sign.verifierFor
import at.asitplus.signum.supreme.sign.verify
import eu.europa.ec.eudi.walletprovider.domain.toNonBlankString
import eu.europa.ec.eudi.walletprovider.port.output.jose.JwtSignatureValidationFailure
import eu.europa.ec.eudi.walletprovider.port.output.jose.ValidateJwtSignature
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer

class SignumValidateJwtSignature<T : Any>(
    private val verifier: Verifier,
    private val deserializer: DeserializationStrategy<T>,
    private val json: Json,
) : ValidateJwtSignature<T> {
    override suspend fun invoke(unvalidated: String): Either<JwtSignatureValidationFailure, JwsSigned<T>> =
        either {
            val parsed =
                JwsSigned
                    .deserialize(unvalidated)
                    .getOrElse {
                        raise(
                            JwtSignatureValidationFailure.UnparsableJwt(
                                "Jwt cannot be parsed".toNonBlankString(),
                                it,
                            ),
                        )
                    }

            verifier
                .verify(parsed.plainSignatureInput, parsed.signature)
                .getOrElse {
                    raise(
                        JwtSignatureValidationFailure.InvalidSignature(
                            "Jwt signature is invalid".toNonBlankString(),
                            it,
                        ),
                    )
                }

            val claims =
                catch({
                    json.decodeFromString(deserializer, parsed.payload.decodeToString())
                }) {
                    raise(
                        JwtSignatureValidationFailure.UnparsableJwt(
                            "Attestation payload cannot be parsed".toNonBlankString(),
                            it,
                        ),
                    )
                }

            JwsSigned(
                header = parsed.header,
                payload = claims,
                plainSignatureInput = parsed.plainSignatureInput,
                signature = parsed.signature,
            )
        }

    companion object {
        inline operator fun <reified T : Any> invoke(
            verifier: Verifier,
            json: Json,
        ): SignumValidateJwtSignature<T> = SignumValidateJwtSignature(verifier, serializer<T>(), json)

        inline operator fun <reified T : Any> invoke(
            signer: Signer,
            json: Json,
        ): SignumValidateJwtSignature<T> =
            SignumValidateJwtSignature(
                signer.signatureAlgorithm.verifierFor(signer.publicKey).getOrThrow(),
                serializer<T>(),
                json,
            )
    }
}
