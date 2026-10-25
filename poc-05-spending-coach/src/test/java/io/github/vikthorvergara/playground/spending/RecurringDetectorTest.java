package io.github.vikthorvergara.playground.spending;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static io.github.vikthorvergara.playground.spending.Rows.cat;
import static io.github.vikthorvergara.playground.spending.Rows.spend;
import static org.assertj.core.api.Assertions.assertThat;

class RecurringDetectorTest {

    @Test
    void monthlySubscriptionIsDetectedWithAnnualisedAmount() {
        List<SpendingAnalysis.RecurringCharge> charges = RecurringDetector.detect(List.of(
                cat(spend("1", "2026-07-05", "NETFLIX.COM", "39.90"), Category.SUBSCRIPTIONS),
                cat(spend("2", "2026-08-05", "NETFLIX.COM", "39.9"), Category.SUBSCRIPTIONS),
                cat(spend("3", "2026-09-05", "NETFLIX.COM", "39.90"), Category.SUBSCRIPTIONS)));

        assertThat(charges).singleElement().isEqualTo(new SpendingAnalysis.RecurringCharge("NETFLIX COM", Category.SUBSCRIPTIONS,
                new BigDecimal("39.90"), 30, 3, LocalDate.of(2026, 9, 5), new BigDecimal("485.45")));
    }

    @Test
    void weeklyAndFortnightlyCadencesAreRecognised() {
        List<SpendingAnalysis.RecurringCharge> charges = RecurringDetector.detect(List.of(
                cat(spend("1", "2026-07-01", "GYM", "10.00"), Category.HEALTH),
                cat(spend("2", "2026-07-08", "GYM", "10.00"), Category.HEALTH),
                cat(spend("3", "2026-07-15", "GYM", "10.50"), Category.HEALTH),
                cat(spend("4", "2026-07-23", "GYM", "10.00"), Category.HEALTH),
                cat(spend("5", "2026-07-01", "CLEANER", "80.00"), Category.HOUSING),
                cat(spend("6", "2026-07-15", "CLEANER", "80.00"), Category.HOUSING),
                cat(spend("7", "2026-07-30", "CLEANER", "80.00"), Category.HOUSING)));

        assertThat(charges).extracting(SpendingAnalysis.RecurringCharge::merchant, SpendingAnalysis.RecurringCharge::cadenceDays)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("CLEANER", 14), org.assertj.core.groups.Tuple.tuple("GYM", 7));
    }

    @Test
    void amountsOutsideToleranceOrIrregularIntervalsAreNotRecurring() {
        assertThat(RecurringDetector.detect(List.of(
                cat(spend("1", "2026-07-05", "SHOP", "39.90"), Category.SHOPPING),
                cat(spend("2", "2026-08-05", "SHOP", "39.90"), Category.SHOPPING),
                cat(spend("3", "2026-09-05", "SHOP", "60.00"), Category.SHOPPING)))).isEmpty();
        assertThat(RecurringDetector.detect(List.of(
                cat(spend("1", "2026-07-05", "SHOP", "39.90"), Category.SHOPPING),
                cat(spend("2", "2026-07-20", "SHOP", "39.90"), Category.SHOPPING),
                cat(spend("3", "2026-09-05", "SHOP", "39.90"), Category.SHOPPING)))).isEmpty();
        assertThat(RecurringDetector.detect(List.of(
                cat(spend("1", "2026-07-05", "SHOP", "39.90"), Category.SHOPPING),
                cat(spend("2", "2026-08-05", "SHOP", "39.90"), Category.SHOPPING)))).isEmpty();
    }

    @Test
    void transfersAndIncomeAreNotCharges() {
        Transaction salary = Rows.tx("1", "2026-07-05", "bank.csv", Layout.Kind.BANK, "SALARIO", "6500.00", Transaction.Flow.INCOME);
        assertThat(RecurringDetector.detect(List.of(
                new RuledLedger.Categorised(salary, Category.INCOME, Origin.FLOW, null),
                new RuledLedger.Categorised(Rows.tx("2", "2026-08-05", "bank.csv", Layout.Kind.BANK, "SALARIO", "6500.00", Transaction.Flow.INCOME), Category.INCOME, Origin.FLOW, null),
                new RuledLedger.Categorised(Rows.tx("3", "2026-09-05", "bank.csv", Layout.Kind.BANK, "SALARIO", "6500.00", Transaction.Flow.INCOME), Category.INCOME, Origin.FLOW, null))))
                .isEmpty();
    }
}
