# POC 01: Meeting Notes → Action Items

## Goal

Build the smallest useful Embabel agent end to end and learn the core programming model:
how actions, goals and typed domain objects let the GOAP planner chain steps
without hand-written orchestration.

## Problem

Given raw meeting notes as plain text, produce a structured list of action items,
each with an owner, a due date (if mentioned) and a priority.

## Scope

**In scope**
- One agent class annotated with `@Agent`.
- Three actions, each a method annotated with `@Action`:
  1. `parseNotes(UserInput) -> MeetingNotes`: plain Java, reads the title and participants.
  2. `extractActionItems(MeetingNotes) -> ActionItemDraft`: LLM call returning a typed object.
  3. `prioritise(ActionItemDraft, MeetingNotes) -> ActionItemReport`: marked with `@AchievesGoal`;
     matches owners against the participant list and sorts by priority and due date.
- Java records as domain objects (`MeetingNotes`, `ActionItem`, `ActionItemDraft`, `ActionItemReport`).
- Run through the Embabel interactive shell (`x "<notes>"`).

**Out of scope**
- External tools, persistence, multiple LLMs, UI.

## Agent design

```
UserInput ──parseNotes──▶ MeetingNotes ──extractActionItems──▶ ActionItemDraft ──prioritise──▶ ActionItemReport (goal)
```

The planner infers this chain from method signatures alone. There is no explicit wiring,
which is the main thing this POC should prove.

## Done criteria

- [x] `mvn -pl poc-01-meeting-notes verify` passes with no network access.
- [x] Unit tests cover each action with a stubbed LLM response.
- [ ] Running the shell with a sample transcript prints a valid `ActionItemReport`.
- [ ] Logs show the plan the planner chose (the three actions in order).
- [x] A short "Learnings" section is appended here after implementation.

## Questions to answer

- How does the planner pick between two actions that produce the same type?
- What does the LLM call look like when it returns a typed record, and how are malformed responses handled?
- How are prompts kept testable (templates vs inline strings)?

## Running

```bash
export ANTHROPIC_API_KEY=...
mvn -pl poc-01-meeting-notes spring-boot:run
# in the shell:
x "# Sprint 42 planning
Attendees: Ana, Bruno
Bruno: I'll fix the checkout bug by Friday."
```

The default model is set in `src/main/resources/application.yml` (`embabel.models.default-llm`).

## Learnings

- No wiring is needed: the three methods chain purely because the output type of one is the
  input type of the next. The planner also passes `MeetingNotes` into `prioritise` even though it
  was produced two steps earlier, because everything stays on the blackboard.
- Only one of the three actions needs an LLM. Parsing and ordering are plain Java, which keeps
  them cheap, deterministic and easy to test.
- `FakeOperationContext` (it ships in `embabel-agent-api`) records every prompt, so tests can assert
  on the prompt text without calling a model.
- `AgentMetadataReader().createAgentMetadata(agent)` is a quick way to check in a unit test that the
  agent has the actions and goal we expect.
- Typed output: `createObject(prompt, ActionItemDraft.class)` builds a JSON schema from the record.
  `@JsonPropertyDescription` on record components is the easiest way to steer individual fields.
