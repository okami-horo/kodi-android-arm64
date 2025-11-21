package org.xbmc.kodi.danmaku.dev;

import org.xbmc.kodi.danmaku.DanmakuEngine;
import org.xbmc.kodi.danmaku.model.DanmakuConfig;
import org.xbmc.kodi.danmaku.model.DanmakuItem;
import org.xbmc.kodi.danmaku.model.DanmakuTrack;
import org.xbmc.kodi.danmaku.model.MediaKey;

import java.util.ArrayList;
import java.util.List;

/**
 * Provides a debug pathway to inject canned danmaku without external files.
 */
public class DeveloperDanmakuInjector {
    private final DanmakuEngine engine;

    public DeveloperDanmakuInjector(DanmakuEngine engine) {
        this.engine = engine;
    }

    public void inject(MediaKey mediaKey) {
        if (mediaKey == null) {
            return;
        }
        List<DanmakuItem> demo = sampleItems();
        DanmakuTrack track = new DanmakuTrack(
                "dev-" + mediaKey.hashCode(),
                "Developer Injected",
                DanmakuTrack.SourceType.LOCAL,
                "developer",
                DanmakuConfig.defaults(),
                mediaKey);
        engine.bindTrack(track, demo, DanmakuConfig.defaults());
        engine.setVisibility(true);
        engine.updatePlaybackState(0L, 1.0f, true);
    }

    private List<DanmakuItem> sampleItems() {
        List<DanmakuItem> items = new ArrayList<>();
        items.add(new DanmakuItem(0, DanmakuItem.Type.SCROLL, "DFM ready", 0xffffff, 20f, 1f, null));
        items.add(new DanmakuItem(1_000L, DanmakuItem.Type.TOP, "Top aligned", 0xff9933, 22f, 1f, null));
        items.add(new DanmakuItem(2_000L, DanmakuItem.Type.BOTTOM, "Bottom aligned", 0x33ccff, 22f, 1f, null));
        items.add(new DanmakuItem(3_000L, DanmakuItem.Type.SCROLL, "Sync check", 0xff00ff, 20f, 1f, null));
        return items;
    }
}
