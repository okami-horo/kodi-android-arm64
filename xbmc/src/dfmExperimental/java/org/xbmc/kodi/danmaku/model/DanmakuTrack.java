package org.xbmc.kodi.danmaku.model;

import java.util.Objects;

/**
 * Represents a danmaku track bound to a media item.
 */
public final class DanmakuTrack {
    public enum SourceType {
        LOCAL
    }

    private final String id;
    private final String name;
    private final SourceType sourceType;
    private final String filePath;
    private final DanmakuConfig config;
    private final MediaKey mediaKey;

    public DanmakuTrack(String id, String name, SourceType sourceType, String filePath, DanmakuConfig config, MediaKey mediaKey) {
        this.id = Objects.requireNonNull(id, "id");
        this.name = Objects.requireNonNull(name, "name");
        this.sourceType = Objects.requireNonNull(sourceType, "sourceType");
        this.filePath = Objects.requireNonNull(filePath, "filePath");
        this.config = config == null ? DanmakuConfig.defaults() : config;
        this.mediaKey = Objects.requireNonNull(mediaKey, "mediaKey");
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public SourceType getSourceType() {
        return sourceType;
    }

    public String getFilePath() {
        return filePath;
    }

    public DanmakuConfig getConfig() {
        return config;
    }

    public MediaKey getMediaKey() {
        return mediaKey;
    }

    public DanmakuTrack withConfig(DanmakuConfig newConfig) {
        return new DanmakuTrack(id, name, sourceType, filePath, newConfig, mediaKey);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DanmakuTrack)) return false;
        DanmakuTrack that = (DanmakuTrack) o;
        return id.equals(that.id) && mediaKey.equals(that.mediaKey);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, mediaKey);
    }

    @Override
    public String toString() {
        return "DanmakuTrack{" +
                "id='" + id + '\'' +
                ", name='" + name + '\'' +
                ", sourceType=" + sourceType +
                ", filePath='" + filePath + '\'' +
                ", mediaKey=" + mediaKey +
                '}';
    }
}
