package io.github.vikthorvergara.playground.docsqa;

import com.embabel.agent.domain.io.UserInput;
import com.embabel.agent.test.unit.FakeOperationContext;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DocsQaAgentTest {

    @TempDir
    static Path root;

    private static DocsQaAgent agent;

    @BeforeAll
    static void setUp() throws Exception {
        Files.writeString(root.resolve("loop.md"), """
                # Loops

                The critic loop is capped at **three rounds**, then the best
                draft is returned.
                """);
        agent = new DocsQaAgent(new DocsIndex(new DocsProperties(root, List.of("loop.md"), 5)));
    }

    @Test
    void askTrimsTheQuestion() {
        assertThat(agent.ask(new UserInput("  How many rounds?  "))).isEqualTo(new Question("How many rounds?"));
    }

    @Test
    void draftAnswerGivesTheLlmSearchToolsAndTheDocumentList() {
        var context = FakeOperationContext.create();
        var draft = new DraftAnswer("Three.", List.of(new Citation("loop.md", "capped at three rounds")));
        context.expectResponse(draft);

        assertThat(agent.draftAnswer(new Question("How many rounds?"), context)).isEqualTo(draft);

        var invocation = context.getLlmInvocations().getFirst();
        assertThat(invocation.getPrompt())
                .contains("Question: How many rounds?")
                .contains("Available documents: loop.md")
                .contains(DocsQaAgent.NOT_FOUND);
        assertThat(invocation.getInteraction().getTools()).isNotEmpty();
    }

    @Test
    void verifyKeepsOnlyQuotesThatAppearInTheCitedFile() {
        var draft = new DraftAnswer("Three rounds.", List.of(
                new Citation("loop.md", "capped at three rounds, then the best draft"),
                new Citation("loop.md", "capped at five rounds"),
                new Citation("other.md", "capped at three rounds"),
                new Citation("loop.md", "the")));

        Answer answer = agent.verifyCitations(draft, new Question("How many rounds?"));

        assertThat(answer.citations()).extracting(Citation::quote)
                .containsExactly("capped at three rounds, then the best draft");
        assertThat(answer.rejected()).hasSize(3);
        assertThat(answer.grounded()).isTrue();
        assertThat(answer.toMarkdown())
                .startsWith("Three rounds.")
                .contains("- `loop.md`: \"capped at three rounds, then the best draft\"")
                .contains("3 citation(s) dropped");
    }

    @Test
    void answerWithoutVerifiedCitationsIsFlagged() {
        Answer answer = agent.verifyCitations(new DraftAnswer(DocsQaAgent.NOT_FOUND, List.of()), new Question("Pricing?"));

        assertThat(answer.grounded()).isFalse();
        assertThat(answer.toMarkdown()).contains("Not verified");
    }
}
