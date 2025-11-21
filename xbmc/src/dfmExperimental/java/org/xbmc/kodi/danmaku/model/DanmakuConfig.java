package org.xbmc.kodi.danmaku.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Styling, density and filtering options attached to a danmaku track.
 */
public final class DanmakuConfig {
    private final float textScaleSp;
    private final float scrollSpeedFactor;
    private final float alpha;
    private final int maxOnScreen;
    private final int maxLines;
    private final List<String> keywordFilter;
    private final TypeEnabled typeEnabled;
    private final long offsetMs;

    public DanmakuConfig(
            float textScaleSp,
            float scrollSpeedFactor,
            float alpha,
            int maxOnScreen,
            int maxLines,
            List<String> keywordFilter,
            TypeEnabled typeEnabled,
            long offsetMs
    ) {
        this.textScaleSp = textScaleSp;
        this.scrollSpeedFactor = scrollSpeedFactor;
        this.alpha = alpha;
        this.maxOnScreen = maxOnScreen;
        this.maxLines = maxLines;
        this.keywordFilter = keywordFilter == null
                ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<>(keywordFilter));
        this.typeEnabled = typeEnabled == null ? TypeEnabled.allEnabled() : typeEnabled;
        this.offsetMs = offsetMs;
    }

    public static DanmakuConfig defaults() {
        return new DanmakuConfig(
                1.0f, // text scale
                1.0f, // scroll speed factor
                1.0f, // alpha
                0,    // max on screen (0 = engine default)
                0,    // max lines (0 = engine default)
                Collections.emptyList(),
                TypeEnabled.allEnabled(),
                0L
        );
    }

    public float getTextScaleSp() {
        return textScaleSp;
    }

    public float getScrollSpeedFactor() {
        return scrollSpeedFactor;
    }

    public float getAlpha() {
        return alpha;
    }

    public int getMaxOnScreen() {
        return maxOnScreen;
    }

    public int getMaxLines() {
        return maxLines;
    }

    public List<String> getKeywordFilter() {
        return keywordFilter;
    }

    public TypeEnabled getTypeEnabled() {
        return typeEnabled;
    }

    public long getOffsetMs() {
        return offsetMs;
    }

    public DanmakuConfig withOffset(long offsetDeltaMs) {
        return new DanmakuConfig(
                textScaleSp,
                scrollSpeedFactor,
                alpha,
                maxOnScreen,
                maxLines,
                keywordFilter,
                typeEnabled,
                offsetMs + offsetDeltaMs
        );
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DanmakuConfig)) return false;
        DanmakuConfig that = (DanmakuConfig) o;
        return Float.compare(that.textScaleSp, textScaleSp) == 0
                && Float.compare(that.scrollSpeedFactor, scrollSpeedFactor) == 0
                && Float.compare(that.alpha, alpha) == 0
                && maxOnScreen == that.maxOnScreen
                && maxLines == that.maxLines
                && offsetMs == that.offsetMs
                && Objects.equals(keywordFilter, that.keywordFilter)
                && Objects.equals(typeEnabled, that.typeEnabled);
    }

    @Override
    public int hashCode() {
        return Objects.hash(textScaleSp, scrollSpeedFactor, alpha, maxOnScreen, maxLines, keywordFilter, typeEnabled, offsetMs);
    }

    @Override
    public String toString() {
        return "DanmakuConfig{" +
                "textScaleSp=" + textScaleSp +
                ", scrollSpeedFactor=" + scrollSpeedFactor +
                ", alpha=" + alpha +
                ", maxOnScreen=" + maxOnScreen +
                ", maxLines=" + maxLines +
                ", keywordFilter=" + keywordFilter +
                ", typeEnabled=" + typeEnabled +
                ", offsetMs=" + offsetMs +
                '}';
    }

    public static final class TypeEnabled {
        private final boolean scroll;
        private final boolean top;
        private final boolean bottom;
        private final boolean positioned;

        public TypeEnabled(boolean scroll, boolean top, boolean bottom, boolean positioned) {
            this.scroll = scroll;
            this.top = top;
            this.bottom = bottom;
            this.positioned = positioned;
        }

        public static TypeEnabled allEnabled() {
            return new TypeEnabled(true, true, true, true);
        }

        public boolean isScroll() {
            return scroll;
        }

        public boolean isTop() {
            return top;
        }

        public boolean isBottom() {
            return bottom;
        }

        public boolean isPositioned() {
            return positioned;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof TypeEnabled)) return false;
            TypeEnabled that = (TypeEnabled) o;
            return scroll == that.scroll && top == that.top && bottom == that.bottom && positioned == that.positioned;
        }

        @Override
        public int hashCode() {
            return Objects.hash(scroll, top, bottom, positioned);
        }

        @Override
        public String toString() {
            return "TypeEnabled{" +
                    "scroll=" + scroll +
                    ", top=" + top +
                    ", bottom=" + bottom +
                    ", positioned=" + positioned +
                    '}';
        }
    }
}
