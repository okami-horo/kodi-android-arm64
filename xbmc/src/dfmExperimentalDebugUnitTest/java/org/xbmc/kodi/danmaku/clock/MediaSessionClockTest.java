package org.xbmc.kodi.danmaku.clock;

import android.media.session.PlaybackState;
import android.os.SystemClock;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.concurrent.atomic.AtomicLong;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, manifest = Config.NONE)
public class MediaSessionClockTest {

    @Test
    public void anchorsFromPlaybackState() {
        FakeTime time = new FakeTime(10_000L);
        MediaSessionClock clock = new MediaSessionClock(time::now);

        PlaybackState state = new PlaybackState.Builder()
                .setState(PlaybackState.STATE_PLAYING, 1_000L, 1.5f, time.now())
                .build();

        time.advance(500L); // simulate time after state timestamp
        clock.updateFromPlaybackState(state);

        long expected = 1_000L + (long) (500L * 1.5f);
        assertEquals(expected, clock.nowMs());
        assertTrue(clock.isPlaying());
        assertEquals(1.5f, clock.getSpeed(), 0.0001f);
    }

    private static final class FakeTime {
        private final AtomicLong nowMs;

        FakeTime(long initialMs) {
            this.nowMs = new AtomicLong(initialMs);
        }

        long now() {
            return nowMs.get();
        }

        void advance(long deltaMs) {
            SystemClock.sleep(0); // ensure SystemClock class is loaded; no-op in unit tests
            nowMs.addAndGet(deltaMs);
        }
    }
}
