package io.github.vikthorvergara.playground.spending;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.util.List;

public record ColumnMapping(
        @JsonPropertyDescription("Zero-based index of the column holding the transaction date")
        int dateColumn,
        @JsonPropertyDescription("Date pattern of that column, such as dd/MM/yyyy or yyyy-MM-dd")
        String dateFormat,
        @JsonPropertyDescription("Zero-based index of a second date column holding the posting date, or null")
        Integer postingDateColumn,
        @JsonPropertyDescription("Zero-based index of the description or merchant column")
        int descriptionColumn,
        @JsonPropertyDescription("Zero-based index of a single signed amount column, or null when debits and credits are split")
        Integer amountColumn,
        @JsonPropertyDescription("Zero-based index of the debit column when debits and credits are split, or null")
        Integer debitColumn,
        @JsonPropertyDescription("Zero-based index of the credit column when debits and credits are split, or null")
        Integer creditColumn,
        @JsonPropertyDescription("Zero-based index of a debit/credit marker column such as D/C, or null")
        Integer markerColumn,
        @JsonPropertyDescription("Value in the marker column that means a debit, such as D or DEB, or null")
        String debitMarker,
        @JsonPropertyDescription("Zero-based index of a status column, or null")
        Integer statusColumn,
        @JsonPropertyDescription("Status values that mean the row is pending and must be skipped")
        List<String> skipStatuses,
        @JsonPropertyDescription("Zero-based index of a currency column, or null")
        Integer currencyColumn,
        @JsonPropertyDescription("BANK for a current or savings account, CARD for a credit card")
        Layout.Kind kind,
        @JsonPropertyDescription("True when a positive amount in the amount column is money spent, as on most card exports")
        boolean positiveIsSpend) {

    public Layout toLayout(String name, char delimiter, Layout.AmountFormat amountFormat) {
        return new Layout(name, kind == null ? Layout.Kind.BANK : kind, delimiter, dateColumn, dateFormat, postingDateColumn,
                descriptionColumn, amountColumn, debitColumn, creditColumn, markerColumn, debitMarker, statusColumn,
                skipStatuses, currencyColumn, positiveIsSpend, amountFormat);
    }

    @Override
    public String toString() {
        return "ColumnMapping[date=" + dateColumn + " description=" + descriptionColumn + " amount=" + amountColumn
                + " debit=" + debitColumn + " credit=" + creditColumn + "]";
    }
}
