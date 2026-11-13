package de.thm.mni.wikidata.neo4j.progress;

import java.io.PrintStream;
import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public final class ProgressBar implements AutoCloseable {

    static final int WIDTH = 30;
    private static final Duration MIN_ELAPSED_FOR_ETA = Duration.ofSeconds(3);
    private static final String CLEAR_TO_END_OF_LINE = "\u001B[K";

    private final ProgressTracker tracker;
    private final PrintStream out;
    private final boolean interactive;
    private final Duration interval;
    private final long startNanos = System.nanoTime();
    private final ScheduledExecutorService scheduler =
            Executors.newSingleThreadScheduledExecutor(runnable -> {
                Thread thread = new Thread(runnable, "progress-bar");
                thread.setDaemon(true);
                return thread;
            });

    public ProgressBar(ProgressTracker tracker, PrintStream out, boolean interactive, Duration interval) {
        this.tracker = tracker;
        this.out = out;
        this.interactive = interactive;
        this.interval = interval;
    }

    public ProgressBar start() {
        scheduler.scheduleAtFixedRate(this::print, 0, interval.toMillis(), TimeUnit.MILLISECONDS);
        return this;
    }

    @Override
    public void close() {
        scheduler.shutdownNow();
        try {
            scheduler.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        print();
        if (interactive) {
            out.println();
        }
    }

    private void print() {
        Duration elapsed = Duration.ofNanos(System.nanoTime() - startNanos);
        String line = render(tracker.fraction(), elapsed);
        if (interactive) {
            out.print("\r" + line + CLEAR_TO_END_OF_LINE);
            out.flush();
        } else {
            out.println(line);
        }
    }

    static String render(double fraction, Duration elapsed) {
        int filled = (int) Math.round(fraction * WIDTH);
        String bar = "#".repeat(filled) + "-".repeat(WIDTH - filled);
        String remaining = fraction >= 1.0 ? "finalizing in neo4j" : "ETA " + eta(fraction, elapsed);
        return String.format(
                Locale.ROOT,
                "[%s] %5.1f%%  %-13s elapsed %s  %s",
                bar,
                fraction * 100,
                phase(fraction),
                format(elapsed),
                remaining);
    }

    static String phase(double fraction) {
        if (fraction >= 1.0) {
            return "Finishing";
        }
        return fraction < 0.5 ? "Nodes" : "Relationships";
    }

    static String eta(double fraction, Duration elapsed) {
        if (fraction <= 0 || elapsed.compareTo(MIN_ELAPSED_FOR_ETA) < 0) {
            return "--";
        }
        long remainingMillis = (long) (elapsed.toMillis() * (1 - fraction) / fraction);
        return format(Duration.ofMillis(remainingMillis));
    }

    public static String format(Duration duration) {
        long seconds = duration.toSeconds();
        long hours = seconds / 3600;
        long minutes = seconds % 3600 / 60;
        long rest = seconds % 60;
        if (hours > 0) {
            return String.format(Locale.ROOT, "%dh %02dm", hours, minutes);
        }
        if (minutes > 0) {
            return String.format(Locale.ROOT, "%dm %02ds", minutes, rest);
        }
        return rest + "s";
    }
}
