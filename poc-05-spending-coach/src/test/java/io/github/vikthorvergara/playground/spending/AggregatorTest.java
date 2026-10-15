package io.github.vikthorvergara.playground.spending;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

import static io.github.vikthorvergara.playground.spending.Layout.Kind.BANK;
import static io.github.vikthorvergara.playground.spending.Layout.Kind.CARD;
import static io.github.vikthorvergara.playground.spending.Rows.cat;
import static io.github.vikthorvergara.playground.spending.Rows.tx;
import static io.github.vikthorvergara.playground.spending.Transaction.Flow.CREDIT;
import static io.github.vikthorvergara.playground.spending.Transaction.Flow.INCOME;
import static io.github.vikthorvergara.playground.spending.Transaction.Flow.REFUND;
import static io.github.vikthorvergara.playground.spending.Transaction.Flow.SPEND;
import static io.github.vikthorvergara.playground.spending.Transaction.Flow.TRANSFER;
import static org.assertj.core.api.Assertions.assertThat;

class AggregatorTest {

    private static final YearMonth JULY = YearMonth.of(2026, 7);
    private static final YearMonth AUGUST = YearMonth.of(2026, 8);
    private static final YearMonth SEPTEMBER = YearMonth.of(2026, 9);

    @Test
    void totalsEqualTheRowsToTheCentAndTransfersCountNowhere(@TempDir Path work) throws Exception {
        Files.copy(Path.of("src/test/resources/statements/rules.csv"), work.resolve("rules.csv"));
        Fixtures.Run run = Fixtures.run(work, false, Fixtures.ALL);
        SpendingAnalysis analysis = run.analysis();

        for (YearMonth month : analysis.months()) {
            BigDecimal fromRows = run.categorised().rows().stream()
                    .filter(row -> YearMonth.from(row.tx().date()).equals(month))
                    .map(row -> Aggregator.isSpend(row) ? row.tx().amount()
                            : row.tx().flow() == REFUND ? row.tx().amount().negate() : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            assertThat(analysis.totalSpend(month)).as(month.toString()).isEqualByComparingTo(fromRows);
        }
        assertThat(analysis.months()).containsExactly(JULY, AUGUST, SEPTEMBER);
        assertThat(analysis.categoryTotal(Category.TRANSFER)).isEqualByComparingTo("4490.75");
        assertThat(analysis.categoryTotal(Category.GIFTS_AND_DONATIONS)).isEqualByComparingTo("120.00");
        assertThat(analysis.unmatchedTransfers()).isEqualByComparingTo("120.00");
        assertThat(analysis.foreignRows()).isEqualTo(1);
        assertThat(analysis.cashFlow()).extracting(SpendingAnalysis.MonthlyCashFlow::complete).containsExactly(true, true, true);
        assertThat(analysis.cashFlow().get(1).transfersOut()).isEqualByComparingTo("500.00");
        assertThat(analysis.cashFlow().get(0).transfersOut()).isEqualByComparingTo("0");
        assertThat(analysis.recurring()).extracting(SpendingAnalysis.RecurringCharge::merchant)
                .contains("NETFLIX COM", "SPOTIFY", "ACADEMIA FORMA", "ALUGUEL APTO", "TARIFA MANUTENCAO CONTA", "ENEL ENERGIA");
        assertThat(analysis.anomalies()).extracting(SpendingAnalysis.Anomaly::type)
                .contains(SpendingAnalysis.AnomalyType.DUPLICATE_CHARGE, SpendingAnalysis.AnomalyType.LARGE_FOR_MERCHANT, SpendingAnalysis.AnomalyType.FEE_OR_INTEREST);
        assertThat(analysis.budgets()).isNotEmpty();
        assertThat(analysis.spendByCategory("2026-07")).contains("HOUSING: 2100.00").contains("TOTAL_SPEND:");
        assertThat(analysis.topMerchants("", 3)).hasLineCount(3).contains("ALUGUEL APTO (HOUSING): 6300.00 over 3 rows");
        assertThat(analysis.allAmounts()).contains(new BigDecimal("2100.00"), new BigDecimal("485.45"));
    }

    @Test
    void refundInALaterMonthMakesANegativeCell() {
        Transaction debit = tx("c1", "2026-07-10", "card.csv", CARD, "AMAZON", "189.00", SPEND);
        Transaction refund = tx("c2", "2026-08-02", "card.csv", CARD, "AMAZON", "89.00", REFUND, Transaction.REFUND_OF + "c1");

        SpendingAnalysis analysis = analyse(List.of(cat(debit, Category.SHOPPING), cat(refund, Category.SHOPPING)), false);

        assertThat(analysis.byMonth()).containsExactly(
                new SpendingAnalysis.CategoryMonth(JULY, Category.SHOPPING, new BigDecimal("189.00"), 1),
                new SpendingAnalysis.CategoryMonth(AUGUST, Category.SHOPPING, new BigDecimal("-89.00"), 1));
        assertThat(analysis.totalSpend(AUGUST)).isEqualByComparingTo("-89.00");
    }

    @Test
    void transfersAndCardCreditsAreExcludedFromSpend() {
        List<RuledLedger.Categorised> rows = List.of(
                new RuledLedger.Categorised(tx("1", "2026-07-10", "bank.csv", BANK, "TRANSF POUPANCA", "500.00", TRANSFER, Transaction.TRANSFER_OUT), Category.TRANSFER, Origin.FLOW, null),
                new RuledLedger.Categorised(tx("2", "2026-07-11", "card.csv", CARD, "CASHBACK", "10.00", CREDIT), Category.OTHER, Origin.FLOW, null),
                new RuledLedger.Categorised(tx("3", "2026-07-12", "bank.csv", BANK, "PIX JOAO", "120.00", TRANSFER, Transaction.TRANSFER_OUT), Category.GIFTS_AND_DONATIONS, Origin.USER_RULE, null),
                new RuledLedger.Categorised(tx("4", "2026-07-13", "bank.csv", BANK, "PIX ANA", "70.00", TRANSFER, Transaction.TRANSFER_IN), Category.TRANSFER, Origin.FLOW, null));

        SpendingAnalysis analysis = analyse(rows, false);

        assertThat(analysis.totalSpend(JULY)).isEqualByComparingTo("120.00");
        assertThat(analysis.cashFlow().getFirst().transfersOut()).isEqualByComparingTo("500.00");
        assertThat(analysis.cashFlow().getFirst().cardCredits()).isEqualByComparingTo("10.00");
        assertThat(analysis.cashFlow().getFirst().savingsRate()).isNull();
    }

    @Test
    void completenessComesFromAccountCoverageWindowsIncludingTwoCardCycles() {
        List<RuledLedger.Categorised> rows = new ArrayList<>();
        rows.add(cat(tx("b1", "2026-07-01", "bank.csv", BANK, "RENT", "100.00", SPEND), Category.HOUSING));
        rows.add(cat(tx("b2", "2026-09-30", "bank.csv", BANK, "RENT", "100.00", SPEND), Category.HOUSING));
        rows.add(cat(tx("c1", "2026-07-10", "card-jul.csv", CARD, "SHOP", "10.00", SPEND), Category.SHOPPING));
        rows.add(cat(tx("c2", "2026-08-09", "card-jul.csv", CARD, "SHOP", "10.00", SPEND), Category.SHOPPING));
        rows.add(cat(tx("c3", "2026-08-10", "card-aug.csv", CARD, "SHOP", "10.00", SPEND), Category.SHOPPING));
        rows.add(cat(tx("c4", "2026-09-09", "card-aug.csv", CARD, "SHOP", "10.00", SPEND), Category.SHOPPING));
        List<RuledLedger.Categorised> sameAccount = rows.stream().map(row -> row.tx().kind() == CARD
                ? cat(new Transaction(row.tx().id(), row.tx().date(), "CARD-shared", row.tx().sourceFile(), CARD, row.tx().rawDescription(),
                row.tx().merchant(), row.tx().amount(), "BRL", SPEND, List.of()), row.category())
                : row).toList();

        SpendingAnalysis analysis = analyse(sameAccount, false);

        assertThat(analysis.cashFlow()).extracting(SpendingAnalysis.MonthlyCashFlow::complete).containsExactly(false, true, false);
        assertThat(analysis.budgets()).isEmpty();
    }

    @Test
    void budgetsNeedTwoCompleteMonthsAndSavingsRateNeedsIncome() {
        List<RuledLedger.Categorised> rows = List.of(
                cat(tx("1", "2026-07-01", "bank.csv", BANK, "RENT", "100.00", SPEND), Category.HOUSING),
                cat(tx("2", "2026-07-30", "bank.csv", BANK, "FOOD", "55.00", SPEND), Category.GROCERIES),
                cat(tx("3", "2026-08-01", "bank.csv", BANK, "RENT", "100.00", SPEND), Category.HOUSING),
                cat(tx("4", "2026-08-30", "bank.csv", BANK, "FOOD", "61.00", SPEND), Category.GROCERIES),
                new RuledLedger.Categorised(tx("5", "2026-08-05", "bank.csv", BANK, "SALARY", "1000.00", INCOME), Category.INCOME, Origin.FLOW, null));

        SpendingAnalysis analysis = analyse(rows, false);

        assertThat(analysis.budgets()).containsExactly(
                new SpendingAnalysis.BudgetSuggestion(Category.HOUSING, new BigDecimal("100.00"), new BigDecimal("100.00"), new BigDecimal("100.00")),
                new SpendingAnalysis.BudgetSuggestion(Category.GROCERIES, new BigDecimal("58.00"), new BigDecimal("60.00"), new BigDecimal("61.00")));
        assertThat(analysis.cashFlow().get(0).savingsRate()).isNull();
        assertThat(analysis.cashFlow().get(1).savingsRate()).isEqualByComparingTo("0.8390");
        assertThat(analysis.cashFlow().get(1).net()).isEqualByComparingTo("839.00");
    }

    @Test
    void coachableNeedsTheFlagAndTenSpendRows() {
        List<RuledLedger.Categorised> ten = new ArrayList<>();
        for (int i = 1; i <= 10; i++) {
            ten.add(cat(tx(String.valueOf(i), "2026-07-%02d".formatted(i), "bank.csv", BANK, "SHOP " + i, "10.00", SPEND), Category.SHOPPING));
        }

        assertThat(analyse(ten, true).coachable()).isTrue();
        assertThat(analyse(ten, false).coachable()).isFalse();
        assertThat(analyse(ten.subList(0, 9), true).coachable()).isFalse();
    }

    private static SpendingAnalysis analyse(List<RuledLedger.Categorised> rows, boolean coach) {
        Ledger raw = new Ledger(rows.stream().map(RuledLedger.Categorised::tx).toList(), "BRL", List.of(), BigDecimal.ZERO, List.of());
        return Aggregator.analyse(new CategorisedLedger(rows, List.of()), raw, coach);
    }
}
