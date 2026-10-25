package io.github.vikthorvergara.playground.spending;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.math.BigDecimal;
import java.util.List;
import java.util.NavigableSet;

public record CoachingNotes(
        @JsonPropertyDescription("Three or four plain sentences on where the money goes")
        String summary,
        @JsonPropertyDescription("Observations worth the reader's attention, each tied to a category and an exact amount from the tools")
        List<Insight> insights,
        @JsonPropertyDescription("Concrete changes with the monthly amount they would free up, quoted exactly from the tools")
        List<SavingOpportunity> savings) {

    public record Insight(
            @JsonPropertyDescription("A short title")
            String title,
            @JsonPropertyDescription("One or two sentences")
            String detail,
            @JsonPropertyDescription("The category this is about")
            Category category,
            @JsonPropertyDescription("An amount quoted exactly as a tool returned it, or null")
            BigDecimal amount) {

        @Override
        public String toString() {
            return title;
        }
    }

    public record SavingOpportunity(
            @JsonPropertyDescription("What to change")
            String what,
            @JsonPropertyDescription("The monthly amount quoted exactly as a tool returned it, or null")
            BigDecimal monthlySaving,
            @JsonPropertyDescription("How to do it in one sentence")
            String how) {

        @Override
        public String toString() {
            return what;
        }
    }

    static final int MAX_INSIGHTS = 5;
    static final int MAX_SAVINGS = 3;

    public CoachingNotes {
        insights = insights == null ? List.of() : List.copyOf(insights);
        savings = savings == null ? List.of() : List.copyOf(savings);
    }

    public static CoachingNotes empty() {
        return new CoachingNotes("", List.of(), List.of());
    }

    public boolean isEmpty() {
        return (summary == null || summary.isBlank()) && insights.isEmpty() && savings.isEmpty();
    }

    public CoachingNotes keepOnly(NavigableSet<BigDecimal> amounts) {
        List<Insight> keptInsights = insights.stream()
                .filter(insight -> insight.amount() == null || amounts.contains(insight.amount()))
                .limit(MAX_INSIGHTS)
                .toList();
        List<SavingOpportunity> keptSavings = savings.stream()
                .filter(saving -> saving.monthlySaving() == null || amounts.contains(saving.monthlySaving()))
                .limit(MAX_SAVINGS)
                .toList();
        return new CoachingNotes(summary, keptInsights, keptSavings);
    }

    @Override
    public String toString() {
        return "CoachingNotes[" + insights.size() + " insights, " + savings.size() + " savings]";
    }
}
