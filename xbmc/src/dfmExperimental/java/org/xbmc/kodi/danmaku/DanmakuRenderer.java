package org.xbmc.kodi.danmaku;

import org.xbmc.kodi.danmaku.model.DanmakuConfig;
import org.xbmc.kodi.danmaku.model.DanmakuItem;

import java.util.List;

/**
 * Thin abstraction over the danmaku rendering surface so engines can be tested with fakes.
 */
public interface DanmakuRenderer {

    void prepare(List<DanmakuItem> items, DanmakuConfig config);

    void play();

    void pause();

    void seekTo(long positionMs);

    void setSpeed(float speed);

    void setVisible(boolean visible);

    boolean isVisible();

    boolean isPrepared();

    void release();
}
