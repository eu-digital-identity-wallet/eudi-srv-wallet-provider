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
package eu.europa.ec.eudi.walletprovider.port.input.challenge

import eu.europa.ec.eudi.walletprovider.domain.PositiveDuration
import eu.europa.ec.eudi.walletprovider.domain.challenge.Challenge
import eu.europa.ec.eudi.walletprovider.domain.challenge.ChallengeRepository
import eu.europa.ec.eudi.walletprovider.domain.time.Clock
import eu.europa.ec.eudi.walletprovider.port.output.persistence.RunInTransaction
import java.security.SecureRandom
import kotlin.random.asKotlinRandom
import kotlin.time.Duration

fun interface GenerateChallenge {
    suspend operator fun invoke(): Challenge
}

class GenerateChallengeLive(
    private val clock: Clock,
    private val length: Length,
    private val validity: PositiveDuration,
    private val runInTransaction: RunInTransaction,
    private val challengeRepository: ChallengeRepository,
) : GenerateChallenge {
    private val secureRandom = SecureRandom().asKotlinRandom()

    override suspend operator fun invoke(): Challenge {
        val createdAt = clock.now()
        val expiresAt = createdAt + validity.value
        return Challenge(
            secureRandom.nextBytes(length.value.toInt()),
            createdAt = createdAt,
            expiresAt = expiresAt,
            false,
        ).also {
            runInTransaction {
                challengeRepository.store(it)
            }
        }
    }
}

@JvmInline
value class Length(
    val value: UInt,
) {
    init {
        require(value.toInt() in Challenge.MIN_LENGTH..Challenge.MAX_LENGTH) {
            "value must be between ${Challenge.MIN_LENGTH} and ${Challenge.MAX_LENGTH}"
        }
    }

    override fun toString(): String = value.toString()
}
