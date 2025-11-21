package org.xbmc.kodi.danmaku.clock;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.concurrent.atomic.AtomicLong;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
public class PlaybackClockTest {

    @Test
    public void advancesWithSpeed() {
        FakeTime time = new FakeTime(1_000L);
        SoftClock clock = new SoftClock(time::now);

        clock.anchor(0L, 1.0f, time.now(), true);
        time.advance(500L);

        assertEquals(500L, clock.nowMs());
        assertTrue(clock.isPlaying());
        assertEquals(1.0f, clock.getSpeed(), 0.0001f);
    }

    @Test
    public void pauseStopsProgress() {
        FakeTime time = new FakeTime(0L);
        SoftClock clock = new SoftClock(time::now);
        clock.anchor(1_000L, 1.0f, time.now(), true);

        time.advance(250L);
        clock.pause(clock.nowMs(), time.now());
        long pausedPosition = clock.nowMs();
        assertFalse(clock.isPlaying());

        time.advance(500L);
        assertEquals("Position should stay frozen while paused", pausedPosition, clock.nowMs());
    }

    @Test
    public void seekRealignsAnchor() {
        FakeTime time = new FakeTime(10_000L);
        SoftClock clock = new SoftClock(time::now);
        clock.anchor(0L, 1.0f, time.now(), true);

        time.advance(1_000L); // reaches ~1s
        clock.seek(5_000L, time.now()); // jump to 5s
        time.advance(200L);

        assertEquals(5_200L, clock.nowMs());
        assertTrue(clock.isPlaying());
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
            nowMs.addAndGet(deltaMs);
        }
    }
}
