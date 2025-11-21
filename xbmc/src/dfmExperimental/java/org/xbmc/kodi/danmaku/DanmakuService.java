package org.xbmc.kodi.danmaku;

import org.xbmc.kodi.danmaku.model.DanmakuConfig;
import org.xbmc.kodi.danmaku.model.DanmakuTrack;
import org.xbmc.kodi.danmaku.model.MediaKey;
import org.xbmc.kodi.danmaku.model.TrackCandidate;

import java.util.List;

/**
 * Internal service contract mapping playback events and UI actions to danmaku behavior.
 */
public interface DanmakuService {

    List<TrackCandidate> getTrackCandidates(MediaKey mediaKey);

    void selectTrack(MediaKey mediaKey, String trackId);

    void setVisibility(boolean visible);

    void updateConfig(DanmakuConfig config);

    void seek(long positionMs);

    void updateSpeed(float speed);

    DanmakuStatus getStatus();

    DanmakuTrack getActiveTrack();

    final class DanmakuStatus {
        private final boolean visible;
        private final boolean playing;
        private final long positionMs;
        private final float speed;

        public DanmakuStatus(boolean visible, boolean playing, long positionMs, float speed) {
            this.visible = visible;
            this.playing = playing;
            this.positionMs = positionMs;
            this.speed = speed;
        }

        public boolean isVisible() {
            return visible;
        }

        public boolean isPlaying() {
            return playing;
        }

        public long getPositionMs() {
            return positionMs;
        }

        public float getSpeed() {
            return speed;
        }
    }
}
