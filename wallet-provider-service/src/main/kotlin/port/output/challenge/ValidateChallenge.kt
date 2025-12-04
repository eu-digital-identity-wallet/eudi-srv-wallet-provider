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
package eu.europa.ec.eudi.walletprovider.port.output.challenge

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import arrow.core.right
import eu.europa.ec.eudi.walletprovider.domain.Challenge
import eu.europa.ec.eudi.walletprovider.domain.NonBlankString
import eu.europa.ec.eudi.walletprovider.domain.RFC7519
import eu.europa.ec.eudi.walletprovider.domain.toNonBlankString
import eu.europa.ec.eudi.walletprovider.port.input.challenge.GenerateChallengeLive
import eu.europa.ec.eudi.walletprovider.port.output.jose.JwtSignatureValidationFailure
import eu.europa.ec.eudi.walletprovider.port.output.jose.ValidateJwtSignature
import kotlin.time.Instant

fun interface ValidateChallenge {
    suspend operator fun invoke(
        challenge: Challenge,
        at: Instant,
    ): Either<ChallengeValidationFailure, Unit>
}

class ChallengeValidationFailure(
    val error: NonBlankString,
    val cause: Throwable? = null,
)

class ValidateChallengeLive(
    private val validateJwtSignature: ValidateJwtSignature<GenerateChallengeLive.ChallengeClaims>,
) : ValidateChallenge {
    override suspend fun invoke(
        challenge: Challenge,
        at: Instant,
    ): Either<ChallengeValidationFailure, Unit> =
        either {
            validateJwtSignature(challenge.value.decodeToString()).fold(
                ifLeft = {
                    val (error, cause) =
                        when (it) {
                            is JwtSignatureValidationFailure.InvalidSignature -> {
                                "Challenge is not valid: ${it.error}".toNonBlankString() to it.cause
                            }

                            is JwtSignatureValidationFailure.UnparsableJwt -> {
                                "Challenge is not valid: ${it.error}".toNonBlankString() to it.cause
                            }
                        }
                    raise(ChallengeValidationFailure(error, cause))
                },
                ifRight = { challengeJwt ->
                    ensure(challengeJwt.header.type == GenerateChallengeLive.CHALLENGE_JWT_TYPE) {
                        ChallengeValidationFailure(
                            (
                                "Challenge is not valid, contains invalid `${RFC7519.TYPE}`. " +
                                    "Expected: '${GenerateChallengeLive.CHALLENGE_JWT_TYPE}', " +
                                    "found: '${challengeJwt.header.type ?: ""}'."
                            ).toNonBlankString(),
                        )
                    }

                    val challengeClaims = challengeJwt.payload
                    ensure(challengeClaims.notBefore <= at) {
                        ChallengeValidationFailure("Challenge is not active yet.".toNonBlankString())
                    }

                    ensure(at < challengeClaims.expiresAt) {
                        ChallengeValidationFailure("Challenge is expired.".toNonBlankString())
                    }
                },
            )
        }
}

val ValidateChallengeNoop = ValidateChallenge { _, _ -> Unit.right() }
