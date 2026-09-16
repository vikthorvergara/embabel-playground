package io.github.vikthorvergara.playground.prreview;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

public record ReviewRequest(
        @JsonPropertyDescription("Unified diff to review, as produced by `git diff`")
        String diff,
        @JsonPropertyDescription("Skip the human confirmation step. Meant for non-interactive callers.")
        boolean autoApprove) {
}
