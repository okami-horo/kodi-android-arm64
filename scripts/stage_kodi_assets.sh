#!/usr/bin/env bash
set -euo pipefail

# Stage Kodi runtime assets and native libs into this repo before Gradle packaging.
# Priority of sources (auto-detected; override by env):
#  1) KODI_OUTPUT: <kodi>/output/android-arm64/{assets,jniLibs}
#  2) KODI_BUILD_DIR + KODI_SRC: <kodi>/build.android.arm64 (libkodi.so, system) + <kodi>/media
#  3) KODI_APK_URL: download official Kodi arm64 APK and extract assets + libs
#
# Required after staging (will fail if missing):
#  - xbmc/assets/system/settings/settings.xml
#  - xbmc/assets/media/splash.(jpg|png)
#  - xbmc/assets/addons/audioencoder.kodi.builtin.aac/addon.xml
#  - xbmc/lib/arm64-v8a/libkodi.so

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT_DIR"

log() { echo "[stage] $*"; }
die() { echo "[stage][ERROR] $*" >&2; exit 1; }

require_tools() {
  for t in curl unzip rsync; do
    command -v "$t" >/dev/null 2>&1 || die "missing tool: $t"
  done
}

copy_if_dir() {
  local src="$1" dst="$2"
  if [ -d "$src" ]; then
    mkdir -p "$dst"
    rsync -a --delete "$src"/ "$dst"/
    return 0
  fi
  return 1
}

stage_from_kodi_output() {
  local out="$1"
  log "staging from KODI_OUTPUT=$out"
  copy_if_dir "$out/assets" xbmc/assets || die "missing $out/assets"
  copy_if_dir "$out/jniLibs/arm64-v8a" xbmc/lib/arm64-v8a || die "missing $out/jniLibs/arm64-v8a"
}

stage_from_kodi_build() {
  local build_dir="$1" kodi_src="$2"
  log "staging from KODI_BUILD_DIR=$build_dir, KODI_SRC=$kodi_src"
  copy_if_dir "$build_dir/system" xbmc/assets/system || die "missing $build_dir/system"
  mkdir -p xbmc/lib/arm64-v8a
  [ -f "$build_dir/libkodi.so" ] || die "missing $build_dir/libkodi.so"
  rsync -a "$build_dir/libkodi.so" xbmc/lib/arm64-v8a/
  # addons from output if available
  if [ -d "$kodi_src/output/android-arm64/assets" ]; then
    find "$kodi_src/output/android-arm64/assets" -maxdepth 2 -type f -name addon.xml | while read -r addonxml; do
      addon_dir="$(dirname "$addonxml")"; name="$(basename "$addon_dir")"
      rsync -a --delete "$addon_dir/" "xbmc/assets/addons/$name/"
    done
  fi
  # media (splash, fonts)
  copy_if_dir "$kodi_src/media" xbmc/assets/media || true
}

stage_from_apk() {
  local url="$1"; [ -n "$url" ] || die "KODI_APK_URL is empty"
  require_tools
  local tmp; tmp="$(mktemp -d)"
  log "downloading Kodi APK: $url"
  curl -fL --retry 3 -o "$tmp/kodi.apk" "$url"
  log "extracting"
  unzip -q -o "$tmp/kodi.apk" -d "$tmp/apk"
  copy_if_dir "$tmp/apk/assets" xbmc/assets || die "APK has no assets/"
  copy_if_dir "$tmp/apk/lib/arm64-v8a" xbmc/lib/arm64-v8a || die "APK has no arm64-v8a libs"
  rm -rf "$tmp"
}

main() {
  mkdir -p xbmc/assets xbmc/lib/arm64-v8a

  if [ -n "${KODI_OUTPUT:-}" ] && [ -d "$KODI_OUTPUT" ]; then
    stage_from_kodi_output "$KODI_OUTPUT"
  elif [ -n "${KODI_BUILD_DIR:-}" ] && [ -d "$KODI_BUILD_DIR" ] && [ -n "${KODI_SRC:-}" ] && [ -d "$KODI_SRC" ]; then
    stage_from_kodi_build "$KODI_BUILD_DIR" "$KODI_SRC"
  elif [ -n "${KODI_APK_URL:-}" ]; then
    stage_from_apk "$KODI_APK_URL"
  else
    die "no staging source. Set KODI_OUTPUT or (KODI_BUILD_DIR+KODI_SRC) or KODI_APK_URL"
  fi

  # Ensure media splash exists; try to backfill from KODI_SRC/media if missing
  if ! ls xbmc/assets/media/splash.* >/dev/null 2>&1; then
    if [ -n "${KODI_SRC:-}" ] && [ -d "$KODI_SRC/media" ]; then
      copy_if_dir "$KODI_SRC/media" xbmc/assets/media || true
    fi
  fi

  # Generate colors file to avoid merge conflicts seen during initial build
  mkdir -p xbmc/res/values
  cat > xbmc/res/values/colors.generated.xml <<'EOF'
<?xml version="1.0" encoding="utf-8"?>
<resources>
  <color name="principal_color">#31AFE1</color>
  <color name="recommendation_color">#31AFE1</color>
</resources>
EOF

  # Validate critical paths
  ls xbmc/lib/arm64-v8a/libkodi.so >/dev/null 2>&1 || die "libkodi.so not staged"
  ls xbmc/assets/system/settings/settings.xml >/dev/null 2>&1 || die "system/settings.xml not staged"
  ls xbmc/assets/addons/audioencoder.kodi.builtin.aac/addon.xml >/dev/null 2>&1 || die "AAC addon not staged"
  ls xbmc/assets/media/splash.* >/dev/null 2>&1 || die "media/splash.(jpg|png) not staged"

  log "staging complete"
}

main "$@"

