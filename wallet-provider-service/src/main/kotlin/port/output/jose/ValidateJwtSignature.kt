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
package eu.europa.ec.eudi.walletprovider.port.output.jose

import arrow.core.Either
import at.asitplus.signum.indispensable.josef.JwsSigned
import eu.europa.ec.eudi.walletprovider.domain.NonBlankString

fun interface ValidateJwtSignature<T : Any> {
    suspend operator fun invoke(unvalidated: String): Either<JwtSignatureValidationFailure, JwsSigned<T>>
}

sealed interface JwtSignatureValidationFailure {
    class UnparsableJwt(
        val error: NonBlankString,
        val cause: Throwable? = null,
    ) : JwtSignatureValidationFailure

    class InvalidSignature(
        val error: NonBlankString,
        val cause: Throwable? = null,
    ) : JwtSignatureValidationFailure
}
