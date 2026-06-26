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

import arrow.core.NonEmptySet
import arrow.core.toNonEmptySetOrThrow
import at.asitplus.signum.indispensable.josef.JwsAlgorithm
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours

/**
 * [Specification of Wallet Unit Attestations (WUA) used in issuance of PID and Attestations](https://github.com/eu-digital-identity-wallet/eudi-doc-standards-and-technical-specifications/blob/main/docs/technical-specifications/ts3-wallet-unit-attestation.md)
 */
object TS3 {
    const val WALLET_VERSION: String = "wallet_version"
    const val WALLET_SOLUTION_CERTIFICATION_INFORMATION: String = "wallet_solution_certification_information"
    const val CLIENT_STATUS: String = "client_status"
    const val KEY_STORAGE_STATUS: String = "key_storage_status"
    val ALLOWED_SIGNATURE_ALGORITHMS: NonEmptySet<JwsAlgorithm.Signature.EC> =
        JwsAlgorithm.Signature.EC
            .entries
            .toNonEmptySetOrThrow()
    val MIN_KEY_ATTESTATION_VALIDITY: Duration = 31.days
    val MAX_WALLET_INSTANCE_ATTESTATION_VALIDITY: Duration = 24.hours
}
