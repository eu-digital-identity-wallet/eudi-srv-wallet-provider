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

import arrow.fx.coroutines.resourceScope
import at.asitplus.signum.indispensable.CryptoPublicKey
import at.asitplus.signum.indispensable.Digest
import at.asitplus.signum.indispensable.ECCurve
import at.asitplus.signum.indispensable.pki.CertificateChain
import at.asitplus.signum.indispensable.pki.X509Certificate
import at.asitplus.signum.supreme.os.JKSProvider
import at.asitplus.signum.supreme.sign.Signer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.file.Files
import java.nio.file.StandardOpenOption
import java.security.KeyStore

internal suspend fun SigningKeyConfiguration.load(): Pair<Signer, CertificateChain> {
    val keystore =
        resourceScope {
            withContext(Dispatchers.IO) {
                val inputStream = install(Files.newInputStream(keystoreFile, StandardOpenOption.READ))
                KeyStore
                    .getInstance(keystoreType.value)
                    .apply {
                        load(inputStream, keystorePassword?.value.orEmpty().toCharArray())
                    }
            }
        }

    val keystoreProvider =
        JKSProvider {
            withBackingObject {
                store = keystore
            }
        }.getOrThrow()

    val (digest, curve) =
        when (algorithm) {
            SigningAlgorithm.ES256 -> Digest.SHA256 to ECCurve.SECP_256_R_1
            SigningAlgorithm.ES384 -> Digest.SHA384 to ECCurve.SECP_384_R_1
            SigningAlgorithm.ES512 -> Digest.SHA512 to ECCurve.SECP_521_R_1
        }

    val signer =
        keystoreProvider
            .getSignerForKey(keyAlias.value) {
                ec {
                    this.digest = digest
                }
                privateKeyPassword = keyPassword?.value.orEmpty().toCharArray()
            }.getOrThrow()

    val publicKey = signer.publicKey
    require(publicKey is CryptoPublicKey.EC) {
        "Signing key must be an EC key"
    }
    require(curve == publicKey.curve) {
        "Signing key must be on curve: ${curve.name}"
    }

    val certificateChain: CertificateChain =
        keystore
            .getCertificateChain(keyAlias.value)
            .map {
                X509Certificate.decodeFromDer(it.encoded)
            }.dropRootCaIfNeeded()

    return signer to certificateChain
}

private fun CertificateChain.dropRootCaIfNeeded(): CertificateChain =
    if (size > 1 && last().isSelfSigned())
        dropLast(1)
    else
        this

private fun X509Certificate.isSelfSigned(): Boolean = tbsCertificate.issuerName == tbsCertificate.subjectName
