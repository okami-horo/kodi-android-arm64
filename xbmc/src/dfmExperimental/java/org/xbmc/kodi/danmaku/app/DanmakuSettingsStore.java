package org.xbmc.kodi.danmaku.app;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;

import org.xbmc.kodi.danmaku.model.DanmakuConfig;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Central storage for danmaku UI preferences and helpers to map them to DanmakuConfig.
 */
final class DanmakuSettingsStore {
    static final String PREFS_NAME = "danmaku_settings";
    static final String KEY_ENABLED = "danmaku_enabled";
    static final String KEY_TEXT_SCALE = "danmaku_text_scale";
    static final String KEY_SPEED = "danmaku_speed";
    static final String KEY_ALPHA = "danmaku_alpha";
    static final String KEY_MAX_ON_SCREEN = "danmaku_max_on_screen";
    static final String KEY_KEYWORDS = "danmaku_keyword_filter";
    static final String KEY_TYPE_SCROLL = "danmaku_type_scroll";
    static final String KEY_TYPE_TOP = "danmaku_type_top";
    static final String KEY_TYPE_BOTTOM = "danmaku_type_bottom";
    static final String KEY_TYPE_POSITIONED = "danmaku_type_positioned";
    static final String KEY_OFFSET = "danmaku_offset";

    private static final int DEFAULT_TEXT_SCALE = 100;
    private static final int DEFAULT_SPEED = 100;
    private static final int DEFAULT_ALPHA = 100;
    private static final int DEFAULT_MAX_ON_SCREEN = 0;
    private static final int DEFAULT_OFFSET = 0;

    private DanmakuSettingsStore() {
    }

    static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    static void ensureDefaults(SharedPreferences prefs) {
        SharedPreferences.Editor editor = null;
        if (!prefs.contains(KEY_ENABLED)) {
            editor = editor(prefs, editor).putBoolean(KEY_ENABLED, true);
        }
        if (!prefs.contains(KEY_TEXT_SCALE)) {
            editor = editor(prefs, editor).putInt(KEY_TEXT_SCALE, DEFAULT_TEXT_SCALE);
        }
        if (!prefs.contains(KEY_SPEED)) {
            editor = editor(prefs, editor).putInt(KEY_SPEED, DEFAULT_SPEED);
        }
        if (!prefs.contains(KEY_ALPHA)) {
            editor = editor(prefs, editor).putInt(KEY_ALPHA, DEFAULT_ALPHA);
        }
        if (!prefs.contains(KEY_MAX_ON_SCREEN)) {
            editor = editor(prefs, editor).putInt(KEY_MAX_ON_SCREEN, DEFAULT_MAX_ON_SCREEN);
        }
        if (!prefs.contains(KEY_KEYWORDS)) {
            editor = editor(prefs, editor).putString(KEY_KEYWORDS, "");
        }
        if (!prefs.contains(KEY_TYPE_SCROLL)) {
            editor = editor(prefs, editor).putBoolean(KEY_TYPE_SCROLL, true);
        }
        if (!prefs.contains(KEY_TYPE_TOP)) {
            editor = editor(prefs, editor).putBoolean(KEY_TYPE_TOP, true);
        }
        if (!prefs.contains(KEY_TYPE_BOTTOM)) {
            editor = editor(prefs, editor).putBoolean(KEY_TYPE_BOTTOM, true);
        }
        if (!prefs.contains(KEY_TYPE_POSITIONED)) {
            editor = editor(prefs, editor).putBoolean(KEY_TYPE_POSITIONED, true);
        }
        if (!prefs.contains(KEY_OFFSET)) {
            editor = editor(prefs, editor).putInt(KEY_OFFSET, DEFAULT_OFFSET);
        }
        if (editor != null) {
            editor.apply();
        }
    }

    private static SharedPreferences.Editor editor(SharedPreferences prefs, SharedPreferences.Editor current) {
        return current == null ? prefs.edit() : current;
    }

    static DanmakuConfig buildConfig(SharedPreferences prefs) {
        float textScale = prefs.getInt(KEY_TEXT_SCALE, DEFAULT_TEXT_SCALE) / 100f;
        float speed = prefs.getInt(KEY_SPEED, DEFAULT_SPEED) / 100f;
        float alpha = prefs.getInt(KEY_ALPHA, DEFAULT_ALPHA) / 100f;
        int maxOnScreen = prefs.getInt(KEY_MAX_ON_SCREEN, DEFAULT_MAX_ON_SCREEN);
        long offset = prefs.getInt(KEY_OFFSET, DEFAULT_OFFSET);
        List<String> keywords = parseKeywords(prefs.getString(KEY_KEYWORDS, ""));
        DanmakuConfig.TypeEnabled types = new DanmakuConfig.TypeEnabled(
                prefs.getBoolean(KEY_TYPE_SCROLL, true),
                prefs.getBoolean(KEY_TYPE_TOP, true),
                prefs.getBoolean(KEY_TYPE_BOTTOM, true),
                prefs.getBoolean(KEY_TYPE_POSITIONED, true)
        );
        return new DanmakuConfig(
                textScale,
                speed,
                alpha,
                maxOnScreen,
                0,
                keywords,
                types,
                offset
        );
    }

    static List<String> parseKeywords(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return Collections.emptyList();
        }
        String[] parts = raw.split(",");
        List<String> list = new ArrayList<>();
        for (String part : parts) {
            String value = part.trim();
            if (!value.isEmpty()) {
                list.add(value);
            }
        }
        return list;
    }

    static String formatKeywords(@NonNull List<String> keywords) {
        if (keywords.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < keywords.size(); i++) {
            if (i > 0) {
                builder.append(", ");
            }
            builder.append(keywords.get(i));
        }
        return builder.toString();
    }
}
