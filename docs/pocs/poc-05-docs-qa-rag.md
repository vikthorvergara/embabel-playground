# POC 05: Docs Q&A (RAG with verified citations)

## Goal

Learn Embabel's RAG support: index documents, let the LLM search them through tools, and make
sure the answer is actually grounded in what it found.

## Problem

Answer questions about this project ("Why does POC 02 have a single goal?", "What did we learn
about RepeatUntilAcceptable?") from its own Markdown docs, with citations that can be trusted.

## Scope

**In scope**
- Index `README.md` and `docs/**/*.md` at startup into an in-memory Lucene index
  (`embabel-agent-rag-lucene`), one section per Markdown heading.
- Keyword (BM25) search only, so no embedding model or extra API key is needed.
- Give the index to the LLM as a `ToolishRag` reference: it gets search tools and decides what
  to look up.
- Ask for an answer plus citations (source path and a verbatim quote).
- Verify every citation in plain Java: the file must exist in the index and the quote must appear
  in it (ignoring whitespace, case and Markdown emphasis). Unverifiable citations are dropped and
  an answer with none left is flagged as not verified.

**Out of scope**
- Embeddings and hybrid search, re-indexing on file changes, other file types.

## Agent design

```
UserInput ─ask─▶ Question ─draftAnswer (LLM + ToolishRag search tools)─▶ DraftAnswer
                                                                             │
                                  verifyCitations (plain Java) ─▶ Answer (goal)
```

## Done criteria

- [x] Index builds from the real docs at startup (README, the POC definitions and the retrospective).
- [x] Keyword search over the real docs finds the right file for a known topic (tested).
- [x] Citations that are made up, point at the wrong file or quote something the file does not
      say are dropped (tested, including a full planner run against the real docs).
- [ ] Ask 10 real questions with an API key and note how many answers are grounded.
- [x] "Learnings" section appended after implementation.

## Running

```bash
export ANTHROPIC_API_KEY=...
mvn -pl poc-05-docs-qa spring-boot:run
# in the shell:
x "Why does the dependency advisor have a single goal?"
```

`docs-qa.root` defaults to `..` in `application.yml` because `spring-boot:run` starts in the module
directory. `docs-qa.paths` and `docs-qa.top-k` control what is indexed and how much one search returns.

## Learnings

- **RAG is just another reference.** `withReference(new ToolishRag(name, description, searchOps))`
  adds the search tools and a prompt contribution describing them. The agent code does not call
  the index at all; the LLM does, through tools.
- **No embeddings needed to start.** `LuceneSearchOperations` works with keyword search when no
  `EmbeddingService` is configured, and can be given one later for hybrid search.
- **Documents are built by hand.** A `MaterializedDocument` of `LeafSection`s per heading was
  simple to produce; `writeAndChunkDocument` does the chunking. The document's `url` metadata is
  copied to each chunk, which is what makes the source path available for citations.
- **Grounding is checked outside the LLM.** Asking for verbatim quotes turns "is this answer
  supported?" into a string match, which is cheap and deterministic. Same idea as POC 02: the
  LLM proposes, plain code decides what survives.
- Headings are repeated in each section's text so a keyword search on a heading finds the
  section. Code fences are skipped when looking for headings (`# comment` in a bash block).
