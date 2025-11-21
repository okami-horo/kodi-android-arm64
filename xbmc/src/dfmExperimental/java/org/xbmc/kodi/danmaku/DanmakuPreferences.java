package org.xbmc.kodi.danmaku;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.Nullable;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;

import org.xbmc.kodi.danmaku.model.DanmakuConfig;
import org.xbmc.kodi.danmaku.model.DanmakuTrack;
import org.xbmc.kodi.danmaku.model.MediaKey;

/**
 * SharedPreferences-backed storage keyed by MediaKey to remember track selection and settings.
 */
public class DanmakuPreferences {
    private static final String PREF_NAME = "danmaku_prefs";
    private static final String KEY_LAST_TRACK_PREFIX = "last_track_";
    private static final String KEY_CONFIG_PREFIX = "config_";

    private final SharedPreferences preferences;
    private final Gson gson;

    public DanmakuPreferences(Context context) {
        this(context.getApplicationContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE),
                new GsonBuilder().create());
    }

    public DanmakuPreferences(SharedPreferences preferences, Gson gson) {
        this.preferences = preferences;
        this.gson = gson;
    }

    public void saveLastTrack(MediaKey mediaKey, DanmakuTrack track) {
        preferences.edit()
                .putString(lastTrackKey(mediaKey), track.getId())
                .apply();
    }

    @Nullable
    public String getLastTrackId(MediaKey mediaKey) {
        return preferences.getString(lastTrackKey(mediaKey), null);
    }

    public void saveConfig(MediaKey mediaKey, DanmakuConfig config) {
        preferences.edit()
                .putString(configKey(mediaKey), gson.toJson(config))
                .apply();
    }

    @Nullable
    public DanmakuConfig getConfig(MediaKey mediaKey) {
        String raw = preferences.getString(configKey(mediaKey), null);
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        try {
            return gson.fromJson(raw, DanmakuConfig.class);
        } catch (JsonSyntaxException ex) {
            // Corrupted entry, drop it to avoid blocking playback.
            preferences.edit().remove(configKey(mediaKey)).apply();
            return null;
        }
    }

    public void clear(MediaKey mediaKey) {
        preferences.edit()
                .remove(lastTrackKey(mediaKey))
                .remove(configKey(mediaKey))
                .apply();
    }

    private String lastTrackKey(MediaKey mediaKey) {
        return KEY_LAST_TRACK_PREFIX + mediaKey.serialize();
    }

    private String configKey(MediaKey mediaKey) {
        return KEY_CONFIG_PREFIX + mediaKey.serialize();
    }
}
