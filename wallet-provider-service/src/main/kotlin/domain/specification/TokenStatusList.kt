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
package eu.europa.ec.eudi.walletprovider.domain.specification

/**
 * [Token Status List (TSL)](https://www.ietf.org/archive/id/draft-ietf-oauth-status-list-12.html)
 */
object TokenStatusList {
    const val STATUS: String = "status"
    const val STATUS_LIST: String = "status_list"
    const val INDEX: String = "idx"
    const val URI: String = "uri"
}
