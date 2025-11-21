package org.xbmc.kodi.danmaku;

import org.xbmc.kodi.danmaku.model.DanmakuConfig;
import org.xbmc.kodi.danmaku.model.DanmakuItem;

import java.util.ArrayList;
import java.util.List;

final class FakeRenderer implements DanmakuRenderer {
    int prepareCount;
    int playCount;
    int pauseCount;
    int releaseCount;
    int visibleCount;
    long lastSeekMs = -1L;
    float lastSpeed = 1.0f;
    boolean visible;
    boolean prepared;
    List<DanmakuItem> items = new ArrayList<>();
    DanmakuConfig config = DanmakuConfig.defaults();

    @Override
    public void prepare(List<DanmakuItem> items, DanmakuConfig config) {
        prepareCount++;
        this.items = new ArrayList<>(items);
        this.config = config == null ? DanmakuConfig.defaults() : config;
        prepared = true;
    }

    @Override
    public void play() {
        playCount++;
    }

    @Override
    public void pause() {
        pauseCount++;
    }

    @Override
    public void seekTo(long positionMs) {
        lastSeekMs = positionMs;
    }

    @Override
    public void setSpeed(float speed) {
        lastSpeed = speed;
    }

    @Override
    public void setVisible(boolean visible) {
        visibleCount++;
        this.visible = visible;
    }

    @Override
    public boolean isVisible() {
        return visible;
    }

    @Override
    public boolean isPrepared() {
        return prepared;
    }

    @Override
    public void release() {
        releaseCount++;
        prepared = false;
    }
}
