package io.github.vikthorvergara.playground.prreview;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

public record ReviewComment(
        @JsonPropertyDescription("Path of the file, as shown in the diff")
        String file,
        @JsonPropertyDescription("Line number in the new version of the file, or null for a file-level comment")
        Integer line,
        Severity severity,
        @JsonPropertyDescription("The review comment. Specific, actionable, one issue per comment.")
        String comment) {

    public enum Severity {
        BLOCKER,
        SUGGESTION,
        NIT
    }
}
