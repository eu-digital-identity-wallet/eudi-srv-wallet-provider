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
import eu.europa.ec.eudi.walletprovider.domain.android.PackageName
import eu.europa.ec.eudi.walletprovider.domain.arf.CertificationInformation
import eu.europa.ec.eudi.walletprovider.domain.arf.ProviderName
import eu.europa.ec.eudi.walletprovider.domain.arf.SolutionId
import eu.europa.ec.eudi.walletprovider.domain.arf.SolutionVersion
import eu.europa.ec.eudi.walletprovider.domain.ios.BundleIdentifier
import eu.europa.ec.eudi.walletprovider.domain.ios.IosEnvironment
import eu.europa.ec.eudi.walletprovider.domain.ios.TeamIdentifier
import eu.europa.ec.eudi.walletprovider.domain.server.Port
import eu.europa.ec.eudi.walletprovider.domain.walletapplicationattestation.WalletApplicationAttestationValidity
import eu.europa.ec.eudi.walletprovider.domain.walletapplicationattestation.WalletName
import kotlinx.serialization.json.JsonPrimitive
import java.nio.file.Path
import kotlin.io.encoding.Base64
import kotlin.reflect.KType
import kotlin.reflect.full.memberProperties
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

data class WalletProviderConfiguration(
    val server: ServerConfiguration = ServerConfiguration(),
    val signingKey: SigningKeyConfiguration = SigningKeyConfiguration.GenerateRandom,
    val attestationVerification: AttestationVerificationConfiguration = AttestationVerificationConfiguration.Disabled,
    val challenge: ChallengeConfiguration = ChallengeConfiguration(),
    val walletApplicationAttestation: WalletApplicationAttestationConfiguration,
)

data class ServerConfiguration(
    val port: Port = Port(8080u),
    val preWait: ZeroOrPositiveDuration = ZeroOrPositiveDuration(30.seconds),
    val grace: ZeroOrPositiveDuration = ZeroOrPositiveDuration(5.seconds),
    val timeout: ZeroOrPositiveDuration = ZeroOrPositiveDuration(5.seconds),
)

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
            is StringNode ->
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
            else -> ConfigFailure.DecodeError(node, type).invalid()
        }
}

sealed interface AttestationVerificationConfiguration {
    data object Disabled : AttestationVerificationConfiguration

    data class Enabled(
        val androidAttestation: AndroidAttestationConfiguration = AndroidAttestationConfiguration(),
        val iosAttestation: IosAttestationConfiguration = IosAttestationConfiguration(),
        val verificationTimeSkew: Duration = 0.seconds,
    ) : AttestationVerificationConfiguration
}

data class AndroidAttestationConfiguration(
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
    )
}

sealed interface AttestationStatementValidity {
    data object Ignored : AttestationStatementValidity

    data class Enforced(
        val skew: Duration = 5.minutes,
    ) : AttestationStatementValidity
}

data class IosAttestationConfiguration(
    val applications: List<ApplicationConfiguration> = emptyList(),
    val attestationStatementValiditySkew: Duration = 5.minutes,
) {
    data class ApplicationConfiguration(
        val team: TeamIdentifier,
        val bundle: BundleIdentifier,
        val environment: IosEnvironment = IosEnvironment.Production,
    )
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
            is StringNode ->
                runCatching {
                    Base64.UrlSafe.decode(node.value)
                }.fold(
                    { it.valid() },
                    { ConfigFailure.DecodeError(node, type).invalid() },
                )
            else -> ConfigFailure.DecodeError(node, type).invalid()
        }
}

data class WalletApplicationAttestationConfiguration(
    val issuer: Issuer = Issuer("eudi-srv-wallet-provider"),
    val validity: WalletApplicationAttestationValidity = WalletApplicationAttestationValidity.ArfMax,
    val walletName: WalletName? = null,
    val walletLink: StringUrl? = null,
    val walletInformation: WalletInformationConfiguration,
)

data class WalletInformationConfiguration(
    val provider: ProviderName,
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
            is StringNode ->
                runCatching {
                    CertificationInformation(JsonPrimitive((node.value)))
                }.fold(
                    { it.valid() },
                    { ConfigFailure.DecodeError(node, type).invalid() },
                )
            else -> ConfigFailure.DecodeError(node, type).invalid()
        }
}
