package io.github.vikthorvergara.playground.decisions;

/**
 * @param weightedScore weighted average of the criterion scores, between 1 and 5
 */
public record RankedOption(int rank, String option, double weightedScore, OptionAssessment assessment) {
}
