package io.github.vikthorvergara.playground.spending;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

@Component
public class LayoutStore {

    static final List<String> HEADER = List.of("fingerprint", "kind", "delimiter", "date_column", "date_format", "posting_date_column",
            "description_column", "amount_column", "debit_column", "credit_column", "marker_column", "debit_marker", "status_column",
            "skip_statuses", "currency_column", "positive_is_spend", "decimal_separator", "thousands_separator");

    private final Path file;

    public LayoutStore(SpendingProperties properties) {
        this.file = properties.learnedLayoutsFile();
    }

    public Map<String, Layout> load() {
        Map<String, Layout> layouts = new LinkedHashMap<>();
        if (!Files.exists(file)) {
            return layouts;
        }
        try {
            for (CsvTable.Row row : CsvTable.tokenise(CsvTable.decode(Files.readAllBytes(file)).text(), ',')) {
                List<String> cells = row.cells();
                if (cells.size() < HEADER.size() || cells.getFirst().equals(HEADER.getFirst())) {
                    continue;
                }
                layouts.put(cells.getFirst(), fromRow(cells));
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read " + file, e);
        }
        return layouts;
    }

    public synchronized void learn(String fingerprint, Layout layout) {
        Map<String, Layout> layouts = new TreeMap<>(load());
        layouts.put(fingerprint, layout);
        List<List<Object>> rows = new ArrayList<>();
        rows.add(new ArrayList<>(HEADER));
        layouts.forEach((key, value) -> rows.add(toRow(key, value)));
        try {
            CsvWriter.writeAtomically(file, new CsvWriter(CsvWriter.Format.COMMA_DOT).render(rows));
        } catch (IOException e) {
            throw new UncheckedIOException("Could not write " + file, e);
        }
    }

    static List<Object> toRow(String fingerprint, Layout layout) {
        return Arrays.asList(fingerprint, layout.kind(), String.valueOf(layout.delimiter()), layout.dateColumn(), layout.dateFormat(),
                layout.postingDateColumn(), layout.descriptionColumn(), layout.amountColumn(), layout.debitColumn(), layout.creditColumn(),
                layout.markerColumn(), layout.debitMarker(), layout.statusColumn(), String.join("|", layout.skipStatuses()),
                layout.currencyColumn(), layout.positiveIsSpend(), String.valueOf(layout.amounts().decimalSeparator()),
                String.valueOf(layout.amounts().thousandsSeparator()));
    }

    static Layout fromRow(List<String> cells) {
        return new Layout("learned-" + cells.get(0), Layout.Kind.valueOf(cells.get(1)), cells.get(2).charAt(0),
                Integer.parseInt(cells.get(3)), blankToNull(cells.get(4)), optionalInt(cells.get(5)), Integer.parseInt(cells.get(6)),
                optionalInt(cells.get(7)), optionalInt(cells.get(8)), optionalInt(cells.get(9)), optionalInt(cells.get(10)),
                blankToNull(cells.get(11)), optionalInt(cells.get(12)),
                cells.get(13).isBlank() ? List.of() : List.of(cells.get(13).split("\\|")), optionalInt(cells.get(14)),
                Boolean.parseBoolean(cells.get(15)), new Layout.AmountFormat(cells.get(16).charAt(0), cells.get(17).charAt(0)));
    }

    private static Integer optionalInt(String cell) {
        return cell.isBlank() ? null : Integer.valueOf(cell.strip());
    }

    private static String blankToNull(String cell) {
        return cell.isBlank() ? null : cell;
    }
}
