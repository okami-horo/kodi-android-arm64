package org.xbmc.kodi.danmaku;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.xbmc.kodi.danmaku.clock.SoftClock;
import org.xbmc.kodi.danmaku.model.MediaKey;
import org.xbmc.kodi.danmaku.source.local.BiliXmlParser;
import org.xbmc.kodi.danmaku.source.local.LocalTrackDiscovery;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.io.FileOutputStream;
import java.util.concurrent.atomic.AtomicLong;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, manifest = Config.NONE)
public class DanmakuEngineSwitchTrackTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    @Test
    public void switchTrackResyncsToClockAndLoadsFromDisk() throws Exception {
        FakeTime time = new FakeTime(0L);
        SoftClock clock = new SoftClock(time::now);
        FakeRenderer renderer = new FakeRenderer();
        DanmakuEngine engine = new DanmakuEngine(
                renderer,
                clock,
                new DanmakuPreferences(RuntimeEnvironment.getApplication()),
                new BiliXmlParser(),
                time::now,
                new LocalTrackDiscovery());

        File video = tmp.newFile("clip.mkv");
        File first = tmp.newFile("clip.xml");
        File second = tmp.newFile("clip.alt.xml");
        writeDanmaku(first, "0.5,1,25,255,0,0,0,0", "hello");
        writeDanmaku(second, "1.0,1,20,65280,0,0,0,0", "alt");

        MediaKey key = new MediaKey(video.getAbsolutePath(), 123L, 456L);

        // Discovery should find first track and auto-select when it is the only candidate so far.
        engine.getTrackCandidates(key);
        engine.updatePlaybackState(2_000L, 1.0f, true);
        engine.setVisibility(true);
        engine.tick();

        assertTrue("Initial track should prepare renderer", renderer.prepareCount > 0);
        assertEquals(2_000L, renderer.lastSeekMs);

        // Switch to the second track and ensure it loads from disk, then re-seeks to current clock.
        engine.selectTrack(key, second.getAbsolutePath());
        engine.tick();

        assertTrue("Switching track re-prepares renderer", renderer.prepareCount > 1);
        assertEquals("Switch should reseek to current position", 2_000L, renderer.lastSeekMs);
        assertTrue("New track items should be loaded from disk", renderer.items.stream().anyMatch(item -> "alt".equals(item.getText())));
    }

    private void writeDanmaku(File file, String pAttr, String text) throws Exception {
        String xml = "<i><d p=\"" + pAttr + "\">" + text + "</d></i>";
        try (FileOutputStream out = new FileOutputStream(file)) {
            out.write(xml.getBytes(StandardCharsets.UTF_8));
        }
    }

    private static final class FakeTime {
        private final AtomicLong nowMs;

        FakeTime(long initial) {
            this.nowMs = new AtomicLong(initial);
        }

        long now() {
            return nowMs.get();
        }
    }
}
