package org.xbmc.kodi.danmaku.ui;

import android.view.MenuItem;

import org.xbmc.kodi.R;
/**
 * Handles OSD menu actions related to danmaku.
 */
public class OsdActions {
    public interface Listener {
        void onToggleVisibility();

        void onSelectTrack();

        void onInjectSample();

        void onOpenSettings();
    }

    private final Listener listener;

    public OsdActions(Listener listener) {
        this.listener = listener;
    }

    public boolean onMenuItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_toggle_danmaku) {
            listener.onToggleVisibility();
            return true;
        } else if (id == R.id.action_select_danmaku_track) {
            listener.onSelectTrack();
            return true;
        } else if (id == R.id.action_inject_danmaku) {
            listener.onInjectSample();
            return true;
        } else if (id == R.id.action_open_danmaku_settings) {
            listener.onOpenSettings();
            return true;
        }
        return false;
    }
}
