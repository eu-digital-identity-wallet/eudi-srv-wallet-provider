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
package eu.europa.ec.eudi.walletprovider.adapter.jades

import arrow.core.NonEmptyList
import arrow.core.raise.context.result
import at.asitplus.signum.indispensable.josef.JwsAlgorithm
import at.asitplus.signum.indispensable.josef.JwsCompactTyped
import at.asitplus.signum.indispensable.josef.io.joseCompliantSerializer
import at.asitplus.signum.indispensable.pki.X509Certificate
import at.asitplus.signum.indispensable.toJcaCertificate
import at.asitplus.signum.supreme.signature
import eu.europa.ec.eudi.walletprovider.domain.JwsSigner
import eu.europa.ec.eudi.walletprovider.domain.JwtType
import eu.europa.ec.eudi.walletprovider.port.output.jose.SignJwt
import eu.europa.esig.dss.enumerations.DigestAlgorithm
import eu.europa.esig.dss.enumerations.JWSSerializationType
import eu.europa.esig.dss.enumerations.SignatureAlgorithm
import eu.europa.esig.dss.enumerations.SignatureLevel
import eu.europa.esig.dss.enumerations.SignaturePackaging
import eu.europa.esig.dss.jades.JAdESSignatureParameters
import eu.europa.esig.dss.jades.JAdESSignatureParameters.X5CHeaderPlacement
import eu.europa.esig.dss.jades.JAdESSigningTimeType
import eu.europa.esig.dss.jades.signature.JAdESService
import eu.europa.esig.dss.model.InMemoryDocument
import eu.europa.esig.dss.model.SignatureValue
import eu.europa.esig.dss.model.x509.CertificateToken
import eu.europa.esig.dss.spi.validation.CommonCertificateVerifier
import kotlinx.serialization.serializer
import java.util.Date
import kotlin.time.Instant
import kotlin.time.toJavaInstant

@Suppress("FunctionName")
internal inline fun <reified T : Any> JadesSignJwt(
    signer: JwsSigner,
    certificateChain: NonEmptyList<X509Certificate>,
    type: JwtType,
): SignJwt<T> {
    val signatureAlgorithm =
        signer.signingAlgorithm.toSignatureAlgorithm().getOrElse {
            throw IllegalArgumentException("signer is not using a JwsAlgorithm supported by DSS", it)
        }

    return object : SignJwt<T> {
        override val signingAlgorithm: JwsAlgorithm
            get() = signer.signingAlgorithm

        private val service = JAdESService(CommonCertificateVerifier())

        override suspend fun invoke(
            at: Instant,
            claims: T,
        ): JwsCompactTyped<T> {
            val payload = joseCompliantSerializer.encodeToString(serializer<T>(), claims).encodeToByteArray()
            val unsignedDocument = InMemoryDocument(payload)
            val parameters =
                JAdESSignatureParameters().apply {
                    signatureLevel = SignatureLevel.JAdES_BASELINE_B
                    signaturePackaging = SignaturePackaging.ENVELOPING

                    jwsSerializationType = JWSSerializationType.COMPACT_SERIALIZATION

                    signingCertificate = CertificateToken(certificateChain.head.toJcaCertificate().getOrThrow())
                    isIncludeKeyIdentifier = true

                    this.certificateChain = certificateChain.map { CertificateToken(it.toJcaCertificate().getOrThrow()) }
                    isIncludeCertificateChain = true
                    x5CHeaderPlacement = X5CHeaderPlacement.protectedHeader

                    signingCertificateDigestMethod = DigestAlgorithm.SHA256

                    isIncludeSignatureType = true
                    signatureType = type.value

                    jadesSigningTimeType = JAdESSigningTimeType.IAT
                    bLevel().apply {
                        signingDate = Date.from(at.toJavaInstant())
                    }

                    encryptionAlgorithm = signatureAlgorithm.encryptionAlgorithm
                    digestAlgorithm = signatureAlgorithm.digestAlgorithm
                }

            val dataToSign = service.getDataToSign(unsignedDocument, parameters)
            val signature = signer.sign(dataToSign.bytes).signature

            val signedDocument =
                service.signDocument(
                    unsignedDocument,
                    parameters,
                    SignatureValue(signatureAlgorithm, signature.rawByteArray),
                )
            val serialized = signedDocument.openStream().bufferedReader().use { it.readText() }
            return JwsCompactTyped(serialized)
        }
    }
}

private fun JwsAlgorithm.toSignatureAlgorithm(): Result<SignatureAlgorithm> =
    result {
        when (this) {
            JwsAlgorithm.MAC.HS256 -> SignatureAlgorithm.HMAC_SHA256
            JwsAlgorithm.MAC.HS384 -> SignatureAlgorithm.HMAC_SHA384
            JwsAlgorithm.MAC.HS512 -> SignatureAlgorithm.HMAC_SHA512
            JwsAlgorithm.Signature.EC.ES256 -> SignatureAlgorithm.ECDSA_SHA256
            JwsAlgorithm.Signature.EC.ES384 -> SignatureAlgorithm.ECDSA_SHA384
            JwsAlgorithm.Signature.EC.ES512 -> SignatureAlgorithm.ECDSA_SHA512
            JwsAlgorithm.Signature.RSA.RS256 -> SignatureAlgorithm.RSA_SHA256
            JwsAlgorithm.Signature.RSA.RS384 -> SignatureAlgorithm.RSA_SHA384
            JwsAlgorithm.Signature.RSA.RS512 -> SignatureAlgorithm.RSA_SHA512
            JwsAlgorithm.Signature.RSA.PS256 -> SignatureAlgorithm.RSA_SSA_PSS_SHA256_MGF1
            JwsAlgorithm.Signature.RSA.PS384 -> SignatureAlgorithm.RSA_SSA_PSS_SHA384_MGF1
            JwsAlgorithm.Signature.RSA.PS512 -> SignatureAlgorithm.RSA_SSA_PSS_SHA512_MGF1
            else -> throw UnsupportedOperationException("Unsupported algorithm: $this")
        }
    }
