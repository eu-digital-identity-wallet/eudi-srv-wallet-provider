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
package eu.europa.ec.eudi.walletprovider.adapter.persistence

import org.jetbrains.exposed.v1.core.vendors.ForUpdateOption
import org.jetbrains.exposed.v1.core.vendors.H2Dialect
import org.jetbrains.exposed.v1.core.vendors.MariaDBDialect
import org.jetbrains.exposed.v1.core.vendors.MysqlDialect
import org.jetbrains.exposed.v1.core.vendors.OracleDialect
import org.jetbrains.exposed.v1.core.vendors.PostgreSQLDialect
import org.jetbrains.exposed.v1.core.vendors.SQLServerDialect
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabase

val R2dbcDatabase.forUpdateOption: ForUpdateOption
    get() =
        when (dialect) {
            is H2Dialect -> ForUpdateOption.ForUpdate
            is MariaDBDialect -> ForUpdateOption.ForUpdate
            is MysqlDialect -> ForUpdateOption.MySQL.ForUpdate(mode = ForUpdateOption.MySQL.MODE.NO_WAIT)
            is OracleDialect -> ForUpdateOption.Oracle.ForUpdateNoWait
            is PostgreSQLDialect -> ForUpdateOption.PostgreSQL.ForUpdate(mode = ForUpdateOption.PostgreSQL.MODE.NO_WAIT)
            is SQLServerDialect -> ForUpdateOption.ForUpdate
            else -> error("Unsupported database dialect: $dialect")
        }
