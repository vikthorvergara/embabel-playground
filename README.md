# embabel-playground

A playground for learning the [Embabel Agent Framework](https://github.com/embabel/embabel-agent)
(JVM, Spring Boot, GOAP-planned agents) through small, focused proofs of concept.

Each POC builds on the previous one. Its definition lives in `docs/pocs/` and sets out
the goal, scope, agent design and "done" criteria before any code is written.

## POC roadmap

| #  | POC | Focus | Definition |
|----|-----|-------|------------|
| 01 | Meeting Notes → Action Items | Agent basics: `@Agent`, `@Action`, `@AchievesGoal`, typed domain objects | [poc-01](docs/pocs/poc-01-meeting-notes-agent.md) |
| 02 | Dependency Upgrade Advisor | Tools (`@LlmTool`), conditions (`@Condition`), branching plans, deterministic vs LLM actions | [poc-02](docs/pocs/poc-02-dependency-advisor.md) |
| 03 | PR Review Agent | Per-action model selection, critic/revise loop, human-in-the-loop confirmation, cost tracking | [poc-03](docs/pocs/poc-03-pr-review-critic.md) |

## Shared conventions

- **Stack:** Java 21, Spring Boot 4.1, Maven, Embabel Agent 1.5.1.
- **Layout:** one Maven module per POC (`poc-01-...`, `poc-02-...`), sharing a parent POM.
- **LLM access:** API keys only through environment variables (`ANTHROPIC_API_KEY`), never committed.
- **Default model:** `claude-sonnet-4-6` via `embabel-agent-starter-anthropic`, set per module in
  `application.yml`.
- **Tests:** every POC has unit tests that run without network or LLM access, using
  Embabel's `FakeOperationContext` to stub LLM calls.

## Build

```bash
mvn verify                                        # all modules, no API key needed
mvn -pl poc-01-meeting-notes spring-boot:run      # interactive shell, needs ANTHROPIC_API_KEY
```

Each POC runs as an interactive Embabel shell (`x "<input>"`).

## Results

Each definition ends with "Running" and "Learnings" sections written after implementation.
