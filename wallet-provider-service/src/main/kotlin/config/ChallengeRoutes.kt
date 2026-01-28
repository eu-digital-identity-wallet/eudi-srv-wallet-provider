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
package eu.europa.ec.eudi.walletprovider.config

import eu.europa.ec.eudi.walletprovider.domain.Challenge
import eu.europa.ec.eudi.walletprovider.port.input.challenge.GenerateChallenge
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("ChallengeRoutes")

fun Application.configureChallengeRoutes(generateChallenge: GenerateChallenge) {
    routing {
        route("/challenge") {
            post {
                val challenge = generateChallenge()
                logger.info("Generated new Challenge: {}", challenge)

                call.respond(HttpStatusCode.OK, ChallengeResponse(challenge))
            }
        }
    }
}

@Serializable
private data class ChallengeResponse(
    val challenge: Challenge,
)
