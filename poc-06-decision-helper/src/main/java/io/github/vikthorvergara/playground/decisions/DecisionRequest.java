package io.github.vikthorvergara.playground.decisions;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.util.List;

public record DecisionRequest(
        @JsonPropertyDescription("The decision to make, as one question")
        String question,
        @JsonPropertyDescription("The options being compared, as short names")
        List<String> options,
        @JsonPropertyDescription("What matters when choosing, with weights")
        List<Criterion> criteria) {
}
