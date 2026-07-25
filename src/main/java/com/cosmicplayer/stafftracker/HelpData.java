package com.cosmicplayer.stafftracker;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.UnaryOperator;

/**
 * Players helped, stored as one total per local calendar day in
 * config/stafftracker/data.json. The counter rolls over at midnight in the
 * client's time zone. Weeks run Sunday to Saturday. Undo removes one count
 * from the current day.
 */
public final class HelpData {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static HelpData instance = new HelpData();

    /** A total for one period. Start is the day itself, the week's Sunday, or the month's first day. */
    public record PeriodTotal(LocalDate start, int count) {}

    private TreeMap<String, Integer> daily = new TreeMap<>();

    public static HelpData get() {
        return instance;
    }

    public static void load() {
        Path file = path();
        if (Files.exists(file)) {
            try {
                instance = GSON.fromJson(Files.readString(file), HelpData.class);
            } catch (Exception e) {
                backupCorruptFile(file);
                instance = new HelpData();
            }
        }
        if (instance == null) {
            instance = new HelpData();
        }
        if (instance.daily == null) {
            instance.daily = new TreeMap<>();
        }
    }

    /** The Sunday that starts the week containing the given day. */
    public static LocalDate weekStart(LocalDate date) {
        return date.with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY));
    }

    public void increment() {
        daily.merge(LocalDate.now().toString(), 1, Integer::sum);
        save();
    }

    /** Removes one count from today. Does nothing when today is already zero. */
    public void undo() {
        String today = LocalDate.now().toString();
        Integer count = daily.get(today);
        if (count == null) {
            return;
        }
        if (count <= 1) {
            daily.remove(today);
        } else {
            daily.put(today, count - 1);
        }
        save();
    }

    /** The total for the period the HUD is set to show. */
    public int countFor(StaffTrackerConfig.HudView view) {
        return switch (view) {
            case DAY -> today();
            case WEEK -> thisWeek();
            case MONTH -> thisMonth();
        };
    }

    public int today() {
        return daily.getOrDefault(LocalDate.now().toString(), 0);
    }

    public int thisWeek() {
        return sumSince(weekStart(LocalDate.now()));
    }

    public int thisMonth() {
        return sumSince(LocalDate.now().withDayOfMonth(1));
    }

    /** Daily totals, newest first. Only days with counts. */
    public List<PeriodTotal> days() {
        List<PeriodTotal> out = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : daily.descendingMap().entrySet()) {
            LocalDate date = parse(entry.getKey());
            if (date != null) {
                out.add(new PeriodTotal(date, entry.getValue()));
            }
        }
        return out;
    }

    /** Weekly totals, newest first. */
    public List<PeriodTotal> weeks() {
        return grouped(HelpData::weekStart);
    }

    /** Monthly totals, newest first. */
    public List<PeriodTotal> months() {
        return grouped(date -> date.withDayOfMonth(1));
    }

    /**
     * One entry per day from start to end inclusive, oldest first, zero days included.
     * Days after today are left out. Used by the week and month detail screens.
     */
    public List<PeriodTotal> daysBetween(LocalDate start, LocalDate end) {
        LocalDate today = LocalDate.now();
        if (end.isAfter(today)) {
            end = today;
        }
        List<PeriodTotal> out = new ArrayList<>();
        for (LocalDate date = start; !date.isAfter(end); date = date.plusDays(1)) {
            out.add(new PeriodTotal(date, daily.getOrDefault(date.toString(), 0)));
        }
        return out;
    }

    private List<PeriodTotal> grouped(UnaryOperator<LocalDate> periodStart) {
        TreeMap<LocalDate, Integer> totals = new TreeMap<>();
        for (Map.Entry<String, Integer> entry : daily.entrySet()) {
            LocalDate date = parse(entry.getKey());
            if (date != null) {
                totals.merge(periodStart.apply(date), entry.getValue(), Integer::sum);
            }
        }
        List<PeriodTotal> out = new ArrayList<>();
        for (Map.Entry<LocalDate, Integer> entry : totals.descendingMap().entrySet()) {
            out.add(new PeriodTotal(entry.getKey(), entry.getValue()));
        }
        return out;
    }

    private int sumSince(LocalDate start) {
        int sum = 0;
        for (int value : daily.tailMap(start.toString()).values()) {
            sum += value;
        }
        return sum;
    }

    private static LocalDate parse(String key) {
        try {
            return LocalDate.parse(key);
        } catch (Exception e) {
            return null;
        }
    }

    private void save() {
        try {
            Path file = path();
            Files.createDirectories(file.getParent());
            Path temp = file.resolveSibling(file.getFileName() + ".tmp");
            Files.writeString(temp, GSON.toJson(this));
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) {
            // Keep playing even if the disk write fails. Data stays in memory.
        }
    }

    private static void backupCorruptFile(Path file) {
        try {
            Files.move(file, file.resolveSibling("data.json.bak"), StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception ignored) {
        }
    }

    private static Path path() {
        return FabricLoader.getInstance().getConfigDir().resolve(StaffTrackerClient.MOD_ID).resolve("data.json");
    }
}
