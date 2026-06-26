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
 * [OpenID for Verifiable Credential Issuance 1.0](https://openid.net/specs/openid-4-verifiable-credential-issuance-1_0.html)
 */
object OpenId4VCI {
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
