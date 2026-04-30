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
package eu.europa.ec.eudi.walletprovider.domain.walletinformation

import eu.europa.ec.eudi.walletprovider.domain.ARF
import eu.europa.ec.eudi.walletprovider.domain.NonBlankString
import eu.europa.ec.eudi.walletprovider.domain.TS3
import kotlinx.serialization.Required
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive



@JvmInline
@Serializable
value class CertificationInformation(
    val value: JsonElement,
) {
    init {
        require((value is JsonPrimitive && value.isString && value.content.isNotBlank()) || value is JsonObject)
    }

    override fun toString(): String = value.toString()
}

@JvmInline
@Serializable
value class WalletSecureCryptographicDeviceType(
    val value: String,
) {
    init {
        require(value.isNotBlank()) { "value must not be blank" }
    }

    override fun toString(): String = value

    companion object {
        val Remote: WalletSecureCryptographicDeviceType =
            WalletSecureCryptographicDeviceType(ARF.WALLET_SECURE_CRYPTOGRAPHIC_DEVICE_TYPE_REMOTE)
        val LocalExternal: WalletSecureCryptographicDeviceType =
            WalletSecureCryptographicDeviceType(ARF.WALLET_SECURE_CRYPTOGRAPHIC_DEVICE_TYPE_LOCAL_EXTERNAL)
        val LocalInternal: WalletSecureCryptographicDeviceType =
            WalletSecureCryptographicDeviceType(ARF.WALLET_SECURE_CRYPTOGRAPHIC_DEVICE_TYPE_LOCAL_INTERNAL)
        val LocalNative: WalletSecureCryptographicDeviceType =
            WalletSecureCryptographicDeviceType(ARF.WALLET_SECURE_CRYPTOGRAPHIC_DEVICE_TYPE_LOCAL_NATIVE)
        val Hybrid: WalletSecureCryptographicDeviceType =
            WalletSecureCryptographicDeviceType(ARF.WALLET_SECURE_CRYPTOGRAPHIC_DEVICE_TYPE_HYBRID)
    }
}
