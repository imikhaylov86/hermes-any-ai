# Release & Testing Workflow Rules

## Connected Device Development
- Always test iteratively on the connected Android smartphone before releasing.
- Build draft debug APKs via `./gradlew assembleDebug`.
- Install strictly to User 0 (personal profile) using `scripts/install_to_device.ps1`.

## Release Builds: Strictly On Demand
- Do NOT build, tag, or publish releases automatically.
- Release builds (`assembleRelease` / `.github/workflows/release.yml`) are executed STRICTLY on explicit user request.
- When requested:
  1. Increment `versionCode` (must be greater than previous) and update `versionName` in `app/build.gradle.kts`.
  2. Build locally via `scripts/build_release.ps1` (with `-Install` if testing on device).
  3. Publish via GitHub Actions manual dispatch or git tag push.

## User Data Preservation
- Same keystore signature is required for all updates to prevent `INSTALL_FAILED_UPDATE_INCOMPATIBLE`.
- Room database migrations must preserve user chat history and session data.
- The in-app updater uses `UpdateManager`, `FileProvider`, and `REQUEST_INSTALL_PACKAGES` to update in-place seamlessly.
