package io.github.vikthorvergara.playground.spending;

import com.embabel.agent.domain.io.FileArtifact;
import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.List;

public record SpendingReport(
        String runId,
        List<String> files,
        String summaryCsv,
        SpendingAnalysis analysis,
        CoachingNotes notes,
        RunCost cost,
        String route,
        @JsonIgnore List<FileArtifact> artifacts) {

    public static final String ROUTE_RULES_ONLY = "rules-only";
    public static final String ROUTE_MODEL = "model";

    public String toMarkdown() {
        StringBuilder md = new StringBuilder("## Spending report ").append(runId).append("\n\n");
        md.append("Months: ").append(analysis.months()).append(". Currency: ").append(analysis.currency()).append(".\n\n");
        md.append("Top categories:\n");
        analysis.categoriesByTotal().stream()
                .filter(SpendingAnalysis::isSpendCategory)
                .limit(5)
                .forEach(category -> md.append("- ").append(category).append(": ")
                        .append(SpendingAnalysis.money(analysis.categoryTotal(category))).append('\n'));
        md.append("\n```csv\n").append(summaryCsv).append("```\n\n");
        if (analysis.byMonth().stream().anyMatch(cell -> cell.total().signum() < 0)) {
            md.append("A negative cell means a refund landed in a later month than its purchase.\n\n");
        }
        if (analysis.foreignRows() > 0) {
            md.append(analysis.foreignRows()).append(" row(s) in other currencies are in foreign.csv and excluded from every total.\n\n");
        }
        if (!notes.isEmpty()) {
            md.append(notes.summary()).append("\n\n");
        }
        md.append("Files in work/reports/").append(runId).append("/: ").append(String.join(", ", files)).append("\n\n");
        md.append("Route: ").append(route).append("\n\n_").append(cost).append('_');
        return md.toString();
    }

    @Override
    public String toString() {
        return toMarkdown();
    }
}
