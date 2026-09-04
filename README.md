# GitAPK

A Git-powered open-source Android app store. 📦📱

GitAPK builds an Android app catalog from public Git repositories and their release artifacts. The catalog is separate from the Android client, so adding or updating an app does not require publishing a new client release.

## Project layout

- `android/` — Android client
- `catalog/apps/` — individual app manifests
- `catalog/catalog.json` — generated app index consumed by the client
- `catalog/sources.json` — repositories tracked by the catalog updater
- `scripts/` — catalog/update tooling
- `.github/workflows/` — Android builds, validation, and automatic catalog updates

## Installing apps

The Android client has an **Install** button for catalog entries. It downloads the APK into the app's private cache and opens Android's package installer using a secure `FileProvider` URI. On Android 8+, Android may ask you to allow GitAPK to install apps from this source the first time.

GitAPK does not silently install APKs: the normal Android package-install confirmation remains in control.

## Catalog updates

`catalog/sources.json` lists upstream GitHub repositories. The scheduled catalog workflow checks each repository's latest release, selects an APK asset, downloads it to calculate its SHA-256 checksum, and regenerates the catalog. The workflow runs daily and can also be started manually from GitHub Actions.

## Verification metadata

Every generated app entry includes a SHA-256 checksum for the APK artifact. The client can use this metadata for download verification before future installs.

## Goals

- Open-source Android apps
- GitHub/GitLab/Codeberg-friendly repository sources
- One-tap download and Android installation flow
- Release/APK metadata
- SHA-256 verification metadata
- Simple, lightweight Android client
- Search, categories, app details, updates, favorites, and custom repositories

## Status

🚧 Early development — the client, catalog format, APK installation flow, and automated GitHub release catalog are now in place.

## License

Apache-2.0
