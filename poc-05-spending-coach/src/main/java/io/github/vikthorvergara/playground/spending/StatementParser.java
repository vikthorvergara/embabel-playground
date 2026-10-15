package io.github.vikthorvergara.playground.spending;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class StatementParser {

    public record Parsed(List<Transaction> rows, int pending, boolean dateOrderAssumed, List<String> warnings) {
    }

    private StatementParser() {
    }

    public static Parsed parse(int fileIndex, RawStatements.RawStatement file, Layout layout, Dates.Order tieBreak,
                               SpendingProperties.MonthBy monthBy) {
        CsvTable table = CsvTable.parse(file.text());
        List<Transaction> rows = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        if (table.header().size() < layout.width()) {
            warnings.add(file.name() + ": layout needs " + layout.width() + " columns, file has " + table.header().size());
            return new Parsed(rows, 0, false, warnings);
        }
        List<String> dateCells = new ArrayList<>();
        for (CsvTable.Row row : table.rows()) {
            dateCells.add(row.cells().get(layout.dateColumn()));
            if (layout.postingDateColumn() != null) {
                dateCells.add(row.cells().get(layout.postingDateColumn()));
            }
        }
        Dates.Decision decision = Dates.decide(dateCells, tieBreak);
        String account = layout.kind() + "-" + file.fingerprint();
        int pending = 0;
        for (CsvTable.Row row : table.rows()) {
            List<String> cells = row.cells();
            if (isPending(cells, layout)) {
                pending++;
                continue;
            }
            boolean usePosting = monthBy == SpendingProperties.MonthBy.POSTING_DATE && layout.postingDateColumn() != null;
            LocalDate date = Dates.parse(cells.get(usePosting ? layout.postingDateColumn() : layout.dateColumn()), decision.order());
            if (date == null) {
                warnings.add(file.name() + " line " + row.line() + ": unreadable date");
                continue;
            }
            Signed signed = signed(cells, layout);
            if (signed == null) {
                warnings.add(file.name() + " line " + row.line() + ": unreadable amount");
                continue;
            }
            String raw = cells.get(layout.descriptionColumn()).strip();
            String currency = layout.currencyColumn() == null ? null : blankToNull(cells.get(layout.currencyColumn()));
            List<String> flags = decision.assumed() ? List.of(Transaction.DATE_ORDER_ASSUMED) : List.of();
            rows.add(new Transaction(fileIndex + "-" + row.line(), date, account, file.name(), layout.kind(), raw,
                    MerchantNormaliser.normalise(raw), signed.amount(), currency,
                    signed.spend() ? Transaction.Flow.SPEND : Transaction.Flow.INCOME, flags));
        }
        return new Parsed(rows, pending, decision.assumed(), warnings);
    }

    public static boolean parses(List<List<String>> rows, Layout layout) {
        int checked = 0;
        for (List<String> cells : rows) {
            if (cells.size() < layout.width()) {
                return false;
            }
            if (isPending(cells, layout)) {
                continue;
            }
            if (!Dates.isDate(cells.get(layout.dateColumn())) || signed(cells, layout) == null) {
                return false;
            }
            checked++;
        }
        return checked > 0;
    }

    private record Signed(BigDecimal amount, boolean spend) {
    }

    private static Signed signed(List<String> cells, Layout layout) {
        if (layout.amountColumn() != null) {
            BigDecimal value = Amounts.parse(cells.get(layout.amountColumn()), layout.amounts());
            if (value == null || value.signum() == 0) {
                return null;
            }
            if (layout.markerColumn() != null) {
                return new Signed(value.abs(), Amounts.isDebitMarker(cells.get(layout.markerColumn()), layout.debitMarker()));
            }
            boolean spend = layout.positiveIsSpend() ? value.signum() > 0 : value.signum() < 0;
            return new Signed(value.abs(), spend);
        }
        if (layout.debitColumn() != null && layout.creditColumn() != null) {
            BigDecimal debit = Amounts.parse(cells.get(layout.debitColumn()), layout.amounts());
            BigDecimal credit = Amounts.parse(cells.get(layout.creditColumn()), layout.amounts());
            if (debit != null && debit.signum() != 0) {
                return new Signed(debit.abs(), true);
            }
            if (credit != null && credit.signum() != 0) {
                return new Signed(credit.abs(), false);
            }
        }
        return null;
    }

    private static boolean isPending(List<String> cells, Layout layout) {
        if (layout.postingDateColumn() != null && cells.get(layout.postingDateColumn()).isBlank()) {
            return true;
        }
        if (layout.statusColumn() == null) {
            return false;
        }
        String status = cells.get(layout.statusColumn()).strip().toUpperCase(Locale.ROOT);
        return layout.skipStatuses().stream().anyMatch(skip -> skip.strip().toUpperCase(Locale.ROOT).equals(status));
    }

    private static String blankToNull(String cell) {
        return cell == null || cell.isBlank() ? null : cell.strip().toUpperCase(Locale.ROOT);
    }
}
