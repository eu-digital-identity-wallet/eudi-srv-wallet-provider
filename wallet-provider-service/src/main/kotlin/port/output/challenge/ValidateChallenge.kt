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
package eu.europa.ec.eudi.walletprovider.port.output.challenge

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import arrow.core.raise.ensureNotNull
import arrow.core.right
import eu.europa.ec.eudi.walletprovider.domain.NonBlankString
import eu.europa.ec.eudi.walletprovider.domain.challenge.ChallengeRepository
import eu.europa.ec.eudi.walletprovider.domain.challenge.isActive
import eu.europa.ec.eudi.walletprovider.domain.toNonBlankString
import eu.europa.ec.eudi.walletprovider.port.output.persistence.RunInTransaction
import kotlin.time.Instant

fun interface ValidateChallenge {
    suspend operator fun invoke(
        value: ByteArray,
        at: Instant,
    ): Either<ChallengeValidationFailure, Unit>
}

class ChallengeValidationFailure(
    val error: NonBlankString,
    val cause: Throwable? = null,
)

class ValidateChallengeLive(
    private val runInTransaction: RunInTransaction,
    private val challengeRepository: ChallengeRepository,
) : ValidateChallenge {
    override suspend fun invoke(
        value: ByteArray,
        at: Instant,
    ): Either<ChallengeValidationFailure, Unit> =
        either {
            runInTransaction {
                val challenge =
                    ensureNotNull(challengeRepository.findByValueAndLock(value)) {
                        ChallengeValidationFailure("Challenge is not valid.".toNonBlankString())
                    }
                ensure(challenge.unused) {
                    ChallengeValidationFailure("Challenge has already been used.".toNonBlankString())
                }
                ensure(challenge.isActive(at)) {
                    ChallengeValidationFailure("Challenge is not active.".toNonBlankString())
                }
                challengeRepository.store(challenge.copy(unused = false))
            }
        }
}

val ValidateChallengeNoop = ValidateChallenge { _, _ -> Unit.right() }
