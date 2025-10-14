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
package eu.europa.ec.eudi.walletprovider.domain.walletapplicationattestation

import at.asitplus.signum.indispensable.josef.ConfirmationClaim
import at.asitplus.signum.indispensable.josef.JwsSigned
import eu.europa.ec.eudi.walletprovider.domain.*
import eu.europa.ec.eudi.walletprovider.domain.tokenstatuslist.Status
import eu.europa.ec.eudi.walletprovider.domain.walletinformation.WalletInformation
import kotlinx.serialization.Required
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class WalletApplicationAttestationClaims(
    @Required @SerialName(RFC7519.ISSUER) val issuer: Issuer,
    @Required @SerialName(RFC7519.SUBJECT) val subject: ClientId,
    @Required @SerialName(RFC7519.EXPIRES_AT) val expiresAt: EpochSecondsInstant,
    @Required @SerialName(RFC7800.CONFIRMATION) val confirmation: ConfirmationClaim,
    @SerialName(RFC7519.ISSUED_AT) val issuedAt: EpochSecondsInstant? = null,
    @SerialName(RFC7519.NOT_BEFORE) val notBefore: EpochSecondsInstant? = null,
    @SerialName(OpenId4VCISpec.WALLET_NAME) val walletName: WalletName? = null,
    @SerialName(OpenId4VCISpec.WALLET_LINK) val walletLink: WalletLink? = null,
    @SerialName(TokenStatusListSpec.STATUS) val status: Status? = null,
    @Required @SerialName(ARF.EUDI_WALLET_INFO) val walletInformation: WalletInformation,
)

typealias WalletName = NonBlankString
typealias WalletLink = StringUrl
typealias WalletApplicationAttestation = JwsSigned<WalletApplicationAttestationClaims>
