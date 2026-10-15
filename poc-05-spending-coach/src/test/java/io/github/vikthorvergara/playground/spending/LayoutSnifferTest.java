package io.github.vikthorvergara.playground.spending;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class LayoutSnifferTest {

    private final LayoutSniffer sniffer = new LayoutSniffer(List.of(Fixtures.GENERIC_SIGNED), Map.of());

    @Test
    void bankFixtureResolvesThroughTheSynonymTable() {
        Layout layout = sniff("bank-q3.csv").orElseThrow();

        assertThat(layout.kind()).isEqualTo(Layout.Kind.BANK);
        assertThat(layout.delimiter()).isEqualTo(';');
        assertThat(layout.dateColumn()).isZero();
        assertThat(layout.descriptionColumn()).isEqualTo(1);
        assertThat(layout.debitColumn()).isEqualTo(2);
        assertThat(layout.creditColumn()).isEqualTo(3);
        assertThat(layout.amountColumn()).isNull();
        assertThat(layout.amounts()).isEqualTo(Layout.AmountFormat.DECIMAL_COMMA);
        assertThat(sniff("savings-q3.csv")).isPresent();
    }

    @Test
    void cardFixtureResolvesThroughTheSynonymTable() {
        Layout layout = sniff("card-q3.csv").orElseThrow();

        assertThat(layout.kind()).isEqualTo(Layout.Kind.CARD);
        assertThat(layout.delimiter()).isEqualTo(',');
        assertThat(layout.dateColumn()).isZero();
        assertThat(layout.descriptionColumn()).isEqualTo(1);
        assertThat(layout.amountColumn()).isEqualTo(2);
        assertThat(layout.currencyColumn()).isEqualTo(3);
        assertThat(layout.statusColumn()).isEqualTo(4);
        assertThat(layout.skipStatuses()).contains("PENDING");
        assertThat(layout.positiveIsSpend()).isTrue();
        assertThat(layout.amounts()).isEqualTo(Layout.AmountFormat.DECIMAL_DOT);
        assertThat(sniff("card-q3-overlap.csv")).isPresent();
    }

    @Test
    void unknownHeaderIsNotRecognised() {
        assertThat(sniff("unknown-bank.csv")).isEmpty();
    }

    @Test
    void portugueseHeadersResolve() {
        Layout simple = LayoutSniffer.fromSynonyms("extrato.csv", CsvTable.parse("Data;Lançamento;Valor\n01/07/2026;X;12,50\n")).orElseThrow();
        assertThat(simple.dateColumn()).isZero();
        assertThat(simple.descriptionColumn()).isEqualTo(1);
        assertThat(simple.amountColumn()).isEqualTo(2);
        assertThat(simple.kind()).isEqualTo(Layout.Kind.BANK);

        Layout card = LayoutSniffer.fromSynonyms("x.csv", CsvTable.parse(
                "Data Compra;Data Lançamento;Estabelecimento;Valor;Moeda;Situação\n01/07/2026;03/07/2026;LOJA;12,50;BRL;OK\n")).orElseThrow();
        assertThat(card.dateColumn()).isZero();
        assertThat(card.postingDateColumn()).isEqualTo(1);
        assertThat(card.descriptionColumn()).isEqualTo(2);
        assertThat(card.amountColumn()).isEqualTo(3);
        assertThat(card.currencyColumn()).isEqualTo(4);
        assertThat(card.statusColumn()).isEqualTo(5);
        assertThat(card.kind()).isEqualTo(Layout.Kind.CARD);
    }

    @Test
    void configuredLayoutMatchesASignedFileNoSynonymKnows() {
        CsvTable table = CsvTable.parse("when,what,how_much\n2026-07-01,SHOP,-12.50\n2026-07-02,SALARY,100.00\n");

        Layout layout = sniffer.sniff("other.csv", table, table.fingerprint()).orElseThrow();

        assertThat(layout.name()).isEqualTo("other.csv");
        assertThat(layout.kind()).isEqualTo(Layout.Kind.BANK);
        assertThat(layout.amountColumn()).isEqualTo(2);
        assertThat(layout.positiveIsSpend()).isFalse();
    }

    @Test
    void learnedLayoutIsFoundByFingerprint() {
        CsvTable table = Fixtures.table("unknown-bank.csv");
        Layout learnt = new Layout("learned-x", Layout.Kind.BANK, ';', 0, "dd/MM/yyyy", null, 1, 2, null, null, 3, "D", null,
                List.of(), null, false, Layout.AmountFormat.DECIMAL_COMMA);

        Optional<Layout> found = new LayoutSniffer(List.of(), Map.of(table.fingerprint(), learnt)).sniff("unknown-bank.csv", table, table.fingerprint());

        assertThat(found).isPresent();
        assertThat(found.get().name()).isEqualTo("unknown-bank.csv");
        assertThat(found.get().markerColumn()).isEqualTo(3);
        assertThat(new LayoutSniffer(List.of(), Map.of("other", learnt)).sniff("unknown-bank.csv", table, table.fingerprint())).isEmpty();
    }

    @Test
    void notAStatementCheckRejectsNamesAndAcceptsEveryStatementFixture() {
        assertThat(LayoutSniffer.looksLikeStatement(Fixtures.table("not-a-statement.csv"))).isFalse();
        for (String name : List.of("bank-q3.csv", "savings-q3.csv", "card-q3.csv", "card-q3-overlap.csv", "unknown-bank.csv")) {
            assertThat(LayoutSniffer.looksLikeStatement(Fixtures.table(name))).as(name).isTrue();
        }
    }

    private Optional<Layout> sniff(String name) {
        CsvTable table = Fixtures.table(name);
        return sniffer.sniff(name, table, table.fingerprint());
    }
}
