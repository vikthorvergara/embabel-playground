# POC 06: Decision Helper (parallel fan-out with ScatterGather)

## Goal

Learn how Embabel runs work in parallel and combines the results, and keep the final decision
deterministic even though most of the inputs come from an LLM.

## Problem

"Kafka, RabbitMQ or SQS for order events? Throughput matters most." should produce a ranked
comparison against weighted criteria and a recommendation with its reasoning.

## Scope

**In scope**
- `frameDecision`: one LLM call turns free text into a `DecisionRequest` (options plus weighted
  criteria), then plain Java trims, dedupes, caps (`decisions.max-options`, `max-criteria`) and
  clamps weights to 1..5.
- `compareOptions`: a `ScatterGather` sub-process with one generator per option. Each generator
  asks the "analyst" model to score that option on every criterion. They run in parallel
  (`decisions.max-concurrency`).
- Consolidation in plain Java: drop unknown criteria, clamp scores, fill in missing ones, compute
  the weighted average and rank. Ties keep the order the options were given in.
- `recommend`: the "judge" model explains the result. It is told the winner and cannot change it.
- Models by role in `application.yml` (`analyst`, `judge`), like POC 03.

**Out of scope**
- Web research per option, pairwise comparison, letting the user edit the criteria mid-run.

## Agent design

```
UserInput ─frameDecision─▶ DecisionRequest ─compareOptions─▶ Comparison ─recommend─▶ Recommendation (goal)
                                                  │
                         ScatterGather sub-process: analyst(option 1) ┐
                                                    analyst(option 2) ├─▶ rank (plain Java)
                                                    analyst(option 3) ┘
```

## Done criteria

- [x] One analyst call per option, running concurrently (the flow test uses a barrier that only
      opens when all three calls are in flight).
- [x] The ranking comes from the weighted scores, not from the LLM (tested with known scores).
- [x] LLM output outside the frame (renamed options, unknown criteria, out-of-range scores) is
      corrected before ranking.
- [ ] Run three real decisions with an API key and check whether the explanations match the scores.
- [x] "Learnings" section appended after implementation.

## Running

```bash
export ANTHROPIC_API_KEY=...
mvn -pl poc-06-decision-helper spring-boot:run
# in the shell:
x "Kafka, RabbitMQ or SQS for order events? Throughput matters most, we are a small team."
```

## Learnings

- **ScatterGather** is a builder over a list of generator functions plus a consolidation
  function. Run it with `context.asSubProcess(Comparison.class, builder.build())`, like
  `RepeatUntilAcceptable` in POC 03. Concurrency is a constructor argument of
  `ScatterGatherBuilder`, not a `with...` method, so use the constructor to set it.
- Generators run on the platform's thread pool (`embabel-platform-N`), so whatever they touch has
  to be thread safe. Here each one only reads the immutable `DecisionRequest`.
- **Failed actions are retried by default.** While checking that the concurrency test really fails
  with `max-concurrency=1`, the run kept going far longer than one timeout because the action was
  retried. Worth knowing before giving an action an expensive or non-idempotent side effect
  (`actionRetryPolicy` on `@Action` or `@Agent` controls it).
- **The LLM scores, Java decides.** Asking for 1..5 scores per criterion and computing the
  weighted average in code makes the ranking reproducible and easy to explain. The judge only
  writes the explanation for a result that is already fixed.
- Prompts in text blocks wrap wherever the source line ends. A unit test asserting on a key
  sentence ("The recommendation is Kafka.") caught one that had been split across two lines.
