package dev.akivcraft.loader;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

public final class AkivCraftLoadingLog {
    private static final int MAX_ENTRIES = 80;
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final ArrayDeque<Entry> entries = new ArrayDeque<>();
    private static volatile String stage = "Starting AkivCraft";

    private AkivCraftLoadingLog() {
    }

    public static void stage(String value) {
        if (value == null || value.isBlank()) return;
        stage = value;
        info(value);
    }

    public static void info(String message) {
        add("INFO", message);
    }

    public static void warn(String message) {
        add("WARN", message);
    }

    public static void error(String message) {
        add("ERROR", message);
    }

    public static String stage() {
        return stage;
    }

    public static List<Entry> snapshot(int limit) {
        synchronized (entries) {
            var result = new ArrayList<Entry>(Math.min(limit, entries.size()));
            var skip = Math.max(0, entries.size() - limit);
            var index = 0;
            for (var entry : entries) {
                if (index++ >= skip) result.add(entry);
            }
            return result;
        }
    }

    private static void add(String level, String message) {
        if (message == null || message.isBlank()) return;
        var entry = new Entry(LocalTime.now().format(TIME_FORMAT), level, message);
        synchronized (entries) {
            entries.addLast(entry);
            while (entries.size() > MAX_ENTRIES) entries.removeFirst();
        }
    }

    public record Entry(String time, String level, String message) {
    }
}
