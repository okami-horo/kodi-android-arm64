package org.xbmc.kodi.danmaku.model;

import java.util.Objects;

/**
 * Normalized danmaku entry before converting to DFM-specific types.
 */
public final class DanmakuItem {
    private final long timeMs;
    private final Type type;
    private final String text;
    private final int color;
    private final float textSizeSp;
    private final float alpha;
    private final Position position; // only relevant for POSITIONED

    public DanmakuItem(long timeMs, Type type, String text, int color, float textSizeSp, float alpha, Position position) {
        this.timeMs = timeMs;
        this.type = Objects.requireNonNull(type, "type");
        this.text = Objects.requireNonNull(text, "text");
        this.color = color;
        this.textSizeSp = textSizeSp;
        this.alpha = alpha;
        this.position = position;
    }

    public long getTimeMs() {
        return timeMs;
    }

    public Type getType() {
        return type;
    }

    public String getText() {
        return text;
    }

    public int getColor() {
        return color;
    }

    public float getTextSizeSp() {
        return textSizeSp;
    }

    public float getAlpha() {
        return alpha;
    }

    public Position getPosition() {
        return position;
    }

    public enum Type {
        SCROLL,
        TOP,
        BOTTOM,
        POSITIONED
    }

    public static final class Position {
        private final float x;
        private final float y;

        public Position(float x, float y) {
            this.x = x;
            this.y = y;
        }

        public float getX() {
            return x;
        }

        public float getY() {
            return y;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Position)) return false;
            Position position = (Position) o;
            return Float.compare(position.x, x) == 0 && Float.compare(position.y, y) == 0;
        }

        @Override
        public int hashCode() {
            return Objects.hash(x, y);
        }

        @Override
        public String toString() {
            return "Position{" +
                    "x=" + x +
                    ", y=" + y +
                    '}';
        }
    }
}
