package io.github.vikthorvergara.playground.meetingnotes;

import java.util.List;
import java.util.stream.Collectors;

public record ActionItemReport(String meetingTitle, List<ActionItem> items) {

    public String toMarkdown() {
        if (items.isEmpty()) {
            return "## " + meetingTitle + "\n\nNo action items.";
        }
        return items.stream()
                .map(item -> "- [%s] %s (owner: %s%s)".formatted(
                        item.priority(),
                        item.description(),
                        item.owner(),
                        item.dueDate() == null ? "" : ", due " + item.dueDate()))
                .collect(Collectors.joining("\n", "## " + meetingTitle + "\n\n", ""));
    }

    @Override
    public String toString() {
        return toMarkdown();
    }
}
