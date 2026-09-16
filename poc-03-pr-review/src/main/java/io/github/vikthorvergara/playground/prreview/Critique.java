package io.github.vikthorvergara.playground.prreview;

import com.embabel.agent.api.common.workflow.loop.Feedback;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.util.List;

public record Critique(
        @JsonPropertyDescription("Overall quality of the review between 0.0 and 1.0")
        double score,
        @JsonPropertyDescription("One verdict per review comment, in the same order as the comments")
        List<Verdict> verdicts,
        @JsonPropertyDescription("What the reviewer should change in the next version: missing issues, wrong claims, noise")
        String feedback) implements Feedback {

    public record Verdict(
            @JsonPropertyDescription("Zero-based index of the comment being judged")
            int index,
            @JsonPropertyDescription("false if the comment is wrong, vague, or not worth the author's time")
            boolean keep,
            String reason) {
    }

    @Override
    public double getScore() {
        return score;
    }

    public long rejectedCount() {
        return verdicts.stream().filter(v -> !v.keep()).count();
    }
}
