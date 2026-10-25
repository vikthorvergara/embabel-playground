package io.github.vikthorvergara.playground.spending;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class StatementParserTest {

    private static final LayoutSniffer SNIFFER = new LayoutSniffer(List.of(), Map.of());

    @Test
    void bothSignConventionsNormaliseToPositiveAmountsWithAFlow() {
        StatementParser.Parsed bank = parse("bank-q3.csv");
        Transaction salary = bank.rows().stream().filter(tx -> tx.rawDescription().startsWith("SALARIO")).findFirst().orElseThrow();
        Transaction rent = bank.rows().stream().filter(tx -> tx.rawDescription().startsWith("ALUGUEL")).findFirst().orElseThrow();
        assertThat(salary.flow()).isEqualTo(Transaction.Flow.INCOME);
        assertThat(salary.amount()).isEqualTo(new BigDecimal("6500.00"));
        assertThat(rent.flow()).isEqualTo(Transaction.Flow.SPEND);
        assertThat(rent.amount()).isEqualTo(new BigDecimal("2100.00"));

        StatementParser.Parsed card = parse("card-q3.csv");
        Transaction payment = card.rows().stream().filter(tx -> tx.rawDescription().startsWith("PAYMENT")).findFirst().orElseThrow();
        Transaction uber = card.rows().stream().filter(tx -> tx.rawDescription().startsWith("UBER")).findFirst().orElseThrow();
        assertThat(payment.flow()).isEqualTo(Transaction.Flow.INCOME);
        assertThat(payment.amount()).isEqualTo(new BigDecimal("1850.00"));
        assertThat(uber.flow()).isEqualTo(Transaction.Flow.SPEND);
        assertThat(uber.merchant()).isEqualTo("UBER TRIP");
        assertThat(uber.date()).isEqualTo(LocalDate.of(2026, 7, 1));
    }

    @Test
    void pendingRowsAreSkippedAndCounted() {
        StatementParser.Parsed card = parse("card-q3.csv");

        assertThat(card.pending()).isEqualTo(2);
        assertThat(card.rows()).noneMatch(tx -> tx.date().isAfter(LocalDate.of(2026, 9, 28)));
        assertThat(card.rows()).hasSize(28);
    }

    @Test
    void postingDateIsUsedWhenConfiguredAndEmptyPostingDatesArePending() {
        String text = "Data Compra;Data Lançamento;Estabelecimento;Valor\n01/07/2026;03/07/2026;LOJA UM;12,50\n02/07/2026;;LOJA DOIS;5,00\n";
        RawStatements.RawStatement file = new RawStatements.RawStatement("card.csv", text, "inline", "ff00ff00");
        Layout layout = LayoutSniffer.fromSynonyms("card.csv", CsvTable.parse(text)).orElseThrow();

        StatementParser.Parsed byTransaction = StatementParser.parse(1, file, layout, Dates.Order.DAY_FIRST, SpendingProperties.MonthBy.TRANSACTION_DATE);
        StatementParser.Parsed byPosting = StatementParser.parse(1, file, layout, Dates.Order.DAY_FIRST, SpendingProperties.MonthBy.POSTING_DATE);

        assertThat(byTransaction.rows()).singleElement().extracting(Transaction::date).isEqualTo(LocalDate.of(2026, 7, 1));
        assertThat(byPosting.rows()).singleElement().extracting(Transaction::date).isEqualTo(LocalDate.of(2026, 7, 3));
        assertThat(byPosting.pending()).isEqualTo(1);
    }

    @Test
    void currencyColumnIsKeptPerRow() {
        StatementParser.Parsed card = parse("card-q3.csv");

        assertThat(card.rows()).filteredOn(tx -> tx.rawDescription().startsWith("STEAM")).singleElement()
                .extracting(Transaction::currency).isEqualTo("USD");
        assertThat(card.rows()).filteredOn(tx -> !tx.rawDescription().startsWith("STEAM")).allMatch(tx -> "BRL".equals(tx.currency()));
        assertThat(parse("bank-q3.csv").rows()).allMatch(tx -> tx.currency() == null);
    }

    @Test
    void idsAccountsAndSourceFilesAreSet() {
        RawStatements.RawStatement file = Fixtures.raw("bank-q3.csv");
        Transaction first = parse("bank-q3.csv").rows().getFirst();

        assertThat(first.id()).isEqualTo("1-5");
        assertThat(first.account()).isEqualTo("BANK-" + file.fingerprint());
        assertThat(first.sourceFile()).isEqualTo("bank-q3.csv");
        assertThat(first.flags()).isEmpty();
    }

    @Test
    void unsettledDateOrderIsFlaggedOnEveryRow() {
        RawStatements.RawStatement file = Fixtures.raw("unknown-bank.csv");
        Layout layout = new Layout("unknown-bank.csv", Layout.Kind.BANK, ';', 0, null, null, 1, 2, null, null, 3, "D", null,
                List.of(), null, false, Layout.AmountFormat.DECIMAL_COMMA);

        StatementParser.Parsed parsed = StatementParser.parse(1, file, layout, Dates.Order.MONTH_FIRST, SpendingProperties.MonthBy.TRANSACTION_DATE);

        assertThat(parsed.dateOrderAssumed()).isTrue();
        assertThat(parsed.rows()).hasSize(12).allMatch(tx -> tx.flags().contains(Transaction.DATE_ORDER_ASSUMED));
        assertThat(parsed.rows().getFirst().date()).isEqualTo(LocalDate.of(2026, 1, 7));
        assertThat(parsed.rows().getFirst().flow()).isEqualTo(Transaction.Flow.INCOME);
        assertThat(parsed.rows().get(1).merchant()).isEqualTo("PADARIA ESTRELA");
    }

    @Test
    void verificationNeedsEveryRowToParse() {
        CsvTable table = Fixtures.table("card-q3.csv");
        Layout layout = LayoutSniffer.fromSynonyms("card-q3.csv", table).orElseThrow();
        Layout wrong = new Layout("x", Layout.Kind.CARD, ',', 1, null, null, 0, 2, null, null, null, null, null, List.of(), null, true, null);

        assertThat(StatementParser.parses(table.rows(5), layout)).isTrue();
        assertThat(StatementParser.parses(table.rows(5), wrong)).isFalse();
        assertThat(StatementParser.parses(List.of(), layout)).isFalse();
    }

    private static StatementParser.Parsed parse(String name) {
        RawStatements.RawStatement file = Fixtures.raw(name);
        CsvTable table = CsvTable.parse(file.text());
        Layout layout = SNIFFER.sniff(name, table, file.fingerprint()).orElseThrow();
        return StatementParser.parse(1, file, layout, Dates.Order.DAY_FIRST, SpendingProperties.MonthBy.TRANSACTION_DATE);
    }
}
