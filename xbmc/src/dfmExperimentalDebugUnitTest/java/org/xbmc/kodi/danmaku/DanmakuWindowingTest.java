package org.xbmc.kodi.danmaku;

import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.xbmc.kodi.danmaku.clock.SoftClock;
import org.xbmc.kodi.danmaku.model.DanmakuConfig;
import org.xbmc.kodi.danmaku.model.DanmakuItem;
import org.xbmc.kodi.danmaku.model.DanmakuTrack;
import org.xbmc.kodi.danmaku.model.MediaKey;
import org.xbmc.kodi.danmaku.perf.DanmakuPerformanceSampler;
import org.xbmc.kodi.danmaku.source.local.BiliXmlParser;
import org.xbmc.kodi.danmaku.source.local.LocalTrackDiscovery;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicLong;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, manifest = Config.NONE)
public class DanmakuWindowingTest {

    @Test
    public void rePreparesWhenLeavingWindowAndKeepsWindowedItems() {
        FakeTime time = new FakeTime(0L);
        SoftClock clock = new SoftClock(time::now);
        FakeRenderer renderer = new FakeRenderer();
        DanmakuEngine.WindowingConfig windowing = new DanmakuEngine.WindowingConfig(50L, 50L, 10L, 0L);
        DanmakuEngine engine = new DanmakuEngine(
                renderer,
                clock,
                new DanmakuPreferences(RuntimeEnvironment.getApplication()),
                new BiliXmlParser(),
                time::now,
                new LocalTrackDiscovery(),
                windowing,
                DanmakuPerformanceSampler.noOp());

        MediaKey mediaKey = new MediaKey("/media", 10L, 1L);
        DanmakuTrack track = new DanmakuTrack(
                "t1",
                "sample",
                DanmakuTrack.SourceType.LOCAL,
                "/tmp/sample.xml",
                DanmakuConfig.defaults(),
                mediaKey);
        engine.bindTrack(track, Arrays.asList(
                new DanmakuItem(0, DanmakuItem.Type.SCROLL, "zero", 0xffffff, 18f, 1f, null),
                new DanmakuItem(80, DanmakuItem.Type.SCROLL, "eighty", 0xffffff, 18f, 1f, null),
                new DanmakuItem(120, DanmakuItem.Type.TOP, "one twenty", 0xffffff, 18f, 1f, null)
        ), DanmakuConfig.defaults());
        engine.setVisibility(true);
        engine.updatePlaybackState(0L, 1.0f, true);
        engine.tick();

        assertEquals("Initial prepare should window around start", 1, renderer.prepareCount);
        assertEquals(1, renderer.items.size());

        time.advance(70L); // well past initial window end (50ms)
        engine.tick();

        assertEquals("Window shift should trigger a new prepare", 2, renderer.prepareCount);
        assertEquals("New window should include items in view", 2, renderer.items.size());
    }

    @Test
    public void throttlesWindowReprepare() {
        FakeTime time = new FakeTime(0L);
        SoftClock clock = new SoftClock(time::now);
        FakeRenderer renderer = new FakeRenderer();
        DanmakuEngine.WindowingConfig windowing = new DanmakuEngine.WindowingConfig(30L, 30L, 5L, 50L);
        DanmakuEngine engine = new DanmakuEngine(
                renderer,
                clock,
                new DanmakuPreferences(RuntimeEnvironment.getApplication()),
                new BiliXmlParser(),
                time::now,
                new LocalTrackDiscovery(),
                windowing,
                DanmakuPerformanceSampler.noOp());

        MediaKey mediaKey = new MediaKey("/media", 10L, 1L);
        DanmakuTrack track = new DanmakuTrack(
                "t1",
                "sample",
                DanmakuTrack.SourceType.LOCAL,
                "/tmp/sample.xml",
                DanmakuConfig.defaults(),
                mediaKey);
        engine.bindTrack(track, Arrays.asList(
                new DanmakuItem(0, DanmakuItem.Type.SCROLL, "zero", 0xffffff, 18f, 1f, null),
                new DanmakuItem(60, DanmakuItem.Type.SCROLL, "sixty", 0xffffff, 18f, 1f, null)
        ), DanmakuConfig.defaults());
        engine.setVisibility(true);
        engine.updatePlaybackState(0L, 1.0f, true);
        engine.tick();

        assertEquals(1, renderer.prepareCount);

        time.advance(40L); // outside initial window but within throttle interval
        engine.tick();
        assertEquals("Throttle should prevent immediate reprepare", 1, renderer.prepareCount);

        time.advance(20L); // throttle interval elapsed (60ms since prepare)
        engine.tick();
        assertEquals("Should reprepare once throttle window passes", 2, renderer.prepareCount);
    }

    private static final class FakeTime {
        private final AtomicLong nowMs;

        FakeTime(long initial) {
            this.nowMs = new AtomicLong(initial);
        }

        long now() {
            return nowMs.get();
        }

        void advance(long delta) {
            nowMs.addAndGet(delta);
        }
    }
}
