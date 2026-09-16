# POC 03: PR Review Agent (multi-model critic loop + human in the loop)

## Goal

Explore quality control patterns: one model drafts, another critiques, and a human
confirms before anything leaves the system. Learn how Embabel handles LLM selection
per action, loops, and pausing for user input.

## Problem

Given a unified diff, produce review comments that are specific, correct and not noisy.

## Scope

**In scope**
- Per-action LLM selection by role:
  - drafter (fast/cheap model) writes a `ReviewDraft`.
  - critic (stronger model) returns a `Critique`: a keep/drop verdict per comment, an overall score and feedback.
  - The drafter revises using the critique until the score passes a threshold or 3 rounds have run.
    The loop uses Embabel's `RepeatUntilAcceptable` workflow, run as a sub-process of the `review` action.
- Explicit model configuration in `application.yml` (roles such as `drafter`, `critic`),
  not hard-coded model names.
- A human confirmation step before the final `ApprovedReview` goal, using `WaitFor.confirmation`.
  A `ReviewRequest.autoApprove` flag skips it (used by POC 04).
- A cost and latency summary per run (tokens and time per action).

**Out of scope**
- Posting to GitHub (kept for POC 04 or later), fine-tuning, repository-wide context.

## Agent design

```
UserInput ─▶ ReviewRequest ─parseDiff─▶ Diff ─review─▶ CritiquedReview
                                                │
                              RepeatUntilAcceptable sub-process:
                              draft (drafter) ─▶ critique (critic) ─┐
                                    ▲                               │ score < 0.8 and rounds < 3
                                    └───────────────────────────────┘

CritiquedReview ─awaitHumanApproval─▶ ApprovedReview (goal)   when needsHumanApproval
CritiquedReview ─autoApproveReview──▶ ApprovedReview (goal)   when autoApprove
```

## Done criteria

- [x] The loop always terminates (iteration cap tested).
- [x] Swapping the critic model is a config-only change (`embabel.models.llms.critic`).
- [x] Rejecting a review in the confirmation step ends the run with no output.
- [ ] Run a fixed set of 5 sample diffs with and without the critic (`review.critic-enabled=false`),
      and record the comment counts and a subjective quality note. Needs a real API key.
- [x] "Learnings" section appended after implementation.

## Questions to answer

- Is the critic worth its cost? Compare on the 5-diff sample set.
- How does GOAP planning express a bounded loop cleanly?
- Where does human-in-the-loop state live while the agent waits?

## Running

```bash
export ANTHROPIC_API_KEY=...
mvn -pl poc-03-pr-review spring-boot:run
# in the shell (the whole diff inside the quotes):
x "$(git diff main)"
# compare without the critic:
mvn -pl poc-03-pr-review spring-boot:run -Dspring-boot.run.arguments=--review.critic-enabled=false
```

Models are configured by role in `application.yml`:

```yaml
embabel:
  models:
    llms:
      drafter: claude-haiku-4-5
      critic: claude-sonnet-4-6
```

## Learnings

- **Loops in GOAP** are expressed with actions that can re-run (`canRerun`) plus a condition that
  only becomes true when the loop should stop. `RepeatUntilAcceptable` packages exactly that
  (generate, evaluate, `acceptable` condition, consolidate), so there was no need to hand-roll it.
- **Quirk in 1.5.1:** after the critic accepts a draft, the workflow's goal also wants a `ReviewDraft`
  to be the last bound object, so the planner runs the generator one more time. That extra draft
  is never critiqued, yet it is what `asSubProcess` returns. The flow test caught it (4 drafts for 3
  critiques). Fix: the generator returns `bestSoFar()` without calling the LLM once the loop is
  finished. Worth reporting upstream.
- `withLlmByRole("critic")` puts the role in `LlmOptions.criteria`, not `LlmOptions.role`. Tests that
  check which model was used need to look at the criteria.
- Human in the loop: `WaitFor.confirmation(value, message)` pauses the process in `WAITING` state
  and only binds `value` once someone accepts. While waiting, the state lives on the `AgentProcess`
  itself, so it survives only as long as the process does (in memory by default).
- Cost: `AgentProcess.getLlmInvocations()` has usage and model per call, which is enough for the
  per-run summary. With Anthropic models the pricing comes from Embabel's model metadata.
