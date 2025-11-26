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
package eu.europa.ec.eudi.walletprovider.domain.walletunitattestation

import arrow.core.NonEmptyList
import arrow.core.serialization.NonEmptyListSerializer
import at.asitplus.signum.indispensable.josef.JsonWebKey
import at.asitplus.signum.indispensable.josef.JwsSigned
import eu.europa.ec.eudi.walletprovider.domain.*
import eu.europa.ec.eudi.walletprovider.domain.tokenstatuslist.Status
import eu.europa.ec.eudi.walletprovider.domain.walletinformation.GeneralInformation
import eu.europa.ec.eudi.walletprovider.domain.walletinformation.WalletSecureCryptographicDeviceInformation
import kotlinx.serialization.Required
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class WalletUnitAttestationClaims(
    @Required @SerialName(RFC7519.ISSUER) val issuer: Issuer,
    @Required @SerialName(RFC7519.SUBJECT) val subject: ClientId,
    @Required @SerialName(RFC7519.ISSUED_AT) val issuedAt: EpochSecondsInstant,
    @SerialName(RFC7519.EXPIRES_AT) val expiresAt: EpochSecondsInstant? = null,
    @Required @Serializable(with = NonEmptyListSerializer::class) @SerialName(OpenId4VCISpec.ATTESTED_KEYS)
    val attestedKeys: NonEmptyList<JsonWebKey>,
    @Serializable(with = NonEmptyListSerializer::class) @SerialName(OpenId4VCISpec.KEY_STORAGE)
    val keyStorage: NonEmptyList<AttackPotentialResistance>? = null,
    @Serializable(with = NonEmptyListSerializer::class) @SerialName(OpenId4VCISpec.USER_AUTHENTICATION)
    val userAuthentication: NonEmptyList<AttackPotentialResistance>? = null,
    @SerialName(OpenId4VCISpec.CERTIFICATION) val certification: StringUrl? = null,
    @SerialName(OpenId4VCISpec.NONCE) val nonce: Nonce? = null,
    @SerialName(TokenStatusListSpec.STATUS) val status: Status? = null,
    @Required @SerialName(ARF.EUDI_WALLET_INFORMATION) val walletInformation: WalletInformation,
) {
    @Serializable
    data class WalletInformation(
        @Required @SerialName(ARF.GENERAL_INFORMATION) val generalInformation: GeneralInformation,
        @Required @SerialName(ARF.WALLET_SECURE_CRYPTOGRAPHIC_DEVICE_INFORMATION)
        val walletSecureCryptographicDeviceInformation: WalletSecureCryptographicDeviceInformation,
    )
}

@JvmInline
@Serializable
value class AttackPotentialResistance(
    val value: String,
) {
    init {
        require(value.isNotBlank()) { "value must not be blank" }
    }

    override fun toString(): String = value

    companion object {
        val Iso18045High: AttackPotentialResistance = AttackPotentialResistance(OpenId4VCISpec.ATTACK_POTENTIAL_RESISTANCE_ISO_18045_HIGH)
        val Iso18045Moderate: AttackPotentialResistance =
            AttackPotentialResistance(OpenId4VCISpec.ATTACK_POTENTIAL_RESISTANCE_ISO_18045_MODERATE)
        val Iso18045EnhancedBasic: AttackPotentialResistance =
            AttackPotentialResistance(OpenId4VCISpec.ATTACK_POTENTIAL_RESISTANCE_ISO_18045_ENHANCED_BASIC)
        val Iso18045Basic: AttackPotentialResistance = AttackPotentialResistance(OpenId4VCISpec.ATTACK_POTENTIAL_RESISTANCE_ISO_18045_BASIC)
    }
}

typealias Nonce = String

typealias WalletUnitAttestation = JwsSigned<WalletUnitAttestationClaims>
