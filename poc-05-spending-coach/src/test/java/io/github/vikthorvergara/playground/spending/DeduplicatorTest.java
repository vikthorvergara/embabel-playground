package io.github.vikthorvergara.playground.spending;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static io.github.vikthorvergara.playground.spending.Layout.Kind.CARD;
import static io.github.vikthorvergara.playground.spending.Rows.tx;
import static io.github.vikthorvergara.playground.spending.Transaction.Flow.SPEND;
import static org.assertj.core.api.Assertions.assertThat;

class DeduplicatorTest {

    @Test
    void overlapDropsOnlyTheSurplusCopies() {
        List<String> warnings = new ArrayList<>();
        List<Transaction> rows = List.of(
                tx("a1", "2026-07-12", "card.csv", CARD, "CAFE 0023", "18.50", SPEND),
                tx("a2", "2026-07-13", "card.csv", CARD, "CAFE 0023", "18.50", SPEND),
                tx("a3", "2026-08-20", "card.csv", CARD, "SUPER 12", "412.80", SPEND),
                tx("b1", "2026-08-20", "card.csv", CARD, "SUPER 12", "412.80", SPEND),
                tx("b2", "2026-09-01", "card.csv", CARD, "UBER", "27.60", SPEND));

        List<Transaction> kept = Deduplicator.dedupe(withFiles(rows, "a", "card-1.csv", "card-2.csv"), warnings);

        assertThat(kept).extracting(Transaction::id).containsExactly("a1", "a2", "a3", "b2");
        assertThat(warnings).containsExactly("1 duplicate row(s) dropped from overlapping exports");
    }

    @Test
    void twoIdenticalRowsInOneFileSurvive() {
        List<Transaction> rows = List.of(
                tx("a1", "2026-07-12", "card.csv", CARD, "CAFE", "18.50", SPEND),
                tx("a2", "2026-07-12", "card.csv", CARD, "CAFE", "18.50", SPEND));

        assertThat(Deduplicator.dedupe(rows, new ArrayList<>())).hasSize(2);
    }

    @Test
    void multiplicityIsKeptAcrossFiles() {
        List<Transaction> rows = List.of(
                tx("a1", "2026-07-12", "card.csv", CARD, "CAFE", "18.50", SPEND),
                tx("a2", "2026-07-12", "card.csv", CARD, "CAFE", "18.50", SPEND),
                tx("b1", "2026-07-12", "card.csv", CARD, "CAFE", "18.50", SPEND),
                tx("b2", "2026-07-12", "card.csv", CARD, "CAFE", "18.50", SPEND),
                tx("b3", "2026-07-12", "card.csv", CARD, "CAFE", "18.50", SPEND));

        assertThat(Deduplicator.dedupe(withFiles(rows, "a", "card-1.csv", "card-2.csv"), new ArrayList<>()))
                .extracting(Transaction::id).containsExactly("a1", "a2", "b1");
    }

    private static List<Transaction> withFiles(List<Transaction> rows, String firstPrefix, String firstFile, String secondFile) {
        return rows.stream().map(tx -> new Transaction(tx.id(), tx.date(), tx.account(),
                tx.id().startsWith(firstPrefix) ? firstFile : secondFile, tx.kind(), tx.rawDescription(), tx.merchant(), tx.amount(),
                tx.currency(), tx.flow(), tx.flags())).toList();
    }
}
