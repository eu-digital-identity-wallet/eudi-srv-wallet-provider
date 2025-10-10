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
package eu.europa.ec.eudi.walletprovider.domain.time

import kotlinx.datetime.TimeZone
import kotlin.time.Instant

interface Clock {
    fun now(): Instant

    fun timeZone(): TimeZone

    companion object {
        val System: Clock =
            object : Clock {
                override fun now(): Instant =
                    kotlin.time.Clock.System
                        .now()

                override fun timeZone(): TimeZone = TimeZone.currentSystemDefault()
            }
    }
}

fun Clock.toKotlinClock(): kotlin.time.Clock =
    object : kotlin.time.Clock {
        override fun now(): Instant = this@toKotlinClock.now()
    }
