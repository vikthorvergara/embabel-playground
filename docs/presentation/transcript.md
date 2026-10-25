# Embabel in five minutes: transcript

Spoken word for word. The clock is the target time to reach each slide. About 650 words, 4:45 at a calm pace, 15 seconds of slack.

## 0:00 Slide 1

Embabel is an agent framework for the JVM, built on Spring. Five proofs of concept later, I will show you the fifth, the one where the framework had to earn its place.

## 0:20 Slide 2

The idea is small. You write Java methods with typed inputs and a typed output. You annotate them. One method is marked as the goal. There is no graph to wire. A planner looks at the types on the blackboard and at what each method produces, and it picks the order at runtime. After every step it plans again.

## 0:45 Slide 3

This is how the planner sees your code. Each action consumes types and produces one. The goal is a type too. The planner searches a path from what it has to what the goal needs: goal oriented action planning, from game AI, with your records as the world state.

## 1:05 Slide 4

Branching is two actions that produce the same type with opposite preconditions. A condition is a method returning a boolean. One thing bit me in the second POC: a condition no action lists in its `post` array is invisible to the planner, the plan never forms, the process ends stuck. So the action that settles a fork declares both sides in `post`.

## 1:30 Slide 5

The POC. Bank and card CSV exports in: any encoding, decimal commas, pending rows, overlapping files. Seven files out: spending by category and month, every row with how its category was decided, recurring charges, anomalies, cash flow, a coaching note. Every number is BigDecimal in Java. The model is asked three things only: a column mapping for a header nobody has seen, a category for merchants the rules do not know, and the note. Each answer is checked in Java: the mapping must parse five rows, unknown merchant ids are dropped, the note keeps only amounts that exist.

## 2:05 Slide 6

Thirteen actions, six conditions, three forks. The same branching shape three times in one agent. Each fork has a cheap deterministic side and a model side, and the planner takes the model side only when the data forces it.

## 2:20 Slide 7

Code. `sniffLayouts` recognises each file from a synonym table, configured layouts and layouts learned on earlier runs, and declares both conditions in `post`. Below it the two condition methods, one line each. Then `useKnownLayouts`, producing the same `LayoutMappings` type as the model-backed `inferLayouts`. That is the whole fork.

## 2:45 Slide 8

The categoriser. It sends distinct merchants, not rows, in batches of forty through `parallelMap`. Temperature zero. A guardrail checks every prompt for account numbers, tax ids and emails before it leaves the JVM. The output record uses the category enum with Bean Validation, so an invented category never reaches the report: it fails binding and Embabel retries, three times at most by configuration.

## 3:10 Slide 9

The recording. Synthetic statements, a rules file covering every merchant, no coaching requested. Watch the plan lines.

## 3:45 Slide 10

What you saw. The first plan listed `inferLayouts` and `coach`, because nothing was settled yet. After `sniffLayouts` it replanned to `useKnownLayouts`; after `analyse`, to `skipCoaching`. Nine actions, zero model calls, route rules-only. Same bank next month: merchants learned on the first run keep it at zero. Ninety-six tests in the module, none needs an API key.

## 4:00 Slide 11

Trade-offs. Plus: the types are the contract, so the planner, the JSON schema for the model, the MCP tool schema and the REST body come from the same records. Deterministic is the default, the model the exception. And it all tests without a key. Minus: a forgotten `post` entry means a stuck planner and nothing tells you why. Terminating from inside an action leaves the failure info null, so I bind the reason to the blackboard myself. And each flow test class boots Spring for a hundred seconds.

## 4:30 Slide 12

Where it sits. Spring AI alone gives you tool calling; you write the control flow. LangChain4j, the same. LangGraph makes you draw the graph. Embabel plans the graph from your types and conditions and re-plans after every step. It fits when the workflow is mostly deterministic with a few judgement calls. Here the planner earned its keep at the third fork. Thank you.
