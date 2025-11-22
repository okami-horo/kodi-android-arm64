package org.xbmc.kodi.danmaku.source.local;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.xbmc.kodi.danmaku.model.MediaKey;
import org.xbmc.kodi.danmaku.model.TrackCandidate;

import java.io.File;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, manifest = Config.NONE)
public class LocalTrackDiscoveryTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    @Test
    public void prefersSameNameXmlAndSortsByScore() throws Exception {
        File video = tmp.newFile("movie.mkv");
        File sameName = tmp.newFile("movie.xml");
        File variant = tmp.newFile("movie.zh.xml");
        File other = tmp.newFile("other.xml");
        // Non-xml should be ignored
        tmp.newFile("movie.ass");

        MediaKey key = new MediaKey(video.getAbsolutePath(), 10L, 20L);
        LocalTrackDiscovery discovery = new LocalTrackDiscovery();

        List<TrackCandidate> candidates = discovery.discover(key);

        assertEquals(3, candidates.size());
        assertEquals(sameName.getAbsolutePath(), candidates.get(0).getTrack().getId());
        assertEquals("同名匹配", candidates.get(0).getReason());
        assertTrue("Same-name score should beat variants", candidates.get(0).getScore() > candidates.get(1).getScore());
        assertTrue("Variant should score above unrelated", candidates.get(1).getScore() > candidates.get(2).getScore());
        assertEquals(variant.getAbsolutePath(), candidates.get(1).getTrack().getId());
        assertEquals(other.getAbsolutePath(), candidates.get(2).getTrack().getId());
    }
}
