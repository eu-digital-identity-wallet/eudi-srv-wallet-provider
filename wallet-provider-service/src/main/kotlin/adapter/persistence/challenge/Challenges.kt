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

import eu.europa.ec.eudi.walletprovider.domain.challenge.Challenge
import eu.europa.ec.eudi.walletprovider.domain.challenge.ChallengeRepository
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.dao.id.ULongIdTable
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.vendors.ForUpdateOption
import org.jetbrains.exposed.v1.datetime.timestamp
import org.jetbrains.exposed.v1.r2dbc.insert
import org.jetbrains.exposed.v1.r2dbc.selectAll
import kotlin.time.Instant

object Challenges : ULongIdTable(name = "challenges", columnName = "id") {
    val value: Column<ByteArray> = binary("value", Challenge.MAX_LENGTH).uniqueIndex("challenges_value_unique_idx")
    val createdAt: Column<Instant> = timestamp("created_at")
    val expiresAt: Column<Instant> = timestamp("expires_at")
    val unused: Column<Boolean> = bool("unused")
}

fun ChallengeRepository(forUpdateOption: ForUpdateOption): ChallengeRepository =
    object : ChallengeRepository {
        override suspend fun store(challenge: Challenge) {
            Challenges.insert {
                it[value] = challenge.value
                it[createdAt] = challenge.createdAt
                it[expiresAt] = challenge.expiresAt
                it[unused] = challenge.unused
            }
        }

        override suspend fun findByValueAndLock(value: ByteArray): Challenge? =
            Challenges
                .selectAll()
                .forUpdate(forUpdateOption)
                .where { Challenges.value.eq(value) }
                .map {
                    Challenge(
                        it[Challenges.value],
                        createdAt = it[Challenges.createdAt],
                        expiresAt = it[Challenges.expiresAt],
                        it[Challenges.unused],
                    )
                }.firstOrNull()
    }
