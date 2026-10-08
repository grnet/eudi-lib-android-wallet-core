# EUDI wallet-core, GRNET fork

A fork of [eudi-lib-android-wallet-core](https://github.com/eu-digital-identity-wallet/eudi-lib-android-wallet-core)
for GRNET's wallet app,
[grnet/eudi-app-android-wallet-ui](https://github.com/grnet/eudi-app-android-wallet-ui).
Upstream's own README is [at the root](../README.md).

## Branches

| Branch | What |
| --- | --- |
| `grnet` | upstream's release it is based on, plus the changes below; releases are tagged here |
| `main` | tracks upstream `main`, unchanged |
| `v0.29.0-grnet`, `v0.30.2-grnet` | earlier experiments, built locally with `build-local.sh`; kept for reference |

## What differs from upstream

`grnet` is upstream `v0.31.0` with its own version number, a release
workflow, and these changes:

- **Sharper credential icons in the Android credential selector**
  (`0.31.0-grnet.2`, `wallet-core/.../dcapi/internal/Utils.kt`). Upstream
  shrinks each issuer logo to 48×48 px in a single bilinear step, which skips
  most of a large logo's thin lines and leaves it speckled, and Android then
  enlarges the 48 px result to about 100 px. The icon is now 144 px, and a logo
  more than twice that size is halved repeatedly before the last step. A
  candidate to send upstream.
- **TS12 card payments shown as payments in the Android credential selector**
  (`0.31.0-grnet.3`, `wallet-core/matcher`). The selector's contents come from
  the matcher, a WebAssembly program wallet-core registers with Credential
  Manager. Upstream bundles multipaz's, which shows every credential as an
  identity document. The matcher is now built from multipaz `0.101.0`'s source,
  kept in `wallet-core/matcher`, with a patch: the credential a
  `urn:eudi:sca:payment:1` payment binds to is shown as Credential Manager's
  payment entry, with the payee, the amount and the card. Its README describes
  the patch, how to build it, and its test. A candidate for multipaz.
- **Payment cards shown by their own name, number and card art**
  (`0.31.0-grnet.3`). The WE BUILD rulebook for SCA-Card (DPC) attestations,
  `rb-sca-card-dpc` §2.9 and §4.1, has the issuer deliver each card's display
  meta-data, unsigned, in the `display` array of the credential response:
  `alias`, `last_four`, `card_art`, the issuer and the network branding.
  - GRNET's OpenID4VCI library, `0.14.1-grnet.1`, passes that array through
    (upstream drops it, as OpenID4VCI 1.0 does not define it).
  - `ProcessResponse` stores it with the document before storing the document,
    as `IssuerMetadata.credentialDisplay`, through the new
    `DocumentManager.setCredentialDisplay`.
  - The Digital Credentials API registration (`CardDisplay`) then shows the
    card as its `alias`, `•••• last_four` and its `DEFAULT` card art, read from
    an HTTPS or `data:` URL, with its shape kept. A display whose network
    differs from the signed `network` claim is ignored for the claim (IR-04).
    Other credentials are shown as before.

Known limitations of these changes:

- **The matcher needs Credential Manager's payment entry.** It imports
  `credman_v2.AddPaymentEntryToSetV2`, as CMWallet's matcher does. On a phone
  whose Credential Manager lacks that function the matcher cannot load at all,
  so no credential is offered for any request, not only payments. WebAssembly
  has no fallback for a missing import.
- **The payment entry lists no claims.** The card bound to a payment is shown
  as the card, the payee and the amount, as CMWallet shows it, and not with
  the claims its query requests.
- **Deferred issuance keeps no card display.** The OpenID4VCI library passes
  the display array through for immediate issuance only.
- **Card art is downloaded on every registration**, which runs whenever a
  document is stored or deleted, without a cache, and with the platform's
  default request headers. The rulebook asks wallets to avoid identifying
  headers when fetching images; caching the icon at issuance would do both.
- **The amount in the selector is reprinted from a double**, so an amount
  beyond 15 significant digits, or in exponent form, can read differently
  from the request's literal. Ordinary amounts such as `4.70` are exact.

These are further candidates, and each would come as its own change and
release:

- relaxing HAIP's encrypted-response requirement, for verifiers using plain
  `direct_post` (on `v0.30.2-grnet`)

Presenting OpenID4VP transaction data with SD-JWT VC credentials, once a
candidate here (on `v0.29.0-grnet`), is in upstream from `v0.31.0`.

## Versions

Versions look like `<upstream version>-grnet.<N>`, e.g. `0.30.2-grnet.1`: the
upstream release the build is based on, then GRNET's release counter on that
base. A published version is never replaced, so every change, however small,
gets a new `N`. After a rebase onto a new upstream release, `N` starts again
at 1.

The version is `VERSION_NAME` in `gradle.properties`, shared by all three
artifacts.

## Releases

Published to a Maven repository on GitHub Pages:

    https://grnet.github.io/eudi-lib-android-wallet-core/maven/

All three artifacts keep upstream's group, `eu.europa.ec.eudi`:
`eudi-lib-android-wallet-core`, `eudi-lib-android-wallet-document-manager` and
`eudi-lib-android-iso18013-data-transfer`. Downloading them needs no login.
They are not signed, because signing is a Maven Central requirement and the
keys are upstream's.

To release, set `VERSION_NAME`, commit, and push a matching tag:

    git tag v0.30.2-grnet.1
    git push origin v0.30.2-grnet.1

`.github/workflows/grnet-release.yml` checks that the tag matches
`VERSION_NAME`, refuses a version that is already published, builds, and adds
the version to the `gh-pages` branch next to the earlier ones. It adds the
repository through a Gradle init script, `.github/grnet/pages-repository.init.gradle`,
so the build itself is unchanged.

To rehearse a release locally, into a directory of your choice:

    GRNET_MAVEN_DIR=/tmp/grnet-maven ./gradlew \
        --init-script .github/grnet/pages-repository.init.gradle \
        publishAllPublicationsToGrnetPagesRepository \
        -PRELEASE_SIGNING_ENABLED=false -PsignAllPublications=false \
        --no-configuration-cache

## Using a release

The app takes `*-grnet.N` versions of these artifacts from this repository
only, through an `exclusiveContent` block in its `settings.gradle.kts`, and its
version catalog pins the version. To build the app against a local rehearsal,
pass `-PgrnetMavenUrl=file:///tmp/grnet-maven`.

From `0.31.0-grnet.3` this library depends on GRNET's release of the OpenID4VCI
library, `eu.europa.ec.eudi:eudi-lib-jvm-openid4vci-kt:0.14.1-grnet.1`, from
[grnet/eudi-lib-jvm-openid4vci-kt](https://github.com/grnet/eudi-lib-jvm-openid4vci-kt)'s
own Maven repository, `https://grnet.github.io/eudi-lib-jvm-openid4vci-kt/maven/`.
This build and the app both take `*-grnet.N` versions of it from there only;
`-PgrnetOpenId4VciMavenUrl=file:///…` points either at a local rehearsal.
Release that library first.
