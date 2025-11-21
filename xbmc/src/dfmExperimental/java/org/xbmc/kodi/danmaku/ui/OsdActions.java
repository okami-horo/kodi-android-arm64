package org.xbmc.kodi.danmaku.ui;

import android.view.MenuItem;

import org.xbmc.kodi.R;
import org.xbmc.kodi.danmaku.DanmakuEngine;
import org.xbmc.kodi.danmaku.DanmakuService;

/**
 * Handles OSD menu actions related to danmaku.
 */
public class OsdActions {
    public interface Listener {
        void onSelectTrack();

        void onInjectSample();
    }

    private final DanmakuEngine engine;
    private final Listener listener;

    public OsdActions(DanmakuEngine engine, Listener listener) {
        this.engine = engine;
        this.listener = listener;
    }

    public boolean onMenuItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_toggle_danmaku) {
            DanmakuService.DanmakuStatus status = engine.getStatus();
            engine.setVisibility(!status.isVisible());
            return true;
        } else if (id == R.id.action_select_danmaku_track) {
            listener.onSelectTrack();
            return true;
        } else if (id == R.id.action_inject_danmaku) {
            listener.onInjectSample();
            return true;
        }
        return false;
    }
}
