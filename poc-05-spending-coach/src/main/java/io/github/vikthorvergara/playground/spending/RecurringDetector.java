package io.github.vikthorvergara.playground.spending;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public final class RecurringDetector {

    static final int MIN_OCCURRENCES = 3;
    static final int[] CADENCES = {7, 14, 30};
    static final BigDecimal TOLERANCE = new BigDecimal("0.10");

    private RecurringDetector() {
    }

    public static List<SpendingAnalysis.RecurringCharge> detect(List<RuledLedger.Categorised> rows) {
        Map<String, List<RuledLedger.Categorised>> byMerchant = rows.stream()
                .filter(Aggregator::isSpend)
                .sorted(Comparator.comparing((RuledLedger.Categorised row) -> row.tx().date()).thenComparing(row -> row.tx().id()))
                .collect(Collectors.groupingBy(row -> row.tx().merchant(), LinkedHashMap::new, Collectors.toList()));
        List<SpendingAnalysis.RecurringCharge> charges = new ArrayList<>();
        for (Map.Entry<String, List<RuledLedger.Categorised>> entry : byMerchant.entrySet()) {
            List<RuledLedger.Categorised> occurrences = entry.getValue();
            if (occurrences.size() < MIN_OCCURRENCES) {
                continue;
            }
            Integer cadence = cadenceOf(occurrences);
            List<BigDecimal> amounts = occurrences.stream().map(row -> row.tx().amount()).toList();
            BigDecimal typical = Amounts.median(amounts);
            if (cadence == null || !withinTolerance(amounts, typical)) {
                continue;
            }
            Category category = occurrences.stream()
                    .collect(Collectors.groupingBy(RuledLedger.Categorised::category, LinkedHashMap::new, Collectors.counting()))
                    .entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(Category.UNCATEGORISED);
            BigDecimal annualised = typical.multiply(BigDecimal.valueOf(365)).divide(BigDecimal.valueOf(cadence), 2, RoundingMode.HALF_EVEN);
            charges.add(new SpendingAnalysis.RecurringCharge(entry.getKey(), category, typical, cadence, occurrences.size(),
                    occurrences.getLast().tx().date(), annualised));
        }
        charges.sort(Comparator.comparing(SpendingAnalysis.RecurringCharge::annualised).reversed()
                .thenComparing(SpendingAnalysis.RecurringCharge::merchant));
        return charges;
    }

    private static Integer cadenceOf(List<RuledLedger.Categorised> occurrences) {
        List<Long> intervals = new ArrayList<>();
        for (int i = 1; i < occurrences.size(); i++) {
            intervals.add(ChronoUnit.DAYS.between(occurrences.get(i - 1).tx().date(), occurrences.get(i).tx().date()));
        }
        for (int cadence : CADENCES) {
            int slack = cadence <= 7 ? 2 : cadence <= 14 ? 3 : 5;
            if (intervals.stream().allMatch(interval -> Math.abs(interval - cadence) <= slack)) {
                return cadence;
            }
        }
        return null;
    }

    private static boolean withinTolerance(List<BigDecimal> amounts, BigDecimal typical) {
        BigDecimal slack = typical.multiply(TOLERANCE);
        return amounts.stream().allMatch(amount -> amount.subtract(typical).abs().compareTo(slack) <= 0);
    }
}
