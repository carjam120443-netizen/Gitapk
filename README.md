# GitAPK

A Git-powered open-source Android app store.

GitAPK is designed to build an Android app catalog from public Git repositories and their release artifacts. The project keeps catalog data separate from the Android client so new apps can be added without publishing a new client release.

## Project layout

- `android/` — Android client
- `catalog/` — app metadata and generated catalog
- `scripts/` — catalog/update tooling
- `.github/workflows/` — automated catalog validation and updates

## Goals

- Open-source Android apps
- GitHub/GitLab/Codeberg-friendly repository sources
- Release/APK metadata
- SHA-256 verification metadata
- Simple, lightweight Android client
- Community repository support in the future

## Status

🚧 Early development — initial repository structure and Android client scaffold.

## License

Apache-2.0
