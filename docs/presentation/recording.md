# Recording script for slide 9

Target: 35 seconds in the talk. Record once at full length, cut afterwards. The run must make zero model calls, so it goes through the POC 04 server with `coach: false`; the shell path always asks for coaching and would show one call.

## Before recording

1. Copy the synthetic statements to a folder outside the repo:
   `poc-05-spending-coach/src/test/resources/statements/{bank-q3,savings-q3,card-q3,card-q3-overlap}.csv` to `statements/`.
2. Copy `poc-05-spending-coach/src/test/resources/statements/rules.csv` to `work/rules.csv` in the directory the server runs from, so every merchant is covered.
3. Delete `work/merchants-learned.csv` and `work/layouts-learned.csv` if present.
4. Set `ANTHROPIC_API_KEY=test-key`; no real key is needed. Plan logging is turned on by the start argument below.
5. Two terminals side by side, dark theme, large font: server log on the left, curl on the right.

## Take

```bash
mvn install -DskipTests
```

```bash
mvn -pl poc-04-agent-server spring-boot:run -Dspring-boot.run.arguments=--agent-server.show-planning=true
```

```bash
curl -F files=@statements/bank-q3.csv -F files=@statements/savings-q3.csv -F files=@statements/card-q3.csv -F files=@statements/card-q3-overlap.csv localhost:8080/uploads
```

```bash
curl -X POST localhost:8080/agents/analyse_spending -H 'Content-Type: application/json' -d '{"sources": ["<uuid>/bank-q3.csv", "<uuid>/savings-q3.csv", "<uuid>/card-q3.csv", "<uuid>/card-q3-overlap.csv"], "inline": [], "coach": false}'
```

```bash
curl -o summary.csv localhost:8080/reports/<runId>/summary.csv
```

Keep on screen, in this order:

1. The "formulated plan:" lines. The first lists `inferLayouts` and `coach`; after `sniffLayouts` the plan starts with `useKnownLayouts`; after `analyse` it ends `skipCoaching -> writeReport`.
2. The JSON answer: `"route": "rules-only"` and the fenced `summary.csv` in `markdown`.
3. `summary.csv` opened in a spreadsheet.

## Cut

- Drop the Spring boot banner and the Maven lines.
- Keep: plan lines (15 s), JSON route and table (12 s), spreadsheet (8 s).
- Under 40 seconds total. No audio in the clip; the transcript carries the words.
