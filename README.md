# @shyware/sdk-android

Kotlin/Gradle package for the Shyware SDK — Android client.

> **Early access.** The `DPIAHelpers` module ships DPIA stack-6 protocol verification helpers. A full consumer-facing Android SDK is in development.

## What it does

The Kotlin domain clients accept unsigned configuration (`signing.required: false`,
`signing.backend: "none"`). KMS/HSM is optional for period-close attestation;
identity and device authentication are separate concerns.

Beacon-conditioned voting IDs and receipt checks use SHA-256 over decoded beacon
bytes followed by decoded nonce bytes. Pass the original `beaconBlockHash` to
`buildBallot` and `verifyReceipt`; a missing or malformed beacon is rejected.
Legacy receipts without the original beacon cannot be verified through this path.

`castBallot` uses the server-built submission returned by `/api/vote/cast`. If that
endpoint returns only an opaque canonical ballot ID, the result has empty nonce,
beacon and transaction fields, and no recoverable receipt is saved. A receipt is
saved only when the server returns matching beacon/nonce material and posture
permits retention. Local draft material is never substituted for a server receipt.

Provides `DPIAHelpers` — a Kotlin library of assertion utilities used to verify structural invariant compliance (two-list write, rejection predicate, count-match) against a live or mock Shyware node from Android/JVM test suites.

The stack-6 DPIA evidence suite runs 14 suites, 385 assertions covering the write-kernel, vote-write, and cover-traffic families.

## Requirements

- Kotlin 1.9+
- Android Gradle Plugin 8+
- minSdk 26 / compileSdk 34

## Installation

### Gradle (Kotlin DSL)

```kotlin
dependencies {
    implementation("com.shyware:sdk-android:0.4.0")
}
```

Add the Shyware Maven registry to your `settings.gradle.kts`:

```kotlin
dependencyResolutionManagement {
    repositories {
        maven("https://maven.shyware.fyi/releases")
        mavenCentral()
    }
}
```

## Quick start

```kotlin
import com.shyware.dpia.DPIARunner
import com.shyware.dpia.Suite

// Verify rejection predicate over a test node
val result = DPIARunner.run(nodeUrl = nodeUrl, suite = Suite.WRITE_KERNEL)
check(result.passed == result.total)
```

Full documentation: [docs.shyware.fyi](https://docs.shyware.fyi)

## License

Evaluation use only. Production deployment requires a Commercial License.
See [LICENSE](./LICENSE) and [shyware.fyi/legal](https://shyware.fyi/legal/).

Patent Pending, U.S. App. No. 64/074,348.
Copyright © 2026 Nicholas Carducci / Shyware LLC.
