package org.xbmc.kodi.danmaku.model;

import java.util.Objects;

/**
 * Scored candidate track for a given media key.
 */
public final class TrackCandidate {
    private final DanmakuTrack track;
    private final int score;
    private final String reason;

    public TrackCandidate(DanmakuTrack track, int score, String reason) {
        this.track = Objects.requireNonNull(track, "track");
        this.score = score;
        this.reason = reason;
    }

    public DanmakuTrack getTrack() {
        return track;
    }

    public int getScore() {
        return score;
    }

    public String getReason() {
        return reason;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TrackCandidate)) return false;
        TrackCandidate that = (TrackCandidate) o;
        return score == that.score && track.equals(that.track) && Objects.equals(reason, that.reason);
    }

    @Override
    public int hashCode() {
        return Objects.hash(track, score, reason);
    }

    @Override
    public String toString() {
        return "TrackCandidate{" +
                "track=" + track +
                ", score=" + score +
                ", reason='" + reason + '\'' +
                '}';
    }
}
