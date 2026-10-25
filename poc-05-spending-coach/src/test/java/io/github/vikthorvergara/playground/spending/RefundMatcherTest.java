package io.github.vikthorvergara.playground.spending;

import org.junit.jupiter.api.Test;

import java.util.List;

import static io.github.vikthorvergara.playground.spending.Layout.Kind.BANK;
import static io.github.vikthorvergara.playground.spending.Layout.Kind.CARD;
import static io.github.vikthorvergara.playground.spending.Rows.tx;
import static io.github.vikthorvergara.playground.spending.Transaction.Flow.CREDIT;
import static io.github.vikthorvergara.playground.spending.Transaction.Flow.INCOME;
import static io.github.vikthorvergara.playground.spending.Transaction.Flow.REFUND;
import static io.github.vikthorvergara.playground.spending.Transaction.Flow.SPEND;
import static org.assertj.core.api.Assertions.assertThat;

class RefundMatcherTest {

    @Test
    void partialRefundIsMatchedToTheLatestDebitOfTheSameMerchant() {
        List<Transaction> rows = RefundMatcher.match(List.of(
                tx("c1", "2026-07-10", "card.csv", CARD, "AMAZON MKTPLACE 7721", "189.00", SPEND),
                tx("c2", "2026-08-02", "card.csv", CARD, "AMAZON MKTPLACE 7721", "89.00", INCOME)));

        assertThat(rows.get(1).flow()).isEqualTo(REFUND);
        assertThat(rows.get(1).linked(Transaction.REFUND_OF)).isEqualTo("c1");
        assertThat(rows.get(0).flow()).isEqualTo(SPEND);
    }

    @Test
    void bankSideReversalIsARefundNotIncome() {
        List<Transaction> rows = RefundMatcher.match(List.of(
                tx("b1", "2026-07-01", "bank.csv", BANK, "LOJA X", "50.00", SPEND),
                tx("b2", "2026-07-05", "bank.csv", BANK, "LOJA X", "50.00", INCOME),
                tx("b3", "2026-07-05", "bank.csv", BANK, "SALARIO EMPRESA", "6500.00", INCOME)));

        assertThat(rows.get(1).flow()).isEqualTo(REFUND);
        assertThat(rows.get(2).flow()).isEqualTo(INCOME);
    }

    @Test
    void unmatchedCardCreditIsCredit() {
        List<Transaction> rows = RefundMatcher.match(List.of(
                tx("c1", "2026-05-01", "card.csv", CARD, "LOJA Y", "40.00", SPEND),
                tx("c2", "2026-07-15", "card.csv", CARD, "LOJA Y", "40.00", INCOME),
                tx("c3", "2026-07-16", "card.csv", CARD, "CASHBACK", "10.00", INCOME),
                tx("c4", "2026-07-16", "card.csv", CARD, "LOJA Z", "5.00", INCOME),
                tx("c5", "2026-07-10", "card.csv", CARD, "LOJA Z", "3.00", SPEND)));

        assertThat(rows).extracting(Transaction::flow).containsExactly(SPEND, CREDIT, CREDIT, CREDIT, SPEND);
    }

    @Test
    void refundInAnotherAccountIsNotMatched() {
        List<Transaction> rows = RefundMatcher.match(List.of(
                tx("c1", "2026-07-10", "card.csv", CARD, "LOJA X", "50.00", SPEND),
                tx("b1", "2026-07-12", "bank.csv", BANK, "LOJA X", "50.00", INCOME)));

        assertThat(rows.get(1).flow()).isEqualTo(INCOME);
    }
}
