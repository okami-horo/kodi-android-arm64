package org.xbmc.kodi.danmaku.perf;

import android.util.Log;

import androidx.annotation.VisibleForTesting;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.function.LongSupplier;

/**
 * Lightweight sampler that records engine performance metrics and writes a debug report.
 */
public final class DanmakuPerformanceSampler {
    private static final String TAG = "DanmakuPerf";
    private static final long MIN_FLUSH_INTERVAL_MS = 5_000L;

    private final LongSupplier realtimeNow;
    private final File reportFile;
    private final Stats prepareStats = new Stats();
    private final Stats resyncStats = new Stats();
    private final Stats windowWidthStats = new Stats();

    private boolean disabled;
    private int lastPreparedTotal;
    private int lastPreparedWindow;
    private int throttleCount;
    private long lastFlushRealtime;

    public static DanmakuPerformanceSampler createDefault(LongSupplier realtimeNow) {
        File dir = new File("xbmc/build/reports/danmaku/perf");
        return new DanmakuPerformanceSampler(realtimeNow, new File(dir, "last-run.json"), false);
    }

    public static DanmakuPerformanceSampler noOp() {
        return new DanmakuPerformanceSampler(() -> 0L, null, true);
    }

    public DanmakuPerformanceSampler(LongSupplier realtimeNow, File reportFile) {
        this(realtimeNow, reportFile, false);
    }

    private DanmakuPerformanceSampler(LongSupplier realtimeNow, File reportFile, boolean disabled) {
        this.realtimeNow = realtimeNow;
        this.reportFile = reportFile;
        this.disabled = disabled || reportFile == null;
    }

    public void recordPrepare(long durationMs, int totalItems, int windowedItems) {
        if (disabled) {
            return;
        }
        prepareStats.add(durationMs);
        lastPreparedTotal = totalItems;
        lastPreparedWindow = windowedItems;
        flushIfDue();
    }

    public void recordResync(long driftMs) {
        if (disabled) {
            return;
        }
        resyncStats.add(driftMs);
        flushIfDue();
    }

    public void recordWindow(long startMs, long endMs, int windowedItems) {
        if (disabled) {
            return;
        }
        windowWidthStats.add(Math.max(0L, endMs - startMs));
        lastPreparedWindow = windowedItems;
        flushIfDue();
    }

    public void recordThrottle(String reason) {
        if (disabled) {
            return;
        }
        throttleCount++;
        flushIfDue();
        logDebug("throttled: " + reason);
    }

    public void flush() {
        if (disabled) {
            return;
        }
        if (reportFile == null) {
            return;
        }
        File parent = reportFile.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            Log.w(TAG, "Unable to create perf report directory: " + parent.getAbsolutePath());
            disabled = true;
            return;
        }
        String content = buildJson();
        try (FileWriter writer = new FileWriter(reportFile, false)) {
            writer.write(content);
            lastFlushRealtime = realtimeNow.getAsLong();
        } catch (IOException ex) {
            Log.w(TAG, "Failed to write perf report", ex);
            disabled = true;
        }
    }

    @VisibleForTesting
    int getThrottleCount() {
        return throttleCount;
    }

    @VisibleForTesting
    Stats getPrepareStats() {
        return prepareStats;
    }

    @VisibleForTesting
    Stats getResyncStats() {
        return resyncStats;
    }

    @VisibleForTesting
    Stats getWindowWidthStats() {
        return windowWidthStats;
    }

    private void flushIfDue() {
        long now = realtimeNow.getAsLong();
        if (now - lastFlushRealtime >= MIN_FLUSH_INTERVAL_MS) {
            flush();
        }
    }

    private String buildJson() {
        return "{\n"
                + "  \"prepareCount\": " + prepareStats.count + ",\n"
                + "  \"prepareAvgMs\": " + prepareStats.avg() + ",\n"
                + "  \"prepareMaxMs\": " + prepareStats.max + ",\n"
                + "  \"resyncCount\": " + resyncStats.count + ",\n"
                + "  \"resyncMaxDriftMs\": " + resyncStats.max + ",\n"
                + "  \"windowCount\": " + windowWidthStats.count + ",\n"
                + "  \"windowAvgWidthMs\": " + windowWidthStats.avg() + ",\n"
                + "  \"throttleCount\": " + throttleCount + ",\n"
                + "  \"lastPreparedTotal\": " + lastPreparedTotal + ",\n"
                + "  \"lastPreparedWindow\": " + lastPreparedWindow + ",\n"
                + "  \"timestampMs\": " + realtimeNow.getAsLong() + "\n"
                + "}\n";
    }

    private void logDebug(String message) {
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, message);
        }
    }

    public static final class Stats {
        private long count;
        private long total;
        private long max = 0L;

        void add(long value) {
            count++;
            total += value;
            if (value > max) {
                max = value;
            }
        }

        public long avg() {
            return count == 0 ? 0L : total / count;
        }

        public long getCount() {
            return count;
        }

        public long getMax() {
            return max;
        }
    }
}
