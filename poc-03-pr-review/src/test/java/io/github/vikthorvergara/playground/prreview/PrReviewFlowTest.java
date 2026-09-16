package io.github.vikthorvergara.playground.prreview;

import com.embabel.agent.core.Agent;
import com.embabel.agent.core.AgentProcess;
import com.embabel.agent.core.AgentProcessStatusCode;
import com.embabel.agent.core.ProcessOptions;
import com.embabel.agent.core.hitl.ConfirmationRequest;
import com.embabel.agent.core.hitl.ConfirmationResponse;
import com.embabel.agent.domain.io.UserInput;
import com.embabel.agent.test.integration.EmbabelMockitoIntegrationTest;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.context.TestPropertySource;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs the real planner, including the RepeatUntilAcceptable sub-process. Only the LLM is mocked.
 */
@TestPropertySource(properties = {
        "ANTHROPIC_API_KEY=test-key",
        "embabel.agent.shell.interactive.enabled=false",
        "review.max-iterations=3",
        "review.score-threshold=0.8"
})
class PrReviewFlowTest extends EmbabelMockitoIntegrationTest {

    private static final ReviewDraft DRAFT = new ReviewDraft("Swallows a missing user and logs a password.", List.of(
            new ReviewComment("src/main/java/com/example/UserService.java", 16, ReviewComment.Severity.BLOCKER,
                    "Logs the user's password.")));

    @Test
    void loopStopsAtTheIterationCapWhenTheCriticIsNeverSatisfied() throws IOException {
        stubDrafter();
        stubCritic(0.3);

        AgentProcess process = start(new UserInput(DiffParserTest.sample()));

        assertThat(process.getStatus()).isEqualTo(AgentProcessStatusCode.WAITING);
        assertThat(llmCalls(ReviewDraft.class)).isEqualTo(3);
        assertThat(llmCalls(Critique.class)).isEqualTo(3);

        ApprovedReview approved = respond(process, true);
        assertThat(approved.rounds()).isEqualTo(3);
        assertThat(approved.criticScores()).containsExactly(0.3, 0.3, 0.3);
        assertThat(approved.autoApproved()).isFalse();
        assertThat(process.getStatus()).isEqualTo(AgentProcessStatusCode.COMPLETED);
    }

    @Test
    void goodFirstDraftIsAcceptedAfterOneRound() throws IOException {
        stubDrafter();
        stubCritic(0.9);

        AgentProcess process = start(new UserInput(DiffParserTest.sample()));

        assertThat(llmCalls(ReviewDraft.class)).isEqualTo(1);
        assertThat(respond(process, true).criticScores()).containsExactly(0.9);
    }

    @Test
    void rejectedReviewProducesNoOutput() throws IOException {
        stubDrafter();
        stubCritic(0.9);

        AgentProcess process = start(new UserInput(DiffParserTest.sample()));

        assertThat(respond(process, false)).isNull();
    }

    @Test
    void autoApprovedRequestsNeverWaitForAHuman() throws IOException {
        stubDrafter();
        stubCritic(0.9);

        AgentProcess process = start(new ReviewRequest(DiffParserTest.sample(), true));

        assertThat(process.getStatus()).isEqualTo(AgentProcessStatusCode.COMPLETED);
        assertThat(process.last(ApprovedReview.class).autoApproved()).isTrue();
    }

    private void stubDrafter() {
        // The role check proves the drafter and critic are resolved by role, so swapping models is config only
        whenCreateObject(prompt -> prompt.contains("You are reviewing a pull request"), ReviewDraft.class,
                interaction -> Roles.selectsRole(interaction.getLlm(), ReviewProperties.DRAFTER_ROLE))
                .thenReturn(DRAFT);
    }

    private void stubCritic(double score) {
        whenCreateObject(prompt -> prompt.contains("checking another reviewer's work"), Critique.class,
                interaction -> Roles.selectsRole(interaction.getLlm(), ReviewProperties.CRITIC_ROLE))
                .thenReturn(new Critique(score, List.of(new Critique.Verdict(0, true, "Real issue.")), "Also check the null user."));
    }

    private AgentProcess start(Object input) {
        Agent agent = agentPlatform.agents().stream()
                .filter(a -> a.getName().equals("PrReviewAgent"))
                .findFirst()
                .orElseThrow();
        return agentPlatform.runAgentFrom(agent, new ProcessOptions(), Map.of("it", input));
    }

    private ApprovedReview respond(AgentProcess process, boolean accepted) {
        assertThat(process.getStatus()).isEqualTo(AgentProcessStatusCode.WAITING);
        var request = (ConfirmationRequest<?>) process.lastResult();
        assertThat(request.getMessage()).startsWith("Release this review with 1 comment(s)?");
        request.onResponse(new ConfirmationResponse(UUID.randomUUID().toString(), request.getId(), accepted, false, Instant.now()), process);
        if (accepted) {
            process.run();
        }
        return process.last(ApprovedReview.class);
    }

    private long llmCalls(Class<?> outputClass) {
        return Mockito.mockingDetails(llmOperations).getInvocations().stream()
                .filter(i -> i.getMethod().getName().equals("createObject"))
                .filter(i -> outputClass.equals(i.getArgument(2)))
                .count();
    }
}
