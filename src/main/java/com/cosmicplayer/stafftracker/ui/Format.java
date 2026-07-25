package com.cosmicplayer.stafftracker.ui;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Turns dates and counts into short labels the screens can show. */
public final class Format {
    private static final DateTimeFormatter WEEKDAY_DATE = DateTimeFormatter.ofPattern("EEE, MMM d", Locale.US);
    private static final DateTimeFormatter MONTH_DAY = DateTimeFormatter.ofPattern("MMM d", Locale.US);
    private static final DateTimeFormatter MONTH_YEAR = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.US);
    private static final DateTimeFormatter MONTH_ONLY = DateTimeFormatter.ofPattern("MMMM", Locale.US);

    private Format() {
    }

    /** adds a comma, so 1775 becomes "1,775". MERICA */
    public static String count(int value) {
        return String.format(Locale.US, "%,d", value);
    }

    /** "Today", "Yesterday", or "Thu, Jul 23". Adds the year when it is not the current one. Just future proofing. */
    public static String day(LocalDate date) {
        LocalDate today = LocalDate.now();
        if (date.equals(today)) {
            return "Today";
        }
        if (date.equals(today.minusDays(1))) {
            return "Yesterday";
        }
        return fullDay(date);
    }

    /** "Thu, Jul 23", with the year appended when it is not the current one. */
    public static String fullDay(LocalDate date) {
        String label = WEEKDAY_DATE.format(date);
        if (date.getYear() != LocalDate.now().getYear()) {
            label += ", " + date.getYear();
        }
        return label;
    }

    /** "Jul 19 - 25", or "Jun 29 - Jul 5" when the week crosses a month. */
    public static String weekRange(LocalDate start) {
        LocalDate end = start.plusDays(6);
        String endPart = start.getMonth() == end.getMonth()
                ? String.valueOf(end.getDayOfMonth())
                : MONTH_DAY.format(end);
        String label = MONTH_DAY.format(start) + " - " + endPart;
        if (start.getYear() != LocalDate.now().getYear()) {
            label += ", " + start.getYear();
        }
        return label;
    }

    /** "July 2026". */
    public static String month(LocalDate firstDay) {
        return MONTH_YEAR.format(firstDay);
    }

    /** "July". Used where the year is already obvious. */
    public static String monthOnly(LocalDate firstDay) {
        return MONTH_ONLY.format(firstDay);
    }
}
