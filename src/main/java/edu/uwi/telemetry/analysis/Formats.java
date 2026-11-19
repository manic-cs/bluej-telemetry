package edu.uwi.telemetry.analysis;

import java.util.Locale;
/**
 * Provides formatting methods for durations, percentages and elapsed times
 * 
 * @author Sawdah Kadir
 */

public final class Formats {
    private Formats() {}

    /**
     * Formats a duration into hours, minutes and seconds
     * @param totalSeconds duration to format
     * @return formatted 
     */

    public static String duration(long totalSeconds) {
        long seconds = Math.max(0, totalSeconds);
        long h = seconds / 3600;
        long m = (seconds % 3600) / 60;
        long s = seconds % 60;

        if (h > 0) {
            return String.format(Locale.ROOT, "%dh %02dm %02ds", h, m, s);
        }

        if (m > 0) {
            return String.format(Locale.ROOT, "%dm %02ds", m, s);
        }

        return s + "s";
    }

    /**
     * Formats a decimal ration as a percentage with one decimal place
     * @param ratio value to convert
     * @return formatted precentage string
     */

    public static String percent(double ratio) {
        return String.format(Locale.ROOT, "%.1f%%", ratio * 100.0);
    }

    /**
     * Formats elapsed seconds as a zero-padded HH:MM:SS string
     * @param totalSeconds time in seconds
     * @return formatted time string
     */

    public static String clock(long totalSeconds) {
        long seconds = Math.max(0, totalSeconds);
        return String.format(Locale.ROOT, "%02d:%02d:%02d", seconds / 3600, (seconds % 3600) / 60, seconds % 60);
    }
}