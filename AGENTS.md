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

### DFM 实验单测（稳定运行指南）
在部分环境下（例如默认 `JAVA_HOME` 指向无效路径、或全局 `~/.gradle/init.gradle` 干扰），执行 `:xbmc:testDfmExperimentalDebugUnitTest` 可能在 Gradle Test Executor 启动时即崩溃（`NoClassDefFoundError: jdk/internal/reflect/GeneratedSerializationConstructorAccessor1`）。

请使用以下“隔离 + 固定 JDK17 + 必要 JVM 选项”的命令运行，能稳定规避该问题：

```
GRADLE_USER_HOME=$(pwd)/.gradle-user \
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 \
JAVA_TOOL_OPTIONS="--add-opens=java.base/java.lang=ALL-UNNAMED \
  --add-opens=java.base/jdk.internal.reflect=ALL-UNNAMED \
  --add-exports=java.base/jdk.internal.reflect=ALL-UNNAMED \
  -Djdk.reflect.useDirectMethodHandle=true" \
./gradlew :xbmc:testDfmExperimentalDebugUnitTest -i --no-daemon
```

说明与要求：
- DFM 单测目录：`xbmc/src/dfmExperimentalDebugUnitTest/java/**`（仅 Debug 变体）
- 本地 JVM 单测依赖 Robolectric：`org.robolectric:robolectric:4.12.2`
- DFM 单测类需使用 `@RunWith(RobolectricTestRunner.class)`，以获得 Android 框架 shadow 与资源支持。
- 如需覆盖率报告：`./gradlew :xbmc:jacocoDfmExperimentalDebugUnitTestReport`

#### 2025-11 更新（已内建到构建脚本）
- 构建脚本已为所有 JVM 单测自动注入必要 JVM 选项并固定 JDK 17 toolchain：一般情况下无需再手动设置 `JAVA_TOOL_OPTIONS`，仅确保使用 JDK 17 即可。
- 为避免极端环境下 Gradle Test Worker 早期反序列化问题，默认关闭了单测 Jacoco；如需覆盖率，请追加参数 `-PenableJacocoTest=true`：
  - `GRADLE_USER_HOME=$(pwd)/.gradle-user JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./gradlew -PenableJacocoTest=true :xbmc:jacocoDfmExperimentalDebugUnitTestReport`
- Robolectric 4.12.2 最高支持到 API 34，而模块 `targetSdk=36`。为保证兼容，已做如下处理：
  - 在所有 DFM 单测类上添加 `@Config(sdk = 34, manifest = Config.NONE)`；
  - 在构建脚本中设置 `robolectric.enabledSdks=34`；
  - 额外提供兜底文件：`xbmc/src/dfmExperimentalDebugUnitTest/resources/robolectric.properties`（内容 `sdk=34`）。
- DFM 单测使用单 Worker 运行（`maxParallelForks=1`），减少并发序列化带来的不确定性。

推荐运行命令（无需额外 `JAVA_TOOL_OPTIONS`）：
```
GRADLE_USER_HOME=$(pwd)/.gradle-user \
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 \
./gradlew :xbmc:testDfmExperimentalDebugUnitTest --tests "org.xbmc.kodi.danmaku.*Test" -i --no-daemon
```

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

## Strict Directory Layout (Spec Authors Read First)

严禁“文件乱放”。以下布局为唯一有效放置规则，规范适用于 specs/ 与任何自动化任务生成的文件：

- Module root: `xbmc/`
  - Manifest: `xbmc/AndroidManifest.xml`
  - Main sources: 仅使用 `xbmc/src/`
    - 主代码（所有上游一致逻辑）放置于：
      - `xbmc/src/channels/**`
      - `xbmc/src/content/**`
      - `xbmc/src/interfaces/**`
      - `xbmc/src/model/**`
      - `xbmc/src/util/**`
      - 以及上述包下的 Java/Kotlin 源码（包名以 `org.xbmc.kodi` 开头）。
    - 禁止：在 `xbmc/java/**` 下新增或复制任何源码（避免与 `xbmc/src/**` 重复编译导致 duplicate class）。主源码集只读取 `src`，不读取 `java`。
  - Flavor: 仅在 `dfmExperimental` 变体下注入弹幕相关代码与资源
    - 代码：`xbmc/src/dfmExperimental/java/**`
    - 资源：`xbmc/src/dfmExperimental/res/**`
    - 要求：任何弹幕功能相关类均不得放入主源码路径（`xbmc/src/**` 的非 flavor 子目录），否则会污染 vanilla 变体。
  - Unit tests:
    - 通用单元测试：`xbmc/src/test/java/**`
    - `dfmExperimental` Debug 变体单测：`xbmc/src/dfmExperimentalDebugUnitTest/java/**`
    - 禁止：将测试代码放入主源码集（例如 `xbmc/src/...` 非 `test/` 或 `*Test/` 目录）。主源码集明确排除了 `dfmExperimentalDebugUnitTest/**` 与 `dfmExperimental/**`（测试/实验代码不会被 vanilla 主编译拾取）。
  - Resources: `xbmc/res/**`
    - 资源文件采用 `lower_snake_case` 命名；XML 属性按 id → layout → appearance → behavior 顺序。
  - Assets: `xbmc/assets/**`
  - JNI libs: `xbmc/lib/arm64-v8a/**`
    - 不要将未裁剪的 `libkodi.unstripped.so` 打进 APK，已在 Gradle 中排除。

放置检查清单（提交前自查）：
- 不在 `xbmc/java/**` 放任何新文件。
- `dfmExperimental` 代码与资源仅在 `xbmc/src/dfmExperimental/**` 之下。
 - `dfmExperimental` 单测仅在 `xbmc/src/dfmExperimentalDebugUnitTest/**` 之下。
- 主源码集不包含任何 `*Test` 目录；如新增测试，仅放入 `test` 或 `dfmExperimentalTest`。
- 新增 JNI 库仅放在 `xbmc/lib/<ABI>/`，且名称不与排除模式冲突。

构建命令与验证：
- Vanilla（上游一致）：`./gradlew :xbmc:assembleVanillaDebug -x lint`
- DFM 实验变体：`./gradlew :xbmc:assembleDfmExperimentalDebug -x lint`
- DFM 单测：`./gradlew :xbmc:testDfmExperimentalDebugUnitTest`
- Jacoco 报告：`./gradlew :xbmc:jacocoDfmExperimentalDebugUnitTestReport`
