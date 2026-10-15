package io.github.vikthorvergara.playground.spending;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static io.github.vikthorvergara.playground.spending.Dates.Order.DAY_FIRST;
import static io.github.vikthorvergara.playground.spending.Dates.Order.MONTH_FIRST;
import static org.assertj.core.api.Assertions.assertThat;

class DatesTest {

    private static final LocalDate FIRST_OF_JULY = LocalDate.of(2026, 7, 1);

    @Test
    void parsesEveryPattern() {
        assertThat(Dates.parse("2026-07-01", DAY_FIRST)).isEqualTo(FIRST_OF_JULY);
        assertThat(Dates.parse("2026/07/01", DAY_FIRST)).isEqualTo(FIRST_OF_JULY);
        assertThat(Dates.parse("01/07/2026", DAY_FIRST)).isEqualTo(FIRST_OF_JULY);
        assertThat(Dates.parse("07/01/2026", MONTH_FIRST)).isEqualTo(FIRST_OF_JULY);
        assertThat(Dates.parse("01.07.2026", DAY_FIRST)).isEqualTo(FIRST_OF_JULY);
        assertThat(Dates.parse("01-07-2026", DAY_FIRST)).isEqualTo(FIRST_OF_JULY);
        assertThat(Dates.parse("1 Jul 2026", MONTH_FIRST)).isEqualTo(FIRST_OF_JULY);
        assertThat(Dates.parse("01/Jul/2026", DAY_FIRST)).isEqualTo(FIRST_OF_JULY);
        assertThat(Dates.parse("1 jul. 2026", DAY_FIRST)).isEqualTo(FIRST_OF_JULY);
    }

    @Test
    void monthNamesInEnglishAndPortuguese() {
        assertThat(Dates.parse("5 ago 2026", DAY_FIRST)).isEqualTo(LocalDate.of(2026, 8, 5));
        assertThat(Dates.parse("12 Setembro 2026", DAY_FIRST)).isEqualTo(LocalDate.of(2026, 9, 12));
        assertThat(Dates.parse("1 fev 2026", DAY_FIRST)).isEqualTo(LocalDate.of(2026, 2, 1));
        assertThat(Dates.parse("3 Março 2026", DAY_FIRST)).isEqualTo(LocalDate.of(2026, 3, 3));
        assertThat(Dates.parse("20 October 2026", DAY_FIRST)).isEqualTo(LocalDate.of(2026, 10, 20));
        assertThat(Dates.parse("20 Out 2026", DAY_FIRST)).isEqualTo(LocalDate.of(2026, 10, 20));
        assertThat(Dates.parse("20 Xyz 2026", DAY_FIRST)).isNull();
    }

    @Test
    void timePartIsDropped() {
        assertThat(Dates.parse("2026-07-01 09:12", DAY_FIRST)).isEqualTo(FIRST_OF_JULY);
        assertThat(Dates.parse("2026-07-01T09:12:00", DAY_FIRST)).isEqualTo(FIRST_OF_JULY);
        assertThat(Dates.parse("01/07/2026 09:12:33", DAY_FIRST)).isEqualTo(FIRST_OF_JULY);
        assertThat(Dates.parse("2026-07-01T09:12:00.000Z", DAY_FIRST)).isEqualTo(FIRST_OF_JULY);
    }

    @Test
    void aDayAboveTwelveAnywhereInTheFileSettlesTheOrder() {
        assertThat(Dates.decide(List.of("05/07/2026", "20/07/2026"), MONTH_FIRST)).isEqualTo(new Dates.Decision(DAY_FIRST, false));
        assertThat(Dates.decide(List.of("07/05/2026", "07/20/2026 10:00"), DAY_FIRST)).isEqualTo(new Dates.Decision(MONTH_FIRST, false));
    }

    @Test
    void thePropertyIsOnlyTheTieBreak() {
        assertThat(Dates.decide(List.of("01/07/2026", "02/08/2026"), MONTH_FIRST)).isEqualTo(new Dates.Decision(MONTH_FIRST, true));
        assertThat(Dates.decide(List.of("01/07/2026", "02/08/2026"), DAY_FIRST)).isEqualTo(new Dates.Decision(DAY_FIRST, true));
        assertThat(Dates.decide(List.of("2026-07-01", "1 Jul 2026"), DAY_FIRST)).isEqualTo(new Dates.Decision(DAY_FIRST, false));
    }

    @Test
    void invalidCellsAreNull() {
        assertThat(Dates.parse("31/02/2026", DAY_FIRST)).isNull();
        assertThat(Dates.parse("POSTED", DAY_FIRST)).isNull();
        assertThat(Dates.parse("", DAY_FIRST)).isNull();
        assertThat(Dates.isDate("12,50")).isFalse();
        assertThat(Dates.isDate("13/07/2026")).isTrue();
        assertThat(Dates.isDate("07/13/2026")).isTrue();
    }
}
