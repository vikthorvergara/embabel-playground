package io.github.vikthorvergara.playground.spending;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static io.github.vikthorvergara.playground.spending.Layout.AmountFormat.DECIMAL_COMMA;
import static io.github.vikthorvergara.playground.spending.Layout.AmountFormat.DECIMAL_DOT;
import static org.assertj.core.api.Assertions.assertThat;

class AmountsTest {

    @Test
    void decimalSeparatorIsDecidedByTheColumnNotTheCell() {
        assertThat(Amounts.formatOf(List.of("1.234,56", "12,00", "1,234"))).isEqualTo(DECIMAL_COMMA);
        assertThat(Amounts.formatOf(List.of("1,850.00", "24.90", "1.234"))).isEqualTo(DECIMAL_DOT);
        assertThat(Amounts.formatOf(List.of("12", "1234"))).isEqualTo(DECIMAL_DOT);
        assertThat(Amounts.parse("1,234", DECIMAL_COMMA)).isEqualByComparingTo("1.23");
        assertThat(Amounts.parse("1,234", DECIMAL_DOT)).isEqualByComparingTo("1234.00");
    }

    @Test
    void thousandsSeparatorsAndCurrencySymbolsAreStripped() {
        assertThat(Amounts.parse("R$ 1.234,56", DECIMAL_COMMA)).isEqualTo(new BigDecimal("1234.56"));
        assertThat(Amounts.parse("\"R$ 1,850.00\"".replace("\"", ""), DECIMAL_DOT)).isEqualTo(new BigDecimal("1850.00"));
        assertThat(Amounts.parse("US$ 14.99", DECIMAL_DOT)).isEqualTo(new BigDecimal("14.99"));
        assertThat(Amounts.parse("€ 12,5", DECIMAL_COMMA)).isEqualTo(new BigDecimal("12.50"));
        assertThat(Amounts.parse("£3.50", DECIMAL_DOT)).isEqualTo(new BigDecimal("3.50"));
        assertThat(Amounts.parse("BRL 10", DECIMAL_DOT)).isEqualTo(new BigDecimal("10.00"));
        assertThat(Amounts.parse("12 345,67", DECIMAL_COMMA)).isEqualTo(new BigDecimal("12345.67"));
    }

    @Test
    void trailingMinusParenthesesAndLeadingMinusAreNegative() {
        assertThat(Amounts.parse("12.00-", DECIMAL_DOT)).isEqualTo(new BigDecimal("-12.00"));
        assertThat(Amounts.parse("(12.00)", DECIMAL_DOT)).isEqualTo(new BigDecimal("-12.00"));
        assertThat(Amounts.parse("-R$ 1,850.00", DECIMAL_DOT)).isEqualTo(new BigDecimal("-1850.00"));
        assertThat(Amounts.parse("R$ -5,00", DECIMAL_COMMA)).isEqualTo(new BigDecimal("-5.00"));
        assertThat(Amounts.parse("+7.10", DECIMAL_DOT)).isEqualTo(new BigDecimal("7.10"));
    }

    @Test
    void textAndDatesAreNotAmounts() {
        assertThat(Amounts.parse("POSTED", DECIMAL_DOT)).isNull();
        assertThat(Amounts.parse("D", DECIMAL_DOT)).isNull();
        assertThat(Amounts.parse("", DECIMAL_DOT)).isNull();
        assertThat(Amounts.parse("1.2.3,4", DECIMAL_DOT)).isNull();
        assertThat(Amounts.isAmount("2026-07-01")).isFalse();
        assertThat(Amounts.isAmount("05/07/2026")).isFalse();
        assertThat(Amounts.isAmount("Ana")).isFalse();
        assertThat(Amounts.isAmount("R$ 24.90")).isTrue();
    }

    @Test
    void markerColumnDecidesTheDirection() {
        assertThat(Amounts.isDebitMarker("D", "D")).isTrue();
        assertThat(Amounts.isDebitMarker("DEB", "D")).isTrue();
        assertThat(Amounts.isDebitMarker(" deb ", "DEB")).isTrue();
        assertThat(Amounts.isDebitMarker("C", "D")).isFalse();
        assertThat(Amounts.isDebitMarker("CRED", "DEB")).isFalse();
        assertThat(Amounts.isDebitMarker("D", null)).isFalse();
    }

    @Test
    void medianUsesScaleTwo() {
        assertThat(Amounts.median(List.of(new BigDecimal("3"), new BigDecimal("1"), new BigDecimal("2")))).isEqualTo(new BigDecimal("2.00"));
        assertThat(Amounts.median(List.of(new BigDecimal("1"), new BigDecimal("2"), new BigDecimal("3"), new BigDecimal("4")))).isEqualTo(new BigDecimal("2.50"));
        assertThat(Amounts.median(List.of())).isEqualTo(new BigDecimal("0.00"));
    }
}
