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

import arrow.fx.coroutines.ResourceScope
import eu.europa.ec.eudi.walletprovider.adapter.persistence.challenge.Challenges
import io.r2dbc.spi.IsolationLevel
import org.jetbrains.exposed.v1.migration.r2dbc.MigrationUtils
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabase
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabaseConfig
import org.jetbrains.exposed.v1.r2dbc.transactions.TransactionManager
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction

context(resources: ResourceScope)
internal suspend fun DatabaseConfiguration.connect(): R2dbcDatabase {
    val database =
        resources.install({
            R2dbcDatabase.connect(
                url = url.value,
                user = username.orEmpty(),
                password = password?.value.orEmpty(),
                databaseConfig =
                    R2dbcDatabaseConfig {
                        defaultR2dbcIsolationLevel = IsolationLevel.REPEATABLE_READ
                    },
            )
        }) { database, _ -> TransactionManager.closeAndUnregister(database) }

    val migrations =
        suspendTransaction {
            MigrationUtils.statementsRequiredForDatabaseMigration(
                Challenges,
                withLogs = true,
            )
        }
    check(migrations.isEmpty()) {
        "Database is not up to date. The following migration are required: \n\t${migrations.joinToString(separator = "\n\t") { "'$it'" }}"
    }

    return database
}
