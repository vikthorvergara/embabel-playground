package io.github.vikthorvergara.playground.spending;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PiiTest {

    @Test
    void findsIbanCpfCardNumbersAndEmails() {
        assertThat(Pii.found("IBAN: BR1800360305000010009795493C1")).isTrue();
        assertThat(Pii.found("CPF 123.456.789-09")).isTrue();
        assertThat(Pii.found("card 4111 1111 1111 1111")).isTrue();
        assertThat(Pii.found("contact maria@mail.example")).isTrue();
        assertThat(Pii.found("NIF 123456789")).isTrue();
        assertThat(Pii.found("phone +55 11 91234-5678")).isTrue();
    }

    @Test
    void datesAndMerchantTextPass() {
        assertThat(Pii.found("2026-09-12 NETFLIX COM 39.90")).isFalse();
        assertThat(Pii.found("m1: UBER TRIP (typical 24.90, seen 4 times)")).isFalse();
        assertThat(Pii.found("Row: 05/07/2026 | SALARIO EMPRESA | 9.999,99 | | 99.999,99")).isFalse();
        assertThat(Pii.found("Header: 0=Data | 1=Historico")).isFalse();
    }

    @Test
    void maskReplacesIdentifiersOnly() {
        assertThat(Pii.mask("PIX JOAO CPF 123.456.789-09")).isEqualTo("PIX JOAO CPF #");
        assertThat(Pii.mask("BR1800360305000010009795493C1 TED")).isEqualTo("# TED");
        assertThat(Pii.mask("ana@mail.example paid")).isEqualTo("# paid");
        assertThat(Pii.mask("LOJA 12 SP")).isEqualTo("LOJA 12 SP");
    }
}
