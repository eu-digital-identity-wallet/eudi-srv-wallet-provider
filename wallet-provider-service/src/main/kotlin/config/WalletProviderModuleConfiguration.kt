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

import arrow.core.Ior
import arrow.core.NonEmptyList
import at.asitplus.attestation.IosAttestationConfiguration
import at.asitplus.attestation.Makoto
import at.asitplus.attestation.NoopAttestationService
import at.asitplus.attestation.android.AndroidAttestationConfiguration
import at.asitplus.signum.indispensable.pki.X509Certificate
import at.asitplus.signum.supreme.sign.Signer
import eu.europa.ec.eudi.walletprovider.adapter.jose.SignumSignJwt
import eu.europa.ec.eudi.walletprovider.adapter.persistence.RunInTransactionLive
import eu.europa.ec.eudi.walletprovider.adapter.persistence.challenge.ChallengeRepositoryLive
import eu.europa.ec.eudi.walletprovider.adapter.platformkeyattestation.MakotoValidatePlatformKeyAttestation
import eu.europa.ec.eudi.walletprovider.adapter.tokenstatuslist.ApiKey
import eu.europa.ec.eudi.walletprovider.adapter.tokenstatuslist.TokenStatusListServiceAllocateStatusListToken
import eu.europa.ec.eudi.walletprovider.config.IosKeyAttestationConfiguration.ApplicationConfiguration.IosEnvironment
import eu.europa.ec.eudi.walletprovider.domain.JwtType
import eu.europa.ec.eudi.walletprovider.domain.specification.AttestationBasedClientAuthentication
import eu.europa.ec.eudi.walletprovider.domain.specification.OpenId4VCI
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
    certificateChain: NonEmptyList<X509Certificate>,
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
        if (null != config.platformKeyAttestationValidation) {
            ValidateChallengeLive(
                runInTransaction = RunInTransactionLive,
                challengeRepository = ChallengeRepositoryLive,
            )
        } else {
            logger.warn("Challenge Validation is currently disabled")
            ValidateChallengeNoop
        }

    val makotoAttestationService = createMakotoAttestationService(config, clock)
    val validatePlatformKeyAttestation = MakotoValidatePlatformKeyAttestation(makotoAttestationService)

    val generateStatusListToken =
        TokenStatusListServiceAllocateStatusListToken(
            httpClient,
            config.tokenStatusListService.serviceUrl,
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
                JwtType(AttestationBasedClientAuthentication.CLIENT_ATTESTATION_JWT_TYPE),
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
                JwtType(OpenId4VCI.KEY_ATTESTATION_JWT_TYPE),
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
): MakotoAttestationService {
    if (null == config.platformKeyAttestationValidation) {
        logger.warn("Platform Key Attestation Validation is currently disabled")
        return NoopAttestationService
    }

    val androidAttestation =
        config.platformKeyAttestationValidation.android?.let {
            AndroidAttestationConfiguration(
                applications =
                    it.applications.map { application ->
                        AndroidAttestationConfiguration.AppData
                            .Builder(
                                packageName = application.packageName.value,
                                signerFingerprints = application.signingCertificateDigests,
                            ).build()
                    },
                requireStrongBox = it.strongBoxRequired,
                allowBootloaderUnlock = it.unlockedBootloaderAllowed,
                requireRollbackResistance = it.rollbackResistanceRequired,
                ignoreLeafValidity = it.leafCertificateValidityIgnored,
                verificationSecondsOffset = it.verificationSkew.inWholeSeconds,
                attestationStatementValiditySeconds = it.attestationStatementValiditySkew.value.inWholeSeconds,
                disableHardwareAttestation = !it.hardwareAttestationEnabled,
                enableSoftwareAttestation = it.softwareAttestationEnabled,
                supremeParser = it.supremeParserEnabled,
            )
        }

    val iosAttestation =
        config.platformKeyAttestationValidation.ios?.let {
            IosAttestationConfiguration(
                applications =
                    it.applications.map { application ->
                        IosAttestationConfiguration.AppData
                            .Builder(
                                teamIdentifier = application.team.value,
                                bundleIdentifier = application.bundle.value,
                            ).sandbox(IosEnvironment.Sandbox == application.environment)
                            .build()
                    },
                attestationStatementValiditySeconds = it.attestationStatementValiditySkew.value.inWholeSeconds,
            )
        }

    return Ior
        .fromNullables(androidAttestation, iosAttestation)
        ?.fold(
            fa = { Makoto(it, clock.toKotlinClock(), config.platformKeyAttestationValidation.verificationTimeSkew) },
            fb = { Makoto(it, clock.toKotlinClock(), config.platformKeyAttestationValidation.verificationTimeSkew) },
            fab = { androidAttestation, iosAttestation ->
                Makoto(
                    androidAttestation,
                    iosAttestation,
                    clock.toKotlinClock(),
                    config.platformKeyAttestationValidation.verificationTimeSkew,
                )
            },
        ) ?: error("At this point either androidAttestation or iosAttestation must have been configured")
}
