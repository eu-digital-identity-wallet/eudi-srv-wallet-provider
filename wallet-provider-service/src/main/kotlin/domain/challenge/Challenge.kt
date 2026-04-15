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
package eu.europa.ec.eudi.walletprovider.domain.challenge

import kotlin.time.Instant

data class Challenge(
    val value: ByteArray,
    val createdAt: Instant,
    val expiresAt: Instant,
    val unused: Boolean,
) {
    init {
        require(value.size in MIN_LENGTH..MAX_LENGTH) { "value must be between $MIN_LENGTH and $MAX_LENGTH bytes long" }
        require(createdAt <= expiresAt) { "createdAt must be less or equal to expiresAt" }
    }

    override fun equals(other: Any?): Boolean =
        other is Challenge &&
            other.value.contentEquals(value) &&
            other.createdAt == createdAt &&
            other.expiresAt == expiresAt &&
            other.unused == unused

    override fun hashCode(): Int {
        var result = value.contentHashCode()
        result = 31 * result + createdAt.hashCode()
        result = 31 * result + expiresAt.hashCode()
        result = 31 * result + unused.hashCode()
        return result
    }

    companion object {
        const val MIN_LENGTH: Int = 32
        const val MAX_LENGTH: Int = 128
    }
}

fun Challenge.isActive(at: Instant): Boolean = at in createdAt..<expiresAt

interface ChallengeRepository {
    suspend fun store(challenge: Challenge)

    suspend fun findByValueAndLock(value: ByteArray): Challenge?
}
