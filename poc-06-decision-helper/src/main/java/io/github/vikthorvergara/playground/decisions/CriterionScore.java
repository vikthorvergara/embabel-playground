package io.github.vikthorvergara.playground.decisions;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

public record CriterionScore(
        @JsonPropertyDescription("Criterion name, exactly as given")
        String criterion,
        @JsonPropertyDescription("Score from 1 (poor) to 5 (excellent)")
        int score,
        @JsonPropertyDescription("One sentence explaining the score")
        String rationale) {
}
