package io.github.vikthorvergara.playground.spending;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static io.github.vikthorvergara.playground.spending.Layout.Kind.BANK;
import static io.github.vikthorvergara.playground.spending.Layout.Kind.CARD;
import static io.github.vikthorvergara.playground.spending.Rows.tx;
import static io.github.vikthorvergara.playground.spending.Transaction.Flow.INCOME;
import static io.github.vikthorvergara.playground.spending.Transaction.Flow.SPEND;
import static io.github.vikthorvergara.playground.spending.Transaction.Flow.TRANSFER;
import static org.assertj.core.api.Assertions.assertThat;

class TransferMatcherTest {

    @Test
    void keywordRowsBecomeTransfersAndUnpairedDebitsAreSummed() {
        TransferMatcher.Result result = TransferMatcher.mark(List.of(
                tx("1", "2026-07-12", "bank.csv", BANK, "PIX JOAO PEREIRA", "120.00", SPEND),
                tx("2", "2026-07-13", "bank.csv", BANK, "TED PARA MARIA", "80.00", SPEND),
                tx("3", "2026-07-14", "bank.csv", BANK, "PIX RECEBIDO ANA", "50.00", INCOME),
                tx("4", "2026-07-15", "bank.csv", BANK, "PIXAR STUDIOS", "30.00", SPEND)), Fixtures.KEYWORDS);

        assertThat(result.rows()).extracting(Transaction::flow).containsExactly(TRANSFER, TRANSFER, TRANSFER, SPEND);
        assertThat(result.rows().get(0).flags()).containsExactly(Transaction.TRANSFER_OUT);
        assertThat(result.rows().get(2).flags()).containsExactly(Transaction.TRANSFER_IN);
        assertThat(result.unmatchedTransfers()).isEqualTo(new BigDecimal("200.00"));
    }

    @Test
    void checkingToSavingsPairIsPairedAcrossFiles() {
        TransferMatcher.Result result = TransferMatcher.mark(List.of(
                tx("1", "2026-08-10", "bank.csv", BANK, "TRANSF POUPANCA", "500.00", SPEND),
                tx("2", "2026-08-10", "savings.csv", BANK, "TRANSF DE CONTA CORRENTE", "500.00", INCOME)), Fixtures.KEYWORDS);

        assertThat(result.rows().get(0).flow()).isEqualTo(TRANSFER);
        assertThat(result.rows().get(0).flags()).containsExactly(Transaction.TRANSFER_OUT, Transaction.TRANSFER_PAIR + "2");
        assertThat(result.rows().get(1).flags()).containsExactly(Transaction.TRANSFER_IN, Transaction.TRANSFER_PAIR + "1");
        assertThat(result.unmatchedTransfers()).isEqualByComparingTo("0");
    }

    @Test
    void cardPaymentPairsWithinThreeDaysOnly() {
        TransferMatcher.Result near = TransferMatcher.mark(List.of(
                tx("1", "2026-07-06", "bank.csv", BANK, "DEBITO CARTAO VISA", "1850.00", SPEND),
                tx("2", "2026-07-09", "card.csv", CARD, "THANK YOU", "1850.00", INCOME)), List.of());
        TransferMatcher.Result far = TransferMatcher.mark(List.of(
                tx("1", "2026-07-06", "bank.csv", BANK, "DEBITO CARTAO VISA", "1850.00", SPEND),
                tx("2", "2026-07-10", "card.csv", CARD, "THANK YOU", "1850.00", INCOME)), List.of());

        assertThat(near.rows()).allMatch(tx -> tx.flow() == TRANSFER);
        assertThat(far.rows()).extracting(Transaction::flow).containsExactly(SPEND, INCOME);
    }

    @Test
    void neverPairsOneCreditTwiceAndNeverPairsInsideOneFile() {
        TransferMatcher.Result result = TransferMatcher.mark(List.of(
                tx("1", "2026-07-01", "bank.csv", BANK, "DEBIT A", "500.00", SPEND),
                tx("2", "2026-07-02", "bank.csv", BANK, "DEBIT B", "500.00", SPEND),
                tx("3", "2026-07-01", "savings.csv", BANK, "CREDIT", "500.00", INCOME),
                tx("4", "2026-07-01", "bank.csv", BANK, "SAME FILE CREDIT", "500.00", INCOME)), List.of());

        assertThat(result.rows().get(0).linked(Transaction.TRANSFER_PAIR)).isEqualTo("3");
        assertThat(result.rows().get(1).flow()).isEqualTo(SPEND);
        assertThat(result.rows().get(3).flow()).isEqualTo(INCOME);
    }
}
