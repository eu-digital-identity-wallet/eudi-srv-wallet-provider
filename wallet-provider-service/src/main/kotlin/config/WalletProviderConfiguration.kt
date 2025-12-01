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
package eu.europa.ec.eudi.walletprovider.config

import arrow.core.NonEmptyList
import at.asitplus.signum.indispensable.SignatureAlgorithm
import com.sksamuel.hoplite.*
import com.sksamuel.hoplite.decoder.Decoder
import com.sksamuel.hoplite.fp.invalid
import com.sksamuel.hoplite.fp.valid
import eu.europa.ec.eudi.walletprovider.domain.*
import eu.europa.ec.eudi.walletprovider.domain.walletinformation.*
import eu.europa.ec.eudi.walletprovider.domain.walletinstanceattestation.WalletLink
import eu.europa.ec.eudi.walletprovider.domain.walletinstanceattestation.WalletName
import eu.europa.ec.eudi.walletprovider.domain.walletunitattestation.AttackPotentialResistance
import eu.europa.ec.eudi.walletprovider.port.input.challenge.Length
import eu.europa.ec.eudi.walletprovider.port.input.challenge.PositiveDuration
import eu.europa.ec.eudi.walletprovider.port.input.walletinstanceattestation.WalletInstanceAttestationValidity
import kotlinx.serialization.json.JsonPrimitive
import java.nio.file.Path
import kotlin.io.encoding.Base64
import kotlin.reflect.KType
import kotlin.reflect.full.memberProperties
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

@Suppress("ktlint:standard:max-line-length")
data class WalletProviderConfiguration(
    val server: ServerConfiguration = ServerConfiguration(),
    val signingKey: SigningKeyConfiguration = SigningKeyConfiguration.GenerateRandom,
    val platformKeyAttestationValidation: PlatformKeyAttestationValidationConfiguration = PlatformKeyAttestationValidationConfiguration.Disabled,
    val challenge: ChallengeConfiguration = ChallengeConfiguration(),
    val issuer: IssuerConfiguration = IssuerConfiguration(),
    val clientId: ClientId = ClientId("wallet-dev"),
    val walletInformation: WalletInformationConfiguration,
    val walletInstanceAttestation: WalletInstanceAttestationConfiguration = WalletInstanceAttestationConfiguration(),
    val walletUnitAttestation: WalletUnitAttestationConfiguration = WalletUnitAttestationConfiguration(),
    val tokenStatusListService: TokenStatusListServiceConfiguration? = null,
)

data class ServerConfiguration(
    val port: Port = Port(8080u),
    val preWait: ZeroOrPositiveDuration = ZeroOrPositiveDuration(30.seconds),
    val grace: ZeroOrPositiveDuration = ZeroOrPositiveDuration(5.seconds),
    val timeout: ZeroOrPositiveDuration = ZeroOrPositiveDuration(5.seconds),
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

sealed interface SigningKeyConfiguration {
    data object GenerateRandom : SigningKeyConfiguration

    data class LoadFromKeystore(
        val keystoreFile: Path,
        val keystorePassword: Secret? = null,
        val keystoreType: NonBlankString = NonBlankString("JKS"),
        val keyAlias: NonBlankString,
        val keyPassword: Secret? = null,
        val algorithm: SignatureAlgorithm,
    ) : SigningKeyConfiguration
}

class SignatureAlgorithmDecoder : Decoder<SignatureAlgorithm> {
    override fun supports(type: KType): Boolean = type.classifier == SignatureAlgorithm::class

    override fun decode(
        node: Node,
        type: KType,
        context: DecoderContext,
    ): ConfigResult<SignatureAlgorithm> =
        when (node) {
            is StringNode -> {
                runCatching {
                    val signatureAlgorithmProperty =
                        SignatureAlgorithm.Companion::class.memberProperties.firstOrNull {
                            it.name ==
                                node.value
                        }
                    requireNotNull(signatureAlgorithmProperty) { "Unknown SignatureAlgorithm '${node.value}'" }
                    signatureAlgorithmProperty.call(SignatureAlgorithm.Companion) as SignatureAlgorithm
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

sealed interface PlatformKeyAttestationValidationConfiguration {
    data object Disabled : PlatformKeyAttestationValidationConfiguration

    data class Enabled(
        val android: AndroidKeyAttestationConfiguration = AndroidKeyAttestationConfiguration(),
        val ios: IosKeyAttestationConfiguration = IosKeyAttestationConfiguration(),
        val verificationTimeSkew: Duration = 0.seconds,
    ) : PlatformKeyAttestationValidationConfiguration
}

data class AndroidKeyAttestationConfiguration(
    val applications: List<ApplicationConfiguration> = emptyList(),
    val strongBoxRequired: Boolean = false,
    val unlockedBootloaderAllowed: Boolean = false,
    val rollbackResistanceRequired: Boolean = false,
    val leafCertificateValidityIgnored: Boolean = false,
    val verificationSkew: Duration = 0.seconds,
    val attestationStatementValidity: AttestationStatementValidity = AttestationStatementValidity.Enforced(),
    val hardwareAttestationEnabled: Boolean = true,
    val nougatAttestationEnabled: Boolean = false,
    val softwareAttestationEnabled: Boolean = false,
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

data class WalletInformationConfiguration(
    val generalInformation: GeneralInformationConfiguration,
    val walletSecureCryptographicDeviceInformation: WalletSecureCryptographicDeviceInformationConfiguration,
)

data class GeneralInformationConfiguration(
    val provider: WalletProviderName,
    val id: SolutionId,
    val version: SolutionVersion,
    val certification: CertificationInformation,
)

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

data class WalletSecureCryptographicDeviceInformationConfiguration(
    val type: WalletSecureCryptographicDeviceType? = null,
    val certification: CertificationInformation,
)

data class WalletInstanceAttestationConfiguration(
    val validity: WalletInstanceAttestationValidity = WalletInstanceAttestationValidity.Default,
    val walletName: WalletName? = null,
    val walletLink: WalletLink? = null,
)

data class WalletUnitAttestationConfiguration(
    val validity: ValidityConfiguration = ValidityConfiguration(),
    val keyStorage: List<AttackPotentialResistance>? = null,
    val userAuthentication: List<AttackPotentialResistance>? = null,
    val certification: StringUrl? = null,
) {
    data class ValidityConfiguration(
        val minimum: Duration = ARF.MIN_WALLET_UNIT_ATTESTATION_VALIDITY,
        val maximum: Duration = ARF.MIN_WALLET_UNIT_ATTESTATION_VALIDITY * 2,
    ) {
        val closedRange: ClosedRange<Duration> = minimum..maximum
    }
}

data class TokenStatusListServiceConfiguration(
    val serviceUrl: StringUrl,
    val apiKey: Secret,
)

data class IssuerConfiguration(
    val publicUrl: Issuer = Issuer.create("http://localhost:8080"),
    val name: Name = Name("Wallet Provider"),
)
