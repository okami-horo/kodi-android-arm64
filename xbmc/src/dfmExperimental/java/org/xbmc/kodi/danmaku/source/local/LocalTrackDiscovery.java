package org.xbmc.kodi.danmaku.source.local;

import org.xbmc.kodi.danmaku.model.DanmakuConfig;
import org.xbmc.kodi.danmaku.model.DanmakuTrack;
import org.xbmc.kodi.danmaku.model.MediaKey;
import org.xbmc.kodi.danmaku.model.TrackCandidate;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Best-effort local discovery of danmaku tracks in the same directory as the media file.
 */
public class LocalTrackDiscovery {
    private static final String EXT_XML = ".xml";

    public List<TrackCandidate> discover(MediaKey mediaKey) {
        if (mediaKey == null || mediaKey.getPath() == null || mediaKey.getPath().isEmpty()) {
            return Collections.emptyList();
        }
        File videoFile = new File(mediaKey.getPath());
        File parent = videoFile.getParentFile();
        if (parent == null || !parent.isDirectory()) {
            return Collections.emptyList();
        }
        String videoBase = baseName(videoFile.getName());
        File[] files = parent.listFiles();
        if (files == null || files.length == 0) {
            return Collections.emptyList();
        }
        List<TrackCandidate> candidates = new ArrayList<>();
        for (File file : files) {
            if (file == null || !file.isFile() || !file.canRead()) {
                continue;
            }
            String name = file.getName();
            if (!name.toLowerCase(Locale.US).endsWith(EXT_XML)) {
                continue;
            }
            String base = baseName(name);
            int score = score(videoBase, base);
            String reason = reason(videoBase, base);
            DanmakuTrack track = new DanmakuTrack(file.getAbsolutePath(), name, DanmakuTrack.SourceType.LOCAL, file.getAbsolutePath(), DanmakuConfig.defaults(), mediaKey);
            candidates.add(new TrackCandidate(track, score, reason));
        }
        candidates.sort(Comparator.comparingInt(TrackCandidate::getScore).reversed()
                .thenComparing(c -> c.getTrack().getName()));
        return candidates;
    }

    private String baseName(String name) {
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    private int score(String videoBase, String candidateBase) {
        if (candidateBase.equalsIgnoreCase(videoBase)) {
            return 100;
        }
        if (candidateBase.startsWith(videoBase)) {
            return 90;
        }
        if (videoBase.startsWith(candidateBase)) {
            return 85;
        }
        if (candidateBase.contains(videoBase)) {
            return 80;
        }
        return 50;
    }

    private String reason(String videoBase, String candidateBase) {
        if (candidateBase.equalsIgnoreCase(videoBase)) {
            return "同名匹配";
        }
        if (candidateBase.startsWith(videoBase) || videoBase.startsWith(candidateBase)) {
            return "前缀匹配";
        }
        if (candidateBase.contains(videoBase)) {
            return "包含匹配";
        }
        return "扩展名匹配";
    }
}
