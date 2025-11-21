package org.xbmc.kodi.danmaku.model;

import java.util.Objects;

/**
 * Unique identifier of a media item built from path + size + mtime.
 * Serialized form: path|size|mtime to allow stable SharedPreferences keys.
 */
public final class MediaKey {
    private final String path;
    private final long size;
    private final long mtime;

    public MediaKey(String path, long size, long mtime) {
        this.path = Objects.requireNonNull(path, "path");
        this.size = size;
        this.mtime = mtime;
    }

    public String getPath() {
        return path;
    }

    public long getSize() {
        return size;
    }

    public long getMtime() {
        return mtime;
    }

    public String serialize() {
        return path + "|" + size + "|" + mtime;
    }

    public static MediaKey deserialize(String raw) {
        if (raw == null || raw.isEmpty()) {
            throw new IllegalArgumentException("mediaKey is empty");
        }
        String[] parts = raw.split("\\|");
        if (parts.length != 3) {
            throw new IllegalArgumentException("mediaKey format must be path|size|mtime");
        }
        try {
            long parsedSize = Long.parseLong(parts[1]);
            long parsedMtime = Long.parseLong(parts[2]);
            return new MediaKey(parts[0], parsedSize, parsedMtime);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("mediaKey numeric parts invalid", ex);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MediaKey)) return false;
        MediaKey mediaKey = (MediaKey) o;
        return size == mediaKey.size && mtime == mediaKey.mtime && path.equals(mediaKey.path);
    }

    @Override
    public int hashCode() {
        return Objects.hash(path, size, mtime);
    }

    @Override
    public String toString() {
        return "MediaKey{" +
                "path='" + path + '\'' +
                ", size=" + size +
                ", mtime=" + mtime +
                '}';
    }
}
