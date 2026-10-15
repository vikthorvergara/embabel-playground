package io.github.vikthorvergara.playground.spending;

import com.embabel.agent.api.annotation.LlmTool;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableSet;
import java.util.TreeSet;
import java.util.stream.Collectors;

public record SpendingAnalysis(
        String currency,
        List<YearMonth> months,
        List<CategoryMonth> byMonth,
        List<RecurringCharge> recurring,
        List<Anomaly> anomalies,
        List<MonthlyCashFlow> cashFlow,
        List<BudgetSuggestion> budgets,
        List<MerchantTotal> topMerchants,
        BigDecimal unmatchedTransfers,
        int foreignRows,
        boolean coachable) {

    public record CategoryMonth(YearMonth month, Category category, BigDecimal total, int count) {
    }

    public record RecurringCharge(String merchant, Category category, BigDecimal typicalAmount, int cadenceDays,
                                  int occurrences, LocalDate lastSeen, BigDecimal annualised) {
    }

    public enum AnomalyType { LARGE_FOR_MERCHANT, DUPLICATE_CHARGE, FEE_OR_INTEREST }

    public record Anomaly(String transactionId, LocalDate date, String merchant, BigDecimal amount, AnomalyType type, String detail) {
    }

    public record MonthlyCashFlow(YearMonth month, BigDecimal income, BigDecimal spend, BigDecimal transfersOut,
                                  BigDecimal cardCredits, BigDecimal net, BigDecimal savingsRate, boolean complete) {
    }

    public record BudgetSuggestion(Category category, BigDecimal medianMonthly, BigDecimal suggested, BigDecimal lastMonth) {
    }

    public record MerchantTotal(YearMonth month, String merchant, Category category, BigDecimal total, int count) {
    }

    public static boolean isSpendCategory(Category category) {
        return category != Category.INCOME && category != Category.TRANSFER;
    }

    @LlmTool(description = "The months covered by the statements, oldest first")
    public List<YearMonth> months() {
        return months;
    }

    @LlmTool(description = "Spend per category for one month in yyyy-MM form, or for the whole period when month is empty")
    public String spendByCategory(@LlmTool.Param(description = "Month as yyyy-MM, or empty for the whole period") String month) {
        YearMonth wanted = month == null || month.isBlank() ? null : YearMonth.parse(month.strip());
        Map<Category, BigDecimal> totals = new EnumMap<>(Category.class);
        Map<Category, Integer> counts = new EnumMap<>(Category.class);
        for (CategoryMonth cell : byMonth) {
            if ((wanted == null || cell.month().equals(wanted)) && isSpendCategory(cell.category())) {
                totals.merge(cell.category(), cell.total(), BigDecimal::add);
                counts.merge(cell.category(), cell.count(), Integer::sum);
            }
        }
        if (totals.isEmpty()) {
            return "No spend recorded for " + (wanted == null ? "the period" : wanted);
        }
        String lines = totals.entrySet().stream()
                .sorted(Map.Entry.<Category, BigDecimal>comparingByValue().reversed())
                .map(entry -> entry.getKey() + ": " + money(entry.getValue()) + " (" + counts.get(entry.getKey()) + " rows)")
                .collect(Collectors.joining("\n"));
        return lines + "\nTOTAL_SPEND: " + money(totals.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    @LlmTool(description = "Total spend per month and the change against the previous month")
    public String monthOverMonth() {
        StringBuilder out = new StringBuilder();
        BigDecimal previous = null;
        for (YearMonth month : months) {
            BigDecimal total = totalSpend(month);
            out.append(month).append(": ").append(money(total));
            if (previous != null && previous.signum() != 0) {
                BigDecimal change = total.subtract(previous).multiply(BigDecimal.valueOf(100)).divide(previous, 1, RoundingMode.HALF_EVEN);
                out.append(" (").append(change.signum() > 0 ? "+" : "").append(change).append("% vs previous month)");
            }
            out.append('\n');
            previous = total;
        }
        return out.toString().strip();
    }

    @LlmTool(description = "Charges that repeat at a steady cadence, such as subscriptions and rent")
    public String recurringCharges() {
        if (recurring.isEmpty()) {
            return "No recurring charges found";
        }
        return recurring.stream()
                .map(charge -> "%s (%s): %s every %d days, %d times, last %s, %s a year".formatted(charge.merchant(), charge.category(),
                        money(charge.typicalAmount()), charge.cadenceDays(), charge.occurrences(), charge.lastSeen(), money(charge.annualised())))
                .collect(Collectors.joining("\n"));
    }

    @LlmTool(name = "anomalies", description = "Rows worth a second look: unusually large charges, duplicate charges, fees and interest")
    public String anomalyReport() {
        if (anomalies.isEmpty()) {
            return "No anomalies found";
        }
        return anomalies.stream()
                .map(anomaly -> "%s %s %s %s: %s".formatted(anomaly.date(), anomaly.merchant(), money(anomaly.amount()), anomaly.type(), anomaly.detail()))
                .collect(Collectors.joining("\n"));
    }

    @LlmTool(name = "cashFlow", description = "Income, spend, transfers out, card credits, net and savings rate per month")
    public String cashFlowReport() {
        return cashFlow.stream()
                .map(flow -> "%s: income %s, spend %s, transfers out %s, card credits %s, net %s, savings rate %s, %s".formatted(
                        flow.month(), money(flow.income()), money(flow.spend()), money(flow.transfersOut()), money(flow.cardCredits()),
                        money(flow.net()), flow.savingsRate() == null ? "n/a" : percent(flow.savingsRate()),
                        flow.complete() ? "complete month" : "incomplete month"))
                .collect(Collectors.joining("\n"));
    }

    @LlmTool(description = "The merchants with the highest spend, for one month in yyyy-MM form or the whole period when month is empty")
    public String topMerchants(
            @LlmTool.Param(description = "Month as yyyy-MM, or empty for the whole period") String month,
            @LlmTool.Param(description = "How many merchants to list") int n) {
        YearMonth wanted = month == null || month.isBlank() ? null : YearMonth.parse(month.strip());
        Map<String, MerchantTotal> merged = new LinkedHashMap<>();
        for (MerchantTotal entry : topMerchants) {
            if (wanted != null && !entry.month().equals(wanted)) {
                continue;
            }
            merged.merge(entry.merchant(), entry, (a, b) -> new MerchantTotal(null, a.merchant(), a.category(), a.total().add(b.total()), a.count() + b.count()));
        }
        if (merged.isEmpty()) {
            return "No merchants recorded for " + (wanted == null ? "the period" : wanted);
        }
        return merged.values().stream()
                .sorted(Comparator.comparing(MerchantTotal::total).reversed())
                .limit(Math.max(1, n))
                .map(entry -> "%s (%s): %s over %d rows".formatted(entry.merchant(), entry.category(), money(entry.total()), entry.count()))
                .collect(Collectors.joining("\n"));
    }

    @LlmTool(description = "A suggested monthly budget per category from the median of complete months")
    public String budgetSuggestions() {
        if (budgets.isEmpty()) {
            return "Not enough complete months to suggest budgets";
        }
        return budgets.stream()
                .map(budget -> "%s: median %s a month, suggested %s, last month %s".formatted(budget.category(),
                        money(budget.medianMonthly()), money(budget.suggested()), money(budget.lastMonth())))
                .collect(Collectors.joining("\n"));
    }

    public BigDecimal totalSpend(YearMonth month) {
        return byMonth.stream()
                .filter(cell -> cell.month().equals(month) && isSpendCategory(cell.category()))
                .map(CategoryMonth::total)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public BigDecimal categoryTotal(Category category) {
        return byMonth.stream()
                .filter(cell -> cell.category() == category)
                .map(CategoryMonth::total)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public BigDecimal monthlyAverage(Category category) {
        if (months.isEmpty()) {
            return BigDecimal.ZERO.setScale(2);
        }
        return categoryTotal(category).divide(BigDecimal.valueOf(months.size()), 2, RoundingMode.HALF_EVEN);
    }

    public List<Category> categoriesByTotal() {
        return byMonth.stream()
                .map(CategoryMonth::category)
                .distinct()
                .sorted(Comparator.comparing(this::categoryTotal).reversed().thenComparing(Comparator.naturalOrder()))
                .toList();
    }

    public NavigableSet<BigDecimal> allAmounts() {
        TreeSet<BigDecimal> amounts = new TreeSet<>();
        for (CategoryMonth cell : byMonth) {
            amounts.add(cell.total());
            amounts.add(cell.total().abs());
        }
        for (Category category : categoriesByTotal()) {
            amounts.add(categoryTotal(category));
            amounts.add(monthlyAverage(category));
        }
        for (YearMonth month : months) {
            amounts.add(totalSpend(month));
        }
        for (RecurringCharge charge : recurring) {
            amounts.add(charge.typicalAmount());
            amounts.add(charge.annualised());
        }
        for (Anomaly anomaly : anomalies) {
            amounts.add(anomaly.amount());
        }
        for (MonthlyCashFlow flow : cashFlow) {
            amounts.add(flow.income());
            amounts.add(flow.spend());
            amounts.add(flow.transfersOut());
            amounts.add(flow.cardCredits());
            amounts.add(flow.net());
        }
        for (BudgetSuggestion budget : budgets) {
            amounts.add(budget.medianMonthly());
            amounts.add(budget.suggested());
            amounts.add(budget.lastMonth());
        }
        for (MerchantTotal merchant : topMerchants) {
            amounts.add(merchant.total());
        }
        amounts.add(unmatchedTransfers);
        return amounts;
    }

    static String money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_EVEN).toPlainString();
    }

    static String percent(BigDecimal rate) {
        return rate.multiply(BigDecimal.valueOf(100)).setScale(1, RoundingMode.HALF_EVEN).toPlainString() + "%";
    }

    @Override
    public String toString() {
        return "SpendingAnalysis[" + months.size() + " months, " + byMonth.size() + " category cells, " + recurring.size()
                + " recurring, " + anomalies.size() + " anomalies, coachable=" + coachable + "]";
    }
}
