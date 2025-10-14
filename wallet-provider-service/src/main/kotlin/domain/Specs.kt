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
package eu.europa.ec.eudi.walletprovider.domain

import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours

object AttestationBasedClientAuthenticationSpec {
    const val CLIENT_ATTESTATION_JWT_TYPE: String = "oauth-client-attestation+jwt"
}

object RFC7519 {
    const val TYPE: String = "typ"
    const val CONTENT_TYPE: String = "cty"

    const val ISSUER: String = "iss"
    const val SUBJECT: String = "sub"
    const val EXPIRES_AT: String = "exp"
    const val ISSUED_AT: String = "iat"
    const val NOT_BEFORE: String = "nbf"
}

object RFC7800 {
    const val CONFIRMATION: String = "cnf"
}

object ARF {
    const val EUDI_WALLET_INFORMATION: String = "eudi_wallet_info"

    const val GENERAL_INFORMATION: String = "general_info"
    const val WALLET_PROVIDER_NAME: String = "wallet_provider_name"
    const val WALLET_SOLUTION_ID: String = "wallet_solution_id"
    const val WALLET_SOLUTION_VERSION: String = "wallet_solution_version"
    const val WALLET_SOLUTION_CERTIFICATION_INFORMATION: String = "wallet_solution_certification_information"
    val MAX_WALLET_APPLICATION_ATTESTATION_VALIDITY: Duration = 24.hours
    val MIN_WALLET_UNIT_ATTESTATION_VALIDITY: Duration = 31.days

    const val WALLET_SECURE_CRYPTOGRAPHIC_DEVICE_INFORMATION: String = "wscd_info"
    const val WALLET_SECURE_CRYPTOGRAPHIC_DEVICE_TYPE: String = "wscd_type"
    const val WALLET_SECURE_CRYPTOGRAPHIC_DEVICE_TYPE_REMOTE: String = "REMOTE"
    const val WALLET_SECURE_CRYPTOGRAPHIC_DEVICE_TYPE_LOCAL_EXTERNAL: String = "LOCAL_EXTERNAL"
    const val WALLET_SECURE_CRYPTOGRAPHIC_DEVICE_TYPE_LOCAL_INTERNAL: String = "LOCAL_INTERNAL"
    const val WALLET_SECURE_CRYPTOGRAPHIC_DEVICE_TYPE_LOCAL_NATIVE: String = "LOCAL_NATIVE"
    const val WALLET_SECURE_CRYPTOGRAPHIC_DEVICE_TYPE_HYBRID: String = "HYBRID"
    const val WALLET_SECURE_CRYPTOGRAPHIC_CERTIFICATION_INFORMATION: String = "wscd_certification_information"
}

object TokenStatusListSpec {
    const val STATUS: String = "status"
    const val STATUS_LIST: String = "status_list"
    const val INDEX: String = "idx"
    const val URI: String = "uri"
}

object OpenId4VCISpec {
    const val KEY_ATTESTATION_JWT_TYPE: String = "key-attestation+jwt"

    const val WALLET_NAME: String = "wallet_name"
    const val WALLET_LINK: String = "wallet_link"

    const val ATTESTED_KEYS: String = "attested_keys"
    const val KEY_STORAGE: String = "key_storage"
    const val USER_AUTHENTICATION: String = "user_authentication"
    const val CERTIFICATION: String = "certification"
    const val NONCE: String = "nonce"

    const val ATTACK_POTENTIAL_RESISTANCE_ISO_18045_HIGH: String = "iso_18045_high"
    const val ATTACK_POTENTIAL_RESISTANCE_ISO_18045_MODERATE: String = "iso_18045_moderate"
    const val ATTACK_POTENTIAL_RESISTANCE_ISO_18045_ENHANCED_BASIC: String = "iso_18045_enhanced-basic"
    const val ATTACK_POTENTIAL_RESISTANCE_ISO_18045_BASIC: String = "iso_18045_basic"
}
