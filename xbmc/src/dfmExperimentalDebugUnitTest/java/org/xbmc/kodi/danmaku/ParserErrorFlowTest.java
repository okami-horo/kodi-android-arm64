package org.xbmc.kodi.danmaku;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
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

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicLong;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, manifest = Config.NONE)
public class ParserErrorFlowTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    @Test
    public void malformedXmlRaisesParseException() {
        String broken = "<i><d p=\"0.1,1,20,255\">oops"; // missing closing tags
        BiliXmlParser parser = new BiliXmlParser();
        try {
            parser.parse(new ByteArrayInputStream(broken.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            assertTrue(ex instanceof BiliXmlParser.ParseException);
            assertEquals(BiliXmlParser.ErrorReason.MALFORMED, ((BiliXmlParser.ParseException) ex).getReason());
            return;
        }
        throw new AssertionError("Expected parse exception");
    }

    @Test
    public void engineExposesLastErrorOnMissingFile() {
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

        MediaKey key = new MediaKey("/no/such/video.mkv", 1L, 1L);

        engine.selectTrack(key, "/no/such/file.xml");

        DanmakuEngine.LoadError error = engine.getLastError();
        assertEquals("missing", error.getReason());
        assertNull(engine.getActiveTrack());
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
