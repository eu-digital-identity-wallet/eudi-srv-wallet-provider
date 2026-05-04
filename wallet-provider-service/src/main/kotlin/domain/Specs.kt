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
package eu.europa.ec.eudi.walletprovider.domain

import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours

object AttestationBasedClientAuthenticationSpec {
    const val CLIENT_ATTESTATION_JWT_TYPE: String = "oauth-client-attestation+jwt"

    const val CLIENT_ATTESTATION_SIGNING_ALGORITHMS_SUPPORTED: String = "client_attestation_signing_alg_values_supported"
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
    val MIN_KEY_ATTESTATION_VALIDITY: Duration = 31.days
    val MAX_WALLET_INSTANCE_ATTESTATION_VALIDITY: Duration = 24.hours

    const val PREFERRED_TTL: String = "preferred_ttl"
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

    const val PROOF_SIGNING_ALGORITHMS_SUPPORTED: String = "proof_signing_alg_values_supported"
}

/**
 * [OAuth 2.0 Protected Resource Metadata](https://www.rfc-editor.org/rfc/rfc9728.html)
 */
object RFC9728 {
    const val WELL_KNOWN_URI_SUFFIX: String = "/.well-known/oauth-protected-resource"

    const val RESOURCE: String = "resource"
    const val JWKS_URI: String = "jwks_uri"
    const val RESOURCE_NAME: String = "resource_name"
    const val RESOURCE_SIGNING_ALGORITHMS_SUPPORTED: String = "resource_signing_alg_values_supported"
}

/**
 * Custom fields not part of any standard specification
 */
object CustomFields {
    const val WALLET_METADATA: String = "wallet_metadata"
}

/**
 * [Specification of Wallet Unit Attestations (WUA) used in issuance of PID and Attestations](https://github.com/eu-digital-identity-wallet/eudi-doc-standards-and-technical-specifications/blob/main/docs/technical-specifications/ts3-wallet-unit-attestation.md)
 */
object TS3 {
    const val WALLET_VERSION: String = "wallet_version"
    const val WALLET_SOLUTION_CERTIFICATION_INFORMATION: String = "wallet_solution_certification_information"
    const val CLIENT_STATUS: String = "client_status"
    const val KEY_STORAGE_STATUS: String = "key_storage_status"
}
