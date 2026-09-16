package io.github.vikthorvergara.playground.prreview;

import com.embabel.agent.api.annotation.AchievesGoal;
import com.embabel.agent.api.annotation.Action;
import com.embabel.agent.api.annotation.Agent;
import com.embabel.agent.api.annotation.Condition;
import com.embabel.agent.api.common.ActionContext;
import com.embabel.agent.api.common.OperationContext;
import com.embabel.agent.api.common.workflow.loop.Attempt;
import com.embabel.agent.api.common.workflow.loop.AttemptHistory;
import com.embabel.agent.api.common.workflow.loop.RepeatUntilAcceptableBuilder;
import com.embabel.agent.core.hitl.WaitFor;
import com.embabel.agent.domain.io.UserInput;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Agent(description = "Reviews a unified diff: a drafter writes comments, a critic scores them, and a human approves the result")
public class PrReviewAgent {

    static final String NEEDS_HUMAN_APPROVAL = "needsHumanApproval";
    static final String AUTO_APPROVE = "autoApprove";

    private final ReviewProperties properties;

    public PrReviewAgent(ReviewProperties properties) {
        this.properties = properties;
    }

    @Action(post = {NEEDS_HUMAN_APPROVAL, AUTO_APPROVE}, description = "Treat user input as a diff to review interactively")
    public ReviewRequest fromUserInput(UserInput input) {
        return new ReviewRequest(input.getContent(), false);
    }

    @Condition(name = NEEDS_HUMAN_APPROVAL)
    public boolean needsHumanApproval(ReviewRequest request) {
        return !request.autoApprove();
    }

    @Condition(name = AUTO_APPROVE)
    public boolean autoApprove(ReviewRequest request) {
        return request.autoApprove();
    }

    @Action(description = "Parse the unified diff")
    public Diff parseDiff(ReviewRequest request) {
        return DiffParser.parse(request.diff());
    }

    @Action(description = "Draft review comments and refine them with a critic until they are good enough")
    public CritiquedReview review(Diff diff, ActionContext context) {
        if (!properties.criticEnabled()) {
            return new CritiquedReview(draft(diff, null, context), List.of());
        }
        List<Double> scores = new ArrayList<>();
        var loop = RepeatUntilAcceptableBuilder.returning(ReviewDraft.class)
                .consuming(Diff.class)
                .withFeedbackClass(Critique.class)
                .withMaxIterations(properties.maxIterations())
                .withScoreThreshold(properties.scoreThreshold())
                .repeating(ctx -> loopFinished(ctx.getAttemptHistory())
                        ? ctx.getAttemptHistory().bestSoFar().getResult()
                        : draft(ctx.getInput(), ctx.lastAttempt(), ctx))
                .withEvaluator(ctx -> {
                    Critique critique = critique(ctx.getInput(), ctx.getResultToEvaluate(), ctx);
                    scores.add(critique.score());
                    return critique;
                })
                .build();
        ReviewDraft best = context.asSubProcess(ReviewDraft.class, loop);
        return new CritiquedReview(best, List.copyOf(scores));
    }

    @AchievesGoal(description = "A code review has been approved by a human")
    @Action(pre = NEEDS_HUMAN_APPROVAL, description = "Ask a human to approve the review before it is released")
    public ApprovedReview awaitHumanApproval(CritiquedReview review, OperationContext context) {
        ApprovedReview approved = approve(review, false, context);
        return WaitFor.confirmation(approved, "Release this review with %d comment(s)?\n\n%s"
                .formatted(review.review().comments().size(), approved.toMarkdown()));
    }

    @AchievesGoal(description = "A code review has been produced for an automated caller")
    @Action(pre = AUTO_APPROVE, description = "Release the review without human confirmation")
    public ApprovedReview autoApproveReview(CritiquedReview review, OperationContext context) {
        return approve(review, true, context);
    }

    /**
     * RepeatUntilAcceptable (1.5.1) runs the generator one extra time after the critic accepts, because
     * its goal wants a ReviewDraft to be the last bound object. Short-circuiting here avoids a wasted
     * LLM call and makes the loop return the best-scored draft instead of an unreviewed one.
     */
    boolean loopFinished(AttemptHistory<Diff, ReviewDraft, Critique> history) {
        Critique last = history.lastFeedback();
        return last != null
                && (history.attemptCount() >= properties.maxIterations() || last.score() >= properties.scoreThreshold());
    }

    ReviewDraft draft(Diff diff, Attempt<ReviewDraft, Critique> previous, OperationContext context) {
        String revision = previous == null ? "" : """

                You already reviewed this diff once. Your previous review and a senior reviewer's critique follow.
                Produce an improved review: drop comments the critique rejected, fix wrong claims, add what was missed.

                Previous review:
                %s

                Critique (score %.2f):
                %s
                """.formatted(numbered(previous.getResult()), previous.getFeedback().score(),
                previous.getFeedback().feedback());
        return context.ai()
                .withLlmByRole(ReviewProperties.DRAFTER_ROLE)
                .createObject("""
                        You are reviewing a pull request. Write review comments for the diff below.

                        Only comment on real problems: bugs, security issues, missing error handling, unclear naming
                        or tests that do not test anything. Do not restate what the code does, do not praise, and do
                        not nitpick formatting that a linter would catch. Fewer, correct comments beat many weak ones.
                        Use BLOCKER only for things that must be fixed before merging.

                        Diff (%s):
                        %s
                        %s""".formatted(diff.summary(), diff.text(), revision),
                        ReviewDraft.class);
    }

    Critique critique(Diff diff, ReviewDraft draft, OperationContext context) {
        return context.ai()
                .withLlmByRole(ReviewProperties.CRITIC_ROLE)
                .createObject("""
                        You are a senior engineer checking another reviewer's work on a pull request.

                        For each numbered comment decide whether to keep it. Reject comments that are wrong,
                        vague, duplicated, or not worth the author's time. Then score the review as a whole
                        between 0.0 and 1.0: 1.0 means every real problem in the diff is covered and nothing is
                        noise. In the feedback, list real problems the reviewer missed.

                        Diff:
                        %s

                        Review:
                        %s
                        """.formatted(diff.text(), numbered(draft)),
                        Critique.class);
    }

    private ApprovedReview approve(CritiquedReview review, boolean auto, OperationContext context) {
        return new ApprovedReview(review.review(), review.rounds(), review.scores(), auto,
                RunStats.from(context.getAgentProcess().getLlmInvocations()));
    }

    static String numbered(ReviewDraft draft) {
        List<ReviewComment> comments = draft.comments();
        String body = IntStream.range(0, comments.size())
                .mapToObj(i -> "%d. [%s] %s%s: %s".formatted(i, comments.get(i).severity(), comments.get(i).file(),
                        comments.get(i).line() == null ? "" : ":" + comments.get(i).line(), comments.get(i).comment()))
                .collect(Collectors.joining("\n"));
        return "Summary: " + draft.summary() + "\n" + (body.isEmpty() ? "(no comments)" : body);
    }
}
