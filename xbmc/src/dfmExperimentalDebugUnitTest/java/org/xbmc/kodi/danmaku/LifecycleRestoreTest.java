package org.xbmc.kodi.danmaku;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
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
@Config(sdk = 34, manifest = Config.NONE)
public class LifecycleRestoreTest {

    @Test
    public void reattachesRendererOnConfigurationChange() {
        FakeTime time = new FakeTime(0L);
        SoftClock clock = new SoftClock(time::now);
        FakeRenderer renderer = new FakeRenderer();
        DanmakuEngine engine = new DanmakuEngine(
                renderer,
                clock,
                new DanmakuPreferences(RuntimeEnvironment.getApplication()),
                new BiliXmlParser(),
                time::now);

        MediaKey key = new MediaKey("/video", 1L, 2L);
        DanmakuTrack track = new DanmakuTrack("demo", "demo", DanmakuTrack.SourceType.LOCAL, "/tmp/demo.xml", DanmakuConfig.defaults(), key);
        engine.bindTrack(track, Collections.singletonList(new DanmakuItem(0, DanmakuItem.Type.SCROLL, "demo", 0xffffff, 18f, 1f, null)), DanmakuConfig.defaults());
        engine.setVisibility(true);
        engine.updatePlaybackState(5_000L, 1.0f, true);
        engine.tick();

        assertEquals(5_000L, renderer.lastSeekMs);
        assertTrue(renderer.playCount > 0);

        engine.detachRenderer();
        FakeRenderer recreated = new FakeRenderer();
        engine.attachRenderer(recreated);
        engine.tick();

        assertTrue("Renderer should be re-prepared after reattach", recreated.prepareCount > 0);
        assertEquals("Position should restore on reattach", 5_000L, recreated.lastSeekMs);
        assertTrue("Playback should remain active", recreated.playCount > 0);
        assertTrue(recreated.visible);
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
