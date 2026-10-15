package io.github.vikthorvergara.playground.spending;

import com.embabel.common.core.validation.ValidationResult;
import com.embabel.common.core.validation.ValidationSeverity;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PiiGuardRailTest {

    private final PiiGuardRail guardRail = new PiiGuardRail();

    @Test
    void identifiersAreCritical() {
        for (String prompt : new String[]{
                "transfer to BR1800360305000010009795493C1",
                "CPF 123.456.789-09",
                "card 4111111111111111",
                "mail ana@mail.invalid"}) {
            ValidationResult result = guardRail.validate(prompt, null);
            assertThat(result.isValid()).as(prompt).isFalse();
            assertThat(result.getErrors()).singleElement().satisfies(error -> {
                assertThat(error.getSeverity()).isEqualTo(ValidationSeverity.CRITICAL);
                assertThat(error.getCode()).isEqualTo("pii");
            });
        }
    }

    @Test
    void datesAndMerchantsPass() {
        ValidationResult result = guardRail.validate("Months: 2026-07, 2026-08. m1: NETFLIX COM (typical 39.90, seen 3 times)", null);

        assertThat(result.isValid()).isTrue();
        assertThat(result.getErrors()).isEmpty();
        assertThat(guardRail.getName()).isEqualTo("pii");
    }
}
