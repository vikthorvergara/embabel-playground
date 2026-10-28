# Embabel in five minutes: transcript

Spoken word for word. The clock is the target time to reach each slide. About 650 words, 4:45 at a calm pace, 15 seconds of slack. The subject is Ragbot, the agentic RAG chatbot published by the Embabel team (`github.com/embabel/ragbot`), described in "Building a Chatbot with Embabel Agentic RAG" (Rod Johnson, January 2026). Code shown is Ragbot's current code, which matches the Embabel 1.5.1 API.

## 0:00 Slide 1

Embabel is an agent framework for the JVM, built on Spring. Today I will build a chatbot with it, step by step, and show it answering questions from documents it has never seen.

## 0:15 Slide 2

Here is the question we want answered: what did Schumann think of Liszt? The documents are Robert Schumann's music criticism from the 1830s and Philip Hale's program notes from the early 1900s. They are obscure on purpose. If the bot answers with quotes, it read them; it did not remember them.

## 0:40 Slide 3

First idea: in Embabel there is no special chatbot type. A chat is an ordinary agent process that lives for a while and reacts to messages. The conversation sits on the blackboard like any other object, and an action fires every time a user message arrives.

## 1:00 Slide 4

This is the smallest possible chatbot. One method. The annotation says: run me whenever a user message arrives, and you may run me again and again. Embabel injects the conversation and a context. We ask a model to respond to the messages so far and send the reply back. That works, but it only knows what the model knows.

## 1:30 Slide 5

So we add documents. Classic RAG is a pipeline: take the question, retrieve some chunks, paste them into the prompt, answer. The retrieval happens once, before the model thinks. Agentic RAG turns it around. The model gets search tools and decides what to search for, reads, and searches again if it needs to. A question that compares two critics needs two searches; a pipeline does one.

## 2:00 Slide 6

That is one line of change. `withReference` hands the model a `ToolishRag`, which exposes the search store as tools: vector search, text search, regex, expanding a chunk to its neighbours. `rendering` points at a prompt template instead of a string. And `respondWithSystemPrompt` fills that template with values from configuration.

## 2:25 Slide 7

The template splits the prompt in two. The voice is how the bot talks: an enthusiastic guide, Shakespeare, a formal lawyer. The objective is what it must do: answer from the archives, quote the sources, do not rely on general knowledge. Both live in template files, and you swap the persona in `application.yml` without touching code.

## 2:50 Slide 8

To make it a chatbot, one Spring bean. `utilityFromPlatform` finds every action in your components and uses utility scoring: when a message arrives it runs the most valuable action whose inputs are present. Today there is one action. Add another, say for a different kind of question, and the bot gains that skill with no extra wiring.

## 3:10 Slide 9

Here it is running. I ingest Schumann, then Hale's notes from Project Gutenberg, and ask three questions: what Schumann thought of Liszt, what Hale thought, and to contrast them. Watch the tool calls on the side: it searches each critic separately before it answers.

## 3:50 Slide 10

Where the documents live is pluggable. Lucene, on disk, with no external service. Neo4j, which adds navigation of document structure. Or any Spring AI vector store through an adapter. The code does not change, but the tools the model gets do: the Spring AI adapter only offers vector search.

## 4:05 Slide 11

Trade-offs. The good: the model chooses what to read, answers come with quotes, and there is no retrieval pipeline to tune by hand. The cost: every search is another round trip to the model, so answers are slower and cost more than one-shot RAG, and quality depends on how well the model uses tools. And the API moves fast: the template call was renamed between the version in the blog post and the current one.

## 4:30 Slide 12

How this compares. Spring AI and LangChain4j both ship retrieval advisors that fetch first and answer second; you can build tool-based search yourself. LangGraph can do agentic RAG, but you draw the loop. In Embabel the chat is an action, the search is a reference, and adding skills means adding actions. Thank you.
