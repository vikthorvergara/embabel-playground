package io.github.vikthorvergara.playground.decisions;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

public record Criterion(
        @JsonPropertyDescription("Short name, e.g. 'Learning curve'")
        String name,
        @JsonPropertyDescription("Importance from 1 (nice to have) to 5 (critical)")
        int weight,
        @JsonPropertyDescription("What a high score means for this criterion")
        String description) {
}
