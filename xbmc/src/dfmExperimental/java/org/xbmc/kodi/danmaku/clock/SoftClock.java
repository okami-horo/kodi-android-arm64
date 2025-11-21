package org.xbmc.kodi.danmaku.clock;

import android.os.SystemClock;

import java.util.function.LongSupplier;

/**
 * In-memory soft clock that extrapolates playback time from a recent anchor.
 * Designed for unit tests by allowing a custom time provider.
 */
public class SoftClock implements PlaybackClock {
    private final LongSupplier timeProvider;

    private long anchorPositionMs;
    private long anchorRealtimeMs;
    private float speed;
    private boolean playing;

    public SoftClock() {
        this(SystemClock::elapsedRealtime);
    }

    public SoftClock(LongSupplier timeProvider) {
        this.timeProvider = timeProvider;
        this.anchorPositionMs = 0L;
        this.anchorRealtimeMs = timeProvider.getAsLong();
        this.speed = 1.0f;
        this.playing = false;
    }

    @Override
    public synchronized long nowMs() {
        if (!playing) {
            return anchorPositionMs;
        }
        long delta = timeProvider.getAsLong() - anchorRealtimeMs;
        return anchorPositionMs + (long) (delta * speed);
    }

    @Override
    public synchronized float getSpeed() {
        return speed;
    }

    @Override
    public synchronized boolean isPlaying() {
        return playing;
    }

    @Override
    public synchronized void anchor(long positionMs, float speed, long anchorRealtimeMs, boolean playing) {
        this.anchorPositionMs = positionMs;
        this.anchorRealtimeMs = anchorRealtimeMs;
        this.speed = speed;
        this.playing = playing;
    }

    @Override
    public synchronized void pause(long positionMs, long anchorRealtimeMs) {
        anchor(positionMs, 0f, anchorRealtimeMs, false);
    }

    @Override
    public synchronized void seek(long positionMs, long anchorRealtimeMs) {
        anchor(positionMs, speed, anchorRealtimeMs, playing);
    }
}
