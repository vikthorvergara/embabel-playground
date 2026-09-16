package io.github.vikthorvergara.playground.prreview;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.util.List;

public record ReviewDraft(
        @JsonPropertyDescription("One or two sentences summarising the change and the overall verdict")
        String summary,
        List<ReviewComment> comments) {
}
