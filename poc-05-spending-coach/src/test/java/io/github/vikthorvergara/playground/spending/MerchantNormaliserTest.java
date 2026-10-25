package io.github.vikthorvergara.playground.spending;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MerchantNormaliserTest {

    @Test
    void stripsCardSuffixesPosPrefixesStoreNumbersAndDigitRuns() {
        assertThat(MerchantNormaliser.normalise("UBER *TRIP 48213")).isEqualTo("UBER TRIP");
        assertThat(MerchantNormaliser.normalise("NETFLIX.COM")).isEqualTo("NETFLIX COM");
        assertThat(MerchantNormaliser.normalise("AMAZON MKTPLACE 7721")).isEqualTo("AMAZON MKTPLACE");
        assertThat(MerchantNormaliser.normalise("COMPRA CARTAO PADARIA ESTRELA")).isEqualTo("PADARIA ESTRELA");
        assertThat(MerchantNormaliser.normalise("POS CARREFOUR LOJA 12 SP")).isEqualTo("CARREFOUR SP");
        assertThat(MerchantNormaliser.normalise("Supermercado Bom Preço #0042")).isEqualTo("SUPERMERCADO BOM PRECO");
        assertThat(MerchantNormaliser.normalise("PAYMENT RECEIVED - THANK YOU")).isEqualTo("PAYMENT RECEIVED THANK YOU");
        assertThat(MerchantNormaliser.normalise("TARIFA MANUTENÇÃO CONTA")).isEqualTo("TARIFA MANUTENCAO CONTA");
        assertThat(MerchantNormaliser.normalise("")).isEmpty();
        assertThat(MerchantNormaliser.normalise(null)).isEmpty();
    }

    @Test
    void capsTheLength() {
        assertThat(MerchantNormaliser.normalise("A".repeat(60))).hasSize(MerchantNormaliser.MAX_LENGTH);
    }
}
