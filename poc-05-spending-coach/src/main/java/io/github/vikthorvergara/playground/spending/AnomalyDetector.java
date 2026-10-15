package io.github.vikthorvergara.playground.spending;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class AnomalyDetector {

    static final BigDecimal LARGE_MULTIPLIER = BigDecimal.valueOf(3);
    static final int MIN_PRIOR = 3;
    static final int DUPLICATE_WINDOW_DAYS = 2;

    private AnomalyDetector() {
    }

    public static List<SpendingAnalysis.Anomaly> detect(List<RuledLedger.Categorised> rows) {
        List<RuledLedger.Categorised> spend = rows.stream()
                .filter(Aggregator::isSpend)
                .sorted(Comparator.comparing((RuledLedger.Categorised row) -> row.tx().date()).thenComparing(row -> row.tx().id()))
                .toList();
        List<SpendingAnalysis.Anomaly> anomalies = new ArrayList<>();
        Map<String, List<BigDecimal>> priorAmounts = new HashMap<>();
        Map<String, List<Transaction>> priorRows = new HashMap<>();
        for (RuledLedger.Categorised row : spend) {
            Transaction tx = row.tx();
            List<BigDecimal> prior = priorAmounts.computeIfAbsent(tx.merchant(), merchant -> new ArrayList<>());
            if (prior.size() >= MIN_PRIOR) {
                BigDecimal median = Amounts.median(prior);
                if (median.signum() > 0 && tx.amount().compareTo(median.multiply(LARGE_MULTIPLIER)) > 0) {
                    BigDecimal times = tx.amount().divide(median, 1, RoundingMode.HALF_EVEN);
                    anomalies.add(new SpendingAnalysis.Anomaly(tx.id(), tx.date(), tx.merchant(), tx.amount(),
                            SpendingAnalysis.AnomalyType.LARGE_FOR_MERCHANT, times + "x the usual " + Amounts.money(median)));
                }
            }
            for (Transaction earlier : priorRows.computeIfAbsent(tx.merchant(), merchant -> new ArrayList<>())) {
                if (earlier.amount().compareTo(tx.amount()) == 0
                        && ChronoUnit.DAYS.between(earlier.date(), tx.date()) <= DUPLICATE_WINDOW_DAYS) {
                    anomalies.add(new SpendingAnalysis.Anomaly(tx.id(), tx.date(), tx.merchant(), tx.amount(),
                            SpendingAnalysis.AnomalyType.DUPLICATE_CHARGE, "same merchant and amount as " + earlier.id() + " on " + earlier.date()));
                    break;
                }
            }
            if (row.category() == Category.FEES_AND_INTEREST) {
                anomalies.add(new SpendingAnalysis.Anomaly(tx.id(), tx.date(), tx.merchant(), tx.amount(),
                        SpendingAnalysis.AnomalyType.FEE_OR_INTEREST, tx.rawDescription()));
            }
            prior.add(tx.amount());
            priorRows.get(tx.merchant()).add(tx);
        }
        return anomalies;
    }
}
