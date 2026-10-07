package io.github.vikthorvergara.playground.docsqa;

import com.embabel.agent.api.invocation.AgentInvocation;
import com.embabel.agent.domain.io.UserInput;
import com.embabel.agent.test.integration.EmbabelMockitoIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Real planner, real index over the project docs, mocked LLM.
 */
@TestPropertySource(properties = {
        "ANTHROPIC_API_KEY=test-key",
        "embabel.agent.shell.interactive.enabled=false",
        "docs-qa.root=.."
})
class DocsQaFlowTest extends EmbabelMockitoIntegrationTest {

    @Test
    void questionIsAnsweredAndCitationsAreVerifiedAgainstTheRealDocs() {
        whenCreateObject(prompt -> prompt.contains("Question: Which Embabel version"), DraftAnswer.class)
                .thenReturn(new DraftAnswer("Embabel Agent 1.5.1 on Spring Boot 4.1.", List.of(
                        new Citation("README.md", "Java 21, Spring Boot 4.1, Maven, Embabel Agent 1.5.1."),
                        new Citation("README.md", "Embabel Agent 2.0"))));

        Answer answer = AgentInvocation.create(agentPlatform, Answer.class)
                .invoke(new UserInput("Which Embabel version does the playground use?"));

        assertThat(answer.grounded()).isTrue();
        assertThat(answer.citations()).hasSize(1);
        assertThat(answer.rejected()).extracting(Citation::quote).containsExactly("Embabel Agent 2.0");
    }
}
