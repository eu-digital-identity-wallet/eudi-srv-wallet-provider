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

import eu.europa.ec.eudi.walletprovider.domain.Base64UrlSafeByteArray
import eu.europa.ec.eudi.walletprovider.domain.Challenge
import eu.europa.ec.eudi.walletprovider.domain.EpochSecondsInstant
import eu.europa.ec.eudi.walletprovider.domain.RFC7519
import eu.europa.ec.eudi.walletprovider.domain.time.Clock
import eu.europa.ec.eudi.walletprovider.port.output.jose.SignJwt
import kotlinx.serialization.Required
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
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
    private val signJwt: SignJwt<ChallengeClaims>,
) : GenerateChallenge {
    private val secureRandom = SecureRandom().asKotlinRandom()

    override suspend operator fun invoke(): Challenge {
        val now = clock.now()
        val expiresAt = now + validity.value
        val challengeClaims =
            ChallengeClaims(
                challenge = secureRandom.nextBytes(length.value.toInt()),
                notBefore = now,
                expiresAt = expiresAt,
            )
        val challengeJwt = signJwt(challengeClaims)
        return Challenge(challengeJwt.serialize().encodeToByteArray())
    }

    companion object {
        const val CHALLENGE_JWT_TYPE = "challenge+jwt"
    }

    @Serializable
    data class ChallengeClaims(
        @Required @SerialName(CHALLENGE) val challenge: Base64UrlSafeByteArray,
        @Required @SerialName(RFC7519.NOT_BEFORE) val notBefore: EpochSecondsInstant,
        @Required @SerialName(RFC7519.EXPIRES_AT) val expiresAt: EpochSecondsInstant,
    ) {
        companion object {
            const val CHALLENGE: String = "challenge"
        }

        override fun equals(other: Any?): Boolean =
            other is ChallengeClaims &&
                challenge.contentEquals(other.challenge) &&
                notBefore == other.notBefore &&
                expiresAt == other.expiresAt

        override fun hashCode(): Int = 31 * (31 * challenge.contentHashCode() + notBefore.hashCode()) + expiresAt.hashCode()
    }
}

@JvmInline
value class Length(
    val value: UInt,
) {
    init {
        require(value > 0u) { "value must be greater than 0" }
    }

    override fun toString(): String = value.toString()
}

@JvmInline
value class PositiveDuration(
    val value: Duration,
) {
    init {
        require(value.isPositive()) { "value must be positive" }
    }

    override fun toString(): String = value.toString()
}
