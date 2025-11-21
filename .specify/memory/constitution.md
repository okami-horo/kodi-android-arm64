<!--
Sync Impact Report
- Version change: n/a → 1.0.0
- Modified principles: [initialized from template → concrete]
- Added sections: Core Principles (5 named), Additional Constraints & Standards,
  Development Workflow & Quality Gates, Governance
- Removed sections: None
- Templates requiring updates:
  - .specify/templates/plan-template.md ✅ updated
  - .specify/templates/spec-template.md ✅ no changes needed (aligned)
  - .specify/templates/tasks-template.md ✅ updated
  - .specify/templates/commands/* (absent) n/a
- Runtime docs reviewed:
  - AGENTS.md ✅ aligned
  - docs/UPSTREAM_ORIGINS.md ✅ referenced
- Follow-up TODOs: None
-->

# kodi-android-arm64 Constitution

## Core Principles

### I. Upstream Fidelity (NON-NEGOTIABLE)
Packaging must mirror upstream Kodi artifacts and Android packaging templates
without functional divergence. Any local changes are limited to packaging,
staging, signing, and configuration required to produce an installable APK.
All mappings to upstream sources MUST be maintained in docs/UPSTREAM_ORIGINS.md
and updated together with changes. Rationale: minimizes maintenance burden and
ensures behavior parity with upstream.

### II. Reproducible, Pinned Build Environment
Builds MUST be reproducible using pinned toolchain versions documented in
AGENTS.md: Java 17, AGP 8.11.1, Android SDK platform android-36 with
build-tools 36.0.0 (35.0.0 acceptable if documented), and NDK 28.2.13676358
when native staging is performed. Use `GRADLE_USER_HOME=$(pwd)/.gradle-user`
to avoid global init.gradle interference. Rationale: consistent outputs across
local and CI environments.

### III. Fast Module Build; Full Packaging via Make
Daily iteration SHOULD use the module build with prebundled JNI libs:
`./gradlew :xbmc:assembleDebug -x lint`. Full packaging that stages assets and
native libs MUST use `make apk`. JNI libs live under `xbmc/lib/arm64-v8a/` and
assets under `xbmc/assets/`. Rationale: optimize for developer speed while
guaranteeing correct release packaging.

### IV. Security & Signing Hygiene
No keystores or secrets may be committed. Debug builds MUST rely on the
documented environment variables (KODI_ANDROID_*). Release signing MUST use
secrets injected via CI. Manifest permissions MUST be reviewed during PRs.
Rationale: protect credentials and uphold least-privilege packaging.

### V. Quality Gates: Lint, Tests, Installability
Android Lint MUST be clean (or justified with tracked issues). New Java/Kotlin
logic MUST include unit tests targeting ≥80% coverage for the added code. Every
packaging change MUST be validated by installing the produced APK and launching
the app on a clean device/emulator (e.g., `adb install -r …` then monkey one
event). Rationale: maintain baseline correctness and developer confidence.

## Additional Constraints & Standards

- Coding style: Java 4-space indent; classes UpperCamelCase; methods/fields
  lowerCamelCase; constants UPPER_SNAKE_CASE. Android resources use
  lower_snake_case. Prefer AndroidX APIs.
- Module config: compileSdk=36, targetSdk=36, minSdk=24; Java toolchain 17.
- JNI libs taken from `xbmc/lib/arm64-v8a/` via `jniLibs.srcDirs = ['lib']`.
- Do not modify upstream functional code in this repository beyond packaging and
  integration glue. Any deviation MUST link to upstream rationale and mapping
  updates in docs/UPSTREAM_ORIGINS.md.
- CI and local builds MUST document exact commands used (`make apk`, Gradle
  assemble tasks), and store reports under `xbmc/build/`.

## Development Workflow & Quality Gates

- Build quickly with `./gradlew :xbmc:assembleDebug -x lint`; run full
  packaging with `make apk` when staging or releasing.
- Lint and tests: `./gradlew :xbmc:lint` and
  `./gradlew :xbmc:testDebugUnitTest` for new logic.
- Install/run check: install produced APK on emulator/device and launch using
  `adb shell monkey -p org.xbmc.kodi -c android.intent.category.LAUNCHER 1`.
- PRs follow Conventional Commits; include build/lint status and verification
  steps (including install/run check for packaging changes).
- Upstream mapping: any file sourced from upstream MUST reference its origin in
  docs/UPSTREAM_ORIGINS.md and be updated in lockstep when upstream changes.
- Use GitHub CLI for PRs where convenient; branch naming `type/short-topic`.

## Governance

- Superseding policy: This constitution governs development in this repository
  and supersedes ad-hoc practices.
- Amendment procedure: Propose edits via PR modifying this file. Require at
  least one maintainer approval and a note in the PR describing impact and any
  required migrations. Update docs/UPSTREAM_ORIGINS.md and templates if
  impacted by the change.
- Versioning policy (for this document): semantic versioning
  MAJOR.MINOR.PATCH per rules below. MAJOR for incompatible removals or
  redefinitions; MINOR for added/expanded principles; PATCH for clarifications.
- Compliance review: All PRs MUST include a short "Constitution Check" summary
  addressing Principles I–V. Reviewers block merges if any gate fails or lacks
  justification.

**Version**: 1.0.0 | **Ratified**: 2025-11-21 | **Last Amended**: 2025-11-21
