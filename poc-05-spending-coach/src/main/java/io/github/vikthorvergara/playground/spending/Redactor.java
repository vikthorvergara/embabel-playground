package io.github.vikthorvergara.playground.spending;

import java.util.List;

public final class Redactor {

    static final int MAX_CELL = 24;

    private Redactor() {
    }

    public static List<List<String>> sample(List<List<String>> rows) {
        return rows.stream().map(row -> row.stream().map(Redactor::cell).toList()).toList();
    }

    static String cell(String raw) {
        String value = raw == null ? "" : raw.strip();
        if (Amounts.isAmount(value) && !Pii.found(value)) {
            return value.replaceAll("\\d", "9");
        }
        if (Dates.isDate(value)) {
            return value;
        }
        String truncated = value.length() > MAX_CELL ? value.substring(0, MAX_CELL) : value;
        return Pii.mask(truncated);
    }
}
