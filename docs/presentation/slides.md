---
marp: true
theme: gaia
_class: invert
---

# 1

**Embabel**

agents on the JVM · planned, not wired

---

## 2

**An agent is a Spring bean**

actions · goals · conditions · your domain types

---

## 3

**Blackboard → planner → action**

GOAP for workflows · Utility AI for chat

re-plans after every step

---

## 4

**`context.ai()`**

model by role · tools · references · templates · guardrails

---

## 5

**Showcase: Ragbot**

a chat over 19th-century music criticism

---

## 6

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

## 7

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

## 8

```java
@Bean
Chatbot chatbot(AgentPlatform agentPlatform) {
    return AgentProcessChatbot.utilityFromPlatform(agentPlatform);
}
```

new action = new skill

---

## 9

Recording, 40 seconds

---

## 10

**Same model beyond chat**

workflows · MCP and REST · tests without a key

---

## 11

**Trade-offs**

types are the contract · deterministic by default · Spring native

young API · missed `post` = stuck · slow integration tests

---

## 12

**Spring AI · LangChain4j · LangGraph**

model calls vs hand-drawn graphs vs a planner
