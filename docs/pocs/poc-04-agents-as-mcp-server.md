# POC 04: Agents as a Service (MCP server + REST + observability)

## Goal

Take the agents from POCs 01–03 out of the shell and make them usable from other
systems: MCP clients (Claude Desktop, Claude Code, IDEs) and plain HTTP. Add enough
observability to debug a run after the fact.

## Problem

An agent that only runs in a local shell is a demo. This POC proves that the same
agents can run as a service without changing their code.

## Scope

**In scope**
- A `poc-04-agent-server` Spring Boot app that depends on the POC 01–03 modules.
- Expose goals as **MCP tools** through Embabel's MCP server support (`@Export(remote = true)`):
  - `extract_action_items`: POC 01, input `MeetingTranscript { text }`
  - `advise_dependency_upgrades`: POC 02, input `PomRequest { pom }`
  - `review_diff`: POC 03, input `AutomatedReviewRequest { diff }`, which always auto-approves
- A thin REST endpoint per goal (`POST /agents/{goal}`) for non-MCP clients, plus `GET /agents`
  to list them. It runs the same single-goal agent the MCP tool builds.
- Observability: the agent process id as correlation id (logs, `X-Agent-Process-Id` header, response
  body), plus Micrometer metrics (runs by outcome, time per action, tokens and cost per agent),
  exposed at `/actuator/prometheus`.
- Optional static API key (`AGENT_SERVER_API_KEY`, sent as `X-API-Key`).
- A Dockerfile and a `docker compose` file for running locally.

**Out of scope**
- Auth beyond a static API key header, multi-tenant isolation, production deployment.

## Architecture

```
MCP client ─┐
            ├──▶ agent-server (Spring Boot) ──▶ Embabel AgentPlatform ──▶ POC 01 / 02 / 03 agents
REST client ┘                     │
                                  └──▶ logs + metrics (Micrometer / OTel)
```

## Done criteria

- [x] An MCP client lists the three tools and runs them. Checked with a plain MCP client over SSE
      (`initialize`, `tools/list`, `tools/call`) against the running server, and in `AgentServerTest`.
      Still to try from Claude Code itself with a real API key.
- [x] REST and MCP calls to the same goal return the same result: the REST `markdown` field is
      exactly the MCP tool text, and REST also returns the typed `result`.
- [x] Each run has a correlation id that appears in logs and responses.
- [ ] `docker compose up` starts the server with keys read from `.env` (git-ignored). Files are
      written but not yet run: no Docker daemon was available.
- [x] A final `docs/RETROSPECTIVE.md` summarises what we learned across all four POCs and whether Embabel fits our use cases.

## Questions to answer

- How much of the agent's typed contract survives the move to MCP tool schemas?
- How are long-running or human-in-the-loop goals exposed over a request/response protocol?
- What is the minimum telemetry needed to explain a bad agent result?

## Running

```bash
export ANTHROPIC_API_KEY=...
mvn install -DskipTests
mvn -pl poc-04-agent-server spring-boot:run
# or: cp .env.example .env && docker compose up --build

curl localhost:8080/agents
curl -X POST localhost:8080/agents/extract_action_items \
  -H 'Content-Type: application/json' \
  -d '{"text": "# Sprint 42\nAttendees: Ana, Bruno\nBruno: I will fix the checkout bug by Friday."}'
```

Connect an MCP client to the SSE endpoint, for example Claude Code:

```bash
claude mcp add --transport sse embabel-playground http://localhost:8080/sse
# with AGENT_SERVER_API_KEY set:
claude mcp add --transport sse embabel-playground http://localhost:8080/sse --header "X-API-Key: $AGENT_SERVER_API_KEY"
```

## Learnings

- **No agent code changed to go remote**, only annotations and entry points: `@Export(remote = true,
  name = ..., startingInputTypes = ...)` on each goal, plus a small input record per goal. The
  modules are imported into the server with `@Import`, so their own `@SpringBootApplication`
  classes stay out of the way (they need `<classifier>exec</classifier>` on the Boot plugin to be
  usable as plain jars).
- **`UserInput` is a poor MCP input.** Its generated JSON schema marks `timestamp` as required, and
  the first real MCP call failed schema validation. Dedicated records (`MeetingTranscript`,
  `PomRequest`) give clients a clean one-field schema.
- **Each MCP goal tool plans across the whole platform**, not just the agent that declared the
  goal. It builds a single-goal agent from every deployed action and binds the tool input. That
  means each exported goal must be reachable from its input type alone, which is why POC 02 was
  reshaped to a single goal and POC 03 got an `AutomatedReviewRequest` entry point.
- **Human in the loop over MCP is possible**: Embabel also publishes `_confirm` and form tools and
  returns instructions to the client when a process is waiting. It was simpler to give remote
  callers an auto-approving input than to rely on every client handling that correctly.
- **Minimum useful telemetry**: the process id (Embabel already prefixes its own log lines with
  it), time per action, and tokens and cost per run. An `AgenticEventListener` bean is enough to
  turn process events into Micrometer metrics, with no extra dependencies.
- The smoke test against real Maven Central was rate-limited (429) from the sandbox. POC 02 handled
  it as designed: those dependencies were reported as "could not check" instead of failing the run.
