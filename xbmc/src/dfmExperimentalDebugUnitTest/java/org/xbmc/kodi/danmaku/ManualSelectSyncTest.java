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
import org.xbmc.kodi.danmaku.ui.ManualFilePicker;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.io.FileOutputStream;
import java.util.concurrent.atomic.AtomicLong;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, manifest = Config.NONE)
public class ManualSelectSyncTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    @Test
    public void manualSelectionLoadsTrackAndResyncs() throws Exception {
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

        File video = tmp.newFile("manual.mkv");
        MediaKey key = new MediaKey(video.getAbsolutePath(), 11L, 22L);
        File manualXml = tmp.newFile("manual.xml");
        writeDanmaku(manualXml, "0.0,1,25,255,0,0,0,0", "from-manual");

        ManualFilePicker picker = new ManualFilePicker();
        assertTrue("Manual selection should accept readable xml", picker.selectLocalFile(manualXml.getAbsolutePath(), key, engine));

        engine.updatePlaybackState(3_000L, 1.0f, true);
        engine.setVisibility(true);
        engine.tick();

        assertTrue("Renderer should be prepared after manual selection", renderer.prepareCount > 0);
        assertEquals("Engine should resync to current clock after manual pick", 3_000L, renderer.lastSeekMs);
        assertTrue(renderer.items.stream().anyMatch(item -> "from-manual".equals(item.getText())));
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
