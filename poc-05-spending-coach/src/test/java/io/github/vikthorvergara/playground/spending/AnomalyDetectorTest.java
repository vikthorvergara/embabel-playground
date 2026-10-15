package io.github.vikthorvergara.playground.spending;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static io.github.vikthorvergara.playground.spending.Rows.cat;
import static io.github.vikthorvergara.playground.spending.Rows.spend;
import static org.assertj.core.api.Assertions.assertThat;

class AnomalyDetectorTest {

    @Test
    void largeForMerchantNeedsThreePriorRows() {
        List<SpendingAnalysis.Anomaly> withThree = AnomalyDetector.detect(List.of(
                cat(spend("1", "2026-07-01", "UBER *TRIP 1", "24.90"), Category.TRANSPORT),
                cat(spend("2", "2026-08-01", "UBER *TRIP 2", "31.20"), Category.TRANSPORT),
                cat(spend("3", "2026-09-01", "UBER *TRIP 3", "27.60"), Category.TRANSPORT),
                cat(spend("4", "2026-09-20", "UBER *TRIP 4", "98.00"), Category.TRANSPORT)));
        List<SpendingAnalysis.Anomaly> withTwo = AnomalyDetector.detect(List.of(
                cat(spend("1", "2026-07-01", "UBER *TRIP 1", "24.90"), Category.TRANSPORT),
                cat(spend("2", "2026-08-01", "UBER *TRIP 2", "31.20"), Category.TRANSPORT),
                cat(spend("4", "2026-09-20", "UBER *TRIP 4", "98.00"), Category.TRANSPORT)));

        assertThat(withThree).singleElement().satisfies(anomaly -> {
            assertThat(anomaly.transactionId()).isEqualTo("4");
            assertThat(anomaly.type()).isEqualTo(SpendingAnalysis.AnomalyType.LARGE_FOR_MERCHANT);
            assertThat(anomaly.amount()).isEqualTo(new BigDecimal("98.00"));
            assertThat(anomaly.detail()).isEqualTo("3.6x the usual 27.60");
        });
        assertThat(withTwo).isEmpty();
    }

    @Test
    void duplicateChargeWithinTwoDays() {
        List<SpendingAnalysis.Anomaly> close = AnomalyDetector.detect(List.of(
                cat(spend("1", "2026-07-12", "CAFE DO CENTRO 0023", "18.50"), Category.DINING),
                cat(spend("2", "2026-07-13", "CAFE DO CENTRO 0023", "18.5"), Category.DINING)));
        List<SpendingAnalysis.Anomaly> apart = AnomalyDetector.detect(List.of(
                cat(spend("1", "2026-07-12", "CAFE DO CENTRO 0023", "18.50"), Category.DINING),
                cat(spend("2", "2026-07-15", "CAFE DO CENTRO 0023", "18.50"), Category.DINING)));

        assertThat(close).singleElement().satisfies(anomaly -> {
            assertThat(anomaly.transactionId()).isEqualTo("2");
            assertThat(anomaly.type()).isEqualTo(SpendingAnalysis.AnomalyType.DUPLICATE_CHARGE);
            assertThat(anomaly.detail()).isEqualTo("same merchant and amount as 1 on 2026-07-12");
        });
        assertThat(apart).isEmpty();
    }

    @Test
    void feeRowsAreFlagged() {
        List<SpendingAnalysis.Anomaly> anomalies = AnomalyDetector.detect(List.of(
                cat(spend("1", "2026-07-15", "TARIFA MANUTENCAO CONTA", "25.90"), Category.FEES_AND_INTEREST),
                cat(spend("2", "2026-07-16", "PADARIA", "5.00"), Category.GROCERIES)));

        assertThat(anomalies).singleElement().satisfies(anomaly -> {
            assertThat(anomaly.type()).isEqualTo(SpendingAnalysis.AnomalyType.FEE_OR_INTEREST);
            assertThat(anomaly.detail()).isEqualTo("TARIFA MANUTENCAO CONTA");
        });
    }
}
