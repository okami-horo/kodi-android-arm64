package org.xbmc.kodi.danmaku;

import android.content.Context;
import android.content.res.Resources;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import org.xbmc.kodi.danmaku.model.DanmakuConfig;
import org.xbmc.kodi.danmaku.model.DanmakuItem;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import master.flame.danmaku.controller.DrawHandler;
import master.flame.danmaku.danmaku.model.BaseDanmaku;
import master.flame.danmaku.danmaku.model.IDanmakus;
import master.flame.danmaku.danmaku.model.android.DanmakuContext;
import master.flame.danmaku.danmaku.model.android.Danmakus;
import master.flame.danmaku.danmaku.parser.BaseDanmakuParser;
import master.flame.danmaku.ui.widget.DanmakuView;

/**
 * FrameLayout wrapper hosting the DFM DanmakuView.
 */
public class DanmakuOverlayView extends FrameLayout implements DanmakuRenderer {
    private final DanmakuView danmakuView;
    private DanmakuContext danmakuContext;
    private boolean prepared;
    private DanmakuConfig config = DanmakuConfig.defaults();

    public DanmakuOverlayView(Context context) {
        this(context, null);
    }

    public DanmakuOverlayView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public DanmakuOverlayView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        danmakuView = new DanmakuView(context, attrs);
        danmakuView.setCallback(new DrawHandler.Callback() {
            @Override
            public void updateTimer(master.flame.danmaku.danmaku.model.DanmakuTimer timer) {
            }

            @Override
            public void prepared() {
                prepared = true;
            }

            @Override
            public void danmakuShown(BaseDanmaku danmaku) {
            }

            @Override
            public void drawingFinished() {
            }
        });
        danmakuView.setLayoutParams(new LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        danmakuView.enableDanmakuDrawingCache(true);
        addView(danmakuView);
    }

    @Override
    public void prepare(List<DanmakuItem> items, DanmakuConfig config) {
        this.config = config == null ? DanmakuConfig.defaults() : config;
        danmakuContext = DanmakuContext.create();
        applyConfig(this.config);
        prepared = false;
        danmakuView.release();
        danmakuView.prepare(new ItemParser(items, this.config, danmakuContext, getResources()), danmakuContext);
        prepared = true;
    }

    @Override
    public void play() {
        if (prepared) {
            danmakuView.resume();
        }
    }

    @Override
    public void pause() {
        if (prepared) {
            danmakuView.pause();
        }
    }

    @Override
    public void seekTo(long positionMs) {
        if (prepared) {
            danmakuView.seekTo(positionMs);
        }
    }

    @Override
    public void setSpeed(float speed) {
        if (danmakuContext != null) {
            danmakuContext.setScrollSpeedFactor(speed);
        }
    }

    @Override
    public void setVisible(boolean visible) {
        if (visible) {
            danmakuView.show();
        } else {
            danmakuView.hide();
        }
    }

    @Override
    public boolean isVisible() {
        return danmakuView.isShown();
    }

    @Override
    public boolean isPrepared() {
        return prepared;
    }

    @Override
    public void release() {
        danmakuView.release();
        prepared = false;
    }

    private void applyConfig(DanmakuConfig config) {
        if (danmakuContext == null) {
            return;
        }
        danmakuContext.setDanmakuTransparency(config.getAlpha());
        danmakuContext.setScrollSpeedFactor(config.getScrollSpeedFactor());
        if (config.getMaxOnScreen() > 0) {
            danmakuContext.setMaximumVisibleSizeInScreen(config.getMaxOnScreen());
        }
        if (config.getMaxLines() > 0) {
            Map<Integer, Integer> lines = new HashMap<>();
            lines.put(BaseDanmaku.TYPE_SCROLL_RL, config.getMaxLines());
            lines.put(BaseDanmaku.TYPE_FIX_TOP, config.getMaxLines());
            lines.put(BaseDanmaku.TYPE_FIX_BOTTOM, config.getMaxLines());
            danmakuContext.setMaximumLines(lines);
        }
    }

    private static final class ItemParser extends BaseDanmakuParser {
        private final List<DanmakuItem> items;
        private final DanmakuConfig config;
        private final DanmakuContext context;
        private final Resources resources;

        ItemParser(List<DanmakuItem> items, DanmakuConfig config, DanmakuContext context, Resources resources) {
            this.items = items;
            this.config = config;
            this.context = context;
            this.resources = resources;
        }

        @Override
        protected IDanmakus parse() {
            Danmakus danmakus = new Danmakus();
            for (DanmakuItem item : items) {
                if (!isEnabled(item, config)) {
                    continue;
                }
                BaseDanmaku danmaku = context.mDanmakuFactory.createDanmaku(mapType(item.getType()), context);
                if (danmaku == null) {
                    continue;
                }
                danmaku.text = item.getText();
                danmaku.textSize = toPx(item.getTextSizeSp() * config.getTextScaleSp());
                danmaku.textColor = item.getColor();
                danmaku.setTime(Math.max(0, item.getTimeMs() + config.getOffsetMs()));
                danmakus.addItem(danmaku);
            }
            return danmakus;
        }

        private int toPx(float sp) {
            return (int) TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_SP,
                    sp,
                    resources.getDisplayMetrics());
        }

        private int mapType(DanmakuItem.Type type) {
            switch (type) {
                case TOP:
                    return BaseDanmaku.TYPE_FIX_TOP;
                case BOTTOM:
                    return BaseDanmaku.TYPE_FIX_BOTTOM;
                case POSITIONED:
                    return BaseDanmaku.TYPE_SPECIAL;
                case SCROLL:
                default:
                    return BaseDanmaku.TYPE_SCROLL_RL;
            }
        }

        private boolean isEnabled(DanmakuItem item, DanmakuConfig config) {
            DanmakuConfig.TypeEnabled enabled = config.getTypeEnabled();
            switch (item.getType()) {
                case TOP:
                    return enabled.isTop();
                case BOTTOM:
                    return enabled.isBottom();
                case POSITIONED:
                    return enabled.isPositioned();
                case SCROLL:
                default:
                    return enabled.isScroll();
            }
        }
    }
}
