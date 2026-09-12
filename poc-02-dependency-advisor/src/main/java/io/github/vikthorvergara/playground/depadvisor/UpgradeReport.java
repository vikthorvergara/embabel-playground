package io.github.vikthorvergara.playground.depadvisor;

import java.util.List;
import java.util.stream.Collectors;

public record UpgradeReport(
        String projectName,
        List<VersionCheck> recommendedOrder,
        List<BreakingChangeAssessment.Note> breakingChangeNotes,
        List<String> unknown,
        List<String> skipped) {

    public boolean isDetailed() {
        return !breakingChangeNotes.isEmpty();
    }

    public String toMarkdown() {
        var sb = new StringBuilder("## Dependency upgrades for ").append(projectName).append("\n\n");
        if (recommendedOrder.isEmpty()) {
            sb.append("Everything we could check is up to date.\n");
        } else {
            sb.append("Recommended order:\n\n");
            for (int i = 0; i < recommendedOrder.size(); i++) {
                VersionCheck check = recommendedOrder.get(i);
                sb.append("%d. `%s` %s → %s (%s)%n".formatted(i + 1,
                        check.dependency().coordinates(),
                        check.dependency().version(),
                        check.latestVersion(),
                        check.upgradeType()));
            }
        }
        if (!breakingChangeNotes.isEmpty()) {
            sb.append("\n### Major upgrades\n\n");
            breakingChangeNotes.forEach(note -> sb.append("- `%s` [%s risk] %s%s%n".formatted(
                    note.coordinates(), note.risk(), note.summary(),
                    note.link() == null ? "" : " (" + note.link() + ")")));
        }
        appendList(sb, "Could not check (lookup failed)", unknown);
        appendList(sb, "Skipped (version managed elsewhere)", skipped);
        return sb.toString().strip();
    }

    private static void appendList(StringBuilder sb, String title, List<String> items) {
        if (!items.isEmpty()) {
            sb.append("\n### ").append(title).append("\n\n")
                    .append(items.stream().map(i -> "- `" + i + "`").collect(Collectors.joining("\n")))
                    .append("\n");
        }
    }

    @Override
    public String toString() {
        return toMarkdown();
    }
}
