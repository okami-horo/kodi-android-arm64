package org.xbmc.kodi.danmaku.clock;

import android.media.session.PlaybackState;
import android.os.SystemClock;

import java.util.function.LongSupplier;

/**
 * Playback clock anchored from Android MediaSession PlaybackState.
 */
public class MediaSessionClock implements PlaybackClock {
    private final SoftClock delegate;
    private final LongSupplier timeProvider;

    public MediaSessionClock() {
        this(SystemClock::elapsedRealtime);
    }

    public MediaSessionClock(LongSupplier timeProvider) {
        this.delegate = new SoftClock(timeProvider);
        this.timeProvider = timeProvider;
    }

    public void updateFromPlaybackState(PlaybackState state) {
        if (state == null) {
            return;
        }
        long anchorRealtime = timeProvider.getAsLong();
        float speed = state.getPlaybackSpeed();
        long position = state.getPosition();
        long stateUpdateRealtime = state.getLastPositionUpdateTime();
        if (stateUpdateRealtime > 0L) {
            long deltaMs = anchorRealtime - stateUpdateRealtime;
            position += (long) (deltaMs * speed);
        }
        boolean playing = state.getState() == PlaybackState.STATE_PLAYING
                || state.getState() == PlaybackState.STATE_BUFFERING;
        delegate.anchor(position, speed, anchorRealtime, playing);
    }

    @Override
    public long nowMs() {
        return delegate.nowMs();
    }

    @Override
    public float getSpeed() {
        return delegate.getSpeed();
    }

    @Override
    public boolean isPlaying() {
        return delegate.isPlaying();
    }

    @Override
    public void anchor(long positionMs, float speed, long anchorRealtimeMs, boolean playing) {
        delegate.anchor(positionMs, speed, anchorRealtimeMs, playing);
    }

    @Override
    public void pause(long positionMs, long anchorRealtimeMs) {
        delegate.pause(positionMs, anchorRealtimeMs);
    }

    @Override
    public void seek(long positionMs, long anchorRealtimeMs) {
        delegate.seek(positionMs, anchorRealtimeMs);
    }
}
