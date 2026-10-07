package io.github.vikthorvergara.playground.docsqa;

import com.embabel.agent.api.annotation.AchievesGoal;
import com.embabel.agent.api.annotation.Action;
import com.embabel.agent.api.annotation.Agent;
import com.embabel.agent.api.common.OperationContext;
import com.embabel.agent.domain.io.UserInput;
import com.embabel.agent.rag.tools.ToolishRag;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@Agent(description = "Answers questions about this project from its Markdown documentation, with verified citations")
public class DocsQaAgent {

    static final String NOT_FOUND = "The documentation does not cover this.";

    private final DocsIndex index;

    public DocsQaAgent(DocsIndex index) {
        this.index = index;
    }

    @Action(description = "Take the user's question")
    public Question ask(UserInput input) {
        return new Question(input.getContent().strip());
    }

    @Action(description = "Search the docs and draft an answer with citations")
    public DraftAnswer draftAnswer(Question question, OperationContext context) {
        var docs = new ToolishRag("docs", "Markdown documentation of the embabel-playground project: "
                + "POC definitions, learnings and the retrospective", index.searchOperations());
        return context.ai()
                .withDefaultLlm()
                .withReference(docs)
                .createObject("""
                        Answer the question using only the project documentation. Search the docs with the tools
                        you have; try a couple of different keywords if the first search finds nothing useful.

                        For every claim, add a citation: the source path exactly as it appears in the search
                        results and a short quote copied word for word from that source. Do not paraphrase
                        inside quotes.

                        If the docs do not answer the question, reply exactly "%s" with no citations.

                        Available documents: %s

                        Question: %s
                        """.formatted(NOT_FOUND, String.join(", ", index.sources().keySet()), question.text()),
                        DraftAnswer.class);
    }

    @AchievesGoal(description = "A question about the project has been answered with verified citations")
    @Action(description = "Keep only citations whose quote really appears in the cited document")
    public Answer verifyCitations(DraftAnswer draft, Question question) {
        Map<Boolean, List<Citation>> checked = draft.citations().stream()
                .collect(Collectors.partitioningBy(isVerbatimIn(index.sources())));
        return new Answer(question.text(), draft.answer(), checked.get(true), checked.get(false));
    }

    /** Whitespace and case are normalised, everything else must match exactly. */
    static Predicate<Citation> isVerbatimIn(Map<String, String> sources) {
        return citation -> {
            String text = sources.get(citation.source());
            String quote = normalise(citation.quote());
            return text != null && quote.length() >= 8 && normalise(text).contains(quote);
        };
    }

    static String normalise(String s) {
        return s == null ? "" : s.replaceAll("[`*_]", "").replaceAll("\\s+", " ").strip().toLowerCase(Locale.ROOT);
    }
}
