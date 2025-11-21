package org.xbmc.kodi.danmaku.bridge;

import android.media.session.MediaController;
import android.media.session.PlaybackState;
import android.os.SystemClock;

import org.xbmc.kodi.danmaku.DanmakuEngine;
import org.xbmc.kodi.danmaku.clock.MediaSessionClock;

/**
 * Bridges MediaSession playback callbacks into the DanmakuEngine.
 */
public class PlayerEventBridge extends MediaController.Callback {
    private final MediaSessionClock clock;
    private final DanmakuEngine engine;

    public PlayerEventBridge(MediaSessionClock clock, DanmakuEngine engine) {
        this.clock = clock;
        this.engine = engine;
    }

    @Override
    public void onPlaybackStateChanged(PlaybackState state) {
        clock.updateFromPlaybackState(state);
        engine.updatePlaybackState(clock.nowMs(), clock.getSpeed(), clock.isPlaying());
    }

    /**
     * Propagates explicit seek commands from the player.
     */
    public void onSeek(long positionMs) {
        clock.seek(positionMs, SystemClock.elapsedRealtime());
        engine.seek(positionMs);
    }

    public void onToggleVisibility(boolean visible) {
        engine.setVisibility(visible);
    }
}
