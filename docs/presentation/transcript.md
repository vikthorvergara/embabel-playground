# Embabel in five minutes: transcript

Spoken word for word. The clock is the target time to reach each slide. About 650 words, 4:45 at a calm pace, 15 seconds of slack. The argument is Rod Johnson's post "The Embabel Vision" (Medium, June 24, 2025), paraphrased; the evidence is POC 05 in this repository.

## 0:00 Slide 1

Embabel is an agent framework for the JVM from Rod Johnson, the person who created Spring. In June 2025 he wrote down where he wants it to go. I will walk you through that vision, and for each part I will show you what held up when I built a real agent with it.

## 0:20 Slide 2

His starting point: the business value of Gen AI is inside existing business systems, and most of the first wave of agent tooling came from Python, which seldom sits next to those systems. So the goal is to make Gen AI safe and dependable where the money already is, on the JVM.

## 0:40 Slide 3

The core idea is planning. Other frameworks either have no planning step, make you chain actions by hand, as in a state machine or a graph, or ask the LLM to decide the next step. Embabel uses a classic AI planner, goal oriented action planning from games, not a model. It is deterministic, so you can predict and explain the route.

## 1:05 Slide 4

And it grows. You add an action or a goal and the planner can combine it with the existing ones in ways nobody programmed explicitly, but only ever using steps you designed. Rod compares it to how people learn: small skills, chained into bigger ones.

## 1:25 Slide 5

Second idea: the domain model. Preconditions and postconditions are mostly the data flow of your own domain objects, so types do the planning work. Those objects can have behaviour too, and you choose which methods are safe for the model to call as tools. The model becomes a data dictionary for the problem.

## 1:45 Slide 6

Third: integration and standards. It follows Spring patterns, so it drops into the applications you already run. It speaks MCP today and A2A is on the roadmap, so your agents can join what he calls the agentic web.

## 2:00 Slide 7

Fourth, my favourite line from the post: no one is going to vibe code their way into Gen AI for critical systems. Engineering practice still matters. Types, tests, refactoring.

## 2:15 Slide 8

Now the proof. I built a spending coach. Bank and card CSV exports in, any encoding, overlapping files. Seven files out: spending by category and month, recurring charges, anomalies, cash flow and a coaching note. Every number is BigDecimal in Java. The model is used for three judgement calls only: mapping an unknown header, naming a category for an unknown merchant, writing the note. Thirteen actions, six conditions, three forks.

## 2:40 Slide 9

Watch the plan lines in the log.

## 3:15 Slide 10

What you saw: the first plan included the model steps, because nothing was settled. After the files were recognised the planner replanned to the known layouts, and after the analysis it skipped coaching. Nine actions, zero model calls. That is planning, deterministic and explainable. And this is the domain model idea in code: the analysis is a plain Java record, and its annotated methods are the tools the coach calls. The model can only quote figures the record returns, and Java drops any amount that does not exist.

## 3:50 Slide 11

Scorecard. Planning, domain model, Spring integration, MCP and testing all held up: ninety-six tests, none needs an API key, and the same agent is served over MCP and REST without a code change. I did not try A2A or natural-language agents, so I make no claim about them. Costs I paid: a condition no action declares in its post list leaves the planner stuck with no explanation, a terminated run does not carry its reason, and goals exported over MCP plan across every agent on the server, so names must stay unique.

## 4:25 Slide 12

Rod is upfront that Embabel does not do everything in the vision yet. He sees code-centric frameworks like CrewAI, Google ADK and LangGraph converging with graphical ones. Embabel bets that a strong domain model is what both sides need. From my run: it is already useful when the workflow is mostly deterministic with a few judgement calls. Thank you.
