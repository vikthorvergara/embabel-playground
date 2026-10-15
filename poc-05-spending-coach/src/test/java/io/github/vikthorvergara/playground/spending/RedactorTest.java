package io.github.vikthorvergara.playground.spending;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RedactorTest {

    @Test
    void amountsBecomeNinesDatesAreKeptAndTextIsTruncatedAndMasked() {
        List<List<String>> sample = Redactor.sample(List.of(
                List.of("05/07/2026", "CPF 123.456.789-09 ANA", "6.500,00", "", "14.308,00"),
                List.of("2026-07-01 09:12", "A VERY LONG DESCRIPTION THAT GOES ON AND ON", "-R$ 1,850.00", "BRL", "POSTED"),
                List.of("01/07/2026", "NIF 123456789", "(12.00)", "D", "")));

        assertThat(sample.get(0)).containsExactly("05/07/2026", "CPF # ANA", "9.999,99", "", "99.999,99");
        assertThat(sample.get(1)).containsExactly("2026-07-01 09:12", "A VERY LONG DESCRIPTION ", "-R$ 9,999.99", "BRL", "POSTED");
        assertThat(sample.get(2)).containsExactly("01/07/2026", "NIF #", "(99.99)", "D", "");
        assertThat(sample.get(1).get(1)).hasSize(Redactor.MAX_CELL);
    }

    @Test
    void sampleOfTheUnknownFixtureCarriesNoIdentifier() {
        CsvTable table = Fixtures.table("unknown-bank.csv");
        String prompt = Prompts.columnMapping(table.header(), Redactor.sample(table.rows(3)));

        assertThat(Pii.found(prompt)).isFalse();
        assertThat(prompt).contains("Header: 0=Dt. Mov. | 1=Lançamento Hist. | 2=Vlr. | 3=D/C").contains("9.999,99").doesNotContain("5.200,00");
    }
}
