package io.github.vikthorvergara.playground.prreview;

import java.util.List;

/**
 * The review that came out of the draft/critique loop, plus the critic score of every round.
 * Scores are empty when the critic is disabled.
 */
public record CritiquedReview(ReviewDraft review, List<Double> scores) {

    public int rounds() {
        return Math.max(1, scores.size());
    }
}
