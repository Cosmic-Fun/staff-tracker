package com.cosmicplayer.stafftracker;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Interaction logs, one JSON file per day in config/stafftracker/logs.
 * Each count press stores who was helped, both planets, and the private
 * message conversation from the last thirty minutes. One file per day
 * keeps deletion simple: dropping a day drops its file.
 */
public final class InteractionLog {
    /** One private message inside an interaction. Planet is the sender's at send time. */
    public record LoggedMessage(long time, boolean outgoing, String planet, String text) {}

    /** One count press. Player and planets are null when no reply was found. */
    public record Interaction(long time, String player, String staffPlanet, String playerPlanet,
                              List<LoggedMessage> messages) {}

    /** An interaction paired with the day it happened on. Used by search results. */
    public record DatedInteraction(LocalDate day, Interaction interaction) {}

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type FILE_TYPE = new TypeToken<List<Interaction>>() {}.getType();
    private static final Map<LocalDate, List<Interaction>> cache = new HashMap<>();

    private InteractionLog() {
    }

    /** The interactions logged on the given day, oldest first. */
    public static List<Interaction> forDay(LocalDate day) {
        return cache.computeIfAbsent(day, InteractionLog::read);
    }

    public static void append(Interaction interaction) {
        LocalDate today = LocalDate.now();
        List<Interaction> interactions = new ArrayList<>(forDay(today));
        interactions.add(interaction);
        cache.put(today, interactions);
        write(today, interactions);
    }

    /** Drops the newest interaction of the day. Called when a count is undone. */
    public static void removeLast(LocalDate day) {
        List<Interaction> interactions = forDay(day);
        if (interactions.isEmpty()) {
            return;
        }
        List<Interaction> trimmed = new ArrayList<>(interactions);
        trimmed.removeLast();
        cache.put(day, trimmed);
        write(day, trimmed);
    }

    /** Removes one specific interaction from a day. */
    public static void remove(LocalDate day, Interaction interaction) {
        List<Interaction> interactions = new ArrayList<>(forDay(day));
        if (!interactions.remove(interaction)) {
            return;
        }
        cache.put(day, interactions);
        write(day, interactions);
    }

    public static void deleteDay(LocalDate day) {
        cache.remove(day);
        try {
            Files.deleteIfExists(file(day));
        } catch (IOException ignored) {
        }
    }

    public static void deleteRange(LocalDate start, LocalDate end) {
        for (LocalDate day = start; !day.isAfter(end); day = day.plusDays(1)) {
            deleteDay(day);
        }
    }

    /** Every interaction whose player name contains the query, newest first. */
    public static List<DatedInteraction> search(String query) {
        String needle = query.toLowerCase(Locale.ROOT);
        List<DatedInteraction> results = new ArrayList<>();
        for (LocalDate day : loggedDays()) {
            for (Interaction interaction : forDay(day)) {
                if (interaction.player() != null
                        && interaction.player().toLowerCase(Locale.ROOT).contains(needle)) {
                    results.add(new DatedInteraction(day, interaction));
                }
            }
        }
        results.sort((a, b) -> Long.compare(b.interaction().time(), a.interaction().time()));
        return results;
    }

    /** Every day that has a log file, newest first. */
    private static List<LocalDate> loggedDays() {
        List<LocalDate> days = new ArrayList<>();
        if (!Files.isDirectory(directory())) {
            return days;
        }
        try (DirectoryStream<Path> files = Files.newDirectoryStream(directory(), "*.json")) {
            for (Path file : files) {
                LocalDate day = parseFileName(file);
                if (day != null) {
                    days.add(day);
                }
            }
        } catch (IOException ignored) {
        }
        days.sort(Comparator.reverseOrder());
        return days;
    }

    private static List<Interaction> read(LocalDate day) {
        Path file = file(day);
        if (!Files.exists(file)) {
            return List.of();
        }
        try {
            List<Interaction> interactions = GSON.fromJson(Files.readString(file), FILE_TYPE);
            return interactions != null ? interactions : List.of();
        } catch (Exception e) {
            return List.of();
        }
    }

    private static void write(LocalDate day, List<Interaction> interactions) {
        try {
            Path file = file(day);
            if (interactions.isEmpty()) {
                Files.deleteIfExists(file);
                return;
            }
            JsonFiles.write(file, GSON.toJson(interactions, FILE_TYPE));
        } catch (IOException ignored) {
            // Keep playing even if the disk write fails. Data stays in memory.
        }
    }

    private static LocalDate parseFileName(Path file) {
        String name = file.getFileName().toString();
        try {
            return LocalDate.parse(name.substring(0, name.length() - ".json".length()));
        } catch (Exception e) {
            return null;
        }
    }

    private static Path file(LocalDate day) {
        return directory().resolve(day + ".json");
    }

    private static Path directory() {
        return FabricLoader.getInstance().getConfigDir().resolve(StaffTrackerClient.MOD_ID).resolve("logs");
    }
}
