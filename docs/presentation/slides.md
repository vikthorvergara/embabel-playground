# Embabel in five minutes: slides

Twelve slides, dark background, one idea each. Everything below a `---` line is the whole text of that slide. The talk walks through building Ragbot, the agentic RAG chatbot from `github.com/embabel/ragbot`. Code is shown highlighted, never as plain text; it is the code at Ragbot's HEAD, which matches the Embabel 1.5.1 API (checked with javap). Render the diagram on slide 5 as an image.

---

## 1

**A chatbot with Embabel**

agentic RAG on the JVM

---

## 2

> What did Schumann think of Liszt?

19th-century criticism · the model cannot know it

---

## 3

**A chat is just an agent process**

`Conversation` on the blackboard · action fires on `UserMessage`

---

## 4

```java
@Action(canRerun = true, trigger = UserMessage.class)
void respond(Conversation conversation, ActionContext context) {
    var reply = context.ai()
            .withAutoLlm()
            .respond(conversation.getMessages());
    context.sendMessage(conversation.addMessage(reply));
}
```

---

## 5

**Pipeline RAG vs agentic RAG**

Image: left, a straight line `question → retrieve → stuff → answer`; right, the model in a loop with search tools `vector · text · regex · expand`.

---

## 6

```java
var reply = context.ai()
        .withLlm(properties.chatLlm())
        .withReference(toolishRag)
        .rendering("ragbot")
        .respondWithSystemPrompt(conversation, Map.of(
                "properties", properties,
                "voice", properties.voice(),
                "objective", properties.objective()));
```

---

## 7

**voice × objective**

Jinja templates · switch persona in `application.yml`

---

## 8

```java
@Bean
Chatbot chatbot(AgentPlatform agentPlatform) {
    return AgentProcessChatbot.utilityFromPlatform(agentPlatform);
}
```

new action = new capability

---

## 9

Recording, 40 seconds

---

## 10

**Lucene · Neo4j · Spring AI VectorStore**

same API · different tools for the model

---

## 11

**Trade-offs**

model chooses what to read · grounded quotes · no pipeline to tune

more round trips · depends on tool use · API moved fast

---

## 12

**Spring AI · LangChain4j · LangGraph**

retrieve-then-answer vs search-while-answering
