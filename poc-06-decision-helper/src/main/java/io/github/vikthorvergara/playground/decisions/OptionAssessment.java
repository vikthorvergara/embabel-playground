package io.github.vikthorvergara.playground.decisions;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.util.List;

public record OptionAssessment(
        String option,
        @JsonPropertyDescription("One score per criterion")
        List<CriterionScore> scores,
        @JsonPropertyDescription("The main risks or downsides of choosing this option")
        List<String> risks) {
}
