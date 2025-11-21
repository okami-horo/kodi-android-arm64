# 本仓文件来源与上游编译产物映射（xbmc/xbmc → kodi-android-arm64）

本文档聚焦“本项目中的各个文件是如何由上游 xbmc/xbmc 的编译或打包流程产生的”。不涉及 APK 打包流程本身（见 docs/UPSTREAM_BUILD.md）。

## 术语与路径
- 上游源码根：`<KODI_SRC>`（例如 `/workspace/xbmc`）
- 上游 Android 构建输出：`<KODI_OUTPUT>`（例如 `<KODI_SRC>/output/android-arm64`）
- 上游构建目录：`<KODI_BUILD_DIR>`（例如 `<KODI_SRC>/build.android.arm64`）
- 本仓 Android 模块：`/workspace/kodi-android-arm64/xbmc`

## 一、直接来自上游构建产物（复制/提取）

这些文件在上游完成配置与构建后，会出现在标准输出目录或构建目录中，本仓仅做复制与最小整理。

- `xbmc/assets/**`
  - 来源：
    - 首选：`<KODI_OUTPUT>/assets/**`
    - 备选：`<KODI_BUILD_DIR>/system/**`（再补充 `<KODI_SRC>/media/**` 与 `output/android-arm64/assets/addons/*`）
  - 内容：Kodi 运行时资源与内置插件（system、addons、language、media、webinterface 等）。
  - 本仓额外处理（按 Makefile/scripts）：
    - 清理 `assets` 下的 `.git/` 目录；
    - 移除 `assets` 中误混入的 `*.so`（原生库应放入 `xbmc/lib/arm64-v8a`）；
    - 对皮肤 `skin.*` 的 `media/*` 目录，仅保留 `.xbt` 资源（去除原始图像以减小体积）；
    - 复制上游根的 `privacy-policy.txt` 至 `xbmc/assets/`。

- `xbmc/lib/arm64-v8a/**`
  - `libkodi.so`
    - 来源：`<KODI_OUTPUT>/jniLibs/arm64-v8a/libkodi.so` 或 `<KODI_BUILD_DIR>/libkodi.so`。
  - 其他 `.so`
    - 部分随 Kodi/内置插件构建，在上游 `output/android-arm64/jniLibs/arm64-v8a/` 或相关前缀路径下可见；
    - `libc++_shared.so` 源自 Android NDK（上游/本仓按需捆绑，用于 C++ 运行时）；
    - 某些依赖库（如 `libshairplay.so`）来自上游 depends 体系的构建产物（`<PREFIX>/lib/`）。
  - 本仓额外处理（按 Makefile）：
    - 将非以 `lib` 开头的 `.so` 统一重命名为 `lib*.so`（兼容旧版打包习惯）；
    - 用 `strip` 去除符号表后再入包；
    - 需要时从 NDK sysroot 回填 `libc++_shared.so`。

## 二、来自上游 Android 包装模板（tools/android/packaging/xbmc）

这些文件在上游位于 `tools/android/packaging/xbmc/`，通常以 `.in` 模板或现成资源形式提供，构建时经 CMake/变量替换生成。迁移到本仓时已是“展开后”的结果。

- 配置与清单
  - `xbmc/AndroidManifest.xml` ← `<KODI_SRC>/tools/android/packaging/xbmc/AndroidManifest.xml.in`
  - `xbmc/build.gradle` ← `<KODI_SRC>/tools/android/packaging/xbmc/build.gradle.in`
  - `xbmc/colors.xml` ← `<KODI_SRC>/tools/android/packaging/xbmc/colors.xml.in`
  - `xbmc/strings.xml` ← `<KODI_SRC>/tools/android/packaging/xbmc/strings.xml.in`
  - `xbmc/searchable.xml` ← `<KODI_SRC>/tools/android/packaging/xbmc/searchable.xml.in`

- 包装层 Java 源码（Android 外壳）
  - `xbmc/src/**` ← `<KODI_SRC>/tools/android/packaging/xbmc/src/org/xbmc/kodi/**`
  - 示例：`xbmc/src/XBMCJsonRPC.java`、`xbmc/src/XBMCSettingsContentObserver.java`、`xbmc/src/XBMCVideoView.java` 等。

- 资源文件
  - `xbmc/res/**` ← `<KODI_SRC>/tools/android/packaging/xbmc/res/**`
  - 包含 `drawable*/`、`values*/`（多语言字符串）、`layout/`、`xml/` 等。

- NDK 配置
  - `xbmc/jni/Android.mk` ← `<KODI_SRC>/tools/android/packaging/xbmc/jni/Android.mk`

- 注意：上述 `.in` 模板在上游构建时会用变量替换（如 `@APP_PACKAGE@`、`@TARGET_SDK@` 等）。迁移到本仓时，这些变量已被固化为具体值（例如 `org.xbmc.kodi`、`compileSdk=36`）。

## 三、本仓在同步过程生成/重组的文件

- `xbmc/java/**`
  - 来源：由本仓 Makefile 的 `java` 目标将 `xbmc/src/**` 拷贝至包名路径（`xbmc/java/org.xbmc.kodi/`）。
  - 说明：`build.gradle` 同时包含 `src` 与 `java`，但以 `src` 为准；`java` 目录主要为历史兼容。

- `xbmc/res/**` 的部分素材
  - 例如 `res/drawable(-xxxhdpi)/applaunch_screen.png` 可能由本仓 Makefile 从 `<KODI_SRC>/media/applaunch_screen.png` 回填或覆盖，以保持启动视觉一致。

- `xbmc/assets/addon-manifest.xml`
  - 上游打包步骤会生成该清单以描述已包含的内置插件集合；迁移到本仓时作为 assets 顶层文件一并保留。

## 四、本仓自有/非上游编译产物（便于独立维护）

- 顶层工程脚手架：`build.gradle`、`settings.gradle`、`gradle/`、`gradlew`、`gradle.properties`
  - 作用：将上游 Android 包装层与运行时产物组装为可独立构建的单模块工程。
- CI：`.github/workflows/*.yml`（配置 SDK/NDK/签名并生成 Debug APK）
- 辅助脚本：`scripts/stage_kodi_assets.sh`（从上游 `output/`、`build/` 或官方 APK 提取 assets/so）
- 打包整合：`Makefile`（与上游 depends 产物联动、拷贝与清理资源、对齐签名）
- 文档与说明：`AGENTS.md`、`docs/UPSTREAM_BUILD.md`、本文件等

## 五、快速反查上游路径（常见文件示例）

- `xbmc/src/XBMCJsonRPC.java` → `tools/android/packaging/xbmc/src/org/xbmc/kodi/XBMCJsonRPC.java`
- `xbmc/src/XBMCSettingsContentObserver.java` → `tools/android/packaging/xbmc/src/org/xbmc/kodi/XBMCSettingsContentObserver.java`
- `xbmc/AndroidManifest.xml` → `tools/android/packaging/xbmc/AndroidManifest.xml.in`
- `xbmc/build.gradle` → `tools/android/packaging/xbmc/build.gradle.in`
- `xbmc/res/values-*/strings.xml` → `tools/android/packaging/xbmc/res/values-*/strings.xml`
- `xbmc/assets/system/**` → `<KODI_OUTPUT>/assets/system/**` 或 `<KODI_BUILD_DIR>/system/**`
- `xbmc/lib/arm64-v8a/libkodi.so` → `<KODI_OUTPUT>/jniLibs/arm64-v8a/libkodi.so` 或 `<KODI_BUILD_DIR>/libkodi.so`

## 六、更新建议（当上游变更时）

1) 运行脚本同步运行时与库：
   - 优先使用 `<KODI_OUTPUT>`：`export KODI_OUTPUT=<KODI_SRC>/output/android-arm64 && ./scripts/stage_kodi_assets.sh`
   - 或使用 `<KODI_BUILD_DIR> + <KODI_SRC>`：`export KODI_BUILD_DIR=...; export KODI_SRC=...; ./scripts/stage_kodi_assets.sh`
2) 若上游 `tools/android/packaging/xbmc` 模板/源码有变：
   - 对应更新本仓 `xbmc/AndroidManifest.xml`、`xbmc/build.gradle`、`xbmc/src/**`、`xbmc/res/**`、`xbmc/jni/**`；
   - 如有新增变量或配置项，请同步到根工程与 CI（AGP/SDK/NDK 版本）。
3) 本仓 `Makefile`/脚本可能需调整（若上游目录结构或产物命名发生变化）。

以上映射可帮助你判断：某个本地文件来自上游的“编译产物/打包模板”，还是本仓为独立工程新增的“包装与自动化”。如需针对特定文件进一步溯源，请告知具体路径。

