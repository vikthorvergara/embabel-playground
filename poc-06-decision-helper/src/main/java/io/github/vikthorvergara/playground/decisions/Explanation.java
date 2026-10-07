package io.github.vikthorvergara.playground.decisions;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

public record Explanation(
        @JsonPropertyDescription("Why the top-ranked option is the recommendation, in two or three sentences")
        String rationale,
        @JsonPropertyDescription("In which situation the runner-up would be the better choice")
        String whenToPickRunnerUp) {
}
