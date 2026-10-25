package io.github.vikthorvergara.playground.spending;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

public final class Amounts {

    private static final Pattern CURRENCY = Pattern.compile("(?i)(R\\$|US\\$|€|\\$|£|(?<![A-Z])[A-Z]{3}(?![A-Z]))");
    private static final Pattern BODY = Pattern.compile("\\d+([.,]\\d+)*");

    private record Body(String digits, boolean negative) {
    }

    private Amounts() {
    }

    public static BigDecimal money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_EVEN);
    }

    public static BigDecimal parse(String raw, Layout.AmountFormat format) {
        Body body = body(raw);
        if (body == null) {
            return null;
        }
        String decimal = String.valueOf(format.decimalSeparator());
        String digits = body.digits().replace(String.valueOf(format.thousandsSeparator()), "");
        if (digits.indexOf(decimal) != digits.lastIndexOf(decimal)) {
            return null;
        }
        try {
            BigDecimal value = money(new BigDecimal(digits.replace(decimal, ".")));
            return body.negative() ? value.negate() : value;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static boolean isAmount(String raw) {
        return body(raw) != null;
    }

    private static Body body(String raw) {
        if (raw == null) {
            return null;
        }
        String s = raw.replace(' ', ' ').strip();
        if (s.isEmpty()) {
            return null;
        }
        boolean negative = false;
        if (s.startsWith("(") && s.endsWith(")")) {
            negative = true;
            s = s.substring(1, s.length() - 1);
        }
        s = CURRENCY.matcher(s).replaceAll("").replace(" ", "").strip();
        if (s.endsWith("-")) {
            negative = true;
            s = s.substring(0, s.length() - 1);
        }
        if (s.startsWith("-")) {
            negative = true;
            s = s.substring(1);
        } else if (s.startsWith("+")) {
            s = s.substring(1);
        }
        return BODY.matcher(s).matches() ? new Body(s, negative) : null;
    }

    public static Layout.AmountFormat formatOf(List<String> column) {
        int commaVotes = 0;
        int dotVotes = 0;
        for (String cell : column) {
            Body body = body(cell);
            if (body == null) {
                continue;
            }
            String digits = body.digits();
            int lastComma = digits.lastIndexOf(',');
            int lastDot = digits.lastIndexOf('.');
            if (lastComma < 0 && lastDot < 0) {
                continue;
            }
            char last = lastComma > lastDot ? ',' : '.';
            int position = Math.max(lastComma, lastDot);
            int trailing = digits.length() - position - 1;
            boolean repeated = digits.indexOf(last) != position;
            boolean decimalIsLast = !repeated && trailing != 3;
            char decimal = decimalIsLast ? last : (last == ',' ? '.' : ',');
            if (decimal == ',') {
                commaVotes++;
            } else {
                dotVotes++;
            }
        }
        return commaVotes > dotVotes ? Layout.AmountFormat.DECIMAL_COMMA : Layout.AmountFormat.DECIMAL_DOT;
    }

    public static Layout.AmountFormat formatOf(CsvTable table, ColumnMapping mapping) {
        List<String> cells = new ArrayList<>();
        for (Integer column : new Integer[]{mapping.amountColumn(), mapping.debitColumn(), mapping.creditColumn()}) {
            if (column != null && column < table.header().size()) {
                cells.addAll(table.column(column, CsvTable.SAMPLE_ROWS));
            }
        }
        return formatOf(cells);
    }

    public static boolean isDebitMarker(String cell, String marker) {
        return marker != null && !marker.isBlank()
                && cell.strip().toUpperCase(Locale.ROOT).startsWith(marker.strip().toUpperCase(Locale.ROOT));
    }

    public static BigDecimal median(List<BigDecimal> values) {
        if (values.isEmpty()) {
            return BigDecimal.ZERO.setScale(2);
        }
        List<BigDecimal> sorted = values.stream().sorted().toList();
        int middle = sorted.size() / 2;
        if (sorted.size() % 2 == 1) {
            return money(sorted.get(middle));
        }
        return money(sorted.get(middle - 1).add(sorted.get(middle)).divide(BigDecimal.valueOf(2), 4, RoundingMode.HALF_EVEN));
    }
}
