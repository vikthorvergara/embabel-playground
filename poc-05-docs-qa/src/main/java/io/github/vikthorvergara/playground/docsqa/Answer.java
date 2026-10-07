package io.github.vikthorvergara.playground.docsqa;

import java.util.List;
import java.util.stream.Collectors;

/**
 * @param citations verified citations: the quote really appears in the named document
 * @param rejected  citations that failed verification (wrong file or quote not found)
 */
public record Answer(String question, String answer, List<Citation> citations, List<Citation> rejected) {

    public boolean grounded() {
        return !citations.isEmpty();
    }

    public String toMarkdown() {
        var sb = new StringBuilder(answer.strip()).append("\n");
        if (!citations.isEmpty()) {
            sb.append("\nSources:\n").append(citations.stream()
                    .map(c -> "- `%s`: \"%s\"".formatted(c.source(), c.quote()))
                    .collect(Collectors.joining("\n"))).append("\n");
        }
        if (!grounded()) {
            sb.append("\n_Not verified: no citation could be matched to the docs._\n");
        }
        if (!rejected.isEmpty()) {
            sb.append("\n_%d citation(s) dropped because the quote was not found in the cited file._\n"
                    .formatted(rejected.size()));
        }
        return sb.toString().strip();
    }

    @Override
    public String toString() {
        return toMarkdown();
    }
}
