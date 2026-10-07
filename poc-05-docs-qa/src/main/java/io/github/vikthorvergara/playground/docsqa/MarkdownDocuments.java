package io.github.vikthorvergara.playground.docsqa;

import com.embabel.agent.rag.model.LeafSection;
import com.embabel.agent.rag.model.MaterializedDocument;
import com.embabel.agent.rag.model.NavigableSection;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Splits a Markdown file into one section per heading. Code fences are respected, so a "#" comment
 * inside a bash block does not start a new section.
 */
final class MarkdownDocuments {

    private static final Pattern HEADING = Pattern.compile("^(#{1,6})\\s+(.+?)\\s*#*\\s*$");

    private MarkdownDocuments() {
    }

    static MaterializedDocument parse(String source, String markdown) {
        String documentTitle = source;
        List<NavigableSection> sections = new ArrayList<>();
        String sectionTitle = null;
        StringBuilder body = new StringBuilder();
        boolean inFence = false;

        for (String line : markdown.lines().toList()) {
            if (line.strip().startsWith("```")) {
                inFence = !inFence;
            }
            Matcher heading = inFence ? null : HEADING.matcher(line);
            if (heading != null && heading.matches()) {
                addSection(sections, source, sectionTitle, body);
                sectionTitle = heading.group(2);
                if (heading.group(1).length() == 1 && documentTitle.equals(source)) {
                    documentTitle = sectionTitle;
                }
                body.setLength(0);
            } else {
                body.append(line).append('\n');
            }
        }
        addSection(sections, source, sectionTitle, body);
        return new MaterializedDocument(source, source, documentTitle, Instant.now(), sections,
                Map.of("url", source, "source", source));
    }

    private static void addSection(List<NavigableSection> sections, String source, String title, StringBuilder body) {
        String text = body.toString().strip();
        if (text.isEmpty()) {
            return;
        }
        String id = source + "#" + (sections.size() + 1);
        String sectionTitle = title == null ? source : title;
        // The heading is repeated in the text so a keyword search on it finds the section
        sections.add(new LeafSection(id, source, sectionTitle, sectionTitle + "\n\n" + text, source,
                Map.of("url", source)));
    }
}
