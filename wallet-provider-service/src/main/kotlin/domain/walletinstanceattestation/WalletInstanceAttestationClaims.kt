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
package eu.europa.ec.eudi.walletprovider.domain.walletinstanceattestation

import at.asitplus.signum.indispensable.josef.ConfirmationClaim
import at.asitplus.signum.indispensable.josef.JwsSigned
import eu.europa.ec.eudi.walletprovider.domain.*
import eu.europa.ec.eudi.walletprovider.domain.tokenstatuslist.Status
import eu.europa.ec.eudi.walletprovider.domain.walletinformation.GeneralInformation
import kotlinx.serialization.Required
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class WalletInstanceAttestationClaims(
    @Required @SerialName(RFC7519.ISSUER) val issuer: Issuer,
    @Required @SerialName(RFC7519.SUBJECT) val subject: ClientId,
    @Required @SerialName(RFC7519.EXPIRES_AT) val expiresAt: EpochSecondsInstant,
    @Required @SerialName(RFC7800.CONFIRMATION) val confirmation: ConfirmationClaim,
    @SerialName(RFC7519.ISSUED_AT) val issuedAt: EpochSecondsInstant? = null,
    @SerialName(RFC7519.NOT_BEFORE) val notBefore: EpochSecondsInstant? = null,
    @SerialName(OpenId4VCISpec.WALLET_NAME) val walletName: WalletName? = null,
    @SerialName(OpenId4VCISpec.WALLET_LINK) val walletLink: WalletLink? = null,
    @SerialName(TokenStatusListSpec.STATUS) val status: Status? = null,
    @Required @SerialName(ARF.EUDI_WALLET_INFORMATION) val walletInformation: WalletInformation,
    @SerialName(CustomFields.WALLET_METADATA) val walletMetadata: WalletMetadata? = null,
) {
    @Serializable
    data class WalletInformation(
        @Required @SerialName(ARF.GENERAL_INFORMATION) val generalInformation: GeneralInformation,
    )
}

typealias WalletName = NonBlankString
typealias WalletLink = StringUrl

/**
 * Custom metadata provided by the Wallet to be included in the issued Wallet Instance Attestation.
 *
 * Can be any valid JSON value (object, array, string, number, boolean, or null).
 */
typealias WalletMetadata = JsonElement

typealias WalletInstanceAttestation = JwsSigned<WalletInstanceAttestationClaims>
