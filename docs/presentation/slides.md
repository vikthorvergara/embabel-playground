---
marp: true
theme: uncover
paginate: true
class: invert
_paginate: false
---

![bg right:35% contain](assets/embabel.png)

# Embabel
<sup><sub>(EM·BAY·BEL)</sub></sup>

agents on the JVM · planned, not wired

---

## An agent is a Spring bean

actions · goals · conditions · your domain types

---

## Blackboard → planner → action

GOAP for workflows · Utility AI for chat

re-plans after every step

---

## `context.ai()`

model by role · tools · references · templates · guardrails

---

## Showcase: Ragbot

a chat over 19th-century music criticism

---

## A chat action

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

## Search as a reference

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

## New action = new skill

```java
@Bean
Chatbot chatbot(AgentPlatform agentPlatform) {
    return AgentProcessChatbot.utilityFromPlatform(agentPlatform);
}
```

---

## Ragbot running

<video src="assets/rag-chat.mp4" controls muted playsinline style="width:100%;max-height:75vh"></video>

---

## Same model beyond chat

workflows · MCP and REST · tests without a key

---

## Trade-offs

types are the contract · deterministic by default · Spring native

young API · missed `post` = stuck · slow integration tests

---

## Spring AI · LangChain4j · LangGraph

model calls vs hand-drawn graphs vs a planner
