package io.github.vikthorvergara.playground.prreview;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

/**
 * Entry point for callers that cannot answer a confirmation prompt (MCP, REST).
 * Always auto-approves, so the run never waits for a human.
 */
public record AutomatedReviewRequest(
        @JsonPropertyDescription("Unified diff to review, as produced by `git diff`")
        String diff) {
}
