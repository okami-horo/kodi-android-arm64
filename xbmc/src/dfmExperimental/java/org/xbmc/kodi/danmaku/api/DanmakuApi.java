package org.xbmc.kodi.danmaku.api;

import org.xbmc.kodi.danmaku.DanmakuService;
import org.xbmc.kodi.danmaku.model.DanmakuConfig;
import org.xbmc.kodi.danmaku.model.MediaKey;
import org.xbmc.kodi.danmaku.model.TrackCandidate;

import java.util.List;
import java.util.Objects;

/**
 * Thin adapter aligning in-app calls with the contracts/danmaku-openapi.yaml endpoints.
 * Network transport is out-of-scope; this only wires to the internal service.
 */
public class DanmakuApi {
    private final DanmakuService service;

    public DanmakuApi(DanmakuService service) {
        this.service = Objects.requireNonNull(service, "service");
    }

    public List<TrackCandidate> listTracks(MediaKey mediaKey) {
        return service.getTrackCandidates(mediaKey);
    }

    public List<TrackCandidate> listTracks(String serializedMediaKey) {
        return listTracks(MediaKey.deserialize(serializedMediaKey));
    }

    public void selectTrack(MediaKey mediaKey, String trackId) {
        service.selectTrack(mediaKey, trackId);
    }

    public void setVisibility(boolean visible) {
        service.setVisibility(visible);
    }

    public void updateConfig(DanmakuConfig config) {
        service.updateConfig(config);
    }

    public void seek(long positionMs) {
        service.seek(positionMs);
    }

    public void updateSpeed(float speed) {
        service.updateSpeed(speed);
    }

    public DanmakuService.DanmakuStatus status() {
        return service.getStatus();
    }
}
