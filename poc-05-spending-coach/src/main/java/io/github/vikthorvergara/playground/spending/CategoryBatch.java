package io.github.vikthorvergara.playground.spending;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;

public record CategoryBatch(
        @JsonPropertyDescription("One entry per merchant id given in the prompt")
        List<MerchantCategory> merchants) {

    public record MerchantCategory(
            @JsonPropertyDescription("The numeric id after the m in the prompt, such as 3 for m3")
            int merchantId,
            @JsonPropertyDescription("The category that fits the merchant best")
            @NotNull Category category,
            @JsonPropertyDescription("How sure you are, between 0 and 1")
            @DecimalMin("0") @DecimalMax("1") BigDecimal confidence,
            @JsonPropertyDescription("A few words on why")
            String reason) {

        @Override
        public String toString() {
            return "m" + merchantId + " " + category + " " + confidence;
        }
    }

    public CategoryBatch {
        merchants = merchants == null ? List.of() : List.copyOf(merchants);
    }

    @Override
    public String toString() {
        return "CategoryBatch[" + merchants.size() + " merchants]";
    }
}
