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

Nothing functional yet. `grnet` is upstream `v0.31.0` with its own version
number and a release workflow.

These changes are candidates, and each would come as its own change and
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
