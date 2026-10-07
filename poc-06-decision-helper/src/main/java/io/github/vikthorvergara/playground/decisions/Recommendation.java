package io.github.vikthorvergara.playground.decisions;

import java.util.List;
import java.util.stream.Collectors;

public record Recommendation(String question, List<Criterion> criteria, List<RankedOption> ranking,
                             Explanation explanation) {

    public String winner() {
        return ranking.getFirst().option();
    }

    public String toMarkdown() {
        String header = "| # | Option | Score | " + criteria.stream().map(c -> c.name() + " (x" + c.weight() + ")")
                .collect(Collectors.joining(" | ")) + " |";
        String separator = "|---|---|---|" + "---|".repeat(criteria.size());
        String rows = ranking.stream().map(r -> "| %d | %s | %.2f | %s |".formatted(r.rank(), r.option(),
                        r.weightedScore(), criteria.stream()
                                .map(c -> r.assessment().scores().stream()
                                        .filter(s -> s.criterion().equals(c.name()))
                                        .map(s -> String.valueOf(s.score()))
                                        .findFirst().orElse("-"))
                                .collect(Collectors.joining(" | "))))
                .collect(Collectors.joining("\n"));
        return """
                ## %s

                **Recommendation: %s**

                %s

                %s
                %s
                %s

                When to pick %s instead: %s""".formatted(question, winner(), explanation.rationale(),
                header, separator, rows,
                ranking.size() > 1 ? ranking.get(1).option() : "another option",
                explanation.whenToPickRunnerUp()).strip();
    }

    @Override
    public String toString() {
        return toMarkdown();
    }
}
