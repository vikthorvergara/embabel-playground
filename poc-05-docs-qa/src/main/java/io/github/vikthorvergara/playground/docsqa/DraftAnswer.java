package io.github.vikthorvergara.playground.docsqa;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.util.List;

public record DraftAnswer(
        @JsonPropertyDescription("The answer, based only on the documentation")
        String answer,
        @JsonPropertyDescription("Passages that support the answer. Empty if the docs do not cover the question.")
        List<Citation> citations) {
}
