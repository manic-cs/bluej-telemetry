package edu.uwi.telemetry.analysis;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Calculates the active and idle durations of a user session.
 * 
 * @author Sawdah Kadir
 */

public final class ActivityTimeCalculator {
    /**
     * Prevents instantiation of this utility class
     */
    private ActivityTimeCalculator() {}

    /**
     * Calculates active and idle time durations within a specific session
     */
    public static ActivityTime calculate(Instant start, Instant end, List<Instant> activity, Duration idleThreshold) {
        if (end.isBefore(start)) {
            return new ActivityTime(Duration.ZERO, Duration.ZERO);
        }

        List<Instant> points = new ArrayList<>();
        points.add(start);
        activity.stream().filter(t -> !t.isBefore(start) && !t.isAfter(end)).sorted().forEach(points::add);
        points.add(end);

        long thresholdMillis = idleThreshold.toMillis();
        long activeMillis = 0;
        long idleMillis = 0;

        for (int i = 1; i < points.size(); i++) {
            long gap = Math.max(0, Duration.between(points.get(i - 1), points.get(i)).toMillis());
            activeMillis += Math.min(gap, thresholdMillis);
            idleMillis += Math.max(0, gap - thresholdMillis);
        }

        return new ActivityTime(Duration.ofMillis(activeMillis), Duration.ofMillis(idleMillis));
    }
}