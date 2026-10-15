package io.github.vikthorvergara.playground.spending;

import java.util.List;

public record Layout(
        String name,
        Kind kind,
        char delimiter,
        int dateColumn,
        String dateFormat,
        Integer postingDateColumn,
        int descriptionColumn,
        Integer amountColumn,
        Integer debitColumn,
        Integer creditColumn,
        Integer markerColumn,
        String debitMarker,
        Integer statusColumn,
        List<String> skipStatuses,
        Integer currencyColumn,
        boolean positiveIsSpend,
        AmountFormat amounts) {

    public enum Kind { BANK, CARD }

    public record AmountFormat(char decimalSeparator, char thousandsSeparator) {

        public static final AmountFormat DECIMAL_DOT = new AmountFormat('.', ',');
        public static final AmountFormat DECIMAL_COMMA = new AmountFormat(',', '.');
    }

    public Layout {
        skipStatuses = skipStatuses == null ? List.of() : List.copyOf(skipStatuses);
        amounts = amounts == null ? AmountFormat.DECIMAL_DOT : amounts;
    }

    public Layout withName(String newName) {
        return new Layout(newName, kind, delimiter, dateColumn, dateFormat, postingDateColumn, descriptionColumn,
                amountColumn, debitColumn, creditColumn, markerColumn, debitMarker, statusColumn, skipStatuses,
                currencyColumn, positiveIsSpend, amounts);
    }

    public Layout withAmounts(AmountFormat format) {
        return new Layout(name, kind, delimiter, dateColumn, dateFormat, postingDateColumn, descriptionColumn,
                amountColumn, debitColumn, creditColumn, markerColumn, debitMarker, statusColumn, skipStatuses,
                currencyColumn, positiveIsSpend, format);
    }

    public int width() {
        int max = Math.max(dateColumn, descriptionColumn);
        for (Integer column : new Integer[]{postingDateColumn, amountColumn, debitColumn, creditColumn, markerColumn, statusColumn, currencyColumn}) {
            if (column != null) {
                max = Math.max(max, column);
            }
        }
        return max + 1;
    }

    @Override
    public String toString() {
        return "Layout[" + name + " " + kind + "]";
    }
}
