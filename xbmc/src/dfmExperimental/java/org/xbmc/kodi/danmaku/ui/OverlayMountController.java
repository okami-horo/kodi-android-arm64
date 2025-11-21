package org.xbmc.kodi.danmaku.ui;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.Nullable;

import org.xbmc.kodi.danmaku.DanmakuEngine;
import org.xbmc.kodi.danmaku.DanmakuOverlayView;
import org.xbmc.kodi.danmaku.DanmakuService;

/**
 * Attaches the danmaku overlay into the player container with predictable z-order.
 */
public class OverlayMountController {
    private final Context context;
    private final DanmakuEngine engine;
    private DanmakuOverlayView overlayView;

    public OverlayMountController(Context context, DanmakuEngine engine) {
        this.context = context;
        this.engine = engine;
    }

    public DanmakuOverlayView ensureAttached(ViewGroup container, @Nullable View osdAnchor) {
        if (overlayView == null) {
            overlayView = new DanmakuOverlayView(context);
        }
        if (overlayView.getParent() != container) {
            if (overlayView.getParent() instanceof ViewGroup) {
                ((ViewGroup) overlayView.getParent()).removeView(overlayView);
            }
            int insertIndex = container.getChildCount();
            if (osdAnchor != null) {
                int osdIndex = container.indexOfChild(osdAnchor);
                insertIndex = osdIndex >= 0 ? osdIndex : container.getChildCount();
            }
            container.addView(overlayView, insertIndex, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT));
        }
        engine.attachRenderer(overlayView);
        return overlayView;
    }

    public void onPause() {
        engine.pause();
    }

    public void onResume() {
        DanmakuService.DanmakuStatus status = engine.getStatus();
        engine.updatePlaybackState(status.getPositionMs(), status.getSpeed(), status.isPlaying());
    }

    public void onConfigurationChanged(ViewGroup container, @Nullable View osdAnchor) {
        ensureAttached(container, osdAnchor);
        engine.tick();
    }

    public void release() {
        engine.detachRenderer();
        if (overlayView != null) {
            ViewGroup parent = (ViewGroup) overlayView.getParent();
            if (parent != null) {
                parent.removeView(overlayView);
            }
            overlayView.release();
        }
    }
}
