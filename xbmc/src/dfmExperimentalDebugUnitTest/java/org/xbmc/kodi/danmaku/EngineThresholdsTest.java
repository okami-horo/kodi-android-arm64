package org.xbmc.kodi.danmaku;

import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.xbmc.kodi.danmaku.clock.SoftClock;
import org.xbmc.kodi.danmaku.model.DanmakuConfig;
import org.xbmc.kodi.danmaku.model.DanmakuItem;
import org.xbmc.kodi.danmaku.model.DanmakuTrack;
import org.xbmc.kodi.danmaku.model.MediaKey;
import org.xbmc.kodi.danmaku.source.local.BiliXmlParser;

import java.util.Collections;
import java.util.concurrent.atomic.AtomicLong;

@RunWith(RobolectricTestRunner.class)
public class EngineThresholdsTest {

    @Test
    public void resyncsWhenDriftExceedsThreshold() {
        FakeTime time = new FakeTime(0L);
        SoftClock clock = new SoftClock(time::now);
        FakeRenderer renderer = new FakeRenderer();
        DanmakuEngine engine = new DanmakuEngine(
                renderer,
                clock,
                new DanmakuPreferences(RuntimeEnvironment.getApplication()),
                new BiliXmlParser(),
                time::now);

        DanmakuTrack track = new DanmakuTrack("t1", "sample", DanmakuTrack.SourceType.LOCAL, "/tmp/sample.xml", DanmakuConfig.defaults(), new MediaKey("/path", 1L, 1L));
        DanmakuConfig config = new DanmakuConfig(1f, 1f, 1f, 0, 0, Collections.emptyList(), DanmakuConfig.TypeEnabled.allEnabled(), 300L);
        engine.bindTrack(track, Collections.singletonList(new DanmakuItem(0, DanmakuItem.Type.SCROLL, "one", 0xffffff, 20f, 1f, null)), config);
        engine.setVisibility(true);
        engine.updatePlaybackState(0L, 1.0f, true);
        engine.tick();

        assertEquals("Offset should be applied on first seek", 300L, renderer.lastSeekMs);

        time.advance(150L);
        engine.tick();
        assertEquals("Within 200ms threshold should not resync", 300L, renderer.lastSeekMs);

        time.advance(75L);
        engine.tick();
        assertEquals("Crossing threshold should trigger resync", 525L, renderer.lastSeekMs);
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
