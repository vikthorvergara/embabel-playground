# Retrospective: four Embabel POCs

Embabel Agent 1.5.1 on Spring Boot 4.1, Java 21. Everything below was built and tested with the
LLM mocked; runs against real models are the next step (see "Still open").

## What each POC showed

| POC | Question | Answer |
|-----|----------|--------|
| 01 Meeting notes | Can typed methods be chained by the planner with no wiring? | Yes. Three `@Action` methods and one `@AchievesGoal` were enough. Only one step needed an LLM. |
| 02 Dependency advisor | Can the plan branch on real data, with tools? | Yes, with `@Condition`s, as long as some action declares them in `post`. Tools are plain objects with `@LlmTool` methods. |
| 03 PR review | Loops, multiple models, human approval? | `RepeatUntilAcceptable` handles the bounded loop, `withLlmByRole` keeps models in config, `WaitFor.confirmation` pauses the process. |
| 04 Agent server | Can the same agents be served over MCP and REST? | Yes, with `@Export(remote = true)` and a tiny controller. Metrics came from one event listener. |

## What worked well

- **Types as the contract.** Records in, records out. The planner, the LLM output schema, the MCP
  tool schema and the REST body all come from the same classes.
- **Deterministic by default.** Most actions ended up as plain Java (parsing, lookups, ordering,
  filtering). The LLM is used for the parts that need judgement, and its output is checked
  afterwards (owners matched to participants, notes only for flagged dependencies).
- **Testability.** `FakeOperationContext` for single actions and `EmbabelMockitoIntegrationTest`
  for the real planner with a mocked LLM cover almost everything without an API key. The flow
  tests caught two issues that unit tests would have missed.

## What bit us

1. A computed `@Condition` that no action lists in `post` makes the planner give up before running
   anything (POC 02).
2. `RepeatUntilAcceptable` in 1.5.1 runs the generator once more after acceptance and returns
   that unreviewed result (POC 03). Worked around; worth reporting upstream.
3. `UserInput` generates an MCP schema with a required `timestamp` (POC 04).
4. MCP goal tools plan across every deployed agent. Goals need to be reachable from their own
   input type, and two goals producing the same type are awkward to export (POC 02 and POC 04).
5. The Embabel shell starts inside `@SpringBootTest` unless `embabel.agent.shell.interactive.enabled=false`.
6. Small things: `withLlmByRole` sets the selection criteria, not `LlmOptions.role`, and the default
   condition names include the fully qualified class name.

## Does it fit?

For agents that are mostly deterministic workflows with a few LLM steps, and that live in a
Spring/JVM codebase: yes. The GOAP planner earns its keep once there are branches and re-planning
(POC 02 and 03). For a single prompt-in, text-out call it is more structure than needed.

## Still open

- Run all four with a real API key and record cost per run from the `agent.llm.*` metrics.
- POC 03: compare 5 sample diffs with and without the critic (`review.critic-enabled=false`).
- POC 04: `docker compose up` and a session from Claude Code against the SSE endpoint.
- Report the `RepeatUntilAcceptable` extra-generation behaviour upstream.
