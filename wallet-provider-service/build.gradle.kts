import org.jetbrains.kotlin.gradle.dsl.JvmDefaultMode
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.plugin.serialization)
    alias(libs.plugins.spotless)
    alias(libs.plugins.jib)
    alias(libs.plugins.kover)
    alias(libs.plugins.dependency.check)
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(enforcedPlatform(libs.kotlin.bom))
    implementation(enforcedPlatform(libs.kotlinx.coroutines.bom))
    implementation(enforcedPlatform(libs.kotlinx.serialization.bom))
    implementation(enforcedPlatform(libs.ktor.bom))
    implementation(enforcedPlatform(libs.slf4j.bom))
    implementation(enforcedPlatform(libs.arrow.bom))
    components.all<VirtualPlatformAlignmentRule> {
        val virtualPlatform = libs.bouncycastle.bom.get()
        params(virtualPlatform.group, virtualPlatform.name)
    }
    components.all<VirtualPlatformAlignmentRule> {
        val virtualPlatform = libs.hoplite.bom.get()
        params(virtualPlatform.group, virtualPlatform.name)
    }
    implementation(enforcedPlatform(libs.exposed.bom))
    implementation(enforcedPlatform(libs.reactor.bom))
    testImplementation(enforcedPlatform(libs.testcontainers.bom))

    implementation(libs.kotlin.stdlib)

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.datetime)

    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.cio)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.ktor.server.caching.headers)
    implementation(libs.ktor.server.forwarded.header)
    implementation(libs.ktor.server.swagger)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.client.content.negotiation)

    implementation(libs.slf4j.api)
    runtimeOnly(libs.logback.classic)

    implementation(libs.arrow.core)
    implementation(libs.arrow.core.serialization)
    implementation(libs.arrow.suspendapp)
    implementation(libs.arrow.suspendapp.ktor)

    implementation(libs.warden.makoto)
    implementation(libs.indispensable.josef)
    implementation(libs.supreme)

    implementation(libs.hoplite.core)

    implementation(libs.exposed.core)
    implementation(libs.exposed.r2dbc)
    implementation(libs.exposed.migration.core)
    implementation(libs.exposed.migration.r2dbc)
    implementation(libs.exposed.kotlin.datetime)
    runtimeOnly(libs.r2dbc.h2)
    runtimeOnly(libs.r2dbc.mariadb)
    runtimeOnly(libs.r2dbc.mysql)
    runtimeOnly(libs.r2dbc.oracle)
    runtimeOnly(libs.r2dbc.postgresql)
    runtimeOnly(libs.r2dbc.mssql)
    runtimeOnly(libs.r2dbc.pool)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.testcontainers.mysql)
    testRuntimeOnly(libs.jdbc.mysql)
    testImplementation(libs.ktor.client.mock)
}

abstract class VirtualPlatformAlignmentRule
    @Inject
    constructor(
        private val group: String,
        private val artifact: String,
    ) : ComponentMetadataRule {
        override fun execute(context: ComponentMetadataContext) {
            context.details.run {
                if (group == id.group) {
                    belongsTo("$group:$artifact:${id.version}", true)
                }
            }
        }
    }

kotlin {
    jvmToolchain {
        languageVersion = JavaLanguageVersion.of(libs.versions.java.get())
        vendor = JvmVendorSpec.ADOPTIUM
        implementation = JvmImplementation.VENDOR_SPECIFIC
    }

    target {
        compilerOptions {
            javaParameters = true
            jvmDefault = JvmDefaultMode.ENABLE
            jvmTarget = JvmTarget.fromTarget(libs.versions.java.get())
            apiVersion = KotlinVersion.DEFAULT
            languageVersion = KotlinVersion.DEFAULT
            optIn.addAll(
                "at.asitplus.attestation.DisabledAttestation",
            )
            freeCompilerArgs.addAll(
                "-Xjsr305=strict",
                "-Xconsistent-data-class-copy-visibility",
            )
        }
    }
}

spotless {
    kotlin {
        ktlint(libs.versions.ktlint.get())
        licenseHeaderFile(rootProject.layout.projectDirectory.file("FileHeader.txt"))
    }

    kotlinGradle {
        ktlint(libs.versions.ktlint.get())
    }
}

tasks.jar {
    manifest {
        attributes["Main-Class"] = "eu.europa.ec.eudi.walletprovider.MainKt"
    }
}

jib {
    from {
        image = "eclipse-temurin:${libs.versions.java.get()}-jre"
    }

    container {
        labels =
            buildMap {
                fun fromEnvironmentVariable(
                    variable: String,
                    label: String,
                ) {
                    System.getenv(variable)?.let { this[label] = it }
                }
                fromEnvironmentVariable("OCI_CREATED", "org.opencontainers.image.created")
                fromEnvironmentVariable("OCI_DESCRIPTION", "org.opencontainers.image.description")
                fromEnvironmentVariable("OCI_LICENSES", "org.opencontainers.image.licenses")
                fromEnvironmentVariable("OCI_REVISION", "org.opencontainers.image.revision")
                fromEnvironmentVariable("OCI_SOURCE", "org.opencontainers.image.source")
                fromEnvironmentVariable("OCI_TITLE", "org.opencontainers.image.title")
                fromEnvironmentVariable("OCI_URL", "org.opencontainers.image.url")
                fromEnvironmentVariable("OCI_VERSION", "org.opencontainers.image.version")
                fromEnvironmentVariable("OCI_AUTHORS", "org.opencontainers.image.authors")
                fromEnvironmentVariable("OCI_REF_NAME", "org.opencontainers.image.ref.name")
                fromEnvironmentVariable("OCI_VENDOR", "org.opencontainers.image.vendor")
            }
    }

    extraDirectories {
        paths {
            path {
                setFrom(
                    rootProject.layout.projectDirectory
                        .dir("openapi")
                        .asFile,
                )
                into = "/openapi"
                includes = listOf("openapi.json")
            }
        }
    }
}

dependencyCheck {
    formats = listOf("XML", "HTML")

    nvd {
        apiKey = System.getenv("NVD_API_KEY") ?: findProperty("nvdApiKey")?.toString()

        delay = 10000
        maxRetryCount = 2
    }
}

tasks.test {
    useJUnitPlatform()
}
