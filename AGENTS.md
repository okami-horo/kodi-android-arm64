# Repository Guidelines

## Project Structure & Module Organization
- Root: `Makefile`, `build.gradle`, `settings.gradle`, Gradle wrapper (`gradlew`, `gradle/`).
- Android module: `xbmc/` (manifest, Java sources in `xbmc/src`, resources in `xbmc/res`, packaged assets in `xbmc/assets`).
- Native/packaging: handled via `Makefile` targets; outputs APKs and bundles shared libs into `xbmc/lib/<ABI>/`.

## Build, Test, and Development Commands
- Build APK via Makefile: `make apk` — runs clean, stages assets, and triggers Gradle assemble/sign.
- Lint: `./gradlew :xbmc:lint` — Android Lint report under `xbmc/build/reports/`.
- Unit tests (if present): `./gradlew :xbmc:testDebugUnitTest`.
Notes: `make apk` expects Android SDK/NDK envs; see Makefile vars like `SDKROOT`, `TOOLCHAIN`, `PREFIX`, `DEPENDS_PATH`. For module-only builds and install/run commands, see the Android Packaging Quick Guide below.

## Coding Style & Naming Conventions
- Java: 4-space indent, UTF-8, max line ~120. Classes `UpperCamelCase`; methods/fields `lowerCamelCase`; constants `UPPER_SNAKE_CASE`.
- Android resources: files and IDs `lower_snake_case` (e.g., `activity_main.xml`, `ic_play.xml`).
- XML: attributes ordered logically (id → layout → appearance → behavior). Prefer AndroidX APIs.

## Testing Guidelines
- Prefer JUnit tests under `xbmc/src/test/java` and instrumented tests under `xbmc/src/androidTest/java`.
- Name tests mirroring classes: `MainTest.java`, method names `methodName_condition_expected()`.
- Target ≥80% coverage for new logic; run `:xbmc:testDebugUnitTest` locally and fix lint warnings.

## Commit & Pull Request Guidelines
- Commits: imperative mood, small and focused. Conventional Commits encouraged (e.g., `feat(xbmc): add search intent` / `fix(build): align signing config`).
- Branches: `type/short-topic` (e.g., `feat/media-session`). Reference issues in body (`Closes #123`).
- PRs: include summary, rationale, build/lint status, steps to verify, and screenshots for UI changes.
- Use GitHub CLI: `gh pr create -t "feat: ..." -b "..." -B main -H your-branch` and `gh pr view --web`.

## Security & Configuration Tips
- Do not commit keystores or secrets. Use Makefile env vars (`KODI_ANDROID_*`) locally.
- Validate APKs on a clean device/emulator; verify permissions declared in `xbmc/AndroidManifest.xml`.

## Local Debug Signing Env (Safe to Document)
The following environment variables configure debug signing for local and CI builds. They use the Android default debug keystore and are not production secrets:
```
export KODI_ANDROID_STORE_FILE="$HOME/.android/debug.keystore"
export KODI_ANDROID_STORE_PASSWORD=android
export KODI_ANDROID_KEY_ALIAS=androiddebugkey
export KODI_ANDROID_KEY_PASSWORD=android
```
Notes:
- These values are for Debug builds only. For Release builds, use a private signing key injected via GitHub Secrets.
- The CI workflow restores/validates the keystore from Secrets. If Secrets are unavailable (e.g., from forks), it falls back to the local debug keystore behavior.

## Android Packaging (arm64) — Quick Guide

This repo ships a Gradle Android module (`xbmc/`) and a Makefile-based flow to stage assets/native libs. Use the module build for fast iteration, or `make apk` for full packaging.

Prerequisites
- Java 17 (Temurin recommended)
- Android Gradle Plugin 8.11.1 (see root `build.gradle`)
- Android SDK: platform `android-36`, build-tools `36.0.0` (optionally `35.0.0`)
- NDK: `28.2.13676358` only if you build native code via `make apk` (module build uses prebundled `jniLibs` under `xbmc/lib/arm64-v8a/`)

Signing (debug keystore)
See "Local Debug Signing Env (Safe to Document)" for the canonical environment variables used by local and CI debug builds.

Fast module build (no native staging)
```
./gradlew :xbmc:assembleDebug -x lint
```
Output: `xbmc/build/outputs/apk/debug/xbmc-debug.apk`

Full packaging (stage assets + native libs)
```
make apk
```
Makefile expects SDK/NDK paths via envs such as `SDKROOT`, and native toolchain/depends via `TOOLCHAIN`, `PREFIX`, `DEPENDS_PATH`. The `package` target calls `./gradlew assemble$(BUILD_TYPE)` and copies the APK to `${CMAKE_SOURCE_DIR}/kodiapp-$(CPU)-$(BUILD_TYPE_LC).apk`.

Install and run
```
adb install -r xbmc/build/outputs/apk/debug/xbmc-debug.apk
adb shell monkey -p org.xbmc.kodi -c android.intent.category.LAUNCHER 1
```

Notes
- Module config: `compileSdk=36`, `targetSdk=36`, `minSdk=24`; Java toolchain 17.
- JNI libs are taken from `xbmc/lib/arm64-v8a/` (via `jniLibs.srcDirs = ['lib']`).
- To avoid interference from a global `~/.gradle/init.gradle`, you can set a per-repo user home: `GRADLE_USER_HOME=$(pwd)/.gradle-user ./gradlew …`.

## Active Technologies
- Java 17（Android 工具链），AGP 8.11.1 + DanmakuFlameMaster `com.github.ctiao:dfm:0.9.25`；AndroidX (001-dfm-integration)
- SharedPreferences（按 MediaKey 持久化轨与配置）；无数据库 (001-dfm-integration)

## Recent Changes
- 001-dfm-integration: Added Java 17（Android 工具链），AGP 8.11.1 + DanmakuFlameMaster `com.github.ctiao:dfm:0.9.25`；AndroidX
