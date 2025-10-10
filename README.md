# Wallet Provider

**Important!** Before you proceed, please read
the [EUDI Wallet Reference Implementation project description](https://github.com/eu-digital-identity-wallet/.github/blob/main/profile/reference-implementation.md)

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://www.apache.org/licenses/LICENSE-2.0)

* [Overview](#overview)
* [Disclaimer](#disclaimer)
* [Features](#features)
* [Technical Details](#technical-details)
* [Endpoints](#endpoints)
* [Deployment](#deployment)
* [How to contribute](#how-to-contribute)
* [License](#license)

## Overview

An implementation of a Wallet Provider service according to [OpenId4VCI 1.0](https://openid.net/specs/openid-4-verifiable-credential-issuance-1_0.html), 
and [EUDI Wallet Standards and Technical Specifications](https://github.com/eu-digital-identity-wallet/eudi-doc-standards-and-technical-specifications).

## Disclaimer

> [!CAUTION]
> Wallet Provider is created strictly for testing and development purposes. It is **NOT** production-grade.
> Wallet Provider is provided on an as-is basis, without warranties or conditions of any kind, either express or implied.

The released software is an initial development release version:

* The initial development release is an early endeavor reflecting the efforts of a short timeboxed period, and by no means can it be considered 
as the final product.
* The initial development release may be changed substantially over time, might introduce new features but also may change or remove existing 
ones, potentially breaking compatibility with your existing code.
* The initial development release is limited in functional scope.
* The initial development release may contain errors or design flaws and other problems that could cause system or other failures and data loss.
* The initial development release has reduced security, privacy, availability, and reliability standards relative to future releases. This could 
make the software slower, less reliable, or more vulnerable to attacks than mature software.
* The initial development release is not yet comprehensively documented.
* Users of the software must perform sufficient engineering and additional testing to properly evaluate their application and determine whether 
any of the open-sourced components is suitable for use in that application.
* We strongly recommend against putting this version of the software into production use.
* Only the latest version of the software will be supported

## Features

Currently, the service supports issuance of key-bound Wallet Application Attestations according to 
[Specification of Wallet Unit Attestations (WUA) used in issuance of PID and Attestations](https://github.com/eu-digital-identity-wallet/eudi-doc-standards-and-technical-specifications/blob/main/docs/technical-specifications/ts3-wallet-unit-attestation.md).

### Issuance of key-bound Wallet Application Attestation

To get a Wallet Application Attestation:

1. The Wallet gets a single-use Challenge from the Wallet Provider
2. The Wallet generates a new Key-Pair and Key Attestation which contains the single-use Challenge provided by the Wallet Provider
3. The Wallet requests a key-bound Wallet Application Attestation from the Wallet Provider
4. The Wallet Provider:
   1. Validates the single-use Challenge
   2. Validates the Key Attestation
   3. Verifies the Key Attestation contains the single-use Challenge
   4. Issues a key-bound Wallet Application Attestation

```mermaid
sequenceDiagram    
    participant W as Wallet
    participant WP as Wallet Provider
    
    
    W->>+WP: Request single-use Challenge
    WP->>+WP: Generate single-use Challenge
    WP->>+W: Provide single-use Challenge
    
    W->>+W: Generate new Key-Pair and Key Attestation (with single-use Challenge)
    
    W->>+WP: Request Wallet Application Attestation Issuance
    
    WP->>+WP: Validate Challenge
    WP->>+WP: Validate Key Attestation
    WP->>+WP: Issue Wallet Application Attestation
    
    WP->>+W: Provide issued Wallet Application Attestation
```

## Technical Details

Wallet Provider uses the [Warden Server-Side Mobile Client Attestation Library](https://github.com/a-sit-plus/warden) for verifying Key Attestations.
Android Wallets must use the [Certification Chain provided by the Android Keystore](https://developer.android.com/privacy-and-security/security-key-attestation).
iOS Wallets must use the [Supreme Attestation Format](https://github.com/a-sit-plus/warden?tab=readme-ov-file#ios) which is based on [Apple's App Attest Service](https://developer.apple.com/documentation/devicecheck/establishing-your-app-s-integrity).

## Endpoints

An OpenAPI specification of the endpoints provided by Wallet Provider is available [here](openapi/openapi.json).

## Deployment

Wallet Provider can be deployed using Docker or any other OCI compliant container runtime. You can find the available container images 
[here](https://github.com/eu-digital-identity-wallet/eudi-srv-wallet-provider/pkgs/container/eudi-srv-wallet-provider).

### Configuration

Wallet Provider can be configured using the environment variables:

#### Server configuration

Variable: `SERVER_PORT`  
Description: Port the HTTP listener of Wallet Provider will bind to.  
Default value: `8080`  

Variable: `SERVER_PREWAIT`  
Description: Period after which the shutdown process begins.  
Default value: `30 seconds`  

Variable: `SERVER_GRACE`  
Description: Period during which already in-flight requests are allowed to continue before the shutdown process begins.  
Default value: `5 seconds`  

Variable: `SERVER_TIMEOUT`  
Description: Period after which the server forcibly shuts down.  
Default value: `5 seconds`  

#### Attestation Signing Key Configuration

By default, Wallet Provider uses an ephemeral EC Key on the `secp256r1` curve to sign Attestations using the `ECDSAwithSHA256` algorithm.

To load a signing key and certificate from a Keystore, use the following environment variables:

Variable: `SIGNINGKEY_KEYSTOREFILE`  
Description: Location of the Keystore.  
Default value: N/A  

Variable: `SIGNINGKEY_KEYSTOREPASSWORD`  
Description: Password of the Keystore.  
Default value: N/A  

Variable: `SIGNINGKEY_KEYSTORETYPE`  
Description: Type of the Keystore.  
Default value: `JKS`  

Variable: `SIGNINGKEY_KEYALIAS`  
Description: Alias of the Key and Certificate Chain to load.  
Default value: N/A  

Variable: `SIGNINGKEY_KEYPASSWORD`   
Description: Password of the Key to load.  
Default value: N/A  

Variable: `SIGNINGKEY_ALGORITHM`   
Description: Algorithm used to sign the generated Attestations.  
Default value: N/A  
Allowed values:
* `ECDSAwithSHA256`
* `ECDSAwithSHA384`
* `ECDSAwithSHA512`
* `RSAwithSHA256andPKCS1Padding`
* `RSAwithSHA384andPKCS1Padding`
* `RSAwithSHA512andPKCS1Padding`
* `RSAwithSHA256andPSSPadding`
* `RSAwithSHA384andPSSPadding`
* `RSAwithSHA512andPSSPadding`

#### Key Attestation Verification Configuration

By default, Wallet Provider performs no Key Attestation verification.

To enable Key Attestation verification, use the following environment variables:

> [!NOTE]
> Due to limitations of the [Warden Server-Side Mobile Client Attestation Library](https://github.com/a-sit-plus/warden), when enabling Key Attestation
verification, you must configure both Android and iOS Key Attestation verification.

#### Android Key Attestations

Variable: `KEYATTESTATIONVERIFICATION_ANDROID_APPLICATIONS_XX_PACKAGENAME`   
Description: Android Package of the Wallet.  
Default value: N/A  

Variable: `KEYATTESTATIONVERIFICATION_ANDROID_APPLICATIONS_XX_SIGNINGCERTIFICATEDIGESTS_XX`   
Description: Base64 Url-Safe encoded DER encoding of the X509 Certificate used to sign the Wallet.  
Default value: N/A  

Variable: `KEYATTESTATIONVERIFICATION_ANDROID_STRONGBOXREQUIRED`   
Description: Whether StrongBox security leve is required.  
Default value: `false`  

Variable: `KEYATTESTATIONVERIFICATION_ANDROID_UNLOCKEDBOOTLOADERALLOWED`  
Description: Whether devices with unlocked bootloaders are allowed.  
Default value: `false`  

Variable: `KEYATTESTATIONVERIFICATION_ANDROID_ROLLBACKRESISTANCEREQUIRED`  
Description: Whether rollback resistance is required.  
Default value: `false`  

Variable: `KEYATTESTATIONVERIFICATION_ANDROID_LEAFCERTIFICATEVALIDITYIGNORED`  
Description: Whether the validity of the leaf certificate is ignored.  
Default value: `false`  

Variable: `KEYATTESTATIONVERIFICATION_ANDROID_VERIFICATIONSKEW`  
Description: Tolerance added to the verification date.  
Default value: `0 seconds`  

Variable: `KEYATTESTATIONVERIFICATION_ANDROID_HARDWAREATTESTATIONENABLED`  
Description: Whether **hardware** Key Attestations are accepted.  
Default value: `true`  

Variable: `KEYATTESTATIONVERIFICATION_ANDROID_NOUGATATTESTATIONENABLED`  
Description: Whether Key Attestations generated on Devices with **Android Nougat** are accepted.  
Default value: `false`  

Variable: `KEYATTESTATIONVERIFICATION_ANDROID_SOFTWAREATTESTATIONENABLED`  
Description: Whether **software** Key Attestations are accepted.  
Default value: `false`  

**Validity of Key Attestation**

By default, Wallet Provider verifies the creation time of the Key Attestation using a default skew of `5 minutes`. You can modify the `skew` using
the following environment variable:

Variable: `KEYATTESTATIONVERIFICATION_ANDROID_ATTESTATIONSTATEMENTVALIDITY_SKEW`  
Description: How far in the past, the creation date of a Key Attestation can be.  
Default value: `5 minutes`  

To disable this check, set the environment variable `KEYATTESTATIONVERIFICATION_ANDROID_ATTESTATIONSTATEMENTVALIDITY` to `Disabled`.

##### iOS Key Attestations

Variable: `KEYATTESTATIONVERIFICATION_IOS_APPLICATIONS_XX_TEAMIDENTIFIER`   
Description: The Team Identifier of the Wallet.  
Default value: `N/A`  

Variable: `KEYATTESTATIONVERIFICATION_IOS_APPLICATIONS_XX_BUNDLEIDENTIFIER`   
Description: The Bundle Identifier of the Wallet.  
Default value: `N/A`  

Variable: `KEYATTESTATIONVERIFICATION_IOS_APPLICATIONS_XX_ENVIRONMENT`    
Description: Environment in which the Wallet is running.  
Default value: `Production`   
Allowed values:
* `Production`
* `Sandbox`

Variable: `KEYATTESTATIONVERIFICATION_IOS_ATTESTATIONSTATEMENTVALIDITY_SKEW`  
Description: How far in the past, the creation date of a Key Attestation can be.  
Default value: `5 minutes`  

### Challenge Configuration

Variable: `CHALLENGE_LENGTH`   
Description: The number of bytes a Challenge is composed of.  
Default value: `128`  

Variable: `CHALLENGE_VALIDITY`   
Description: Duration a Challenge is valid for.  
Default value: `5 minutes`  

### Wallet Application Attestation Configuration

Variable: `WALLETAPPLICATIONATTESTATION_ISSUER`  
Description: Issuer of the Wallet Application Attestations.  
Default value: `eudi-srv-wallet-provider`  

Variable: `WALLETAPPLICATIONATTESTATION_VALIDITY`  
Description: Duration a Wallet Application Attestations is valid for.  
Default value: `24 hours`  
Maximum value: `24 hours`  

Variable: `WALLETAPPLICATIONATTESTATION_WALLETNAME`  
Description: Wallet Name that will be included in the Wallet Application Attestations.  
Default value: N/A  

Variable: `WALLETAPPLICATIONATTESTATION_WALLETLINK`  
Description: Wallet Link that will be included in the Wallet Application Attestations.  
Default value: N/A  

Variable: `WALLETAPPLICATIONATTESTATION_WALLETINFORMATION_PROVIDER`  
Description: Name of the Solution Provider that will be included in the Wallet Application Attestations.  
Default value: N/A  

Variable: `WALLETAPPLICATIONATTESTATION_WALLETINFORMATION_ID`  
Description: Solution Id that will be included in the Wallet Application Attestations.  
Default value: N/A  

Variable: `WALLETAPPLICATIONATTESTATION_WALLETINFORMATION_VERSION`  
Description: Solution Version that will be included in the Wallet Application Attestations.  
Default value: N/A  

Variable: `WALLETAPPLICATIONATTESTATION_WALLETINFORMATION_CERTIFICATION`  
Description: Solution Certification Information that will be included in the Wallet Application Attestations.  
Default value: N/A  

### Duration formats

Duration types support unit strings in the following format (lower case only), with an optional space between the unit value and the unit type.

* `ns`, `nano`, `nanos`, `nanosecond`, `nanoseconds`
* `us`, `micro`, `micros`, `microsecond`, `microseconds`
* `ms`, `milli`, `millis`, `millisecond`, `milliseconds`
* `s`, `second`, `seconds`
* `m`, `minute`, `minutes`
* `h`, `hour`, `hours`
* `d`, `day`, `days`

For example, `10s`, `3 days`, or `12 hours`.

## How to contribute

We welcome contributions to this project. To ensure that the process is smooth for everyone
involved, follow the guidelines found in [CONTRIBUTING.md](CONTRIBUTING.md).

## License

### License details

Copyright (c) 2023 European Commission

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.