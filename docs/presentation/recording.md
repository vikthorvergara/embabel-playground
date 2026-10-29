# Recording script for slide 9

Target: 40 seconds in the talk. The current take is `assets/rag-chat.mp4` (39 s), embedded on the "Ragbot running" slide. Re-record with these steps if the run changes.

## Before recording

1. Clone `github.com/embabel/ragbot` outside this repository.
2. Set a model key. Ragbot's default `application.yml` uses OpenAI, so set `OPENAI_API_KEY`; to use Claude instead, set `ANTHROPIC_API_KEY` and change the chat model in `application.yml` as its README describes. Embeddings come from the default provider, so the key must cover them too.
3. Delete `./.lucene-index` if it exists, so ingestion shows on screen.
4. Two windows side by side, dark theme, large font: the shell on the left, the browser on the right.

## Take

```bash
./scripts/shell.sh
```

Inside the shell:

```
ingest
```

```
ingest https://www.gutenberg.org/files/56208/56208-h/56208-h.htm
```

```
uichat 8888
```

In the browser at `http://localhost:8888`, ask in order:

1. What did Schumann think of Liszt?
2. What did Philip Hale think of Liszt?
3. Contrast Hale and Schumann's attitude to Liszt.

Keep on screen: the tool calls appearing as the model searches, and at least one direct quote in each answer. On the third question, the two separate searches are the point of the talk.

## Check before cutting

- The answers quote the sources. If an answer has no quote, re-ask; if it still has none, say so in the talk rather than cutting around it.
- Note which tools were called on the third question; slide 5 names vector, text, regex and expand, so trim that list to what actually appeared.
- Note the answer time of the third question; the trade-off slide says agentic RAG is slower, and the number makes that concrete.

## Cut

- Drop the Spring boot banner and most of the ingestion log; keep its last line.
- Keep: ingest finishing (5 s), question 1 with tool calls and answer (10 s), question 3 with both searches and the quoted answer (25 s).
- Under 45 seconds total. No audio in the clip; the transcript carries the words.
