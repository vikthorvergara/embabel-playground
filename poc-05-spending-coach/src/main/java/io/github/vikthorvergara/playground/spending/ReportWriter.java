package io.github.vikthorvergara.playground.spending;

import com.embabel.agent.domain.io.FileArtifact;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class ReportWriter {

    public static final String SUMMARY = "summary.csv";
    public static final String TRANSACTIONS = "transactions.csv";
    public static final String RECURRING = "recurring.csv";
    public static final String ANOMALIES = "anomalies.csv";
    public static final String CASHFLOW = "cashflow.csv";
    public static final String FOREIGN = "foreign.csv";
    public static final String COACH = "coach.md";
    public static final List<String> FILES = List.of(SUMMARY, TRANSACTIONS, RECURRING, ANOMALIES, CASHFLOW, FOREIGN, COACH);

    static final List<String> TRANSACTION_COLUMNS = List.of("id", "date", "account", "source_file", "kind", "merchant", "raw_description",
            "amount", "currency", "flow", "category", "origin", "confidence", "flags");

    public record Written(List<FileArtifact> artifacts, List<String> files, String summaryCsv) {
    }

    private final Path reportsDir;
    private final CsvWriter csv;

    public ReportWriter(Path reportsDir, CsvWriter.Format format) {
        this.reportsDir = reportsDir;
        this.csv = new CsvWriter(format);
    }

    public Written write(String runId, SpendingAnalysis analysis, CoachingNotes notes, CategorisedLedger ledger, Ledger raw,
                         RunCost cost, String route) throws IOException {
        Path dir = reportsDir.resolve(runId);
        Files.createDirectories(dir);
        List<FileArtifact> artifacts = new ArrayList<>();
        List<String> files = new ArrayList<>();
        String summary = summaryCsv(analysis);
        write(dir, SUMMARY, CsvWriter.BOM + summary, artifacts, files);
        write(dir, TRANSACTIONS, CsvWriter.BOM + csv.render(transactionRows(ledger.rows(), analysis)), artifacts, files);
        write(dir, RECURRING, CsvWriter.BOM + csv.render(recurringRows(analysis)), artifacts, files);
        write(dir, ANOMALIES, CsvWriter.BOM + csv.render(anomalyRows(analysis)), artifacts, files);
        write(dir, CASHFLOW, CsvWriter.BOM + csv.render(cashFlowRows(analysis)), artifacts, files);
        if (!raw.foreign().isEmpty()) {
            List<RuledLedger.Categorised> foreign = raw.foreign().stream()
                    .map(tx -> new RuledLedger.Categorised(tx, Category.UNCATEGORISED, Origin.NONE, null)).toList();
            write(dir, FOREIGN, CsvWriter.BOM + csv.render(transactionRows(foreign, analysis)), artifacts, files);
        }
        write(dir, COACH, coachMarkdown(analysis, notes, cost, route), artifacts, files);
        return new Written(artifacts, files, summary);
    }

    private void write(Path dir, String name, String content, List<FileArtifact> artifacts, List<String> files) throws IOException {
        Path path = dir.resolve(name);
        Files.writeString(path, content, StandardCharsets.UTF_8);
        artifacts.add(new FileArtifact(path.toFile(), Instant.now()));
        files.add(name);
    }

    String summaryCsv(SpendingAnalysis analysis) {
        List<List<Object>> rows = new ArrayList<>();
        List<Object> header = new ArrayList<>(List.of("category"));
        analysis.months().forEach(month -> header.add(month.toString()));
        header.addAll(List.of("total", "monthly_average", "suggested_budget", "last_month_vs_average_pct"));
        rows.add(header);
        Map<Category, BigDecimal> suggestions = new HashMap<>();
        analysis.budgets().forEach(budget -> suggestions.put(budget.category(), budget.suggested()));
        boolean lastComplete = !analysis.cashFlow().isEmpty() && analysis.cashFlow().getLast().complete();
        List<Category> ordered = new ArrayList<>(analysis.categoriesByTotal().stream().filter(SpendingAnalysis::isSpendCategory).toList());
        for (Category tail : List.of(Category.INCOME, Category.TRANSFER)) {
            if (analysis.byMonth().stream().anyMatch(cell -> cell.category() == tail)) {
                ordered.add(tail);
            }
        }
        for (Category category : ordered) {
            List<Object> row = new ArrayList<>(List.of(category.name()));
            for (YearMonth month : analysis.months()) {
                row.add(analysis.byMonth().stream()
                        .filter(cell -> cell.month().equals(month) && cell.category() == category)
                        .map(SpendingAnalysis.CategoryMonth::total).findFirst().orElse(BigDecimal.ZERO.setScale(2)));
            }
            BigDecimal average = analysis.monthlyAverage(category);
            row.add(analysis.categoryTotal(category));
            row.add(average);
            row.add(SpendingAnalysis.isSpendCategory(category) ? suggestions.get(category) : null);
            row.add(lastComplete ? versusAverage(row.get(analysis.months().size()), average) : null);
            rows.add(row);
        }
        List<Object> total = new ArrayList<>(List.of("TOTAL_SPEND"));
        BigDecimal grand = BigDecimal.ZERO.setScale(2);
        for (YearMonth month : analysis.months()) {
            BigDecimal monthTotal = analysis.totalSpend(month);
            total.add(monthTotal);
            grand = grand.add(monthTotal);
        }
        BigDecimal average = analysis.months().isEmpty() ? BigDecimal.ZERO.setScale(2)
                : grand.divide(BigDecimal.valueOf(analysis.months().size()), 2, RoundingMode.HALF_EVEN);
        total.add(grand);
        total.add(average);
        total.add(null);
        total.add(lastComplete ? versusAverage(total.get(analysis.months().size()), average) : null);
        rows.add(total);
        return csv.render(rows);
    }

    private static BigDecimal versusAverage(Object lastMonth, BigDecimal average) {
        if (average.signum() == 0 || !(lastMonth instanceof BigDecimal last)) {
            return null;
        }
        return last.subtract(average).multiply(BigDecimal.valueOf(100)).divide(average, 1, RoundingMode.HALF_EVEN);
    }

    static List<List<Object>> transactionRows(List<RuledLedger.Categorised> rows, SpendingAnalysis analysis) {
        Map<String, List<String>> anomalyFlags = new HashMap<>();
        for (SpendingAnalysis.Anomaly anomaly : analysis.anomalies()) {
            String flag = switch (anomaly.type()) {
                case DUPLICATE_CHARGE -> "DUPLICATE";
                case LARGE_FOR_MERCHANT -> "LARGE_FOR_MERCHANT";
                case FEE_OR_INTEREST -> "FEE";
            };
            anomalyFlags.computeIfAbsent(anomaly.transactionId(), id -> new ArrayList<>()).add(flag);
        }
        List<List<Object>> out = new ArrayList<>();
        out.add(new ArrayList<>(TRANSACTION_COLUMNS));
        for (RuledLedger.Categorised row : rows) {
            Transaction tx = row.tx();
            List<String> flags = new ArrayList<>(tx.flags());
            flags.addAll(anomalyFlags.getOrDefault(tx.id(), List.of()));
            out.add(Arrays.asList(tx.id(), tx.date(), tx.account(), tx.sourceFile(), tx.kind(), tx.merchant(), tx.rawDescription(),
                    tx.amount(), tx.currency(), tx.flow(), row.category(), row.origin(), row.confidence(), String.join("|", flags)));
        }
        return out;
    }

    private static List<List<Object>> recurringRows(SpendingAnalysis analysis) {
        List<List<Object>> out = new ArrayList<>();
        out.add(Arrays.asList("merchant", "category", "typical_amount", "cadence_days", "occurrences", "last_seen", "annualised"));
        analysis.recurring().forEach(charge -> out.add(Arrays.asList(charge.merchant(), charge.category(), charge.typicalAmount(),
                charge.cadenceDays(), charge.occurrences(), charge.lastSeen(), charge.annualised())));
        return out;
    }

    private static List<List<Object>> anomalyRows(SpendingAnalysis analysis) {
        List<List<Object>> out = new ArrayList<>();
        out.add(Arrays.asList("date", "merchant", "amount", "type", "detail"));
        analysis.anomalies().forEach(anomaly -> out.add(Arrays.asList(anomaly.date(), anomaly.merchant(), anomaly.amount(),
                anomaly.type(), anomaly.detail())));
        return out;
    }

    private static List<List<Object>> cashFlowRows(SpendingAnalysis analysis) {
        List<List<Object>> out = new ArrayList<>();
        out.add(Arrays.asList("month", "income", "spend", "transfers_out", "card_credits", "net", "savings_rate_pct", "complete_month"));
        analysis.cashFlow().forEach(flow -> out.add(Arrays.asList(flow.month(), flow.income(), flow.spend(), flow.transfersOut(),
                flow.cardCredits(), flow.net(),
                flow.savingsRate() == null ? null : flow.savingsRate().multiply(BigDecimal.valueOf(100)).setScale(1, RoundingMode.HALF_EVEN),
                flow.complete())));
        return out;
    }

    static String coachMarkdown(SpendingAnalysis analysis, CoachingNotes notes, RunCost cost, String route) {
        StringBuilder md = new StringBuilder("# Coaching note\n\n");
        if (notes.isEmpty()) {
            md.append("Coaching was not requested for this run.\n\n");
        } else {
            md.append(notes.summary()).append("\n\n");
            if (!notes.insights().isEmpty()) {
                md.append("## Insights\n\n");
                notes.insights().forEach(insight -> md.append("- **").append(insight.title()).append("**")
                        .append(insight.category() == null ? "" : " (" + insight.category() + ")")
                        .append(insight.amount() == null ? "" : ": " + SpendingAnalysis.money(insight.amount()))
                        .append(". ").append(insight.detail()).append('\n'));
                md.append('\n');
            }
            if (!notes.savings().isEmpty()) {
                md.append("## Savings\n\n");
                notes.savings().forEach(saving -> md.append("- ").append(saving.what())
                        .append(saving.monthlySaving() == null ? "" : ": " + SpendingAnalysis.money(saving.monthlySaving()) + " a month")
                        .append(". ").append(saving.how()).append('\n'));
                md.append('\n');
            }
        }
        if (analysis.unmatchedTransfers().signum() > 0) {
            md.append("Transfers out without a matching credit in another account: ")
                    .append(SpendingAnalysis.money(analysis.unmatchedTransfers())).append(" (not counted as spend).\n\n");
        }
        md.append("Route: ").append(route).append("\n\n_").append(cost).append("_\n");
        return md.toString();
    }
}
