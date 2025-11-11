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
package eu.europa.ec.eudi.walletprovider.adapter.keyattestation

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import at.asitplus.attestation.AttestationResult
import at.asitplus.signum.indispensable.Attestation
import at.asitplus.signum.indispensable.toCryptoPublicKey
import eu.europa.ec.eudi.walletprovider.domain.Challenge
import eu.europa.ec.eudi.walletprovider.domain.keyattestation.AttestedKey
import eu.europa.ec.eudi.walletprovider.domain.toNonBlankString
import eu.europa.ec.eudi.walletprovider.port.output.keyattestation.KeyAttestationValidationFailure
import eu.europa.ec.eudi.walletprovider.port.output.keyattestation.ValidateKeyAttestation
import at.asitplus.attestation.AttestationService as WardenAttestationService

class WardenValidateKeyAttestation(
    private val wardenAttestationService: WardenAttestationService,
) : ValidateKeyAttestation {
    override suspend fun invoke(
        unvalidatedKeyAttestation: Attestation,
        challenge: Challenge,
    ): Either<KeyAttestationValidationFailure, AttestedKey> =
        wardenAttestationService
            .verifyKeyAttestation(unvalidatedKeyAttestation, challenge.value)
            .let { verificationResult ->
                when {
                    !verificationResult.isSuccess -> {
                        val errorDetails = verificationResult.details as AttestationResult.Error
                        KeyAttestationValidationFailure
                            .InvalidKeyAttestation(
                                errorDetails.explanation.toNonBlankString(),
                                errorDetails.cause,
                            ).left()
                    }

                    else -> {
                        val publicKey = checkNotNull(verificationResult.attestedPublicKey)
                        publicKey
                            .toCryptoPublicKey()
                            .fold(
                                onFailure = {
                                    KeyAttestationValidationFailure
                                        .UnsupportedAttestedKey(
                                            "Attested PublicKey is not supported".toNonBlankString(),
                                            it,
                                        ).left()
                                },
                                onSuccess = { AttestedKey(it, verificationResult.details).right() },
                            )
                    }
                }
            }
}
