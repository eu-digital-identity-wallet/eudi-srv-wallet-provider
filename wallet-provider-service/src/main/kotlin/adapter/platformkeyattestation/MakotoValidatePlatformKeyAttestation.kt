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
package eu.europa.ec.eudi.walletprovider.adapter.platformkeyattestation

import arrow.core.raise.context.Raise
import arrow.core.raise.context.ensure
import arrow.core.raise.context.raise
import at.asitplus.attestation.AttestationResult
import at.asitplus.signum.indispensable.Attestation
import at.asitplus.signum.indispensable.CryptoPublicKey
import at.asitplus.signum.indispensable.toCryptoPublicKey
import eu.europa.ec.eudi.walletprovider.domain.platformkeyattestation.PlatformAttestedKey
import eu.europa.ec.eudi.walletprovider.domain.toNonBlankString
import eu.europa.ec.eudi.walletprovider.port.output.platformkeyattestation.PlatformKeyAttestationValidationFailure
import eu.europa.ec.eudi.walletprovider.port.output.platformkeyattestation.ValidatePlatformKeyAttestation
import at.asitplus.attestation.AttestationService as MakotoAttestationService

class MakotoValidatePlatformKeyAttestation(
    private val makotoAttestationService: MakotoAttestationService,
) : ValidatePlatformKeyAttestation {
    context(_: Raise<PlatformKeyAttestationValidationFailure>)
    override suspend fun invoke(
        unvalidatedKeyAttestation: Attestation,
        challenge: ByteArray,
    ): PlatformAttestedKey {
        val verificationResult = makotoAttestationService.verifyKeyAttestation(unvalidatedKeyAttestation, challenge)

        if (!verificationResult.isSuccess) {
            val errorDetails = verificationResult.details as AttestationResult.Error
            val error = errorDetails.explanation.toNonBlankString()
            raise(
                PlatformKeyAttestationValidationFailure
                    .InvalidPlatformKeyAttestation(
                        error,
                        errorDetails.cause,
                    ),
            )
        }

        val publicKey = checkNotNull(verificationResult.attestedPublicKey)
        val cryptoPublicKey =
            publicKey
                .toCryptoPublicKey()
                .getOrElse {
                    raise(
                        PlatformKeyAttestationValidationFailure.UnsupportedPlatformAttestedKey(
                            "Attested PublicKey is not supported".toNonBlankString(),
                            it,
                        ),
                    )
                }
        ensure(cryptoPublicKey is CryptoPublicKey.EC) {
            PlatformKeyAttestationValidationFailure.UnsupportedPlatformAttestedKey(
                "Attested PublicKey is not an EC key".toNonBlankString(),
                null,
            )
        }

        return PlatformAttestedKey(cryptoPublicKey, verificationResult.details)
    }
}
