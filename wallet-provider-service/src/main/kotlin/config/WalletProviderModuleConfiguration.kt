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

import at.asitplus.attestation.IosAttestationConfiguration
import at.asitplus.attestation.Makoto
import at.asitplus.attestation.NoopAttestationService
import at.asitplus.attestation.android.AndroidAttestationConfiguration
import at.asitplus.signum.indispensable.pki.CertificateChain
import at.asitplus.signum.supreme.sign.Signer
import eu.europa.ec.eudi.walletprovider.adapter.jose.SignumSignJwt
import eu.europa.ec.eudi.walletprovider.adapter.persistence.RunInTransactionLive
import eu.europa.ec.eudi.walletprovider.adapter.persistence.challenge.ChallengeRepositoryLive
import eu.europa.ec.eudi.walletprovider.adapter.platformkeyattestation.MakotoValidatePlatformKeyAttestation
import eu.europa.ec.eudi.walletprovider.adapter.tokenstatuslist.ApiKey
import eu.europa.ec.eudi.walletprovider.adapter.tokenstatuslist.TokenStatusListServiceAllocateStatusListToken
import eu.europa.ec.eudi.walletprovider.config.IosKeyAttestationConfiguration.ApplicationConfiguration.IosEnvironment
import eu.europa.ec.eudi.walletprovider.domain.AttestationBasedClientAuthenticationSpec
import eu.europa.ec.eudi.walletprovider.domain.JwtType
import eu.europa.ec.eudi.walletprovider.domain.OpenId4VCISpec
import eu.europa.ec.eudi.walletprovider.domain.time.Clock
import eu.europa.ec.eudi.walletprovider.domain.time.toKotlinClock
import eu.europa.ec.eudi.walletprovider.port.input.challenge.GenerateChallengeLive
import eu.europa.ec.eudi.walletprovider.port.input.keyattestation.IssueKeyAttestationLive
import eu.europa.ec.eudi.walletprovider.port.input.walletinstanceattestation.IssueWalletInstanceAttestationLive
import eu.europa.ec.eudi.walletprovider.port.output.challenge.ValidateChallengeLive
import eu.europa.ec.eudi.walletprovider.port.output.challenge.ValidateChallengeNoop
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.http.*
import io.ktor.http.CacheControl.*
import io.ktor.http.content.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.plugins.cachingheaders.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.forwardedheaders.*
import io.ktor.server.plugins.swagger.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.core.vendors.*
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabase
import org.slf4j.LoggerFactory
import at.asitplus.attestation.AttestationService as MakotoAttestationService

private val logger = LoggerFactory.getLogger("WalletProviderModule")

fun Application.configureWalletProviderModule(
    config: WalletProviderConfiguration,
    clock: Clock,
    json: Json,
    database: R2dbcDatabase,
    signer: Signer,
    certificateChain: CertificateChain,
    httpClient: HttpClient,
) {
    logger.info("Configuring Wallet Provider Application using: {}", config)

    configureServerPlugins(json, config.swaggerUi)

    val generateChallenge =
        GenerateChallengeLive(
            clock = clock,
            length = config.challenge.length,
            validity = config.challenge.validity,
            runInTransaction = RunInTransactionLive,
            challengeRepository = ChallengeRepositoryLive,
        )

    val validateChallenge =
        when (config.platformKeyAttestationValidation) {
            PlatformKeyAttestationValidationConfiguration.Disabled -> {
                logger.warn("Challenge Validation is currently disabled")
                ValidateChallengeNoop
            }

            is PlatformKeyAttestationValidationConfiguration.Enabled -> {
                ValidateChallengeLive(
                    runInTransaction = RunInTransactionLive,
                    challengeRepository = ChallengeRepositoryLive,
                )
            }
        }

    val makotoAttestationService = createMakotoAttestationService(config, clock)
    val validatePlatformKeyAttestation = MakotoValidatePlatformKeyAttestation(makotoAttestationService)

    val generateStatusListToken =
        TokenStatusListServiceAllocateStatusListToken(
            httpClient,
            Url(config.tokenStatusListService.serviceUrl.toExternalForm()),
            ApiKey(config.tokenStatusListService.apiKey.value),
            clock,
        )

    val issueWalletInstanceAttestation =
        IssueWalletInstanceAttestationLive(
            clock,
            validateChallenge,
            validatePlatformKeyAttestation,
            config.walletInstanceAttestation.validity,
            issuer = config.issuer.publicUrl,
            clientId = config.clientId,
            walletName = config.walletInstanceAttestation.walletName,
            config.walletInstanceAttestation.walletLink,
            walletVersion = config.walletInstanceAttestation.walletVersion,
            config.walletInstanceAttestation.walletSolutionCertificationInformation,
            config.walletInstanceAttestation.clientStatusValidity,
            generateStatusListToken,
            SignumSignJwt(
                signer,
                certificateChain,
                JwtType(AttestationBasedClientAuthenticationSpec.CLIENT_ATTESTATION_JWT_TYPE),
                json,
            ),
        )

    val issueKeyAttestation =
        IssueKeyAttestationLive(
            clock,
            validateChallenge,
            validatePlatformKeyAttestation,
            config.keyAttestation.validity,
            generateStatusListToken,
            config.keyAttestation.certification,
            SignumSignJwt(
                signer,
                certificateChain,
                JwtType(OpenId4VCISpec.KEY_ATTESTATION_JWT_TYPE),
                json,
            ),
            config.keyAttestation.keyStorageStatusValidity,
        )

    configureChallengeRoutes(generateChallenge)
    configureWalletInstanceAttestationRoutes(issueWalletInstanceAttestation)
    configureKeyAttestationRoutes(issueKeyAttestation)
    configureMetadataRoutes(config.issuer.publicUrl, config.issuer.name, signer, certificateChain)
}

private fun Application.configureServerPlugins(
    json: Json,
    swaggerUiConfiguration: SwaggerUiConfiguration,
) {
    install(ContentNegotiation) {
        json(json)
    }

    install(CachingHeaders) {
        options { _, _ ->
            CachingOptions(NoStore(Visibility.Private))
        }
    }

    install(XForwardedHeaders)
    install(ForwardedHeaders)

    routing {
        swaggerUI(path = swaggerUiConfiguration.path.value, swaggerFile = "openapi/openapi.json")
        get("/") {
            call.respondRedirect(swaggerUiConfiguration.path.value)
        }
    }
}

private fun createMakotoAttestationService(
    config: WalletProviderConfiguration,
    clock: Clock,
): MakotoAttestationService =
    when (config.platformKeyAttestationValidation) {
        PlatformKeyAttestationValidationConfiguration.Disabled -> {
            logger.warn("Platform Key Attestation Validation is currently disabled")
            NoopAttestationService
        }

        is PlatformKeyAttestationValidationConfiguration.Enabled -> {
            val androidAttestation =
                if (!config.platformKeyAttestationValidation.android.enabled)
                    null
                else
                    with(config.platformKeyAttestationValidation.android) {
                        AndroidAttestationConfiguration(
                            applications =
                                applications.map { application ->
                                    AndroidAttestationConfiguration.AppData
                                        .Builder(
                                            packageName = application.packageName.value,
                                            signatureDigests = application.signingCertificateDigests,
                                        ).build()
                                },
                            requireStrongBox = strongBoxRequired,
                            allowBootloaderUnlock = unlockedBootloaderAllowed,
                            requireRollbackResistance = rollbackResistanceRequired,
                            ignoreLeafValidity = leafCertificateValidityIgnored,
                            verificationSecondsOffset = verificationSkew.inWholeSeconds,
                            attestationStatementValiditySeconds =
                                when (attestationStatementValidity) {
                                    AttestationStatementValidity.Ignored -> null
                                    is AttestationStatementValidity.Enforced -> attestationStatementValidity.skew.inWholeSeconds
                                },
                            disableHardwareAttestation = !hardwareAttestationEnabled,
                            enableSoftwareAttestation = softwareAttestationEnabled,
                            supremeParser = supremeParserEnabled,
                        )
                    }

            val iosAttestation =
                if (!config.platformKeyAttestationValidation.ios.enabled)
                    null
                else
                    with(config.platformKeyAttestationValidation.ios) {
                        IosAttestationConfiguration(
                            applications =
                                applications.map { application ->
                                    IosAttestationConfiguration.AppData
                                        .Builder(
                                            teamIdentifier = application.team.value,
                                            bundleIdentifier = application.bundle.value,
                                        ).sandbox(IosEnvironment.Sandbox == application.environment)
                                        .build()
                                },
                            attestationStatementValiditySeconds = attestationStatementValiditySkew.inWholeSeconds,
                        )
                    }

            when {
                null != androidAttestation && null != iosAttestation -> {
                    Makoto(
                        androidAttestationConfiguration = androidAttestation,
                        iosAttestationConfiguration = iosAttestation,
                        clock = clock.toKotlinClock(),
                        verificationTimeOffset = config.platformKeyAttestationValidation.verificationTimeSkew,
                    )
                }

                null != androidAttestation -> {
                    Makoto(
                        androidAttestationConfiguration = androidAttestation,
                        clock = clock.toKotlinClock(),
                        verificationTimeOffset = config.platformKeyAttestationValidation.verificationTimeSkew,
                    )
                }

                null != iosAttestation -> {
                    Makoto(
                        iosAttestationConfiguration = iosAttestation,
                        clock = clock.toKotlinClock(),
                        verificationTimeOffset = config.platformKeyAttestationValidation.verificationTimeSkew,
                    )
                }

                else -> {
                    error("At this point either androidAttestation or iosAttestation must have been configured")
                }
            }
        }
    }
