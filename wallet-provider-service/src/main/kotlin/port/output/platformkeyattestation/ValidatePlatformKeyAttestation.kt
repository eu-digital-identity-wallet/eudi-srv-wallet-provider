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
package eu.europa.ec.eudi.walletprovider.port.output.platformkeyattestation

import arrow.core.raise.context.Raise
import at.asitplus.attestation.AttestationException
import at.asitplus.signum.indispensable.Attestation
import eu.europa.ec.eudi.walletprovider.domain.NonBlankString
import eu.europa.ec.eudi.walletprovider.domain.platformkeyattestation.PlatformAttestedKey

fun interface ValidatePlatformKeyAttestation {
    context(_: Raise<PlatformKeyAttestationValidationFailure>)
    suspend operator fun invoke(
        unvalidatedKeyAttestation: Attestation,
        challenge: ByteArray,
    ): PlatformAttestedKey
}

sealed interface PlatformKeyAttestationValidationFailure {
    class InvalidPlatformKeyAttestation(
        val error: NonBlankString,
        val cause: AttestationException? = null,
    ) : PlatformKeyAttestationValidationFailure

    class UnsupportedPlatformAttestedKey(
        val error: NonBlankString,
        val cause: Throwable? = null,
    ) : PlatformKeyAttestationValidationFailure
}
