package org.xbmc.kodi.danmaku;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

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

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicLong;

@RunWith(RobolectricTestRunner.class)
public class DanmakuEngineTest {

    @Test
    public void playPauseSeekAndSpeedAlignWithClock() {
        FakeTime time = new FakeTime(0L);
        SoftClock clock = new SoftClock(time::now);
        FakeRenderer renderer = new FakeRenderer();
        DanmakuEngine engine = new DanmakuEngine(
                renderer,
                clock,
                new DanmakuPreferences(RuntimeEnvironment.getApplication()),
                new BiliXmlParser(),
                time::now);

        MediaKey mediaKey = new MediaKey("/path/video.mkv", 1024L, 1000L);
        DanmakuTrack track = new DanmakuTrack("track-1", "sample", DanmakuTrack.SourceType.LOCAL, "/tmp/sample.xml", DanmakuConfig.defaults(), mediaKey);
        engine.bindTrack(track, Arrays.asList(new DanmakuItem(0, DanmakuItem.Type.SCROLL, "hi", 0xffffff, 20f, 1f, null)), DanmakuConfig.defaults());
        engine.setVisibility(true);

        engine.updatePlaybackState(1_000L, 1.0f, true);
        engine.tick();

        assertEquals(1_000L, renderer.lastSeekMs);
        assertTrue("Renderer should be told to play when playback is active", renderer.playCount > 0);
        assertEquals(1.0f, renderer.lastSpeed, 0.0001f);

        time.advance(250L);
        engine.tick();
        assertEquals("Drift beyond 200ms should resync to clock", 1_250L, renderer.lastSeekMs);

        engine.updatePlaybackState(clock.nowMs(), clock.getSpeed(), false);
        engine.tick();
        assertTrue("Pause should be propagated", renderer.pauseCount > 0);

        engine.updateSpeed(1.5f);
        assertEquals(1.5f, renderer.lastSpeed, 0.0001f);

        engine.seek(8_000L);
        engine.updatePlaybackState(8_000L, 1.0f, true);
        engine.tick();
        assertEquals(8_000L, renderer.lastSeekMs);
        assertTrue(engine.getStatus().isPlaying());
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
