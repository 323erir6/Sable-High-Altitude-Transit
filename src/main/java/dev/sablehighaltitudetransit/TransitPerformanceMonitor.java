package dev.sablehighaltitudetransit;

import com.mojang.logging.LogUtils;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import org.slf4j.Logger;

import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/** Bounded diagnostics: no stack walks on healthy ticks, no world access off-thread. */
public final class TransitPerformanceMonitor {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final long SLOW_TICK_NS = TimeUnit.MILLISECONDS.toNanos(100);
    private static final long REPORT_NS = TimeUnit.SECONDS.toNanos(5);
    private static final Map<String, Integer> SAMPLES = new HashMap<>();
    private static volatile Thread serverThread;
    private static volatile long tickStarted;
    private static ScheduledExecutorService sampler;
    private static long lastReport;
    private static long accumulatedNanos;
    private static long maximumNanos;
    private static long ticks;
    private static long skippedTerrainBodies;
    private static final java.util.concurrent.atomic.LongAdder skippedEmptySkyReads =
            new java.util.concurrent.atomic.LongAdder();

    private TransitPerformanceMonitor() {}

    public static synchronized void onServerStarted(ServerStartedEvent event) {
        stopSampler();
        lastReport = System.nanoTime();
        accumulatedNanos = maximumNanos = ticks = skippedTerrainBodies = 0;
        skippedEmptySkyReads.reset();
        sampler = Executors.newSingleThreadScheduledExecutor(task -> {
            Thread thread = new Thread(task, "Sable transit diagnostics");
            thread.setDaemon(true);
            return thread;
        });
        sampler.scheduleWithFixedDelay(TransitPerformanceMonitor::sampleSlowTick,
                100, 100, TimeUnit.MILLISECONDS);
    }

    public static void beginTick() {
        serverThread = Thread.currentThread();
        tickStarted = System.nanoTime();
    }

    public static void endTick() {
        long start = tickStarted;
        tickStarted = 0;
        if (start == 0) return;
        long now = System.nanoTime();
        long elapsed = now - start;
        accumulatedNanos += elapsed;
        maximumNanos = Math.max(maximumNanos, elapsed);
        ticks++;
        if (now - lastReport < REPORT_NS) return;

        if (HighAltitudeTransitManager.isTransitActive()) {
            String samples;
            synchronized (SAMPLES) {
                samples = SAMPLES.entrySet().stream()
                        .sorted(Map.Entry.comparingByValue(Comparator.reverseOrder()))
                        .limit(3)
                        .map(entry -> "\nSamples=" + entry.getValue() + "\n" + entry.getKey())
                        .reduce("", String::concat);
                SAMPLES.clear();
            }
            LOGGER.info("[Transit perf] ticks={}, meanMs={}, maxMs={}, terrainLoopsSkipped={}, emptySkyReadsSkipped={}{}",
                    ticks, accumulatedNanos / Math.max(1L, ticks) / 1_000_000.0,
                    maximumNanos / 1_000_000.0, skippedTerrainBodies,
                    skippedEmptySkyReads.sumThenReset(), samples);
        } else {
            synchronized (SAMPLES) { SAMPLES.clear(); }
            skippedEmptySkyReads.reset();
        }
        lastReport = now;
        accumulatedNanos = maximumNanos = ticks = skippedTerrainBodies = 0;
    }

    public static void terrainLoopSkipped() {
        skippedTerrainBodies++;
    }

    public static void emptySkyReadSkipped() {
        // Level reads can also originate from workers; do not share the
        // server-thread-only counter used by the terrain ticket loop.
        skippedEmptySkyReads.increment();
    }

    private static void sampleSlowTick() {
        long start = tickStarted;
        Thread thread = serverThread;
        if (start == 0 || thread == null || !HighAltitudeTransitManager.isTransitActive()
                || System.nanoTime() - start < SLOW_TICK_NS) return;
        try {
            StackTraceElement[] trace = thread.getStackTrace();
            if (tickStarted != start) return; // Tick ended during the sample.
            String stack = Arrays.stream(trace).limit(40)
                    .map(frame -> "    " + frame + "\n").reduce("", String::concat);
            synchronized (SAMPLES) {
                if (SAMPLES.size() < 32 || SAMPLES.containsKey(stack)) {
                    SAMPLES.merge(stack, 1, Integer::sum);
                }
            }
        } catch (RuntimeException exception) {
            // Diagnostics must never interfere with simulation or terminate the sampler.
        }
    }

    public static synchronized void onServerStopped(ServerStoppedEvent event) {
        stopSampler();
    }

    private static void stopSampler() {
        tickStarted = 0;
        serverThread = null;
        if (sampler != null) {
            sampler.shutdownNow();
            sampler = null;
        }
        synchronized (SAMPLES) { SAMPLES.clear(); }
    }
}
