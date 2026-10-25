# Recording script for slide 9

Target: 35 seconds in the talk. Record once at full length, cut afterwards.

## Before recording

1. Copy the synthetic statements to a folder outside the repo:
   `poc-05-spending-coach/src/test/resources/statements/{bank-q3,savings-q3,card-q3,card-q3-overlap}.csv` to `statements/`.
2. Copy `poc-05-spending-coach/src/test/resources/statements/rules.csv` to `work/rules.csv` in the directory you run from, so every merchant is covered and no categoriser call happens.
3. Delete `work/merchants-learned.csv` and `work/layouts-learned.csv` if present.
4. The shell path always asks for coaching, so set a real `ANTHROPIC_API_KEY`. The coaching note is part of the show.
5. Terminal: dark theme, large font, window sized to the slide.

## Take

```bash
mvn -pl poc-05-spending-coach spring-boot:run
```

Inside the shell:

```
x "statements"
```

Keep on screen, in this order:

1. The "formulated plan:" lines. The first lists `inferLayouts` and `coach`; after `sniffLayouts` the plan reads `useKnownLayouts -> parseStatements -> applyRules -> acceptRuledLedger -> analyse -> coach -> writeReport`.
2. The markdown summary printed by the shell: top categories, the fenced `summary.csv`, route and cost line.
3. `work/reports/<process id>/summary.csv` opened in a spreadsheet.
4. One sentence of `coach.md`.

## If the shell prints no plan lines

Record the server path instead, which is exactly what `AgentServerTest` runs with `agent-server.show-planning=true`:

```bash
mvn install -DskipTests
```

```bash
mvn -pl poc-04-agent-server spring-boot:run
```

```bash
curl -F files=@statements/bank-q3.csv -F files=@statements/savings-q3.csv -F files=@statements/card-q3.csv -F files=@statements/card-q3-overlap.csv localhost:8080/uploads
```

```bash
curl -X POST localhost:8080/agents/analyse_spending -H 'Content-Type: application/json' -d '{"sources": ["<uuid>/bank-q3.csv", "<uuid>/savings-q3.csv", "<uuid>/card-q3.csv", "<uuid>/card-q3-overlap.csv"], "inline": [], "coach": false}'
```

With `coach: false` and the full rules file this run makes zero model calls, so `ANTHROPIC_API_KEY=test-key` is enough.

## Cut

- Drop the Spring boot banner and the Maven download lines.
- Keep: plan lines (10 s), summary table (15 s), spreadsheet (5 s), coaching sentence (5 s).
- Under 40 seconds total. No audio in the clip; the transcript carries the words.
