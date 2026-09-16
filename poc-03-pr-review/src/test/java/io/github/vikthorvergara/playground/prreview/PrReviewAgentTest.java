package io.github.vikthorvergara.playground.prreview;

import com.embabel.agent.api.common.workflow.loop.Attempt;
import com.embabel.agent.api.common.workflow.loop.AttemptHistory;
import com.embabel.agent.test.unit.FakeOperationContext;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PrReviewAgentTest {

    private final PrReviewAgent agent = new PrReviewAgent(new ReviewProperties(true, 3, 0.8));
    private final Diff diff = DiffParser.parse("""
            diff --git a/App.java b/App.java
            +++ b/App.java
            +String s = null; s.length();
            """);
    private final ReviewDraft draft = new ReviewDraft("Introduces an NPE.", List.of(
            new ReviewComment("App.java", 1, ReviewComment.Severity.BLOCKER, "s is always null here."),
            new ReviewComment("App.java", null, ReviewComment.Severity.NIT, "Consider a better name.")));

    @Test
    void firstDraftUsesTheDrafterRoleAndHasNoRevisionSection() {
        var context = FakeOperationContext.create();
        context.expectResponse(draft);

        assertThat(agent.draft(diff, null, context)).isEqualTo(draft);

        var invocation = context.getLlmInvocations().getFirst();
        assertThat(Roles.selectsRole(invocation.getInteraction().getLlm(), ReviewProperties.DRAFTER_ROLE)).isTrue();
        assertThat(invocation.getPrompt()).contains("1 file(s), +1 -0").doesNotContain("previous review");
    }

    @Test
    void revisionIncludesPreviousReviewAndCritique() {
        var context = FakeOperationContext.create();
        context.expectResponse(draft);
        var critique = new Critique(0.4, List.of(new Critique.Verdict(1, false, "Noise.")), "Missed the unused import.");

        agent.draft(diff, new Attempt<>(draft, critique), context);

        assertThat(context.getLlmInvocations().getFirst().getPrompt())
                .contains("Your previous review")
                .contains("1. [NIT] App.java: Consider a better name.")
                .contains("Critique (score 0.40)")
                .contains("Missed the unused import.");
    }

    @Test
    void critiqueUsesTheCriticRoleAndNumbersComments() {
        var context = FakeOperationContext.create();
        var critique = new Critique(0.9, List.of(new Critique.Verdict(0, true, "Correct.")), "Good.");
        context.expectResponse(critique);

        assertThat(agent.critique(diff, draft, context)).isEqualTo(critique);

        var invocation = context.getLlmInvocations().getFirst();
        assertThat(Roles.selectsRole(invocation.getInteraction().getLlm(), ReviewProperties.CRITIC_ROLE)).isTrue();
        assertThat(invocation.getPrompt())
                .contains("0. [BLOCKER] App.java:1: s is always null here.")
                .contains("1. [NIT] App.java: Consider a better name.");
    }

    @Test
    void autoApproveSkipsTheHuman() {
        var context = FakeOperationContext.create();

        ApprovedReview approved = agent.autoApproveReview(new CritiquedReview(draft, List.of(0.5, 0.85)), context);

        assertThat(approved.autoApproved()).isTrue();
        assertThat(approved.rounds()).isEqualTo(2);
        assertThat(approved.toMarkdown())
                .contains("- **BLOCKER** `App.java:1` s is always null here.")
                .contains("2 round(s), critic scores [0.5, 0.85]")
                .contains("Auto-approved.");
    }

    @Test
    void conditionsFollowTheRequestFlag() {
        assertThat(agent.needsHumanApproval(new ReviewRequest("d", false))).isTrue();
        assertThat(agent.autoApprove(new ReviewRequest("d", false))).isFalse();
        assertThat(agent.autoApprove(new ReviewRequest("d", true))).isTrue();
    }

    @Test
    void loopIsFinishedOnceAcceptedOrCapped() {
        assertThat(agent.loopFinished(history())).isFalse();
        assertThat(agent.loopFinished(history(0.5))).isFalse();
        assertThat(agent.loopFinished(history(0.5, 0.85))).isTrue();
        assertThat(agent.loopFinished(history(0.1, 0.1, 0.1))).isTrue();
    }

    private AttemptHistory<Diff, ReviewDraft, Critique> history(double... scores) {
        List<Attempt<ReviewDraft, Critique>> attempts = new ArrayList<>();
        for (double score : scores) {
            attempts.add(new Attempt<>(draft, new Critique(score, List.of(), "")));
        }
        return new AttemptHistory<>(diff, attempts, null, Instant.now());
    }

    @Test
    void iterationCapMustBePositive() {
        assertThatThrownBy(() -> new ReviewProperties(true, 0, 0.8)).isInstanceOf(IllegalArgumentException.class);
    }
}
