package io.github.vikthorvergara.playground.spending;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

public final class Aggregator {

    static final int MIN_SPEND_ROWS_FOR_COACHING = 10;
    static final int MIN_COMPLETE_MONTHS_FOR_BUDGET = 2;
    static final int COVERAGE_START_DAY = 3;
    static final int COVERAGE_END_DAY = 28;

    private record Cell(BigDecimal total, int count) {

        Cell add(BigDecimal amount) {
            return new Cell(total.add(amount), count + 1);
        }
    }

    private record Window(LocalDate start, LocalDate end) {

        boolean covers(YearMonth month) {
            return !start.isAfter(month.atDay(COVERAGE_START_DAY)) && !end.isBefore(month.atDay(COVERAGE_END_DAY));
        }
    }

    private Aggregator() {
    }

    public static boolean isSpend(RuledLedger.Categorised row) {
        Transaction tx = row.tx();
        if (!SpendingAnalysis.isSpendCategory(row.category())) {
            return false;
        }
        return tx.flow() == Transaction.Flow.SPEND || (tx.flow() == Transaction.Flow.TRANSFER && tx.outgoing());
    }

    public static SpendingAnalysis analyse(CategorisedLedger ledger, Ledger raw, boolean coach) {
        List<RuledLedger.Categorised> rows = ledger.rows();
        List<YearMonth> months = months(rows);
        Map<String, RuledLedger.Categorised> byId = rows.stream().collect(Collectors.toMap(row -> row.tx().id(), row -> row));
        Map<YearMonth, Map<Category, Cell>> cells = new TreeMap<>();
        Map<YearMonth, BigDecimal> income = new HashMap<>();
        Map<YearMonth, BigDecimal> transfersOut = new HashMap<>();
        Map<YearMonth, BigDecimal> cardCredits = new HashMap<>();
        Map<YearMonth, Map<String, Cell>> merchants = new TreeMap<>();
        Map<YearMonth, Map<String, Category>> merchantCategories = new HashMap<>();
        int spendRows = 0;
        for (RuledLedger.Categorised row : rows) {
            Transaction tx = row.tx();
            YearMonth month = YearMonth.from(tx.date());
            if (isSpend(row)) {
                spendRows++;
                cells.computeIfAbsent(month, m -> new LinkedHashMap<>()).merge(row.category(), new Cell(tx.amount(), 1), (a, b) -> a.add(b.total()));
                merchants.computeIfAbsent(month, m -> new LinkedHashMap<>()).merge(tx.merchant(), new Cell(tx.amount(), 1), (a, b) -> a.add(b.total()));
                merchantCategories.computeIfAbsent(month, m -> new HashMap<>()).putIfAbsent(tx.merchant(), row.category());
            } else if (tx.flow() == Transaction.Flow.REFUND) {
                cells.computeIfAbsent(month, m -> new LinkedHashMap<>()).merge(row.category(), new Cell(tx.amount().negate(), 1), (a, b) -> a.add(b.total()));
            } else if (tx.flow() == Transaction.Flow.INCOME) {
                cells.computeIfAbsent(month, m -> new LinkedHashMap<>()).merge(Category.INCOME, new Cell(tx.amount(), 1), (a, b) -> a.add(b.total()));
                income.merge(month, tx.amount(), BigDecimal::add);
            } else if (tx.flow() == Transaction.Flow.TRANSFER && tx.outgoing()) {
                cells.computeIfAbsent(month, m -> new LinkedHashMap<>()).merge(Category.TRANSFER, new Cell(tx.amount(), 1), (a, b) -> a.add(b.total()));
                if (!isCardPayment(tx, byId)) {
                    transfersOut.merge(month, tx.amount(), BigDecimal::add);
                }
            } else if (tx.flow() == Transaction.Flow.CREDIT) {
                cardCredits.merge(month, tx.amount(), BigDecimal::add);
            }
        }
        List<SpendingAnalysis.CategoryMonth> byMonth = new ArrayList<>();
        cells.forEach((month, categories) -> categories.forEach((category, cell) ->
                byMonth.add(new SpendingAnalysis.CategoryMonth(month, category, Amounts.money(cell.total()), cell.count()))));
        byMonth.sort(Comparator.comparing(SpendingAnalysis.CategoryMonth::category).thenComparing(SpendingAnalysis.CategoryMonth::month));

        Map<String, Window> windows = windows(rows);
        List<SpendingAnalysis.MonthlyCashFlow> cashFlow = new ArrayList<>();
        List<YearMonth> completeMonths = new ArrayList<>();
        for (YearMonth month : months) {
            BigDecimal monthIncome = Amounts.money(income.getOrDefault(month, BigDecimal.ZERO));
            BigDecimal monthSpend = Amounts.money(cells.getOrDefault(month, Map.of()).entrySet().stream()
                    .filter(entry -> SpendingAnalysis.isSpendCategory(entry.getKey()))
                    .map(entry -> entry.getValue().total())
                    .reduce(BigDecimal.ZERO, BigDecimal::add));
            BigDecimal net = monthIncome.subtract(monthSpend);
            BigDecimal savingsRate = monthIncome.signum() == 0 ? null : net.divide(monthIncome, 4, RoundingMode.HALF_EVEN);
            boolean complete = !windows.isEmpty() && windows.values().stream().allMatch(window -> window.covers(month));
            if (complete) {
                completeMonths.add(month);
            }
            cashFlow.add(new SpendingAnalysis.MonthlyCashFlow(month, monthIncome, monthSpend,
                    Amounts.money(transfersOut.getOrDefault(month, BigDecimal.ZERO)),
                    Amounts.money(cardCredits.getOrDefault(month, BigDecimal.ZERO)), net, savingsRate, complete));
        }

        List<SpendingAnalysis.BudgetSuggestion> budgets = new ArrayList<>();
        if (completeMonths.size() >= MIN_COMPLETE_MONTHS_FOR_BUDGET) {
            List<Category> categories = byMonth.stream().map(SpendingAnalysis.CategoryMonth::category)
                    .filter(SpendingAnalysis::isSpendCategory).distinct().sorted().toList();
            for (Category category : categories) {
                List<BigDecimal> totals = completeMonths.stream()
                        .map(month -> cells.getOrDefault(month, Map.of()).getOrDefault(category, new Cell(BigDecimal.ZERO, 0)).total())
                        .toList();
                BigDecimal median = Amounts.median(totals);
                BigDecimal suggested = median.signum() <= 0 ? Amounts.money(median)
                        : Amounts.money(median.divide(BigDecimal.TEN, 0, RoundingMode.CEILING).multiply(BigDecimal.TEN));
                budgets.add(new SpendingAnalysis.BudgetSuggestion(category, median, suggested, Amounts.money(totals.getLast())));
            }
        }

        List<SpendingAnalysis.MerchantTotal> topMerchants = new ArrayList<>();
        merchants.forEach((month, perMerchant) -> perMerchant.forEach((merchant, cell) ->
                topMerchants.add(new SpendingAnalysis.MerchantTotal(month, merchant, merchantCategories.get(month).get(merchant),
                        Amounts.money(cell.total()), cell.count()))));
        topMerchants.sort(Comparator.comparing(SpendingAnalysis.MerchantTotal::total).reversed()
                .thenComparing(SpendingAnalysis.MerchantTotal::merchant).thenComparing(SpendingAnalysis.MerchantTotal::month));

        return new SpendingAnalysis(raw.currency(), months, byMonth, RecurringDetector.detect(rows), AnomalyDetector.detect(rows),
                cashFlow, budgets, topMerchants, Amounts.money(raw.unmatchedTransfers()), raw.foreign().size(),
                coach && spendRows >= MIN_SPEND_ROWS_FOR_COACHING);
    }

    static List<YearMonth> months(List<RuledLedger.Categorised> rows) {
        if (rows.isEmpty()) {
            return List.of();
        }
        YearMonth first = rows.stream().map(row -> YearMonth.from(row.tx().date())).min(Comparator.naturalOrder()).orElseThrow();
        YearMonth last = rows.stream().map(row -> YearMonth.from(row.tx().date())).max(Comparator.naturalOrder()).orElseThrow();
        List<YearMonth> months = new ArrayList<>();
        for (YearMonth month = first; !month.isAfter(last); month = month.plusMonths(1)) {
            months.add(month);
        }
        return months;
    }

    private static Map<String, Window> windows(List<RuledLedger.Categorised> rows) {
        Map<String, Window> windows = new HashMap<>();
        for (RuledLedger.Categorised row : rows) {
            Transaction tx = row.tx();
            windows.merge(tx.account(), new Window(tx.date(), tx.date()), (a, b) ->
                    new Window(a.start().isBefore(b.start()) ? a.start() : b.start(), a.end().isAfter(b.end()) ? a.end() : b.end()));
        }
        return windows;
    }

    private static boolean isCardPayment(Transaction tx, Map<String, RuledLedger.Categorised> byId) {
        String partner = tx.linked(Transaction.TRANSFER_PAIR);
        return partner != null && byId.containsKey(partner) && byId.get(partner).tx().kind() == Layout.Kind.CARD;
    }
}
