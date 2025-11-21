package org.xbmc.kodi.danmaku;

import android.os.SystemClock;
import android.util.Log;

import androidx.annotation.VisibleForTesting;

import org.xbmc.kodi.danmaku.clock.PlaybackClock;
import org.xbmc.kodi.danmaku.model.DanmakuConfig;
import org.xbmc.kodi.danmaku.model.DanmakuItem;
import org.xbmc.kodi.danmaku.model.DanmakuTrack;
import org.xbmc.kodi.danmaku.model.MediaKey;
import org.xbmc.kodi.danmaku.model.TrackCandidate;
import org.xbmc.kodi.danmaku.source.local.BiliXmlParser;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.LongSupplier;

/**
 * Coordinates danmaku rendering, playback alignment, and state persistence.
 */
public class DanmakuEngine implements DanmakuService {
    private static final String TAG = "DanmakuEngine";
    private static final long RESYNC_THRESHOLD_MS = 200L;

    private final PlaybackClock clock;
    private final DanmakuPreferences preferences;
    private final BiliXmlParser parser;
    private final LongSupplier realtimeNow;

    private final Map<String, CachedTrack> trackCache = new HashMap<>();

    private DanmakuRenderer renderer;
    private DanmakuTrack activeTrack;
    private DanmakuConfig activeConfig = DanmakuConfig.defaults();
    private List<DanmakuItem> activeItems = Collections.emptyList();
    private boolean visible;
    private boolean prepared;
    private long lastAppliedPositionMs;

    public DanmakuEngine(DanmakuRenderer renderer,
                         PlaybackClock clock,
                         DanmakuPreferences preferences,
                         BiliXmlParser parser) {
        this(renderer, clock, preferences, parser, SystemClock::elapsedRealtime);
    }

    @VisibleForTesting
    DanmakuEngine(DanmakuRenderer renderer,
                  PlaybackClock clock,
                  DanmakuPreferences preferences,
                  BiliXmlParser parser,
                  LongSupplier realtimeNow) {
        this.renderer = Objects.requireNonNull(renderer, "renderer");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.preferences = Objects.requireNonNull(preferences, "preferences");
        this.parser = Objects.requireNonNull(parser, "parser");
        this.realtimeNow = Objects.requireNonNull(realtimeNow, "realtimeNow");
    }

    /**
     * Registers a prepared track and immediately selects it.
     */
    public void bindTrack(DanmakuTrack track, List<DanmakuItem> items, DanmakuConfig config) {
        CachedTrack cached = new CachedTrack(track, new ArrayList<>(items), config == null ? DanmakuConfig.defaults() : config);
        trackCache.put(cacheKey(track.getMediaKey(), track.getId()), cached);
        selectTrack(track.getMediaKey(), track.getId());
    }

    /**
     * Allows reattaching a recreated renderer (e.g., after rotation).
     */
    public void attachRenderer(DanmakuRenderer newRenderer) {
        if (newRenderer == null) {
            return;
        }
        this.renderer = newRenderer;
        prepared = false;
        if (activeTrack != null && !activeItems.isEmpty()) {
            prepareRenderer();
            applyPlaybackState(true);
        }
    }

    public void detachRenderer() {
        if (renderer != null) {
            renderer.release();
            prepared = false;
        }
    }

    /**
     * Tick should be called from playback/Choreographer to maintain alignment within threshold.
     */
    public void tick() {
        if (!prepared || renderer == null || !renderer.isPrepared() || activeTrack == null) {
            return;
        }
        long effectivePos = clock.nowMs() + activeConfig.getOffsetMs();
        if (Math.abs(effectivePos - lastAppliedPositionMs) >= RESYNC_THRESHOLD_MS) {
            renderer.seekTo(effectivePos);
            lastAppliedPositionMs = effectivePos;
        }
        renderer.setSpeed(clock.getSpeed());
        if (clock.isPlaying()) {
            renderer.play();
        } else {
            renderer.pause();
        }
    }

    /**
     * Anchors the soft clock and updates the renderer immediately.
     */
    public void updatePlaybackState(long positionMs, float speed, boolean playing) {
        clock.anchor(positionMs, speed, realtimeNow.getAsLong(), playing);
        applyPlaybackState(false);
    }

    public void pause() {
        clock.pause(clock.nowMs(), realtimeNow.getAsLong());
        applyPlaybackState(false);
    }

    @Override
    public List<TrackCandidate> getTrackCandidates(MediaKey mediaKey) {
        List<TrackCandidate> result = new ArrayList<>();
        for (CachedTrack cached : trackCache.values()) {
            if (cached.track.getMediaKey().equals(mediaKey)) {
                result.add(toCandidate(cached.track));
            }
        }
        return result;
    }

    @Override
    public void selectTrack(MediaKey mediaKey, String trackId) {
        CachedTrack cached = trackCache.get(cacheKey(mediaKey, trackId));
        if (cached == null) {
            // Lazy load from file if possible
            cached = loadFromFile(mediaKey, trackId);
            if (cached == null) {
                Log.w(TAG, "No matching track for media " + mediaKey + " id=" + trackId);
                return;
            }
        }
        this.activeTrack = cached.track;
        this.activeItems = cached.items;
        this.activeConfig = mergeConfig(cached.track, cached.config);
        prepared = false;
        prepareRenderer();
        applyPlaybackState(true);
        preferences.saveLastTrack(mediaKey, cached.track);
        preferences.saveConfig(mediaKey, activeConfig);
    }

    @Override
    public void setVisibility(boolean visible) {
        this.visible = visible;
        if (renderer != null) {
            renderer.setVisible(visible);
        }
    }

    @Override
    public void updateConfig(DanmakuConfig config) {
        this.activeConfig = config == null ? DanmakuConfig.defaults() : config;
        if (activeTrack != null) {
            preferences.saveConfig(activeTrack.getMediaKey(), activeConfig);
        }
        prepared = false;
        prepareRenderer();
        applyPlaybackState(true);
    }

    @Override
    public void seek(long positionMs) {
        clock.seek(positionMs, realtimeNow.getAsLong());
        long effective = positionMs + activeConfig.getOffsetMs();
        if (renderer != null && renderer.isPrepared()) {
            renderer.seekTo(effective);
            lastAppliedPositionMs = effective;
        }
    }

    @Override
    public void updateSpeed(float speed) {
        clock.anchor(clock.nowMs(), speed, realtimeNow.getAsLong(), clock.isPlaying());
        if (renderer != null && renderer.isPrepared()) {
            renderer.setSpeed(speed);
        }
    }

    @Override
    public DanmakuStatus getStatus() {
        return new DanmakuStatus(visible, clock.isPlaying(), clock.nowMs(), clock.getSpeed());
    }

    @Override
    public DanmakuTrack getActiveTrack() {
        return activeTrack;
    }

    private void applyPlaybackState(boolean forceSeek) {
        if (renderer == null || activeTrack == null) {
            return;
        }
        if (!prepared) {
            prepareRenderer();
        }
        renderer.setVisible(visible);
        if (!renderer.isPrepared()) {
            return;
        }
        long effective = clock.nowMs() + activeConfig.getOffsetMs();
        if (forceSeek || Math.abs(effective - lastAppliedPositionMs) >= RESYNC_THRESHOLD_MS) {
            renderer.seekTo(effective);
            lastAppliedPositionMs = effective;
        }
        renderer.setSpeed(clock.getSpeed());
        if (clock.isPlaying()) {
            renderer.play();
        } else {
            renderer.pause();
        }
    }

    private void prepareRenderer() {
        if (renderer == null || activeItems.isEmpty()) {
            return;
        }
        renderer.prepare(activeItems, activeConfig);
        prepared = renderer.isPrepared();
        lastAppliedPositionMs = clock.nowMs() + activeConfig.getOffsetMs();
        if (prepared && visible) {
            renderer.setVisible(true);
            renderer.seekTo(lastAppliedPositionMs);
        }
    }

    private CachedTrack loadFromFile(MediaKey mediaKey, String trackId) {
        for (CachedTrack cached : trackCache.values()) {
            if (cached.track.getMediaKey().equals(mediaKey) && cached.track.getId().equals(trackId)) {
                return cached;
            }
        }
        // Best-effort parse when caller already has track metadata
        try {
            File file = new File(trackId);
            if (!file.exists()) {
                return null;
            }
            List<DanmakuItem> items;
            try (FileInputStream in = new FileInputStream(file)) {
                items = parser.parse(in);
            }
            DanmakuTrack track = new DanmakuTrack(trackId, file.getName(), DanmakuTrack.SourceType.LOCAL, file.getAbsolutePath(), DanmakuConfig.defaults(), mediaKey);
            CachedTrack cached = new CachedTrack(track, items, track.getConfig());
            trackCache.put(cacheKey(mediaKey, trackId), cached);
            return cached;
        } catch (IOException ex) {
            Log.w(TAG, "Failed to parse danmaku file for track " + trackId, ex);
            return null;
        }
    }

    private DanmakuConfig mergeConfig(DanmakuTrack track, DanmakuConfig candidate) {
        DanmakuConfig saved = preferences.getConfig(track.getMediaKey());
        if (saved != null) {
            return saved;
        }
        if (candidate != null) {
            return candidate;
        }
        return track.getConfig();
    }

    private TrackCandidate toCandidate(DanmakuTrack track) {
        return new TrackCandidate(track, 0, "cached");
    }

    private String cacheKey(MediaKey mediaKey, String trackId) {
        return mediaKey.serialize() + "#" + trackId;
    }

    private static final class CachedTrack {
        final DanmakuTrack track;
        final List<DanmakuItem> items;
        final DanmakuConfig config;

        CachedTrack(DanmakuTrack track, List<DanmakuItem> items, DanmakuConfig config) {
            this.track = track;
            this.items = items;
            this.config = config;
        }
    }
}
