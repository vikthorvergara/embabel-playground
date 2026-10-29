# Embabel in five minutes: transcript

Spoken word for word. The clock is the target time to reach each slide. About 650 words, 4:45 at a calm pace, 15 seconds of slack. The subject is Embabel; Ragbot (`github.com/embabel/ragbot`), the agentic RAG chatbot published by the Embabel team, is the running showcase. Code shown is Ragbot's current code, checked with javap against the Embabel 1.5.1 jars. Claims about workflows, MCP, REST and testing come from the five POCs in this repository.

## 0:00 Slide 1

Embabel is an agent framework for the JVM, created by Rod Johnson, the person behind Spring. Its one big idea: you do not wire the steps of an agent together. You describe the steps, and a planner decides the order at runtime.

## 0:20 Slide 2

An agent is a Spring bean. Its methods are annotated as actions, each with typed inputs and one typed output. A goal says what the agent is trying to produce. Conditions are methods that return a boolean, for branching. And the types are your own domain records, so the contract between steps is code you can compile and refactor.

## 0:45 Slide 3

At runtime everything lives on a blackboard of typed objects. The planner looks at what is there and which actions can run, picks one, runs it, and plans again with the new object on the board. For workflows it uses goal oriented action planning, the technique from game AI, which finds a path to the goal. For chat it uses utility AI, which picks the most valuable action for the moment. The planner is an algorithm, not a model, so the route is predictable and explainable.

## 1:15 Slide 4

Inside an action you talk to models through one fluent API. You choose a model by role, so swapping models is configuration. You can hand the model tools, which are plain Java methods. You can give it references, like a document store. Prompts can be templates. And guardrails check what goes in and out.

## 1:40 Slide 5

To make this concrete, I will use Ragbot, a chatbot the Embabel team published. It answers questions about 19th-century music criticism: obscure on purpose, so the model has to read the documents instead of remembering them.

## 1:55 Slide 6

In Embabel a chat is not a special kind of program. It is the same agent process with a conversation on the blackboard. This is a complete chat action: it fires on every user message, may run again and again, asks a model to respond, and sends the reply.

## 2:20 Slide 7

The real Ragbot action is the same method with three more calls. `withReference` gives the model a search store as tools, so it decides what to read and can search again; that is agentic RAG. `rendering` uses a prompt template instead of a string. And the template is filled from configuration, so the persona changes in YAML, not in code.

## 2:45 Slide 8

To turn actions into a chatbot, one bean. It finds every action in your components and lets the utility planner choose between them when a message arrives. Add another action and the bot has a new skill, without touching this code.

## 3:05 Slide 9

Here it is running. Two critics ingested, three questions about Liszt. Watch the tool calls: for the comparison question it searches each critic on its own before answering, with quotes.

## 3:45 Slide 10

The chat is one shape. The same actions, goals and planner run plain workflows. I built five agents with it: meeting notes, a dependency advisor, a review loop with a critic, a spending coach over bank statements, and a server that exposes them all over MCP and REST without changing their code. A hundred and fifty-three tests, and none needs an API key, because Embabel ships a fake context and a mocked model for tests.

## 4:10 Slide 11

Trade-offs. The good: types are the contract, deterministic code is the default and the model is the exception, and it is Spring all the way down, so it fits the applications you already run. The cost: the API is young and moves fast; Ragbot's template call was renamed between the blog post and today. If you forget to declare a condition in an action's post list, the planner gets stuck and does not say why. And integration tests boot Spring, which is slow.

## 4:35 Slide 12

Compared to the neighbours: Spring AI and LangChain4j give you model calls, and you write the control flow. LangGraph makes you draw the graph. Embabel lets a planner draw it from your types. Thank you.
