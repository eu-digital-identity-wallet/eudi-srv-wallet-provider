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
package eu.europa.ec.eudi.walletprovider.port.output.tokenstatuslist

import arrow.core.Either
import eu.europa.ec.eudi.walletprovider.domain.NonBlankString
import eu.europa.ec.eudi.walletprovider.domain.tokenstatuslist.StatusListToken
import kotlin.time.Instant

fun interface GenerateStatusListToken {
    suspend operator fun invoke(expiresAt: Instant): Either<StatusListTokenGenerationFailure, StatusListToken>
}

sealed interface StatusListTokenGenerationFailure {
    class Unexpected(
        val error: NonBlankString,
        val cause: Throwable? = null,
    ) : StatusListTokenGenerationFailure
}
