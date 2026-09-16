package io.github.vikthorvergara.playground.prreview;

import java.util.stream.Collectors;

public record ApprovedReview(ReviewDraft review, int rounds, java.util.List<Double> criticScores,
                             boolean autoApproved, RunStats stats) {

    public String toMarkdown() {
        String comments = review.comments().stream()
                .map(c -> "- **%s** `%s%s` %s".formatted(c.severity(), c.file(),
                        c.line() == null ? "" : ":" + c.line(), c.comment()))
                .collect(Collectors.joining("\n"));
        return """
                ## Review

                %s

                %s

                _%d round(s), critic scores %s, %s. %s_""".formatted(
                review.summary(),
                comments.isEmpty() ? "No comments." : comments,
                rounds, criticScores, stats,
                autoApproved ? "Auto-approved." : "Approved by a human.").strip();
    }

    @Override
    public String toString() {
        return toMarkdown();
    }
}
