# POC 02: Dependency Upgrade Advisor (tools + conditions)

## Goal

Move past pure LLM transformation. Give the agent **tools** that fetch real data and use
**conditions** to branch the plan, so the planner has to choose between paths at runtime.

## Problem

Given a `pom.xml`, report which dependencies are outdated, how risky each upgrade is
(patch/minor/major) and a recommended upgrade order.

## Scope

**In scope**
- A tool class exposing `@LlmTool` methods:
  - `latestVersion(groupId, artifactId)`: reads `maven-metadata.xml` from Maven Central.
  - `projectUrl(groupId, artifactId, version)`: best-effort SCM/project URL from the artifact's POM,
    which is where release notes usually live.
- Deterministic (non-LLM) actions for parsing the POM and comparing versions.
- Two `@Condition`s, `hasMajorUpgrades` and `noMajorUpgrades`, that route the plan to one of two
  ways of producing a `BreakingChangeAssessment`:
  - no major upgrades → `skipAssessment` (empty, no LLM call at all)
  - major upgrades present → `assessBreakingChanges` (LLM + tools)
- A single goal, `writeReport`, which needs the version matrix and an assessment, whichever path
  produced it.
- An HTTP client with a timeout and an in-memory cache for Maven Central responses.

**Out of scope**
- Actually changing the POM, Gradle support, private repositories.

## Agent design

```
UserInput ─readPom─▶ PomFile ─parsePom─▶ DependencyList ─resolveVersions─▶ VersionMatrix
                                                                                │
                              ┌──────────── noMajorUpgrades ────────────────────┤
                              ▼                                                 ▼ hasMajorUpgrades
                       skipAssessment                              assessBreakingChanges
                        (no LLM)                                      (LLM + tools)
                              └──────────▶ BreakingChangeAssessment ◀───────────┘
                                                    │
                           VersionMatrix + assessment ─writeReport─▶ UpgradeReport (goal)
```

## Done criteria

- [x] Both plan branches are covered by tests, using a fake Maven Central (a JDK `HttpServer` stub).
- [x] The agent never invents version numbers: every version in the report comes from a tool result.
- [x] Network failures degrade to "unknown" rather than failing the whole run.
- [ ] A run against this repo's own POM produces a readable report.
- [x] "Learnings" section appended after implementation.

## Questions to answer

- Mixing deterministic and LLM actions: when should a step *not* use the LLM?
- How are tool results shown to the LLM, and how much do they cost in tokens?
- How does the planner re-plan when a condition changes mid-run?

## Running

```bash
export ANTHROPIC_API_KEY=...
mvn -pl poc-02-dependency-advisor spring-boot:run
# in the shell, pass a path to a project (or paste a pom.xml):
x "/path/to/some/project"
```

## Learnings

- **Conditions need a producer.** The first version got stuck before running a single action:
  the GOAP planner could not find any action that establishes `noMajorUpgrades`, so neither goal
  looked reachable. Declaring `@Action(post = {"hasMajorUpgrades", "noMajorUpgrades"})` on
  `resolveVersions` fixes it: the planner assumes the step settles both conditions, runs it,
  evaluates the real values and re-plans. Useful to know before POC 03's loop.
- One goal, two routes: the first version had two goal actions (`quickReport`, `detailedReport`)
  producing the same type. Making the branch produce an intermediate type instead
  (`BreakingChangeAssessment`, empty on the quick path) keeps a single goal, which is simpler to
  call from code and to expose later.
- Give conditions explicit names (`@Condition(name = ...)`). The default name is the fully
  qualified class name plus the method, which is awkward to reference in `pre`.
- Most of this agent is deterministic: parsing, version lookups, classification and ordering are
  plain Java. The LLM only writes the risk notes, and only when there is a major upgrade. The
  quick path makes zero LLM calls, which the flow test checks.
- The LLM output is filtered afterwards: notes for coordinates we did not flag are dropped, and all
  version numbers in the report come from `VersionMatrix`, never from the LLM.
- `EmbabelMockitoIntegrationTest` (in `embabel-agent-test`) runs the real platform and planner with
  only `LlmOperations` mocked. It needs `embabel.agent.shell.interactive.enabled=false`, otherwise
  the shell starts inside the test and waits for input forever.
- Tools are plain objects with `@LlmTool` methods passed in with `withToolObject(...)`.
  `FakeOperationContext` records which tools were offered, so that is testable too.
- Guava-style versions (`33.4.8-jre`) are "major" whenever the first number changes. The report
  uses plain numeric comparison, so treat MAJOR for those libraries with some scepticism.
