package org.xbmc.kodi.danmaku.api;

import android.util.Log;

import org.xbmc.kodi.BuildConfig;
import org.xbmc.kodi.danmaku.DanmakuService;
import org.xbmc.kodi.danmaku.model.DanmakuConfig;
import org.xbmc.kodi.danmaku.model.MediaKey;
import org.xbmc.kodi.danmaku.model.TrackCandidate;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Thin adapter aligning in-app calls with the contracts/danmaku-openapi.yaml endpoints.
 * Network transport is out-of-scope; this only wires to the internal service.
 */
public class DanmakuApi {
    private static final String TAG = "DanmakuApi";
    private static final DanmakuService.DanmakuStatus DISABLED_STATUS =
            new DanmakuService.DanmakuStatus(false, false, 0L, 1f);

    private final DanmakuService service;
    private final boolean enabled;

    public DanmakuApi(DanmakuService service) {
        this(service, BuildConfig.DANMAKU_ENABLED);
    }

    DanmakuApi(DanmakuService service, boolean enabled) {
        this.service = Objects.requireNonNull(service, "service");
        this.enabled = enabled;
    }

    public List<TrackCandidate> listTracks(MediaKey mediaKey) {
        if (!enabled) {
            logDisabled("listTracks");
            return Collections.emptyList();
        }
        if (mediaKey == null) {
            Log.w(TAG, "listTracks ignored: mediaKey is null");
            return Collections.emptyList();
        }
        return service.getTrackCandidates(mediaKey);
    }

    public List<TrackCandidate> listTracks(String serializedMediaKey) {
        if (!enabled) {
            logDisabled("listTracks");
            return Collections.emptyList();
        }
        MediaKey mediaKey = parseMediaKey(serializedMediaKey);
        if (mediaKey == null) {
            return Collections.emptyList();
        }
        return listTracks(mediaKey);
    }

    public void selectTrack(MediaKey mediaKey, String trackId) {
        if (!enabled) {
            logDisabled("selectTrack");
            return;
        }
        if (mediaKey == null) {
            Log.w(TAG, "selectTrack ignored: mediaKey is null");
            return;
        }
        if (trackId == null || trackId.isEmpty()) {
            Log.w(TAG, "selectTrack ignored: trackId is empty");
            return;
        }
        service.selectTrack(mediaKey, trackId);
    }

    public void setVisibility(boolean visible) {
        if (!enabled) {
            logDisabled("setVisibility");
            return;
        }
        service.setVisibility(visible);
    }

    public void updateConfig(DanmakuConfig config) {
        if (!enabled) {
            logDisabled("updateConfig");
            return;
        }
        service.updateConfig(config);
    }

    public void seek(long positionMs) {
        if (!enabled) {
            logDisabled("seek");
            return;
        }
        service.seek(positionMs);
    }

    public void updateSpeed(float speed) {
        if (!enabled) {
            logDisabled("updateSpeed");
            return;
        }
        service.updateSpeed(speed);
    }

    public DanmakuService.DanmakuStatus status() {
        if (!enabled) {
            logDisabled("status");
            return DISABLED_STATUS;
        }
        try {
            return service.getStatus();
        } catch (RuntimeException ex) {
            Log.w(TAG, "status lookup failed", ex);
            return DISABLED_STATUS;
        }
    }

    private MediaKey parseMediaKey(String serialized) {
        if (serialized == null || serialized.isEmpty()) {
            Log.w(TAG, "mediaKey is empty");
            return null;
        }
        try {
            return MediaKey.deserialize(serialized);
        } catch (IllegalArgumentException ex) {
            Log.w(TAG, "mediaKey is invalid: " + serialized, ex);
            return null;
        }
    }

    private void logDisabled(String action) {
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, action + " ignored: danmaku disabled by flavor");
        }
    }
}
