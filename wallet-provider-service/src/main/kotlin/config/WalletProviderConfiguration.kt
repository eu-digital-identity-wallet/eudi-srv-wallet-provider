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
package eu.europa.ec.eudi.walletprovider.config

import arrow.core.NonEmptyList
import com.sksamuel.hoplite.*
import com.sksamuel.hoplite.decoder.Decoder
import com.sksamuel.hoplite.fp.invalid
import com.sksamuel.hoplite.fp.valid
import eu.europa.ec.eudi.walletprovider.domain.*
import eu.europa.ec.eudi.walletprovider.domain.walletinstanceattestation.WalletLink
import eu.europa.ec.eudi.walletprovider.domain.walletinstanceattestation.WalletName
import eu.europa.ec.eudi.walletprovider.domain.walletinstanceattestation.WalletVersion
import eu.europa.ec.eudi.walletprovider.port.input.challenge.Length
import eu.europa.ec.eudi.walletprovider.port.input.keyattestation.KeyAttestationValidity
import eu.europa.ec.eudi.walletprovider.port.input.walletinstanceattestation.WalletInstanceAttestationValidity
import kotlinx.serialization.json.JsonPrimitive
import java.nio.file.Path
import kotlin.io.encoding.Base64
import kotlin.reflect.KType
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

@Suppress("ktlint:standard:max-line-length")
data class WalletProviderConfiguration(
    val server: ServerConfiguration = ServerConfiguration(),
    val database: DatabaseConfiguration,
    val signingKey: SigningKeyConfiguration,
    val platformKeyAttestationValidation: PlatformKeyAttestationValidationConfiguration = PlatformKeyAttestationValidationConfiguration.Disabled,
    val challenge: ChallengeConfiguration = ChallengeConfiguration(),
    val issuer: IssuerConfiguration = IssuerConfiguration(),
    val clientId: ClientId = ClientId("wallet-dev"),
    val walletInstanceAttestation: WalletInstanceAttestationConfiguration,
    val keyAttestation: KeyAttestationConfiguration,
    val tokenStatusListService: TokenStatusListServiceConfiguration,
    val swaggerUi: SwaggerUiConfiguration = SwaggerUiConfiguration(),
)

data class ServerConfiguration(
    val port: Port = Port(8080u),
    val preWait: ZeroOrPositiveDuration = ZeroOrPositiveDuration(30.seconds),
    val grace: ZeroOrPositiveDuration = ZeroOrPositiveDuration(5.seconds),
    val timeout: ZeroOrPositiveDuration = ZeroOrPositiveDuration(5.seconds),
)

data class DatabaseConfiguration(
    val url: NonBlankString,
    val username: String? = null,
    val password: Secret? = null,
)

@JvmInline
value class Port(
    val value: UInt,
) {
    init {
        require(value > 0u) { "value must be greater than 0" }
    }

    override fun toString(): String = value.toString()
}

@JvmInline
value class ZeroOrPositiveDuration(
    val value: Duration,
) {
    init {
        require(!value.isNegative()) { "value must be positive or 0" }
    }

    override fun toString(): String = value.toString()
}

data class SigningKeyConfiguration(
    val keystoreFile: Path,
    val keystorePassword: Secret? = null,
    val keystoreType: NonBlankString = NonBlankString("JKS"),
    val keyAlias: NonBlankString,
    val keyPassword: Secret? = null,
    val algorithm: SigningAlgorithm,
)

sealed interface PlatformKeyAttestationValidationConfiguration {
    data object Disabled : PlatformKeyAttestationValidationConfiguration

    data class Enabled(
        val android: AndroidKeyAttestationConfiguration = AndroidKeyAttestationConfiguration(),
        val ios: IosKeyAttestationConfiguration = IosKeyAttestationConfiguration(),
        val verificationTimeSkew: Duration = 0.seconds,
    ) : PlatformKeyAttestationValidationConfiguration {
        init {
            require(android.enabled || ios.enabled) { "At least one type of platform Key Attestation must be enabled" }
        }
    }
}

data class AndroidKeyAttestationConfiguration(
    val enabled: Boolean = true,
    val applications: List<ApplicationConfiguration> = emptyList(),
    val strongBoxRequired: Boolean = false,
    val unlockedBootloaderAllowed: Boolean = false,
    val rollbackResistanceRequired: Boolean = false,
    val leafCertificateValidityIgnored: Boolean = false,
    val verificationSkew: Duration = 0.seconds,
    val attestationStatementValidity: AttestationStatementValidity = AttestationStatementValidity.Enforced(),
    val hardwareAttestationEnabled: Boolean = true,
    val softwareAttestationEnabled: Boolean = false,
    val supremeParserEnabled: Boolean = false,
) {
    data class ApplicationConfiguration(
        val packageName: PackageName,
        val signingCertificateDigests: NonEmptyList<Base64UrlSafeByteArray>,
    ) {
        typealias PackageName = NonBlankString
    }
}

sealed interface AttestationStatementValidity {
    data object Ignored : AttestationStatementValidity

    data class Enforced(
        val skew: Duration = 5.minutes,
    ) : AttestationStatementValidity
}

data class IosKeyAttestationConfiguration(
    val enabled: Boolean = true,
    val applications: List<ApplicationConfiguration> = emptyList(),
    val attestationStatementValiditySkew: Duration = 5.minutes,
) {
    data class ApplicationConfiguration(
        val team: TeamIdentifier,
        val bundle: BundleIdentifier,
        val environment: IosEnvironment = IosEnvironment.Production,
    ) {
        typealias TeamIdentifier = NonBlankString
        typealias BundleIdentifier = NonBlankString

        enum class IosEnvironment {
            Production,
            Sandbox,
        }
    }
}

data class ChallengeConfiguration(
    val length: Length = Length(128u),
    val validity: PositiveDuration = PositiveDuration(5.minutes),
)

class Base64UrlSafeByteArrayDecoder : Decoder<Base64UrlSafeByteArray> {
    override fun supports(type: KType): Boolean = type.classifier == Base64UrlSafeByteArray::class

    override fun decode(
        node: Node,
        type: KType,
        context: DecoderContext,
    ): ConfigResult<Base64UrlSafeByteArray> =
        when (node) {
            is StringNode -> {
                runCatching {
                    Base64.UrlSafe.decode(node.value)
                }.fold(
                    { it.valid() },
                    { ConfigFailure.DecodeError(node, type).invalid() },
                )
            }

            else -> {
                ConfigFailure.DecodeError(node, type).invalid()
            }
        }
}

class CertificationInformationDecoder : Decoder<CertificationInformation> {
    override fun supports(type: KType): Boolean = type.classifier == CertificationInformation::class

    override fun decode(
        node: Node,
        type: KType,
        context: DecoderContext,
    ): ConfigResult<CertificationInformation> =
        when (node) {
            is StringNode -> {
                runCatching {
                    CertificationInformation(JsonPrimitive((node.value)))
                }.fold(
                    { it.valid() },
                    { ConfigFailure.DecodeError(node, type).invalid() },
                )
            }

            else -> {
                ConfigFailure.DecodeError(node, type).invalid()
            }
        }
}

data class WalletInstanceAttestationConfiguration(
    val validity: WalletInstanceAttestationValidity = WalletInstanceAttestationValidity.Default,
    val walletName: WalletName,
    val walletLink: WalletLink? = null,
    val walletVersion: WalletVersion,
    val walletCertificationInformation: CertificationInformation,
    val clientStatusValidity: PositiveDuration = PositiveDuration(90.days),
)

data class KeyAttestationConfiguration(
    val validity: KeyAttestationValidity = KeyAttestationValidity.Default,
    val certification: StringUrl,
    val keyStorageStatusValidity: PositiveDuration = PositiveDuration(90.days),
)

data class TokenStatusListServiceConfiguration(
    val serviceUrl: StringUrl,
    val apiKey: Secret,
)

data class IssuerConfiguration(
    val publicUrl: Issuer = Issuer.create("http://localhost:8080"),
    val name: Name = Name("Wallet Provider"),
)

data class SwaggerUiConfiguration(
    val path: NonBlankString = "/swagger".toNonBlankString(),
)

enum class SigningAlgorithm {
    ES256,
    ES384,
    ES512,
}
