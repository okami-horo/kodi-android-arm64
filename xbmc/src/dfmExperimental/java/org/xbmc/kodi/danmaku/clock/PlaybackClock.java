package org.xbmc.kodi.danmaku.clock;

/**
 * Abstraction for a playback-aligned clock that can extrapolate position based on a time anchor.
 */
public interface PlaybackClock {
    /**
     * Current estimated playback position in milliseconds.
     */
    long nowMs();

    /**
     * Last known playback speed (1.0f = normal).
     */
    float getSpeed();

    /**
     * Whether the clock is advancing (playing).
     */
    boolean isPlaying();

    /**
     * Anchor the clock using the given position, speed, and "now" timestamp.
     * @param positionMs playback position in ms at the anchor timestamp
     * @param speed playback speed
     * @param anchorRealtimeMs elapsed realtime in ms when this anchor was observed
     * @param playing whether playback is actively progressing
     */
    void anchor(long positionMs, float speed, long anchorRealtimeMs, boolean playing);

    /**
     * Convenience for pause: freezes at the provided position.
     */
    void pause(long positionMs, long anchorRealtimeMs);

    /**
     * Convenience for seek: re-anchor while keeping current speed/playing flag.
     */
    void seek(long positionMs, long anchorRealtimeMs);
}
