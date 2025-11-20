# Repository Guidelines

## Project Structure & Module Organization
- Root: `Makefile`, `build.gradle`, `settings.gradle`, Gradle wrapper (`gradlew`, `gradle/`).
- Android module: `xbmc/` (manifest, Java sources in `xbmc/src`, resources in `xbmc/res`, packaged assets in `xbmc/assets`).
- Native/packaging: handled via `Makefile` targets; outputs APKs and bundles shared libs into `xbmc/lib/<ABI>/`.

## Build, Test, and Development Commands
- Build APK (Makefile, includes assets/libs): `make apk` — runs clean, stages assets, and triggers Gradle assemble/sign.
- Gradle build (module only): `./gradlew :xbmc:assembleDebug` — faster Java/Android build without native staging.
- Lint: `./gradlew :xbmc:lint` — Android Lint report under `xbmc/build/reports/`.
- Unit tests (if present): `./gradlew :xbmc:testDebugUnitTest`.
- Install to device: `adb install -r xbmc/build/outputs/apk/debug/xbmc-debug.apk`.
Notes: `make apk` expects Android SDK/NDK envs; see Makefile vars like `SDKROOT`, `TOOLCHAIN`, `PREFIX`, `DEPENDS_PATH`.

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

