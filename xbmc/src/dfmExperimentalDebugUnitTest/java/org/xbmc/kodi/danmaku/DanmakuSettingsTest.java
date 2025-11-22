package org.xbmc.kodi.danmaku;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import android.content.SharedPreferences;

import org.junit.Before;
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
import org.xbmc.kodi.danmaku.source.local.BiliXmlParser;
import org.xbmc.kodi.danmaku.source.local.LocalTrackDiscovery;

import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicLong;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, manifest = Config.NONE)
public class DanmakuSettingsTest {

    @Before
    public void clearPrefs() {
        SharedPreferences prefs = RuntimeEnvironment.getApplication()
                .getSharedPreferences("danmaku_prefs", 0);
        prefs.edit().clear().apply();
    }

    @Test
    public void updateConfigFiltersAndPersists() {
        FakeTime time = new FakeTime(0L);
        SoftClock clock = new SoftClock(time::now);
        FakeRenderer renderer = new FakeRenderer();
        DanmakuPreferences preferences = new DanmakuPreferences(RuntimeEnvironment.getApplication());
        DanmakuEngine engine = new DanmakuEngine(
                renderer,
                clock,
                preferences,
                new BiliXmlParser(),
                time::now,
                new LocalTrackDiscovery());

        MediaKey key = new MediaKey("/media/video.mkv", 10L, 20L);
        DanmakuTrack track = new DanmakuTrack("track-1", "video.xml", DanmakuTrack.SourceType.LOCAL, "/tmp/video.xml", DanmakuConfig.defaults(), key);
        DanmakuItem spam = new DanmakuItem(0, DanmakuItem.Type.SCROLL, "spam keyword", 0xffffff, 20f, 1f, null);
        DanmakuItem top = new DanmakuItem(1000, DanmakuItem.Type.TOP, "top line", 0xffffff, 20f, 1f, null);

        engine.bindTrack(track, Arrays.asList(spam, top), DanmakuConfig.defaults());
        engine.setVisibility(true);
        engine.updatePlaybackState(0, 1.0f, true);
        engine.tick();
        assertEquals(2, renderer.items.size());

        DanmakuConfig config = new DanmakuConfig(
                1.2f,
                0.9f,
                0.8f,
                5,
                3,
                Collections.singletonList("spam"),
                new DanmakuConfig.TypeEnabled(true, false, true, true),
                120L
        );

        engine.updateConfig(config);
        engine.tick();

        assertEquals("Filtered list should exclude keyword and disabled types", 0, renderer.items.size());
        DanmakuConfig saved = preferences.getConfig(key);
        assertEquals(config, saved);
    }

    @Test
    public void defaultConfigUsedWhenNoMediaConfig() {
        FakeTime time = new FakeTime(0L);
        SoftClock clock = new SoftClock(time::now);
        FakeRenderer renderer = new FakeRenderer();
        DanmakuPreferences preferences = new DanmakuPreferences(RuntimeEnvironment.getApplication());
        DanmakuConfig defaultConfig = new DanmakuConfig(
                1.1f, 1.0f, 0.7f,
                4, 2,
                Collections.singletonList("hide"),
                DanmakuConfig.TypeEnabled.allEnabled(),
                50L);
        preferences.saveDefaultConfig(defaultConfig);

        DanmakuEngine engine = new DanmakuEngine(
                renderer,
                clock,
                preferences,
                new BiliXmlParser(),
                time::now,
                new LocalTrackDiscovery());

        MediaKey key = new MediaKey("/media/another.mkv", 11L, 22L);
        DanmakuTrack track = new DanmakuTrack("track-2", "another.xml", DanmakuTrack.SourceType.LOCAL, "/tmp/another.xml", DanmakuConfig.defaults(), key);
        DanmakuItem item = new DanmakuItem(0, DanmakuItem.Type.SCROLL, "text", 0xffffff, 20f, 1f, null);

        engine.bindTrack(track, Collections.singletonList(item), DanmakuConfig.defaults());
        engine.setVisibility(true);
        engine.updatePlaybackState(0, 1.0f, true);
        engine.tick();

        assertEquals(defaultConfig, renderer.config);
        assertFalse(renderer.items.isEmpty());
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
