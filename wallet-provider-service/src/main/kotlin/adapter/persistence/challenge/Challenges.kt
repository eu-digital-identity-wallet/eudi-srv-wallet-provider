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
package eu.europa.ec.eudi.walletprovider.adapter.persistence.challenge

import eu.europa.ec.eudi.walletprovider.domain.Challenge
import eu.europa.ec.eudi.walletprovider.port.output.persistence.challenge.IsChallengeActive
import eu.europa.ec.eudi.walletprovider.port.output.persistence.challenge.MarkChallengeInactive
import eu.europa.ec.eudi.walletprovider.port.output.persistence.challenge.StoreActiveChallenge
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.core.dao.id.ULongIdTable
import org.jetbrains.exposed.v1.core.vendors.ForUpdateOption
import org.jetbrains.exposed.v1.datetime.timestamp
import org.jetbrains.exposed.v1.r2dbc.insert
import org.jetbrains.exposed.v1.r2dbc.select
import org.jetbrains.exposed.v1.r2dbc.update
import kotlin.time.Instant

object Challenges : ULongIdTable(name = "challenges", columnName = "id", sequenceName = "challenge_id") {
    val value: Column<ByteArray> = binary("value", Challenge.MAX_LENGTH).uniqueIndex("challenges_value_unique_idx")
    val createdAt: Column<Instant> = timestamp("created_at")
    val expiresAt: Column<Instant> = timestamp("expires_at")
    val active: Column<Boolean> = bool("active")

    init {
        index("challenges_idx_1", false, createdAt, expiresAt, active)
    }
}

val StoreActiveChallengeLive =
    StoreActiveChallenge { challenge, createdAt, expiresAt ->
        Challenges.insert {
            it[value] = challenge.value
            it[Challenges.createdAt] = createdAt
            it[Challenges.expiresAt] = expiresAt
            it[active] = true
        }
    }

val IsChallengeActiveLive =
    IsChallengeActive { challenge, at ->
        val activeChallengeId =
            Challenges
                .select(Challenges.id)
                .forUpdate(ForUpdateOption.ForUpdate)
                .where {
                    (Challenges.value eq challenge.value) and
                        (Challenges.createdAt greaterEq at) and
                        (Challenges.expiresAt less at) and
                        (Challenges.active eq true)
                }.map { it[Challenges.id] }
                .firstOrNull()
        null != activeChallengeId
    }

val MarkChallengeInactiveLive =
    MarkChallengeInactive { challenge ->
        Challenges.update({ Challenges.value eq challenge.value }) {
            it[active] = false
        }
    }
