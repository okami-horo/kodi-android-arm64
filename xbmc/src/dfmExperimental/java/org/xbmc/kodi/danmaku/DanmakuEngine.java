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
import org.xbmc.kodi.danmaku.perf.DanmakuPerformanceSampler;
import org.xbmc.kodi.danmaku.source.local.BiliXmlParser;
import org.xbmc.kodi.danmaku.source.local.LocalTrackDiscovery;

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
    private final LocalTrackDiscovery trackDiscovery;
    private final WindowingConfig windowing;
    private final DanmakuPerformanceSampler perfSampler;

    private final Map<String, CachedTrack> trackCache = new HashMap<>();

    private DanmakuRenderer renderer;
    private DanmakuTrack activeTrack;
    private DanmakuConfig activeConfig = DanmakuConfig.defaults();
    private List<DanmakuItem> activeItems = Collections.emptyList();
    private boolean visible;
    private boolean prepared;
    private boolean windowShiftPending;
    private long lastAppliedPositionMs;
    private LoadError lastError;
    private long windowStartMs;
    private long windowEndMs;
    private long lastWindowPrepareRealtimeMs;

    public DanmakuEngine(DanmakuRenderer renderer,
                         PlaybackClock clock,
                         DanmakuPreferences preferences,
                         BiliXmlParser parser) {
        this(renderer, clock, preferences, parser, SystemClock::elapsedRealtime, new LocalTrackDiscovery(), WindowingConfig.defaultConfig(), DanmakuPerformanceSampler.createDefault(SystemClock::elapsedRealtime));
    }

    @VisibleForTesting
    DanmakuEngine(DanmakuRenderer renderer,
                  PlaybackClock clock,
                  DanmakuPreferences preferences,
                  BiliXmlParser parser,
                  LongSupplier realtimeNow) {
        this(renderer, clock, preferences, parser, realtimeNow, new LocalTrackDiscovery(), WindowingConfig.defaultConfig(), DanmakuPerformanceSampler.noOp());
    }

    @VisibleForTesting
    DanmakuEngine(DanmakuRenderer renderer,
                  PlaybackClock clock,
                  DanmakuPreferences preferences,
                  BiliXmlParser parser,
                  LongSupplier realtimeNow,
                  LocalTrackDiscovery trackDiscovery) {
        this(renderer, clock, preferences, parser, realtimeNow, trackDiscovery, WindowingConfig.defaultConfig(), DanmakuPerformanceSampler.noOp());
    }

    @VisibleForTesting
    DanmakuEngine(DanmakuRenderer renderer,
                  PlaybackClock clock,
                  DanmakuPreferences preferences,
                  BiliXmlParser parser,
                  LongSupplier realtimeNow,
                  LocalTrackDiscovery trackDiscovery,
                  WindowingConfig windowing,
                  DanmakuPerformanceSampler perfSampler) {
        this.renderer = Objects.requireNonNull(renderer, "renderer");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.preferences = Objects.requireNonNull(preferences, "preferences");
        this.parser = Objects.requireNonNull(parser, "parser");
        this.realtimeNow = Objects.requireNonNull(realtimeNow, "realtimeNow");
        this.trackDiscovery = Objects.requireNonNull(trackDiscovery, "trackDiscovery");
        this.windowing = Objects.requireNonNull(windowing, "windowing");
        this.perfSampler = Objects.requireNonNull(perfSampler, "perfSampler");
        this.lastWindowPrepareRealtimeMs = realtimeNow.getAsLong();
        this.windowStartMs = 0L;
        this.windowEndMs = Long.MAX_VALUE;
    }

    /**
     * Registers a prepared track and immediately selects it.
     */
    public void bindTrack(DanmakuTrack track, List<DanmakuItem> items, DanmakuConfig config) {
        if (track == null) {
            Log.w(TAG, "bindTrack ignored: track is null");
            return;
        }
        List<DanmakuItem> safeItems = items == null ? Collections.emptyList() : new ArrayList<>(items);
        DanmakuConfig resolvedConfig = config == null ? DanmakuConfig.defaults() : config;
        CachedTrack cached = new CachedTrack(track, safeItems, resolvedConfig, 100, "prebound");
        trackCache.put(cacheKey(track.getMediaKey(), track.getId()), cached);
        logDebug("bindTrack: " + track.getId() + " items=" + safeItems.size());
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
        logDebug("Renderer reattached; prepared state reset");
        if (activeTrack != null && !activeItems.isEmpty()) {
            prepareRenderer();
            applyPlaybackState(true);
        }
    }

    public void detachRenderer() {
        if (renderer != null) {
            safeRendererCall("release", renderer::release);
            prepared = false;
        }
        perfSampler.flush();
    }

    /**
     * Tick should be called from playback/Choreographer to maintain alignment within threshold.
     */
    public void tick() {
        if (renderer == null || activeTrack == null) {
            return;
        }
        if (activeItems.isEmpty()) {
            return;
        }
        refreshWindow(clock.nowMs());
        if (!prepared || !renderer.isPrepared()) {
            prepareRenderer();
        }
        if (!prepared || !renderer.isPrepared()) {
            return;
        }
        long effectivePos = clock.nowMs() + activeConfig.getOffsetMs();
        long drift = Math.abs(effectivePos - lastAppliedPositionMs);
        if (drift >= RESYNC_THRESHOLD_MS) {
            safeRendererCall("seekTo", () -> renderer.seekTo(effectivePos));
            lastAppliedPositionMs = effectivePos;
            perfSampler.recordResync(drift);
        }
        safeRendererCall("setSpeed", () -> renderer.setSpeed(clock.getSpeed()));
        if (clock.isPlaying()) {
            safeRendererCall("play", renderer::play);
        } else {
            safeRendererCall("pause", renderer::pause);
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
        if (mediaKey == null) {
            Log.w(TAG, "getTrackCandidates ignored: mediaKey is null");
            return Collections.emptyList();
        }
        ensureCandidates(mediaKey);
        List<TrackCandidate> result = new ArrayList<>();
        trackCache.values().stream()
                .filter(cached -> cached.track.getMediaKey().equals(mediaKey))
                .sorted((a, b) -> Integer.compare(b.score, a.score))
                .forEach(cached -> result.add(toCandidate(cached)));
        return result;
    }

    @Override
    public void selectTrack(MediaKey mediaKey, String trackId) {
        selectTrackInternal(mediaKey, trackId, true);
    }

    private void selectTrackInternal(MediaKey mediaKey, String trackId, boolean callEnsure) {
        if (mediaKey == null) {
            Log.w(TAG, "selectTrack ignored: mediaKey is null");
            return;
        }
        if (trackId == null || trackId.isEmpty()) {
            Log.w(TAG, "selectTrack ignored: trackId is empty");
            return;
        }
        lastError = null;
        if (callEnsure) {
            ensureCandidates(mediaKey);
        }
        CachedTrack cached = trackCache.get(cacheKey(mediaKey, trackId));
        if (cached == null) {
            // Lazy load from file if possible
            cached = loadFromFile(mediaKey, trackId);
            if (cached == null) {
                Log.w(TAG, "No matching track for media " + mediaKey + " id=" + trackId);
                return;
            }
        }
        if (cached.items.isEmpty()) {
            CachedTrack parsed = parseTrackFromDisk(cached.track, cached.score, cached.reason);
            if (parsed == null) {
                lastError = new LoadError(cached.track.getFilePath(), "parse_failed");
                Log.w(TAG, "Unable to parse track " + trackId);
                return;
            }
            cached = parsed;
            trackCache.put(cacheKey(mediaKey, trackId), cached);
        }
        this.activeTrack = cached.track;
        this.activeItems = cached.items;
        this.activeConfig = mergeConfig(cached.track, cached.config);
        prepared = false;
        windowShiftPending = false;
        windowStartMs = 0L;
        windowEndMs = Long.MAX_VALUE;
        logDebug("Selected track " + trackId + " items=" + activeItems.size());
        prepareRenderer();
        applyPlaybackState(true);
        preferences.saveLastTrack(mediaKey, cached.track);
        preferences.saveConfig(mediaKey, activeConfig);
    }

    @Override
    public void setVisibility(boolean visible) {
        this.visible = visible;
        if (renderer != null) {
            safeRendererCall("setVisible", () -> renderer.setVisible(visible));
        }
    }

    @Override
    public void updateConfig(DanmakuConfig config) {
        this.activeConfig = config == null ? DanmakuConfig.defaults() : config;
        if (activeTrack != null) {
            preferences.saveConfig(activeTrack.getMediaKey(), activeConfig);
        }
        prepared = false;
        logDebug("updateConfig applied; will re-prepare renderer");
        prepareRenderer();
        applyPlaybackState(true);
    }

    @Override
    public void seek(long positionMs) {
        clock.seek(positionMs, realtimeNow.getAsLong());
        long effective = positionMs + activeConfig.getOffsetMs();
        if (!isWithinWindow(effective)) {
            windowShiftPending = true;
            prepared = false;
            applyPlaybackState(true);
            return;
        }
        if (renderer != null && renderer.isPrepared()) {
            safeRendererCall("seekTo", () -> renderer.seekTo(effective));
            lastAppliedPositionMs = effective;
        }
    }

    @Override
    public void updateSpeed(float speed) {
        clock.anchor(clock.nowMs(), speed, realtimeNow.getAsLong(), clock.isPlaying());
        if (renderer != null && renderer.isPrepared()) {
            safeRendererCall("setSpeed", () -> renderer.setSpeed(speed));
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

    @VisibleForTesting
    LoadError getLastError() {
        return lastError;
    }

    private void ensureCandidates(MediaKey mediaKey) {
        if (mediaKey == null) {
            return;
        }
        boolean hasCached = trackCache.values().stream()
                .anyMatch(cached -> cached.track.getMediaKey().equals(mediaKey));
        if (hasCached) {
            String lastTrackId = preferences.getLastTrackId(mediaKey);
            if (lastTrackId != null && trackCache.containsKey(cacheKey(mediaKey, lastTrackId)) && !lastTrackId.equals(activeId())) {
                selectTrackInternal(mediaKey, lastTrackId, false);
            }
            return;
        }
        List<TrackCandidate> discovered = trackDiscovery.discover(mediaKey);
        for (TrackCandidate candidate : discovered) {
            DanmakuTrack track = candidate.getTrack();
            CachedTrack cached = new CachedTrack(track, Collections.emptyList(), track.getConfig(), candidate.getScore(), candidate.getReason());
            trackCache.put(cacheKey(mediaKey, track.getId()), cached);
        }
        String lastTrackId = preferences.getLastTrackId(mediaKey);
        if (lastTrackId != null && trackCache.containsKey(cacheKey(mediaKey, lastTrackId))) {
            selectTrackInternal(mediaKey, lastTrackId, false);
            return;
        }
        if (!discovered.isEmpty()) {
            String best = discovered.get(0).getTrack().getId();
            if (!best.equals(activeId())) {
                selectTrackInternal(mediaKey, best, false);
            }
        }
    }

    private String activeId() {
        return activeTrack == null ? null : activeTrack.getId();
    }

    private void applyPlaybackState(boolean forceSeek) {
        if (renderer == null || activeTrack == null) {
            return;
        }
        refreshWindow(clock.nowMs());
        if (!prepared) {
            prepareRenderer();
        }
        safeRendererCall("setVisible", () -> renderer.setVisible(visible));
        if (!renderer.isPrepared()) {
            return;
        }
        long effective = clock.nowMs() + activeConfig.getOffsetMs();
        long drift = Math.abs(effective - lastAppliedPositionMs);
        if (forceSeek || drift >= RESYNC_THRESHOLD_MS) {
            safeRendererCall("seekTo", () -> renderer.seekTo(effective));
            lastAppliedPositionMs = effective;
            if (!forceSeek) {
                perfSampler.recordResync(drift);
            }
        }
        safeRendererCall("setSpeed", () -> renderer.setSpeed(clock.getSpeed()));
        if (clock.isPlaying()) {
            safeRendererCall("play", renderer::play);
        } else {
            safeRendererCall("pause", renderer::pause);
        }
    }

    private void prepareRenderer() {
        if (renderer == null) {
            Log.w(TAG, "prepareRenderer skipped: renderer is null");
            return;
        }
        if (activeItems.isEmpty()) {
            logDebug("prepareRenderer skipped: no items for active track");
            return;
        }
        WindowRange windowRange = computeWindow(clock.nowMs());
        List<DanmakuItem> filtered = applyFilters(activeItems, activeConfig);
        List<DanmakuItem> windowed = applyWindow(filtered, activeConfig, windowRange);
        long startPrepareRealtime = realtimeNow.getAsLong();
        try {
            renderer.prepare(windowed, activeConfig);
            prepared = renderer.isPrepared();
        } catch (RuntimeException ex) {
            prepared = false;
            Log.e(TAG, "Renderer prepare failed", ex);
            return;
        }
        long durationMs = Math.max(0L, realtimeNow.getAsLong() - startPrepareRealtime);
        perfSampler.recordPrepare(durationMs, filtered.size(), windowed.size());
        perfSampler.recordWindow(windowRange.startMs, windowRange.endMs, windowed.size());
        windowStartMs = windowRange.startMs;
        windowEndMs = windowRange.endMs;
        windowShiftPending = false;
        lastWindowPrepareRealtimeMs = realtimeNow.getAsLong();
        lastAppliedPositionMs = clock.nowMs() + activeConfig.getOffsetMs();
        if (prepared && visible) {
            safeRendererCall("setVisible", () -> renderer.setVisible(true));
            safeRendererCall("seekTo", () -> renderer.seekTo(lastAppliedPositionMs));
        }
    }

    private CachedTrack parseTrackFromDisk(DanmakuTrack track, int score, String reason) {
        if (track == null) {
            return null;
        }
        File file = new File(track.getFilePath());
        if (!file.exists() || !file.canRead()) {
            Log.w(TAG, "Track file missing or unreadable: " + track.getFilePath());
            return null;
        }
        try (FileInputStream in = new FileInputStream(file)) {
            List<DanmakuItem> items = parser.parse(in);
            lastError = null;
            return new CachedTrack(track, items, track.getConfig(), score, reason);
        } catch (IOException ex) {
            lastError = new LoadError(track.getFilePath(), ex instanceof BiliXmlParser.ParseException ? ((BiliXmlParser.ParseException) ex).getReason().name() : "IO");
            Log.w(TAG, "Failed to parse danmaku file " + track.getFilePath(), ex);
            return null;
        }
    }

    private CachedTrack loadFromFile(MediaKey mediaKey, String trackId) {
        for (CachedTrack cached : trackCache.values()) {
            if (cached.track.getMediaKey().equals(mediaKey) && cached.track.getId().equals(trackId)) {
                return cached;
            }
        }
        // Best-effort parse when caller already has track metadata
        File file = new File(trackId);
        if (!file.exists()) {
            Log.w(TAG, "Track file missing for id=" + trackId);
            lastError = new LoadError(trackId, "missing");
            return null;
        }
        DanmakuTrack track = new DanmakuTrack(trackId, file.getName(), DanmakuTrack.SourceType.LOCAL, file.getAbsolutePath(), DanmakuConfig.defaults(), mediaKey);
        CachedTrack cached = parseTrackFromDisk(track, 80, "file");
        if (cached == null) {
            return null;
        }
        trackCache.put(cacheKey(mediaKey, trackId), cached);
        return cached;
    }

    private DanmakuConfig mergeConfig(DanmakuTrack track, DanmakuConfig candidate) {
        DanmakuConfig saved = preferences.getConfig(track.getMediaKey());
        if (saved != null) {
            return saved;
        }
        DanmakuConfig defaultConfig = preferences.getDefaultConfig();
        if (defaultConfig != null) {
            return defaultConfig;
        }
        if (candidate != null) {
            return candidate;
        }
        return track.getConfig();
    }

    private TrackCandidate toCandidate(CachedTrack cached) {
        String reason = cached.reason == null ? "cached" : cached.reason;
        return new TrackCandidate(cached.track, cached.score, reason);
    }

    private void refreshWindow(long positionMs) {
        if (activeItems.isEmpty()) {
            return;
        }
        boolean beforeWindow = positionMs < windowStartMs;
        boolean afterWindow = positionMs > windowEndMs;
        boolean nearStart = windowStartMs > 0 && positionMs < windowStartMs + windowing.guardMs;
        boolean nearEnd = windowEndMs < Long.MAX_VALUE && positionMs > windowEndMs - windowing.guardMs;
        boolean needsShift = beforeWindow || afterWindow || nearStart || nearEnd || windowShiftPending;
        if (!needsShift) {
            return;
        }
        long now = realtimeNow.getAsLong();
        if (now - lastWindowPrepareRealtimeMs < windowing.reprepareThrottleMs) {
            windowShiftPending = true;
            perfSampler.recordThrottle("window");
            logDebug("Window shift throttled at pos=" + positionMs);
            return;
        }
        prepared = false;
        windowShiftPending = false;
        logDebug("Window shift scheduled at pos=" + positionMs + " currentWindow=[" + windowStartMs + "," + windowEndMs + "]");
    }

    private WindowRange computeWindow(long positionMs) {
        long start = Math.max(0L, positionMs - windowing.behindMs);
        long end = Math.max(start, positionMs + windowing.aheadMs);
        return new WindowRange(start, end);
    }

    private boolean isWithinWindow(long effectivePositionMs) {
        return effectivePositionMs >= windowStartMs && effectivePositionMs <= windowEndMs;
    }

    private List<DanmakuItem> applyWindow(List<DanmakuItem> items, DanmakuConfig config, WindowRange windowRange) {
        if (items == null || items.isEmpty()) {
            return Collections.emptyList();
        }
        List<DanmakuItem> windowed = new ArrayList<>();
        for (DanmakuItem item : items) {
            long effectiveTime = item.getTimeMs() + config.getOffsetMs();
            if (effectiveTime >= windowRange.startMs && effectiveTime <= windowRange.endMs) {
                windowed.add(item);
            }
        }
        return windowed;
    }

    private String cacheKey(MediaKey mediaKey, String trackId) {
        return mediaKey.serialize() + "#" + trackId;
    }

    private void safeRendererCall(String action, Runnable runnable) {
        try {
            runnable.run();
        } catch (RuntimeException ex) {
            prepared = false;
            Log.e(TAG, "Renderer " + action + " failed", ex);
        }
    }

    private void logDebug(String message) {
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, message);
        }
    }

    private List<DanmakuItem> applyFilters(List<DanmakuItem> items, DanmakuConfig config) {
        if (items == null || items.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> keywords = config.getKeywordFilter();
        DanmakuConfig.TypeEnabled enabled = config.getTypeEnabled();
        List<DanmakuItem> filtered = new ArrayList<>();
        for (DanmakuItem item : items) {
            if (!isTypeAllowed(item, enabled)) {
                continue;
            }
            if (matchesKeyword(item, keywords)) {
                continue;
            }
            filtered.add(item);
        }
        return filtered;
    }

    private boolean matchesKeyword(DanmakuItem item, List<String> keywords) {
        if (keywords == null || keywords.isEmpty()) {
            return false;
        }
        String text = item.getText() == null ? "" : item.getText().toLowerCase();
        for (String keyword : keywords) {
            if (keyword == null || keyword.isEmpty()) {
                continue;
            }
            if (text.contains(keyword.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    private boolean isTypeAllowed(DanmakuItem item, DanmakuConfig.TypeEnabled enabled) {
        switch (item.getType()) {
            case TOP:
                return enabled.isTop();
            case BOTTOM:
                return enabled.isBottom();
            case POSITIONED:
                return enabled.isPositioned();
            case SCROLL:
            default:
                return enabled.isScroll();
        }
    }

    public static final class LoadError {
        private final String path;
        private final String reason;

        LoadError(String path, String reason) {
            this.path = path;
            this.reason = reason;
        }

        public String getPath() {
            return path;
        }

        public String getReason() {
            return reason;
        }
    }

    static final class WindowRange {
        final long startMs;
        final long endMs;

        WindowRange(long startMs, long endMs) {
            this.startMs = startMs;
            this.endMs = endMs;
        }
    }

    public static final class WindowingConfig {
        final long behindMs;
        final long aheadMs;
        final long guardMs;
        final long reprepareThrottleMs;

        public WindowingConfig(long behindMs, long aheadMs, long guardMs, long reprepareThrottleMs) {
            this.behindMs = Math.max(0L, behindMs);
            this.aheadMs = Math.max(0L, aheadMs);
            this.guardMs = Math.max(0L, guardMs);
            this.reprepareThrottleMs = Math.max(0L, reprepareThrottleMs);
        }

        public static WindowingConfig defaultConfig() {
            return new WindowingConfig(60_000L, 60_000L, 5_000L, 1_000L);
        }
    }

    private static final class CachedTrack {
        final DanmakuTrack track;
        final List<DanmakuItem> items;
        final DanmakuConfig config;
        final int score;
        final String reason;

        CachedTrack(DanmakuTrack track, List<DanmakuItem> items, DanmakuConfig config, int score, String reason) {
            this.track = track;
            this.items = items;
            this.config = config;
            this.score = score;
            this.reason = reason;
        }
    }
}
