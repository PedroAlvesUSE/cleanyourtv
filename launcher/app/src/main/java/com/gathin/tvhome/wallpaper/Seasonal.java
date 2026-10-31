package com.gathin.tvhome.wallpaper;

import java.time.LocalDate;

/**
 * Search term of the holiday around a date, or null. Only Wallhaven takes a search term, so only that source
 * can follow the season; the other sources keep their normal picture.
 */
public final class Seasonal {
    private Seasonal() { }

    /** Natal (15-25 Dec), Ano Novo (26 Dec-2 Jan), Carnaval, Festa Junina (10-30 Jun), Halloween (24 Oct-1 Nov). */
    public static String query(LocalDate date) {
        int m = date.getMonthValue();
        int d = date.getDayOfMonth();
        if (m == 12 && d >= 15 && d <= 25) return "christmas";
        if ((m == 12 && d >= 26) || (m == 1 && d <= 2)) return "fireworks";
        if (m == 6 && d >= 10) return "bonfire";
        if ((m == 10 && d >= 24) || (m == 11 && d == 1)) return "halloween";
        LocalDate easter = easter(date.getYear());
        // Friday before Carnival to Ash Wednesday.
        if (!date.isBefore(easter.minusDays(52)) && !date.isAfter(easter.minusDays(46))) return "carnival";
        return null;
    }

    /** Gregorian Easter Sunday (anonymous algorithm). */
    static LocalDate easter(int year) {
        int a = year % 19;
        int b = year / 100;
        int c = year % 100;
        int d = b / 4;
        int e = b % 4;
        int f = (b + 8) / 25;
        int g = (b - f + 1) / 3;
        int h = (19 * a + b - d - g + 15) % 30;
        int i = c / 4;
        int k = c % 4;
        int l = (32 + 2 * e + 2 * i - h - k) % 7;
        int m = (a + 11 * h + 22 * l) / 451;
        int month = (h + l - 7 * m + 114) / 31;
        int day = (h + l - 7 * m + 114) % 31 + 1;
        return LocalDate.of(year, month, day);
    }
}
