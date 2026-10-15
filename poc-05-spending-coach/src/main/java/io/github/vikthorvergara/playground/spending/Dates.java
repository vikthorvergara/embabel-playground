package io.github.vikthorvergara.playground.spending;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Dates {

    public enum Order { DAY_FIRST, MONTH_FIRST }

    public record Decision(Order order, boolean assumed) {
    }

    private static final Pattern TIME = Pattern.compile("[T ]\\d{1,2}:\\d{2}(?::\\d{2})?(?:\\.\\d+)?Z?$");
    private static final Pattern ISO = Pattern.compile("(\\d{4})[-/](\\d{1,2})[-/](\\d{1,2})");
    private static final Pattern NUMERIC = Pattern.compile("(\\d{1,2})[./-](\\d{1,2})[./-](\\d{4})");
    private static final Pattern NAMED = Pattern.compile("(\\d{1,2})[ /.-]([\\p{L}]{3,})\\.?[ /.-](\\d{4})");
    private static final Map<String, Integer> MONTHS = Map.ofEntries(
            Map.entry("jan", 1), Map.entry("feb", 2), Map.entry("fev", 2), Map.entry("mar", 3), Map.entry("apr", 4),
            Map.entry("abr", 4), Map.entry("may", 5), Map.entry("mai", 5), Map.entry("jun", 6), Map.entry("jul", 7),
            Map.entry("aug", 8), Map.entry("ago", 8), Map.entry("sep", 9), Map.entry("set", 9), Map.entry("oct", 10),
            Map.entry("out", 10), Map.entry("nov", 11), Map.entry("dec", 12), Map.entry("dez", 12));

    private Dates() {
    }

    public static Decision decide(List<String> cells, Order tieBreak) {
        boolean ambiguous = false;
        for (String cell : cells) {
            Matcher numeric = NUMERIC.matcher(stripTime(cell));
            if (!numeric.matches()) {
                continue;
            }
            int first = Integer.parseInt(numeric.group(1));
            int second = Integer.parseInt(numeric.group(2));
            if (first > 12) {
                return new Decision(Order.DAY_FIRST, false);
            }
            if (second > 12) {
                return new Decision(Order.MONTH_FIRST, false);
            }
            ambiguous = true;
        }
        return new Decision(tieBreak, ambiguous);
    }

    public static LocalDate parse(String cell, Order order) {
        if (cell == null) {
            return null;
        }
        String s = stripTime(cell);
        try {
            Matcher iso = ISO.matcher(s);
            if (iso.matches()) {
                return LocalDate.of(Integer.parseInt(iso.group(1)), Integer.parseInt(iso.group(2)), Integer.parseInt(iso.group(3)));
            }
            Matcher numeric = NUMERIC.matcher(s);
            if (numeric.matches()) {
                int first = Integer.parseInt(numeric.group(1));
                int second = Integer.parseInt(numeric.group(2));
                int year = Integer.parseInt(numeric.group(3));
                return order == Order.DAY_FIRST ? LocalDate.of(year, second, first) : LocalDate.of(year, first, second);
            }
            Matcher named = NAMED.matcher(s);
            if (named.matches()) {
                Integer month = MONTHS.get(MerchantNormaliser.plain(named.group(2)).toLowerCase(Locale.ROOT).substring(0, 3));
                if (month == null) {
                    return null;
                }
                return LocalDate.of(Integer.parseInt(named.group(3)), month, Integer.parseInt(named.group(1)));
            }
        } catch (DateTimeException e) {
            return null;
        }
        return null;
    }

    public static boolean isDate(String cell) {
        return parse(cell, Order.DAY_FIRST) != null || parse(cell, Order.MONTH_FIRST) != null;
    }

    private static String stripTime(String cell) {
        return TIME.matcher(cell.strip()).replaceFirst("").strip();
    }
}
