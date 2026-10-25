# POC 05: Spending Coach (bank and card CSVs in, categorised spending and coaching out)

## Goal

Build the first POC a non-developer would use: a person drops bank and card exports in, gets
spreadsheets of their spending by category back, plus a short note on what to change. Learn how
Embabel behaves on a data-heavy pipeline where almost every step is plain Java and the LLM is
only asked for judgement, and how far the POC 02 branching shape scales when it is used three
times in one agent.

## Problem

Given one or more CSV exports (bank account history, credit card usage, any bank, overlapping
or not, any encoding a bank portal produces), produce:

- `summary.csv`: category by month, with totals, a monthly average and a suggested budget.
- `transactions.csv`: every row with its category and how that category was decided.
- `recurring.csv`, `anomalies.csv`, `cashflow.csv`: subscriptions, things to look at, income
  versus spend per month.
- `coach.md`: a short, grounded note on where the money goes and three things to change.

Every number is computed in `BigDecimal` in Java. The LLM is used in three places only: mapping
the columns of a CSV header nobody has seen before, naming a category for merchants the rule book
does not know, and writing the coaching note. All three are checked in Java afterwards.

## Scope

**In scope**
- Module `poc-05-spending-coach`, one `@Agent` (`SpendingCoachAgent`), one exported goal
  `analyse_spending`, thirteen actions, six conditions in three producer/consumer pairs.
- Deterministic parsing: BOM and charset detection (UTF-8, UTF-16, windows-1252 fallback),
  delimiter sniffing, RFC 4180 quoting, per-column amount format (decimal and thousands
  separators, currency symbols, trailing minus, parentheses, debit/credit marker columns), date
  patterns with month names and date-time cells, date order decided over the whole file, pending
  rows skipped, posting versus transaction date, a synonym table for headers (English and
  Portuguese), configured layouts in `application.yml`, learned layouts in
  `work/layouts-learned.csv`.
- Merchant normalisation, own-account transfer pairing across any two accounts (a card payment or
  a checking-to-savings move is never counted as spend), refund matching in any account,
  de-duplication of overlapping exports by multiplicity, foreign-currency rows kept apart.
- Rule-based categorisation with three layers (`work/rules.csv` owned by the person, built-in
  keyword rules, `work/merchants-learned.csv` written by the app) so the second upload of the
  same bank makes no LLM call for merchants seen before.
- LLM categorisation per distinct merchant in parallel batches on a cheap model at temperature
  0, capped in Java, with the taxonomy as a Java enum and bean validation on the output.
- Deterministic analysis: recurring charges, anomalies, monthly cash flow with a defined
  complete-month flag, median-based budget suggestions.
- A coaching note from a stronger model that reads figures through `@LlmTool` methods on the
  analysis object and a `Persona`, skipped when the caller did not ask for it.
- A PII guardrail on every prompt sharing one pattern set with the redactor, a clean `TERMINATED`
  end with a reason readable over REST for files that are not statements, a per-goal budget on
  the server path, cost in the report footer.
- Shell entry (`x "<folder>"`), REST through the POC 04 controller plus an upload and a download
  endpoint, MCP tool `analyse_spending`.
- Tests without an API key: unit, `FakeOperationContext`, `EmbabelMockitoIntegrationTest`,
  structure test, POC 04 server test.

**Out of scope**
- xlsx output, PDF statements, currency conversion, a web UI, a database, multi-user isolation,
  persistence of learned data beyond files under `work/`.

## Agent design

### Records

Every record that can sit on the blackboard, or nested inside one, overrides `toString()` to print
counts and names only. Embabel's logging listener renders bound objects, and the shell prints
them, so a default record `toString()` would put statement rows in the log.

| Record | Fields | Role |
|--------|--------|------|
| `StatementUpload` | `List<String> sources`, `List<InlineStatement> inline`, `boolean coach`; `InlineStatement(String name, String csv)`; compact constructor replaces a null list with `List.of()` | Entry type for REST and MCP. `sources` are names returned by `POST /uploads` (`<uuid>/bank.csv`), resolved under `work/uploads` only. The shell builds it with inline content. The schema marks all three components required, so clients send `"sources": []` or `"inline": []` when unused; the `@JsonPropertyDescription` on each list and the goal description say so. |
| `RawStatements` | `List<RawStatement> files`; `RawStatement(String name, String text, String charset, String fingerprint)` | Decoded text, BOM stripped, size checked; `fingerprint` is the first eight hex characters of the SHA-256 of the normalised header. The only large object of a run; never placed in a prompt. |
| `Layout` | `String name, Kind kind, char delimiter, int dateColumn, String dateFormat, Integer postingDateColumn, int descriptionColumn, Integer amountColumn, Integer debitColumn, Integer creditColumn, Integer markerColumn, String debitMarker, Integer statusColumn, List<String> skipStatuses, Integer currencyColumn, boolean positiveIsSpend, AmountFormat amounts`; `enum Kind { BANK, CARD }`; `AmountFormat(char decimalSeparator, char thousandsSeparator)` | How to read one bank's CSV: from `spending.layouts`, from the synonym table, from the learned file, or from an LLM `ColumnMapping`. |
| `LayoutDetection` | `Map<String, Layout> known`, `List<String> unknown` (file names) | Output of `sniffLayouts`; `unknown` settles the first condition pair. Holds names, not text. |
| `ColumnMapping` | the column fields of `Layout` (`dateColumn, dateFormat, postingDateColumn, descriptionColumn, amountColumn, debitColumn, creditColumn, markerColumn, debitMarker, statusColumn, skipStatuses, currencyColumn, kind, positiveIsSpend`), each with `@JsonPropertyDescription`; `toLayout(name, delimiter, amountFormat)` | Typed LLM output for one unrecognised header. Delimiter and amount format are never asked of the model; Java decides them per column. |
| `LayoutMappings` | `Map<String, Layout> byFile` | One layout per file, whichever branch produced it. |
| `Transaction` | `String id, LocalDate date, String account, String sourceFile, Kind kind, String rawDescription, String merchant, BigDecimal amount, String currency, Flow flow`; `enum Flow { SPEND, INCOME, TRANSFER, REFUND, CREDIT }` | One normalised row. `id` is `<file index>-<line number>`, `account` is `<kind>-<fingerprint>`, `amount` is positive with scale 2 and `flow` carries the direction. |
| `Ledger` | `List<Transaction> transactions, String currency, List<Transaction> foreign, BigDecimal unmatchedTransfers, List<String> warnings` | All files merged, transfers paired, refunds matched, duplicates removed, rows in other currencies set aside. |
| `Category` | `enum INCOME, HOUSING, UTILITIES, GROCERIES, DINING, TRANSPORT, HEALTH, INSURANCE, SUBSCRIPTIONS, SHOPPING, ENTERTAINMENT, TRAVEL, EDUCATION, FEES_AND_INTEREST, SAVINGS_AND_INVESTMENTS, CASH, GIFTS_AND_DONATIONS, TRANSFER, OTHER, UNCATEGORISED` | The taxonomy. Planner type, LLM JSON schema and CSV column all come from it; a constant outside the enum fails deserialisation and goes through Embabel's data-binding retry instead of reaching the report. |
| `Origin` | `enum USER_RULE, FLOW, RULE, LEARNED, LLM, LLM_LOW_CONFIDENCE, NONE` | Audit trail per row, written to `transactions.csv`. |
| `RuledLedger` | `List<Categorised> rows`; `Categorised(Transaction tx, Category category, Origin origin, BigDecimal confidence)`; `uncategorisedMerchants()` returns `List<Merchant>`; `Merchant(int merchantId, String name, BigDecimal typicalAmount, int occurrences)` | After the rule pass; settles the second condition pair. Only `SPEND` rows can be uncategorised. |
| `CategoryBatch` | `List<MerchantCategory> merchants`; `MerchantCategory(int merchantId, @NotNull Category category, @DecimalMin("0") @DecimalMax("1") BigDecimal confidence, String reason)` | Typed LLM output for one batch of distinct merchants. `confidence` is a `BigDecimal` because `@DecimalMin` on a `double` is a Hibernate Validator extension, not part of the Bean Validation spec. |
| `CategorisedLedger` | `List<Categorised> rows, List<String> warnings` | Final per-row categories, from either branch; batch failures and the merchant cap land in `warnings`. |
| `SpendingAnalysis` | `String currency, List<YearMonth> months, List<CategoryMonth> byMonth, List<RecurringCharge> recurring, List<Anomaly> anomalies, List<MonthlyCashFlow> cashFlow, List<BudgetSuggestion> budgets, List<MerchantTotal> topMerchants, BigDecimal unmatchedTransfers, int foreignRows, boolean coachable`; nested records `CategoryMonth(YearMonth month, Category category, BigDecimal total, int count)`, `RecurringCharge(String merchant, Category category, BigDecimal typicalAmount, int cadenceDays, int occurrences, LocalDate lastSeen, BigDecimal annualised)`, `Anomaly(String transactionId, LocalDate date, String merchant, BigDecimal amount, AnomalyType type, String detail)`, `MonthlyCashFlow(YearMonth month, BigDecimal income, BigDecimal spend, BigDecimal transfersOut, BigDecimal cardCredits, BigDecimal net, BigDecimal savingsRate, boolean complete)`, `BudgetSuggestion(Category category, BigDecimal medianMonthly, BigDecimal suggested, BigDecimal lastMonth)`, `MerchantTotal(String merchant, Category category, BigDecimal total, int count)`; `@LlmTool` methods `months()`, `spendByCategory(String month)`, `monthOverMonth()`, `recurringCharges()`, `anomalies()`, `cashFlow()`, `topMerchants(String month, int n)`, `budgetSuggestions()`; plain methods `allAmounts()` (a `TreeSet<BigDecimal>`), counts-only `toString()` | Every number the report and the coach may use, computed once. Passed to the coach with `withToolObject`. `coachable` settles the third condition pair. |
| `CoachingNotes` | `String summary, List<Insight> insights, List<SavingOpportunity> savings`; `Insight(String title, String detail, Category category, BigDecimal amount)`; `SavingOpportunity(String what, BigDecimal monthlySaving, String how)`; `static empty()`; `keepOnly(NavigableSet<BigDecimal> amounts)` | Typed coach output, trimmed in Java to amounts that exist in the analysis. |
| `TerminationReason` | `String file, String message` | Bound to the blackboard right before `TerminateAgentException` is thrown, so REST callers can read why a run ended. |
| `SpendingReport` | `String runId, List<String> files, String summaryCsv, SpendingAnalysis analysis, CoachingNotes notes, RunCost cost, String route`, plus `@JsonIgnore List<FileArtifact> artifacts`; `toString()` renders the markdown summary with `summary.csv` in a fenced block | Goal output. `runId` is the process id and the report directory name; `files` are relative names for `GET /reports/{runId}/{file}`; `summaryCsv` is the spreadsheet itself, so MCP and REST callers get it without a second call. `com.embabel.agent.domain.io.FileArtifact(File, Instant)` marks each produced file on the blackboard but is kept out of the JSON because it serialises as a server path. |
| `RunCost` | `int llmCalls, int promptTokens, int completionTokens, double costUsd, Set<String> models`; `from(AgentProcess)` | Built from `process.llmInvocationCount()`, `process.usage()`, `process.cost()` and `process.modelsUsed()`, which `LlmInvocationHistory` already provides; nothing is copied from POC 03. |
| `SpendingProperties` | `@ConfigurationProperties("spending")` record: `workDir` (`./work`), `batchSize` (40), `parallelism` (4), `maxMerchants` (400), `lowConfidence` (0.7), `dateOrder` (`DAY_FIRST`), `monthBy` (`TRANSACTION_DATE`), `currency` (`BRL`), `csvFormat` (`COMMA_DOT`), `maxFileBytes` (5 MB), `List<String> transferKeywords`, `List<Layout> layouts`; constants `LAYOUT_ROLE`, `CATEGORISER_ROLE`, `COACH_ROLE` | Same shape as `ReviewProperties`. Transfer window (3 days), refund window (60 days), recurring tolerance (10 percent) and anomaly multiplier (3) stay constants in their detector classes. |

Plain Java helpers, one small class each, no Embabel import:

- `CsvTable`: decodes bytes (UTF-8, UTF-16LE and UTF-16BE BOMs; otherwise strict UTF-8 through a
  `CharsetDecoder` with `CodingErrorAction.REPORT`, falling back to `windows-1252` on a
  `CharacterCodingException`), sniffs the delimiter (comma, semicolon, tab, pipe) by the modal
  count over the first fifty lines, picks the header as the first line with that count (preamble
  lines with the account holder and IBAN are skipped and never kept), parses RFC 4180 fields with
  embedded delimiters and newlines.
- `Amounts`: per-column format over all rows (a column where every value has at most one
  separator followed by exactly two digits is decimal-comma or decimal-dot; a single ambiguous
  cell such as `1,234` is decided by the column, not the cell), strips currency symbols and codes
  (`R$`, `€`, `$`, `£`, ISO codes), handles `12.00-` and `(12.00)`, reads a debit/credit marker
  column (`D`/`C`, `DEB`/`CRED`), returns scale 2.
- `Dates`: patterns `yyyy-MM-dd`, `dd/MM/yyyy`, `MM/dd/yyyy`, `dd.MM.yyyy`, `dd-MM-yyyy`,
  `d MMM yyyy` and `dd/MMM/yyyy` with English and Portuguese month names, each with an optional
  time part (`HH:mm`, `HH:mm:ss`, `'T'`) that is dropped. The `dd/MM` versus `MM/dd` decision is
  taken over every row of the file (any day above 12 settles it) on every parse, whatever the
  layout source; `spending.date-order` is the tie-break only when no row settles it, and that
  assumption is listed in `warnings`.
- `LayoutSniffer`: synonym table (date: `date, data, posting date, transaction date, data lançamento,
  data compra`; posting date: `posting date, data lançamento` when a second date column exists;
  description: `description, descrição, memo, details, merchant, histórico, lançamento, estabelecimento`;
  amount: `amount, valor, value, montante`; debit/credit: `debit, credit, débito, crédito, saída, entrada`;
  status: `status, situação`; currency: `currency, moeda`), configured layouts, learned layouts
  through `LayoutStore`, and the not-a-statement check: at least one column where 80 percent of
  the non-empty cells of the first 200 rows parse as dates and another where 80 percent parse as
  amounts.
- `LayoutStore`: `work/layouts-learned.csv`, one line per header fingerprint, read per run,
  `learn` is `synchronized` and writes to a temporary file moved with `ATOMIC_MOVE` and
  `REPLACE_EXISTING`. Learned layouts carry no date order; `Dates` decides that on every file.
- `StatementParser`, `MerchantNormaliser`, `TransferMatcher`, `RefundMatcher`, `Deduplicator`.
- `RuleBook` (`@Component`): loads the three rule layers at the start of every run (the files are
  small), `learn` merges into `work/merchants-learned.csv` as a map keyed by merchant, rewrites
  the whole file sorted, `synchronized`, temporary file and atomic move.
- `RecurringDetector`, `AnomalyDetector`, `Aggregator`.
- `Pii`, `Redactor`, `PiiGuardRail`, `CoachPersona`, `Prompts`, `CsvWriter`, `ReportWriter`.

### Actions and conditions

Constants on the agent: `ALL_LAYOUTS_KNOWN`, `HAS_UNKNOWN_LAYOUT`, `ALL_CATEGORISED`,
`HAS_UNCATEGORISED`, `WANTS_COACHING`, `NO_COACHING`. MCP goal tools plan across every deployed
agent, so the names stay distinct from POC 02 and 03.

1. `fromUserInput(UserInput) -> StatementUpload`, deterministic. Shell entry: the text is a
   directory or newline-separated file paths; every `*.csv` is read as bytes and decoded into
   `inline`, `coach` is `true`. Remote callers start from `StatementUpload` directly.
2. `collectStatements(StatementUpload) -> RawStatements`, deterministic. Resolves `sources`
   under `work/uploads` (`normalize()` must stay inside it, otherwise the run terminates), adds
   `inline`, decodes through `CsvTable`, computes the header fingerprint, rejects files above
   `spending.max-file-bytes` or with fewer than two lines by terminating with the file name.
   An unreadable path throws `StatementReadException`, a `RuntimeException` implementing
   `com.embabel.agent.core.NonRetryable`, so the default five attempts with backoff never run.
3. `sniffLayouts(RawStatements) -> LayoutDetection`, deterministic,
   `@Action(post = {ALL_LAYOUTS_KNOWN, HAS_UNKNOWN_LAYOUT})`. Per file: delimiter and header
   from `CsvTable`, the not-a-statement check (a file that fails it terminates the run before any
   model call, with `TerminationReason` naming the file), then synonym table, configured layouts,
   learned layouts by fingerprint; amount format per column from `Amounts`.
4. `useKnownLayouts(LayoutDetection) -> LayoutMappings`, deterministic,
   `@Action(pre = ALL_LAYOUTS_KNOWN, readOnly = true)`.
5. `inferLayouts(LayoutDetection, RawStatements, OperationContext) -> LayoutMappings`, LLM,
   `@Action(pre = HAS_UNKNOWN_LAYOUT)`. Role `layout`, temperature 0, the header plus three
   rows through `Redactor.sample`, `ColumnMapping` validated by parsing five rows in Java; a
   mapping that does not parse terminates the run quoting the header. Verified mappings go to
   `work/layouts-learned.csv` keyed by fingerprint, so the same bank is deterministic next month.
   `RawStatements` is read from the blackboard, where it has been since step 2.
6. `parseStatements(RawStatements, LayoutMappings) -> Ledger`, deterministic,
   `@Action(readOnly = true)`. `StatementParser` per file: rows whose status is in
   `skipStatuses`, or whose posting date is empty when the layout has one, are skipped as pending
   and counted; the month comes from `spending.month-by`; rows in a currency other than the
   run's main currency (the most frequent one, or `spending.currency` when files carry none) go
   to `foreign`. `MerchantNormaliser` strips card suffixes, POS prefixes, store numbers and digit
   runs. `Deduplicator` compares files with the same fingerprint over the date range both cover
   and drops only the surplus copies of a (date, signed amount, raw description) key, so two
   coffees on one day in one file both survive. `TransferMatcher` marks any row whose description
   contains a `spending.transfer-keywords` entry (`PAGAMENTO FATURA`, `PAYMENT RECEIVED`, `PIX`,
   `TED`, `DOC`, `TRANSF`, `MB WAY`, and the card issuer names) as `TRANSFER`, then pairs any two
   rows from different source files with equal amount, opposite direction, within three days and
   marks both `TRANSFER` with `TRANSFER_PAIR:<id>`; unpaired transfer debits are summed into
   `unmatchedTransfers`. `RefundMatcher` matches a credit in any account to a debit in the same
   account with the same merchant in the previous sixty days and an amount up to the debit's,
   marking it `REFUND` with `REFUND_OF:<id>` (partial refunds allowed); an unmatched credit on a
   card account is `CREDIT` (cashback, statement credits, refunds older than sixty days), an
   unmatched credit on a bank account is `INCOME`.
7. `applyRules(Ledger) -> RuledLedger`, deterministic,
   `@Action(post = {ALL_CATEGORISED, HAS_UNCATEGORISED})`. In order: `work/rules.csv`
   (`USER_RULE`, may map a transfer row to any category), flow (`TRANSFER` rows to `TRANSFER`,
   `INCOME` to `INCOME`, a `REFUND` to the category of its matched debit, `CREDIT` to `OTHER`,
   origin `FLOW`), built-in keyword rules from `category-rules.csv` on the classpath (`RULE`),
   `work/merchants-learned.csv` (`LEARNED`); remaining `SPEND` rows are `UNCATEGORISED` with
   `NONE`. Transfer rows therefore never reach the model, which keeps the counterparty names in
   PIX, TED and MB WAY descriptions inside the JVM.
8. `acceptRuledLedger(RuledLedger) -> CategorisedLedger`, deterministic,
   `@Action(pre = ALL_CATEGORISED)`. The zero-LLM branch.
9. `categoriseRemainder(RuledLedger, OperationContext) -> CategorisedLedger`, LLM,
   `@Action(pre = HAS_UNCATEGORISED)`. Distinct merchants, not rows, at most
   `spending.max-merchants` of them by occurrence count (the rest stay `UNCATEGORISED` with a
   warning, because a process budget cannot interrupt a single action), in batches of
   `spending.batch-size`, `parallelMap` with `spending.parallelism`, role `categoriser` at
   temperature 0, `PiiGuardRail`. Java post-check: unknown `merchantId` dropped, missing
   merchants stay `UNCATEGORISED`, confidence below `spending.low-confidence` becomes
   `LLM_LOW_CONFIDENCE`, a batch that throws leaves its merchants `UNCATEGORISED` and adds a
   warning (the POC 02 degrade pattern). Accepted merchants above the threshold go to
   `RuleBook.learn`.
10. `analyse(CategorisedLedger, StatementUpload) -> SpendingAnalysis`, deterministic,
    `@Action(post = {WANTS_COACHING, NO_COACHING}, readOnly = true)`. `Aggregator`
    (`HALF_EVEN`, scale 2; a refund subtracts from its debit's category in the refund's month,
    so a category can show a negative month, which is stated in the markdown and tested;
    `TRANSFER` and `CREDIT` excluded from spend), `RecurringDetector` (same merchant at least
    three times, intervals near 7, 14 or 30 days, amounts within tolerance by `compareTo`),
    `AnomalyDetector` (`LARGE_FOR_MERCHANT` above three times the merchant median with at least
    three prior rows, `DUPLICATE_CHARGE` same merchant and amount within two days,
    `FEE_OR_INTEREST` rows), cash flow per month, `BudgetSuggestion` only with two or more
    complete months. A month is complete when every account in the upload covers it: an
    account's coverage window is the union of its files' date ranges, and it covers a month when
    the window starts on or before the 3rd and ends on or after the 28th, which lets two
    consecutive card billing cycles complete the month between them. `savingsRate` is
    `(income - spend) / income` at scale 4 and is left null when income is zero, as it is for a
    card-only upload. `coachable` is `upload.coach() && spend rows >= 10`. `StatementUpload` is
    read from the blackboard (the POC 01 learning).
11. `skipCoaching(SpendingAnalysis) -> CoachingNotes`, deterministic,
    `@Action(pre = NO_COACHING)`. Returns `CoachingNotes.empty()`.
12. `coach(SpendingAnalysis, OperationContext) -> CoachingNotes`, LLM,
    `@Action(pre = WANTS_COACHING)`. Role `coach`, the analysis as a tool object, the `MATH`
    tool group, a `Persona`, `CurrentDate`, `PiiGuardRail`. `keepOnly(analysis.allAmounts())`
    drops any insight or saving whose amount is not in the analysis; at most five insights and
    three savings are kept.
13. `writeReport(SpendingAnalysis, CoachingNotes, CategorisedLedger, OperationContext) -> SpendingReport`,
    deterministic, `@AchievesGoal(description = "Produced a categorised spending report from bank and card statements; sources and inline are arrays, send an empty one when unused", export = @Export(name = "analyse_spending", remote = true, startingInputTypes = StatementUpload.class))`,
    `@Action(actionRetryPolicy = ActionRetryPolicy.FIRE_ONCE)` so files are never written
    twice. `ReportWriter` creates `work/reports/<process id>/` (unique per run, so two runs in
    the same second or two concurrent REST calls cannot collide inside the one action that
    must not retry), writes the files, returns `FileArtifact`s, the relative names, the text of
    `summary.csv`, `RunCost.from(context.getAgentProcess())` and the route (`rules-only` when no
    row has origin `LLM` or `LLM_LOW_CONFIDENCE`, otherwise `model`).

Conditions, one domain parameter each, as in POC 02 and 03:

```java
@Condition(name = ALL_LAYOUTS_KNOWN)
public boolean allLayoutsKnown(LayoutDetection detection) {
    return detection.unknown().isEmpty();
}

@Condition(name = HAS_UNKNOWN_LAYOUT)
public boolean hasUnknownLayout(LayoutDetection detection) {
    return !detection.unknown().isEmpty();
}

@Condition(name = ALL_CATEGORISED)
public boolean allCategorised(RuledLedger ledger) {
    return ledger.uncategorisedMerchants().isEmpty();
}

@Condition(name = HAS_UNCATEGORISED)
public boolean hasUncategorised(RuledLedger ledger) {
    return !ledger.uncategorisedMerchants().isEmpty();
}

@Condition(name = WANTS_COACHING)
public boolean wantsCoaching(SpendingAnalysis analysis) {
    return analysis.coachable();
}

@Condition(name = NO_COACHING)
public boolean noCoaching(SpendingAnalysis analysis) {
    return !analysis.coachable();
}
```

Termination with a reason the caller can read:

```java
private <T> T terminate(OperationContext context, String file, String message) {
    context.addObject(new TerminationReason(file, message));
    throw new TerminateAgentException(message);
}
```

`TerminateAgentException` ends the process `TERMINATED` but leaves `getFailureInfo()` null, and
`AgentProcessExecution.fromProcessStatus` builds the `ProcessExecutionTerminatedException` message
from `String.valueOf(process.getFailureInfo())`, so without the bound record POC 04's 422 body
would say `"error": "null"`. The record is bound before the throw, and the handler reads it.

### Plan

```
UserInput ─fromUserInput─▶ StatementUpload ◀── REST / MCP: analyse_spending {sources, inline, coach}
                                 │
                         collectStatements   (decode, fingerprint, size check; bad path = NonRetryable)
                                 ▼
                           RawStatements ─sniffLayouts─▶ LayoutDetection
                                 │         (not a statement -> TerminationReason + TERMINATED, no model call)
                                 │                             │
                                 │          ┌── allLayoutsKnown ┴── hasUnknownLayout ──┐
                                 │          ▼                                          ▼
                                 │   useKnownLayouts                              inferLayouts
                                 │      (no LLM)                       (LLM "layout": header + 3 masked rows
                                 │          │                           -> ColumnMapping, re-parsed in Java,
                                 │          │                           saved to layouts-learned.csv)
                                 │          └──────────▶ LayoutMappings ◀──────────────┘
                                 │                             │
                                 └──────── parseStatements ◀───┘
                                                 ▼
                                              Ledger   (pending skipped, foreign set aside, merchants normalised,
                                                 │      transfers paired across files, refunds matched, dedupe by multiplicity)
                                            applyRules   (rules.csv > flow > built-in > merchants-learned.csv)
                                                 ▼
                                           RuledLedger
                                                 │
                     ┌──── allCategorised ───────┴──────── hasUncategorised ────┐
                     ▼                                                         ▼
              acceptRuledLedger                                      categoriseRemainder
                  (no LLM)                               (LLM "categoriser": distinct SPEND merchants, cap 400,
                     │                                    batches of 40, parallelMap x4, Category enum,
                     │                                    bean validation, PiiGuardRail, Java post-check)
                     └──────────────▶ CategorisedLedger ◀─────────────────────┘
                                                 │
                            analyse (no LLM; reads StatementUpload.coach)
                                                 ▼
                                         SpendingAnalysis
                                                 │
                     ┌──── noCoaching ───────────┴──────── wantsCoaching ──────┐
                     ▼                                                         ▼
                skipCoaching                                                 coach
                  (no LLM)                               (LLM "coach": @LlmTool methods on the analysis,
                     │                                    MATH tool group, Persona + CurrentDate,
                     │                                    amounts verified in Java)
                     └──────────────▶ CoachingNotes ◀─────────────────────────┘
                                                 │
   SpendingAnalysis + CoachingNotes + CategorisedLedger ─writeReport (FIRE_ONCE)─▶ SpendingReport (goal)
                                                        work/reports/<process id>/: summary.csv, transactions.csv,
                                                        recurring.csv, anomalies.csv, cashflow.csv, foreign.csv, coach.md
```

Zero-LLM route (known layouts, rules cover every merchant, `coach` false): `collectStatements`,
`sniffLayouts`, `useKnownLayouts`, `parseStatements`, `applyRules`, `acceptRuledLedger`,
`analyse`, `skipCoaching`, `writeReport`. The flow test asserts `createObject` is never called on
it.

### The three LLM steps

Layout inference, one call per unknown file:

```java
@Action(pre = HAS_UNKNOWN_LAYOUT, description = "Ask the model for a column mapping for files no layout recognises")
public LayoutMappings inferLayouts(LayoutDetection detection, RawStatements statements, OperationContext context) {
    Map<String, Layout> byFile = new LinkedHashMap<>(detection.known());
    for (String name : detection.unknown()) {
        RawStatements.RawStatement file = statements.named(name);
        CsvTable table = CsvTable.parse(file.text());
        ColumnMapping mapping = context.ai()
                .withLlm(LlmOptions.withLlmForRole(SpendingProperties.LAYOUT_ROLE).withTemperature(0.0))
                .withGuardRails(new PiiGuardRail())
                .createObject(Prompts.columnMapping(table.header(), Redactor.sample(table.rows(3))), ColumnMapping.class);
        Layout layout = mapping.toLayout(name, table.delimiter(), Amounts.formatOf(table, mapping));
        if (!StatementParser.parses(table.rows(5), layout)) {
            return terminate(context, name, "Could not read " + name + ": header " + table.header());
        }
        byFile.put(name, layout);
        layoutStore.learn(file.fingerprint(), layout);
    }
    return new LayoutMappings(byFile);
}
```

`Redactor.sample` works cell by cell: a cell that parses as an amount keeps its sign and
separators with every digit replaced by `9` (`1.234,56` becomes `9.999,99`), so balances never
leave the JVM while the format signal stays; a cell that parses as a date is kept; any other cell
is truncated to 24 characters and passed through `Pii.mask`. The preamble above the header is
never in the prompt. So the model does see three truncated description cells per unknown file;
that is the whole statement-text exposure of a run and the done criteria say so.

Merchant categorisation, bounded fan-out inside one action:

```java
@Action(pre = HAS_UNCATEGORISED, description = "Ask the categoriser model about merchants the rule book does not know")
public CategorisedLedger categoriseRemainder(RuledLedger ruled, OperationContext context) {
    List<RuledLedger.Merchant> merchants = ruled.uncategorisedMerchants().stream()
            .sorted(Comparator.comparingInt(RuledLedger.Merchant::occurrences).reversed())
            .limit(properties.maxMerchants())
            .toList();
    List<List<RuledLedger.Merchant>> batches = Batches.of(merchants, properties.batchSize());
    List<BatchResult> results = context.parallelMap(batches, properties.parallelism(), batch -> categorise(batch, context));
    CategorisedLedger ledger = ruled.apply(results, properties.lowConfidence(), merchants.size());
    ruleBook.learn(ledger.rowsWithOrigin(Origin.LLM));
    return ledger;
}

record BatchResult(List<RuledLedger.Merchant> batch, CategoryBatch answer, String failure) {
}

BatchResult categorise(List<RuledLedger.Merchant> batch, OperationContext context) {
    try {
        CategoryBatch answer = context.ai()
                .withLlm(LlmOptions.withLlmForRole(SpendingProperties.CATEGORISER_ROLE).withTemperature(0.0))
                .withGuardRails(new PiiGuardRail())
                .createObject(Prompts.categorise(batch), CategoryBatch.class);
        return new BatchResult(batch, answer, null);
    } catch (RuntimeException e) {
        return new BatchResult(batch, null, e.getMessage());
    }
}
```

`BatchResult` is a local record, never an LLM output type, so the JSON schema sent to the model
stays the pure `CategoryBatch`. The prompt lists every `Category` with a one-line definition and
each merchant as `m<id>: <masked name> (typical 23.90, seen 4 times)`. Nothing else from the
statement is in it. On this runner also call the `PromptRunner` boolean switch that controls the
auto-generated schema sample (its name starts with `withGenerate`, pass `false`): the sample is
regenerated per batch, costs tokens and can introduce invented merchant text.

Coaching, grounded through tools:

```java
@Action(pre = WANTS_COACHING, description = "Write a short coaching note from the analysis")
public CoachingNotes coach(SpendingAnalysis analysis, OperationContext context) {
    CoachingNotes notes = context.ai()
            .withLlmByRole(SpendingProperties.COACH_ROLE)
            .withToolObject(analysis)
            .withToolGroup(CoreToolGroups.MATH)
            .withPromptContributor(CoachPersona.CALM)
            .withPromptContributor(new CurrentDate())
            .withGuardRails(new PiiGuardRail())
            .createObject(Prompts.coach(analysis.months()), CoachingNotes.class);
    return notes.keepOnly(analysis.allAmounts());
}
```

`CoachPersona.CALM` is
`new Persona("Mara", "a calm, frugal, non-judgemental money coach", "plain language, short sentences", "help the person see where the money goes and pick three changes")`
(`com.embabel.agent.prompt.persona.Persona`). The prompt text carries only the list of months and
tells the model to fetch every figure through the tools and quote amounts exactly; merchant-level
detail is reachable only through `topMerchants` and `recurringCharges`.

`keepOnly` compares with `compareTo`, never `equals`: the parser produces scale 2 (`23.90`) and
Jackson binds the model's JSON number as `23.9`, which `BigDecimal.equals` treats as different.
`allAmounts()` is a `TreeSet`, so `contains` already uses `compareTo`; the anomaly and recurring
detectors compare the same way.

The PII patterns live in one class so the redactor and the guardrail cannot disagree:

```java
public final class Pii {

    static final Pattern DATE = Pattern.compile("\\b(?:\\d{4}-\\d{2}-\\d{2}|\\d{2}[./-]\\d{2}[./-]\\d{4})\\b");
    static final Pattern IBAN = Pattern.compile("\\b[A-Z]{2}\\d{2}[A-Z0-9]{11,30}\\b");
    static final Pattern EMAIL = Pattern.compile("[\\w.+-]+@[\\w-]+\\.[\\w.-]+");
    static final Pattern DIGIT_GROUP = Pattern.compile("\\d(?:[ .-]?\\d){7,}");

    public static boolean found(String text) {
        String withoutDates = DATE.matcher(text).replaceAll(" ");
        return IBAN.matcher(withoutDates).find()
                || EMAIL.matcher(withoutDates).find()
                || DIGIT_GROUP.matcher(withoutDates).find();
    }

    public static String mask(String text) {
        String masked = IBAN.matcher(text).replaceAll("#");
        masked = EMAIL.matcher(masked).replaceAll("#");
        return DIGIT_GROUP.matcher(masked).replaceAll("#");
    }
}
```

`DIGIT_GROUP` is eight or more digits with optional dot, dash or space separators, which covers
a Brazilian CPF (`123.456.789-09`), a Portuguese NIF, 8 to 11 digit account numbers, phone numbers
and card numbers; dates are taken out first so `2026-09-12` in a layout prompt does not trip the
guardrail. `Pii.mask` is applied to description cells only, after the row is split, so dates and
amounts in their own columns are untouched.

```java
public final class PiiGuardRail implements UserInputGuardRail {

    @Override
    public String getName() {
        return "pii";
    }

    @Override
    public String getDescription() {
        return "Blocks prompts that still carry an account, card or tax number";
    }

    @Override
    public ValidationResult validate(String prompt, Blackboard blackboard) {
        if (!Pii.found(prompt)) {
            return new ValidationResult(true, List.of());
        }
        return new ValidationResult(false,
                List.of(new ValidationError("pii", "personal identifier in prompt", ValidationSeverity.CRITICAL)));
    }
}
```

`ValidationResult`, `ValidationError` and `ValidationSeverity` are in
`com.embabel.common.core.validation`. Only `CRITICAL` throws `GuardRailViolationException`;
`categorise` catches it per batch, `inferLayouts` and `coach` let it fail the action.

### Module layout and configuration

`poc-05-spending-coach` under the existing parent (add it to `<modules>`), package
`io.github.vikthorvergara.playground.spending`, flat like POC 01-03. Dependencies:
`embabel-agent-starter-shell`, `embabel-agent-starter-anthropic`, `embabel-agent-test` (test).
Boot plugin with `<classifier>exec</classifier>`. No CSV, spreadsheet, JSON or validation
library: `hibernate-validator` arrives through `embabel-agent-ai`, which declares
`spring-boot-starter-validation`; learned layouts and merchants are CSV lines written by the
module's own `CsvWriter`, because a shell-only module has Jackson 3 databind on the classpath but
no `spring-boot-jackson` autoconfiguration, so there is no `ObjectMapper` bean to lean on. Add
`work/` to `.gitignore`.

```yaml
spring:
  application:
    name: poc-05-spending-coach

embabel:
  models:
    default-llm: claude-sonnet-4-6
    llms:
      layout: claude-haiku-4-5
      categoriser: claude-haiku-4-5
      coach: claude-sonnet-4-6
  agent:
    platform:
      llm-operations:
        data-binding:
          max-attempts: 3

spending:
  work-dir: ${SPENDING_WORK_DIR:./work}
  batch-size: 40
  parallelism: 4
  max-merchants: 400
  low-confidence: 0.7
  date-order: DAY_FIRST
  month-by: TRANSACTION_DATE
  currency: BRL
  csv-format: COMMA_DOT
  max-file-bytes: 5242880
  transfer-keywords:
    - PAGAMENTO FATURA
    - PAYMENT RECEIVED
    - PIX
    - TED
    - DOC
    - TRANSF
    - MB WAY
  layouts:
    - name: generic-signed
      kind: BANK
      delimiter: ","
      date-column: 0
      date-format: yyyy-MM-dd
      description-column: 1
      amount-column: 2
      positive-is-spend: false
```

Two different mistakes give two different errors. A role used in code (`withLlmForRole("layout")`)
that has no `embabel.models.llms` entry fails at the first call with `NoSuitableModelException`;
an entry whose value is not a registered model id (`claude-haiku-4-5` and `claude-sonnet-4-6`
are in Embabel's Anthropic model list) is fatal at startup. The structure test guards the first
by asserting every role constant on `SpendingProperties` has an entry in the module's
`application.yml`; the second shows up the moment the app boots. The data-binding cap of 3
replaces the default 10 retries on a bad enum value or a Jackson error.

`work/` holds `uploads/<uuid>/` (REST uploads, stored as bytes), `rules.csv` (`pattern,category`,
substring on the normalised merchant or `re:` for a regex), `merchants-learned.csv`
(`merchant,category,confidence`, one line per merchant, rewritten by the app),
`layouts-learned.csv`, `reports/<process id>/`.

POC 04 changes, all additive: a dependency on `poc-05-spending-coach` excluding
`embabel-agent-starter-shell`; `@Import` adds `SpendingCoachAgent`, `RuleBook`, `LayoutStore`;
`@EnableConfigurationProperties` adds `SpendingProperties`; `application.yml` gains the three
roles, `spending.work-dir` and the multipart limits, because Spring Boot's defaults are 1 MB per
file and 10 MB per request (`MultipartProperties` in `spring-boot-servlet-4.1.0`) and a
statement near `spending.max-file-bytes` would be rejected by the container before the
controller runs:

```yaml
spring:
  servlet:
    multipart:
      max-file-size: 6MB
      max-request-size: 32MB

agent-server:
  api-key: ${AGENT_SERVER_API_KEY:}
  show-planning: false
  budgets:
    analyse_spending: 0.25
```

`docker-compose.yml` mounts `./work:/work` and sets `SPENDING_WORK_DIR=/work`. `ServerProperties`
becomes `record ServerProperties(String apiKey, boolean showPlanning, Map<String, Double> budgets)`
with a compact constructor that turns a null map into `Map.of()`. `GoalRunner` applies the
budget only to goals that have one, so `review_diff` with three Sonnet rounds keeps its platform
default:

```java
public AgentProcessExecution run(Goal goal, Object input) {
    Agent agent = autonomy.createGoalAgent(input, platform, goal, false);
    ProcessOptions options = new ProcessOptions();
    Double budgetUsd = properties.budgets().get(ExportedGoals.toolName(goal));
    if (budgetUsd != null) {
        options = options.withBudget(new Budget(budgetUsd, 40, 300_000));
    }
    if (properties.showPlanning()) {
        options = options.withVerbosity(new Verbosity().showPlanning());
    }
    return autonomy.runAgent(input, options, agent);
}
```

The budget is checked between actions, so it cannot stop the fan-out inside
`categoriseRemainder`; that is what `spending.max-merchants` and the batch size are for.
`showPlanning()` only widens the plan log; `debug()` is never set, because debug logging prints
bound objects.

`AgentController` reads the bound reason, which is the one place POC 04 knows a POC 05 type:

```java
@ExceptionHandler(ProcessExecutionException.class)
ResponseEntity<Map<String, String>> failed(ProcessExecutionException e) {
    AgentProcess process = e.getAgentProcess();
    TerminationReason reason = process.last(TerminationReason.class);
    String error = reason != null ? reason.message() : String.valueOf(e.getMessage());
    return ResponseEntity.status(HttpStatus.UNPROCESSABLE_CONTENT)
            .header(PROCESS_ID_HEADER, process.getId())
            .body(Map.of("processId", process.getId(), "error", error));
}
```

One new controller in POC 04, `UploadController`: `POST /uploads` (multipart, saves the bytes
under `work/uploads/<uuid>/` with sanitised names, answers `{"sources": ["<uuid>/bank.csv"]}`)
and `GET /reports/{runId}/{file}` (`runId` must match `[A-Za-z0-9-]+`, `file` must be one of the
names the writer produces, `.csv` served as `text/csv` and `.md` as `text/markdown`).
`POST /agents/analyse_spending` and `GET /agents` work unchanged; `AgentMetrics` needs no change.

Memory: a finished process stays in POC 04's in-memory process repository until it is evicted,
with its blackboard. `RawStatements` is the only large object (the decoded text, one copy);
`Ledger`, `RuledLedger` and `CategorisedLedger` share the same `Transaction` instances, so a 5 MB
upload costs roughly twice that on the heap per run. `@Action(clearBlackboard = true)` would also
drop `StatementUpload`, so it is not used.

### Outputs

All files under `work/reports/<process id>/`, UTF-8 with a BOM so Excel reads accents, RFC 4180
quoting, ISO dates. `spending.csv-format` sets delimiter and decimal separator together:
`COMMA_DOT` writes `,` and `12.50`, `SEMICOLON_COMMA` writes `;` and `12,50`, so a decimal-comma
Excel reads numbers as numbers. Written with `Files.write` through a thirty-line `CsvWriter`.

1. `summary.csv`: `category`, one column per month (`2026-07`, `2026-08`, ...), `total`,
   `monthly_average`, `suggested_budget` (blank with fewer than two complete months),
   `last_month_vs_average_pct` (blank when the last month is not complete). Rows by total
   descending, `INCOME` and `TRANSFER` last, a final `TOTAL_SPEND` row. A negative cell means a
   refund landed in a later month than its purchase. This is the spreadsheet the person asked for.
2. `transactions.csv`: `id, date, account, source_file, kind, merchant, raw_description, amount,
   currency, flow, category, origin, confidence, flags` (`DUPLICATE`, `LARGE_FOR_MERCHANT`, `FEE`,
   `REFUND_OF:<id>`, `TRANSFER_PAIR:<id>`, `DATE_ORDER_ASSUMED`). Filter by `origin` to see which
   rule to add to `rules.csv`.
3. `recurring.csv`: `merchant, category, typical_amount, cadence_days, occurrences, last_seen, annualised`.
4. `anomalies.csv`: `date, merchant, amount, type, detail`.
5. `cashflow.csv`: `month, income, spend, transfers_out, card_credits, net, savings_rate_pct, complete_month`;
   `savings_rate_pct` blank when income is zero.
6. `foreign.csv`: rows in other currencies, same columns as `transactions.csv`, written only when
   there are any; they are excluded from every total and mentioned in the markdown.
7. `coach.md`: summary, insights, savings, a note on unmatched transfers when non-zero, then
   the cost line.

`SpendingReport.toString()` is the markdown the REST `markdown` field and the MCP tool return:
the top categories with totals, `summary.csv` in a fenced block, the file names with the run id,
the route and the cost line. REST callers also get `summaryCsv`, `files` and `runId` in the typed
`result` and fetch the rest from `GET /reports/{runId}/{file}`; MCP callers have the spreadsheet
in the tool text.

### Tests

Fixtures are synthetic, under `src/test/resources/statements`: `bank-q3.csv` (semicolon, decimal
comma, `dd/MM/yyyy`, windows-1252 with accents, three preamble lines with a made-up IBAN, debit
and credit columns, salary credits, two card payments, a bank fee, a PIX to a person, a
checking-to-savings transfer), `savings-q3.csv` (same header, the other side of that transfer),
`card-q3.csv` (comma, signed amounts with `R$`, `yyyy-MM-dd HH:mm`, a status column with pending
rows, three subscriptions, one partial refund, one duplicate charge, two payment credits, one
purchase in USD), `card-q3-overlap.csv` (the second half of `card-q3.csv` plus one new row),
`unknown-bank.csv` (a header nothing matches, debit/credit markers, every day of the month 12 or
lower), `not-a-statement.csv` (two columns of names), plus a `rules.csv` covering every merchant
and a smaller one leaving four unknown.

- Unit, plain JUnit: `CsvTable` (three BOMs, invalid UTF-8 falls back to windows-1252, modal
  delimiter, header after a preamble, quoted fields with embedded delimiter and newline),
  `Amounts` (decimal comma decided by the column, thousands separators, `R$`, trailing minus,
  parentheses, marker column), `Dates` (every pattern, month names in both languages, time part
  dropped, day above 12 anywhere in the file flips the order, tie-break from the property),
  `LayoutSniffer` (each layout matches its fixture, the unknown header lands in `unknown`,
  Portuguese headers resolve, the not-a-statement check rejects the names file and accepts every
  statement fixture), `LayoutStore` and `RuleBook` (per-run reload, `learn` rewrites without
  duplicates, two threads learning at once leave a parseable file), `StatementParser` (both sign
  conventions normalise, pending rows skipped, posting versus transaction date, foreign rows set
  aside), `MerchantNormaliser`, `Deduplicator` (overlap drops the surplus copies only, two
  identical rows in one file survive), `TransferMatcher` (keyword rows are `TRANSFER`, the
  checking-to-savings pair is paired, a card payment pairs within three days, never pairs one
  credit twice, unpaired debits summed), `RefundMatcher` (partial refund, bank-side reversal is
  `REFUND` not `INCOME`, unmatched card credit is `CREDIT`), `RecurringDetector`,
  `AnomalyDetector`, `Aggregator` (totals equal the rows to the cent, refund in a later month
  makes a negative cell, `TRANSFER` and `CREDIT` excluded, completeness from coverage windows
  including two card cycles, no suggestion with one complete month, savings rate null at zero
  income), `Pii`, `Redactor` (amounts become nines, dates kept, CPF and NIF masked) and
  `PiiGuardRail` (IBAN, CPF, a 16-digit card number and an email give `CRITICAL`; a prompt with
  ISO dates and merchant text passes), `CsvWriter` (quoting, BOM, both formats, round trip
  through `CsvTable`).
- Actions with `FakeOperationContext`: `categoriseRemainder` sends distinct `SPEND` merchants only,
  at most `spending.max-merchants`, and the prompt passes `Pii.found` negative
  (`context.getLlmInvocations().getFirst().getPrompt()`); a `CategoryBatch` with an unknown id and
  a missing merchant leaves the first dropped and the second `UNCATEGORISED`; the interaction
  selects the `categoriser` role (`getInteraction().getLlm().getCriteria()` is a
  `ByRoleModelSelectionCriteria` for that role, the one-line check POC 03 keeps in `Roles`,
  written inline here); `coach` offers the analysis as a tool object and the persona and current
  date as contributors (`getInteraction().getTools()` contains `spendByCategory` and
  `topMerchants`, `getInteraction().getPromptContributors()` contains the `Persona`; the fake
  context's own runner sees none of this because `withToolObject` returns a copy) and drops an
  insight whose amount is `23.9` when the analysis holds `23.90` only if the value differs, not
  when the scale differs; `inferLayouts` with a mapping that does not parse throws
  `TerminateAgentException` and leaves a `TerminationReason` on the context;
  `sniffLayouts` on the names file does the same without any invocation recorded.
  `parallelMap` works under the fake context (its dummy platform services carry a
  single-thread `ExecutorAsyncer`).
- Flow with `EmbabelMockitoIntegrationTest` (`ANTHROPIC_API_KEY=test-key`,
  `embabel.agent.shell.interactive.enabled=false`, `spending.work-dir` pointed through
  `@DynamicPropertySource` at a directory created per test class, and a `@BeforeEach` that
  copies the fixture `rules.csv` in and deletes `merchants-learned.csv` and `layouts-learned.csv`,
  so test order cannot change a result; no bean caches rule state because `RuleBook` reloads per
  run): (1) full `rules.csv`, `coach` false:
  `AgentInvocation.builder(agentPlatform).options(new ProcessOptions().withListener(events)).build(SpendingReport.class).invoke(upload)`
  completes, `verify(llmOperations, never()).createObject(any(), any(), any(), any(), any())`,
  `summary.csv` equals a golden file, `report.route()` is `rules-only`, and
  `events.getProcessEvents()` shows `acceptRuledLedger` and `skipCoaching` ran; (2) partial rules
  and `coach` true: one `CategoryBatch` call for four merchants
  (`whenCreateObject(prompt -> prompt.contains("m1:"), CategoryBatch.class)`), one `CoachingNotes`
  call with the `coach` role, `transactions.csv` shows `LLM` for the four, `coach.md` holds only
  amounts from the analysis; (3) the same upload a second time in a fresh process makes no
  `CategoryBatch` call because `merchants-learned.csv` now covers them, and `transactions.csv`
  shows `LEARNED`; (4) `unknown-bank.csv`: a stubbed `ColumnMapping` parses, the run completes,
  `layouts-learned.csv` has one line, every row carries `DATE_ORDER_ASSUMED`; (5)
  `not-a-statement.csv`: `process.getStatus()` is `TERMINATED` and
  `process.last(TerminationReason.class).file()` names it, with no `createObject` call; (6)
  `unknown-bank.csv` with a stubbed mapping whose columns are wrong: `TERMINATED` after exactly
  one call. Every flow test that reaches a model step stubs it, because an unstubbed
  `createObject` on the mock returns null and the action would fail instead of terminating.
  Mockito stubs are shared across the `parallelMap` threads; count invocations after `invoke`
  returns.
- Structure: `new AgentMetadataReader().createAgentMetadata(agent)` lists thirteen actions and
  one goal; plain reflection over `SpendingCoachAgent` asserts every `@Condition` name appears in
  some `@Action` `post` array, which guards the POC 02 stuck-planner bug before any flow test;
  every role constant has an `embabel.models.llms` entry.
- POC 04: `AgentServerTest` lists four goals, the MCP tool list contains `analyse_spending`,
  `POST /agents/analyse_spending` with inline CSV returns the markdown with the fenced
  `summary.csv`, `result.runId` equal to the process id header and `result.files`; a names file
  answers 422 with the file name in `error`; `UploadControllerTest` covers multipart save of a
  file above 1 MB, name sanitising, traversal rejection and the content types of
  `GET /reports/{runId}/{file}`.

## Done criteria

- [x] `mvn verify` passes with no network and no API key, both plan forks for layouts and
      categories covered by flow tests.
- [x] A fixture whose merchants are all in `rules.csv` runs with zero LLM calls, and a second
      upload of the same card export makes no categorisation call.
- [x] Every amount in `coach.md` exists in `SpendingAnalysis` (compared by value, not scale);
      every row in `summary.csv` adds up to the rows in `transactions.csv` to the cent; the
      checking-to-savings transfer and the card payment in the fixtures count as spend nowhere.
- [x] No prompt contains an IBAN, an email, a run of eight or more digits outside a date token,
      a balance, a file name or an untruncated description. The only statement text a model ever
      sees is three truncated, digit-masked description cells per unknown file and the masked
      merchant names of uncategorised `SPEND` rows (asserted with the recorded prompts).
- [x] A folder with a non-statement CSV ends `TERMINATED` before any model call, the REST body
      names the file, and a missing path fails without retries.
- [x] Logs show the plan the planner chose for the zero-LLM route
      (`agent-server.show-planning: true`; closes the POC 01 criterion that was never ticked).
- [ ] POC 04 publishes `analyse_spending` as the fourth MCP tool and REST route with no change to
      the agent, and a 5 MB upload passes the multipart limits.
- [ ] A run on a real bank export (local file, never committed) produces a `summary.csv` the
      person would open; cost per 1,000 rows recorded from `agent.llm.cost`. Needs a real key.
- [x] "Learnings" section appended after implementation.

## Questions to answer

- Does the POC 02 shape (settle a condition pair in `post`, two actions produce the next type)
  stay readable when used three times in one agent, or is six conditions where a plan starts to
  need a diagram to follow?
- How much cheaper is categorising distinct merchants than rows, and what does a year of
  statements cost on `claude-haiku-4-5`?
- Does a tool object with `@LlmTool` methods on a data record keep the coach honest on its own,
  or does the Java amount check still drop sentences?
- Where does a bank export break the deterministic sniffer first: preambles, date order, decimal
  commas, sign conventions or pending rows?
- Is a reason bound to the blackboard the right way to explain a `TERMINATED` run, or should
  Embabel's `AgentProcess.terminateAgent(String)`, which fills `failureInfo` at the next tick,
  replace the exception?

## Running

```bash
export ANTHROPIC_API_KEY=...
mvn -pl poc-05-spending-coach spring-boot:run
x "/path/to/statements"
```

The shell reads every `*.csv` in the folder, writes `work/reports/<process id>/` and prints the
markdown summary. Rules the person owns go in `work/rules.csv`, one `pattern,category` per line.
Set `spending.currency` and `spending.csv-format` for the person's bank and spreadsheet before the
first run.

Through the POC 04 server:

```bash
mvn install -DskipTests
mvn -pl poc-04-agent-server spring-boot:run

curl -F files=@bank-2026-09.csv -F files=@card-2026-09.csv localhost:8080/uploads
curl -X POST localhost:8080/agents/analyse_spending \
  -H 'Content-Type: application/json' \
  -d '{"sources": ["<uuid>/bank-2026-09.csv", "<uuid>/card-2026-09.csv"], "inline": [], "coach": true}'
curl -o summary.csv localhost:8080/reports/<runId>/summary.csv
```

`runId` is the `X-Agent-Process-Id` header and `result.runId` of the second call. From an MCP
client (Claude Code already registered against `http://localhost:8080/sse` as in POC 04), the
`analyse_spending` tool takes the same record and returns `summary.csv` inside the tool text.
Small statements can be pasted as `inline` entries, but then the whole statement passes through
the MCP client's own model context before it reaches the server, where the guardrail protects
nothing; upload with `POST /uploads` and pass `sources` for anything real. The budget per run is
`agent-server.budgets.analyse_spending` (0.25 USD, 40 actions, 300k tokens); the shell run and the
other goals keep the platform default.

## Showing off Embabel

Ranked by effort. Every API named here was checked against the 1.5.1 jars unless marked.

1. **Show the plan and the route** (small). `agent-server.show-planning: true` turns on
   `ProcessOptions.withVerbosity(new Verbosity().showPlanning())` in `GoalRunner`, and
   `SpendingReport.route` says `rules-only` or `model` in the markdown. The person sees that a
   rule-covered upload never called a model. The plan log prints action names only, never costs,
   so `@Action(cost = ...)` buys nothing visible and is not used; a route metric would couple POC
   04's generic listener to a POC 05 type, so the route stays in the report.
2. **Prompt caching on the categoriser** (small, measure first). The taxonomy definitions form a
   stable system prompt for every batch: `PromptRunner.withSystemPrompt(taxonomy)` plus
   `AnthropicCachingConfigKt.withAnthropicCaching(LlmOptions.withLlmForRole("categoriser"), true, false, false)`
   (`embabel-agent-anthropic`), or an `AnthropicCachingConfig` with
   `messageTypeMinContentLength(MessageRole.SYSTEM, n)` passed to the two-argument overload. The
   catch: Anthropic caches a prefix only above a per-model minimum, 4096 tokens on
   `claude-haiku-4-5` and 1024 on `claude-sonnet-4-6`, and a twenty-line taxonomy is about 500
   tokens, so on Haiku nothing caches and `cache_creation_input_tokens` stays 0. Embabel's
   `messageTypeMinContentLength` is its own threshold, not Anthropic's. Either pad the system
   prompt past 4096 tokens with per-category hints and the person's rules, or run the categoriser
   on Sonnet; print the cache-read tokens from `Usage.getNativeUsage()` in the cost line either
   way.
3. **One action fewer with a partial result** (small). Replace `useKnownLayouts` and the first
   condition pair with
   `record LayoutDetection(LayoutMappings mappings, UnresolvedFiles unresolved) implements SomeOf`
   where exactly one field is non-null: `mappings` only when every file is known, otherwise
   `unresolved` carrying the known part and the unknown names. `MultiTransformationAction` binds
   the non-null field only, so `parseStatements(RawStatements, LayoutMappings)` cannot run until
   `LayoutMappings` exists, and `inferLayouts(UnresolvedFiles)` is the sole producer on the
   unknown path; the earlier shape that bound both fields at once would have let the planner
   parse with partial mappings. Tried behind the existing flow tests; the POC 02 shape is the
   fallback. First use of `SomeOf` in the playground.
4. **The coach's reasoning next to the advice** (small). Run the coach with
   `LlmOptions.withLlmForRole("coach").withThinking(Thinking.withTokenBudget(4000))` and
   `context.ai().withLlm(options).thinking()` guarded by `supportsThinking()`, writing the
   thinking content to `coach-reasoning.md`. `Thinking.withTokenBudget(int)` has a static bridge
   on the class in `embabel-agent-ai` 1.5.1 (`javap` shows it next to the `Companion`), so the
   plain spelling compiles from Java. The person can audit why a saving was suggested.
5. **Live progress and async uploads** (medium). `POST /uploads` starts the process with
   `platform.createAgentProcessFrom(agent, options, upload)` and `platform.start(process)`,
   answers 202 with the process id, and the browser follows "parsed 1,240 rows", "batch 3 of 8",
   "report written". Actions take `ActionContext` and call `updateProgress(String)`, which sends
   a `ProgressOutputChannelEvent(processId, message)` to the `OutputChannel` set with
   `ProcessOptions.withOutputChannel(...)`; a small `OutputChannel.send` forwards it to an
   `SseEmitter`. Without an output channel the events go to `DevNullOutputChannel`.
6. **A correction form that teaches the app** (medium). With a `review` flag on
   `StatementUpload`, a `reviewCategories` action returns
   `WaitFor.formSubmission(message, CategoryCorrections.class)` where the record has one
   `@Text(label = "...")` field of `merchant = CATEGORY` lines; the process goes `WAITING`, the
   controller or MCP form tool binds the answer with `FormBindingRequest.bind(record, process)`
   and `process.run()` resumes; corrections are appended to `rules.csv` as `USER_RULE`. The
   `WAITING` state lives in memory in 1.5.1, so the review must be answered before a restart.
7. **Memory through a per-user Context** (medium). `ProcessOptions.withContextId(userId)` with
   the autoconfigured `InMemoryContextRepository`: after a run the app calls
   `contextRepository.findById(userId)` or `createWithId(userId)`, `context.addObject(rules)`,
   `contextRepository.save(context)`; the next process starts with the rules on the blackboard.
   The platform only reads the context at creation and never saves, and the store is in-memory
   with FIFO eviction, so this stays a show-off next to the file-based rules.
8. **Two models must agree on low-confidence merchants** (medium).
   `ConsensusBuilder.returning(CategoryBatch.class).sourcedFrom(List.of(haikuSupplier, sonnetSupplier))`
   (or `withSources(List<Function<SupplierActionContext<CategoryBatch>, CategoryBatch>>)`), then
   `Generators.withConsensusBy(ctx -> keep categories both agree on, UNCATEGORISED otherwise)`,
   which returns a `ConsensusSpec` with `asSubProcess(ActionContext)`; the second supplier uses
   `withLlmByRole` on a Sonnet role. Disagreements become the review list instead of a silent
   guess.
9. **Chat with your statement** (large). `AgentProcessChatbot.utilityFromPlatform(agentPlatform)`
   and an `@EmbabelComponent` with `@Action(trigger = UserMessage.class, canRerun = true)` that
   answers from the `SpendingAnalysis` tool object. "What did I spend on dining in August"
   without re-uploading. Needs a user identity for the shell (open decision below).
10. **PDF statements** (large). A `parsePdf` action chosen by the planner from a
    `PdfStatements(List<Path>)` input type, calling
    `createObject(MultimodalContent.withDocument("Extract every transaction row", AgentDocument.fromPath(path)), ParsedRows.class)`
    and returning a `Ledger` so the rest of the plan is unchanged. Whether the 1.5.1 Anthropic path
    accepts PDF parts is the first thing to test, and the whole document leaves the machine, so
    it stays opt-in.

## Dropped or corrected claims

- **`StuckHandler` to bootstrap rules**: dropped. The handler is attached to the annotated agent
  only; the goal agents that REST and MCP build with `Autonomy.createGoalAgent` get
  `AgentScope.getStuckHandler()`, which is `null`, so a first-time user would end `STUCK`. Rules
  are loaded deterministically by `RuleBook` instead.
- **Coaching as an unconditional step**: corrected. The winning design's `coach` had no
  precondition, so a rule-covered statement still made one LLM call and the zero-LLM test could
  not pass. The `wantsCoaching` / `noCoaching` pair settled by `analyse` fixes it.
- **A second exported goal for coaching**: dropped. With two goals reachable from the same input
  in one agent, a direct run (shell, `runAgentFrom`) lets the planner pick the cheaper goal; one
  goal with a flag keeps the shell path predictable.
- **`TerminateAgentException` reason readable by callers**: the reason is logged, the process
  ends `TERMINATED`, `getFailureInfo()` stays null, and `AgentProcessExecution.fromProcessStatus`
  turns that into a 422 with `"error": "null"` in POC 04. Corrected by binding `TerminationReason`
  before the throw and reading it in the handler.
- **A clean `TERMINATED` end for non-statement files through `inferLayouts`**: wrong as first
  drafted, because under the mocked `LlmOperations` an unstubbed call returns null and the action
  fails with retries. The not-a-statement check now runs in `sniffLayouts` before any model call.
- **Unit assertions through `context.getPromptRunner()`**: wrong. `FakePromptRunner.withToolObject`
  returns a copy with the tool appended and the context's own runner is never mutated, so
  `getToolObjects()` and `getPromptContributors()` on it are empty after the action. The proven
  path, used by POC 02, is `context.getLlmInvocations().getFirst().getInteraction()`.
- **`keepOnly` with `BigDecimal.equals`**: wrong, it would have dropped every insight (`23.90`
  versus `23.9`). Corrected to `compareTo` through a `TreeSet`.
- **"No prompt ever contains a balance or a raw description"**: overstated. `inferLayouts` sends
  three rows; the corrected `Redactor.sample` replaces amount digits with nines, keeps dates and
  truncates and masks description cells, and the done criterion now states exactly that exposure.
- **`updateProgress` emits `ProgressUpdateEvent`**: wrong. It sends a
  `ProgressOutputChannelEvent` to the `OutputChannel`, lives on `ExecutingOperationContext`
  (so actions need `ActionContext`), is not available on `FakeOperationContext`, and is lost on
  the default `DevNullOutputChannel`. Kept out of the core.
- **Guardrail signature**: `validate(List<UserMessage>, Blackboard)` is a default method. A Java
  class implements `getName()`, `getDescription()` and `validate(String, Blackboard)`;
  `ValidationResult`, `ValidationError` and `ValidationSeverity` are in
  `com.embabel.common.core.validation`. Only `CRITICAL` throws; `ERROR` and `WARNING` are logged
  and the call proceeds. `com.embabel.agent.api.validation.guardrails.support.TokenBudgetGuardRail(estimator, maxTokens)` defaults to `WARNING`; the
  three-argument constructor with `ValidationSeverity.CRITICAL` is the blocking one.
- **PII thresholds**: the first draft masked eight digits in the redactor and twelve in the
  guardrail, with dotted groups escaping both. One `Pii` class with one eight-digit pattern that
  accepts dot, dash and space separators, plus IBAN and email, is shared by both; dates are
  removed before matching so the layout prompt is not blocked by its own date column.
- **`Identities.withForUser(user)` as a static call**: does not compile; it is an instance method
  (`new Identities(user)` or `Identities.DEFAULT.withForUser(user)`). Identities are not in the
  core because the shell has no user.
- **`ActionException.Permanent(message)`**: only a `(String, Throwable)` constructor is exported
  to Java. The design uses a plain `RuntimeException implements com.embabel.agent.core.NonRetryable`,
  which `SpringAiRetryPolicy` honours the same way.
- **`parallelMap` with `java.util.function.Function`**: the third parameter is
  `kotlin.jvm.functions.Function1`; a lambda compiles, an existing `Function` instance needs `fn::apply`.
- **`@Action(cost = 0.5)` visible in planning logs**: `Plan.infoString` prints action names and
  the goal only, so the adornments were dropped.
- **`Persona` in `com.embabel.common.ai.prompt`**: it is `com.embabel.agent.prompt.persona.Persona`;
  `CurrentDate` is in `com.embabel.common.ai.prompt` but ships in `embabel-agent-ai`.
- **Digit-masked samples with every digit replaced** (Design 4): dropped as a blanket rule. It
  removes the signal that separates a date column from an amount column. The corrected sample
  masks amounts only, keeps dates, and masks identifiers inside text cells.
- **`SomeOf` with both fields bound at once** (first show-off 3): wrong. When some files are
  known and some are not, both `LayoutMappings` and `UnresolvedFiles` would be bound and the
  planner would run `parseStatements` with partial mappings. Reshaped so exactly one field is
  non-null and `inferLayouts` is the only producer of `LayoutMappings` on that path.
- **Prompt caching "cheaper large statements"**: not on `claude-haiku-4-5` with a 500-token
  taxonomy; the minimum cacheable prefix is 4096 tokens there. Stated in show-off 2.
- **`Thinking.withTokenBudget(4000)` needs `Thinking.Companion`**: checked with `javap` on
  `embabel-agent-ai-1.5.1`: the class carries a static `withTokenBudget(int)` next to the
  `Companion`, so the plain call compiles.
- **Consensus and chat API names left to confirm**: settled. `ConsensusBuilder.returning(Class)`,
  `sourcedFrom(List<Supplier<R>>)` or `withSources(...)`, `Generators.withConsensusBy(...)`,
  `ConsensusSpec.asSubProcess(ActionContext)`; `AgentProcessChatbot.utilityFromPlatform(AgentPlatform)`,
  `MultimodalContent.withDocument(String, AgentDocument)`, `AgentDocument.fromPath(Path)` and
  `@EmbabelComponent` all exist in `embabel-agent-api-1.5.1`.
- **`RepeatUntil` escalation to a stronger model**: dropped from the core. `RepeatUntilBuilder`
  shares the `isAcceptable` goal structure with `RepeatUntilAcceptable`, so the POC 03
  extra-iteration quirk applies; two-model agreement is the show-off instead.
- **Human review through `WaitFor.formSubmission` in the core**: moved to the show-off list.
  `@Text` targets fields (record components are fine), `bind` returns `ResponseImpact`, and the
  `WAITING` process survives only in memory in 1.5.1.
- **`ProcessOptions.withContextId` for learned rules**: moved to the show-off list; the platform
  populates the blackboard from a `Context` but never saves one, and `InMemoryContextRepository`
  evicts FIFO and does not survive a restart. Files under `work/` are the durable store here.
- **"A second run makes zero LLM calls"**: true only when `coach` is false or coaching is
  skipped; stated that way in the done criteria.
- **Copying `RunStats` and `Roles` from POC 03**: dropped. `RunStats` is a public record whose
  `from` is package-private, and the repository rule is to reuse, not copy. `RunCost` is built
  from `AgentProcess.llmInvocationCount()`, `usage()`, `cost()` and `modelsUsed()`, which the
  platform already aggregates; the role check is one `instanceof` on
  `ByRoleModelSelectionCriteria`, written inline in the test that needs it.
- **Identifiers that contain a word this repository does not write**: one rule for both. The
  `PromptRunner` switch for the schema sample (name starts with `withGenerate`) and the
  `Creating<T>` few-shot method are described by what they do and never spelled out. The switch
  is used; the few-shot method is not, because with three rule layers and the learned file the
  consistency benefit is small.
- **Learned layouts as YAML or JSON**: dropped. A shell-only module has no `ObjectMapper` bean
  (`spring-boot-jackson` is not on its classpath), YAML would need a writer, and the module
  already has a CSV writer; `layouts-learned.csv` it is.
- **Report directory by timestamp**: wrong, two runs in one second collide inside the
  `FIRE_ONCE` action after all the model spend. The directory is the process id.
- **Budget on every goal in `GoalRunner`**: wrong, it would have capped `review_diff`. Budgets
  are per goal under `agent-server.budgets`, and the merchant cap lives in Java because the
  budget is only checked between actions.
- **`@DecimalMin` on a `double` component**: works on Hibernate Validator as a vendor extension
  only; `confidence` is a `BigDecimal` on the LLM output record.
- **Bean validation needs a new dependency**: no. `embabel-agent-ai` declares
  `spring-boot-starter-validation` at compile scope, and `AbstractLlmOperations` validates the
  created object and retries through `embabel.agent.platform.llm-operations.data-binding.max-attempts`
  (default 10, set to 3 here).
- **MCP tool input as arbitrary server paths**: restricted. `sources` resolve under `work/uploads`
  only; inline content is the path for remote clients on another machine, with the privacy caveat
  stated under Running.

## Open decisions for the user

1. **CSV or xlsx.** Seven CSV files, one per sheet, open in every spreadsheet application and
   diff in tests. A single workbook needs Apache POI (`org.apache.poi:poi-ooxml`, tens of
   megabytes of transitive jars) in one `XlsxWriter` behind the same interface as `CsvWriter`.
   The repository's rules say CSV unless the workbook is a requirement; a zip of the directory
   with `java.util.zip` is the no-dependency middle ground.
2. **Where uploads come from first.** Shell (folder path, no server), REST multipart through
   `POST /uploads` plus the existing goal route, or MCP with inline content. The design supports
   all three; the question is which one gets the real bank export first, and whether a local MCP
   client should be allowed to pass absolute paths (convenient, but then the server reads any
   file it can).
3. **Where categorisation rules live.** `work/rules.csv` per installation (the design), a
   per-user directory `work/<userId>/` once the server has more than one caller, a `rules` field
   on `StatementUpload` so remote callers send their own, or the Embabel `Context` (show-off 7).
   The choice also decides who the user is: the shell has no identity, so a `spending.user-id`
   property or `Identities` from the POC 04 API key would be needed for anything per user.
4. **Coaching in the first cut or after the CSVs are proven.** The flag makes it opt-in; the
   question is whether `coach.md` is built in week one.
5. **Taxonomy.** A fixed enum gives schema strictness and compile-time safety; changing it later
   invalidates `rules.csv` lines. `PETS`, `KIDS` and `TAXES` are the candidates to add before
   the first real run.
6. **Which banks seed `spending.layouts` and the synonym table.** Two or three anonymised header
   rows from the person's own exports make the sniffer real; until then the LLM layout step does
   that work once per bank.
7. **Currency.** The default is to keep the most frequent currency for every total and set the
   other rows aside in `foreign.csv`, with `spending.currency` as the label when files carry
   none. The alternative is to refuse a mixed upload with a message. Conversion stays out.
8. **Spreadsheet locale.** `spending.csv-format: COMMA_DOT` for an English Excel,
   `SEMICOLON_COMMA` for a Portuguese or Brazilian one. Decide the default for the person's
   machine.
9. **Retention.** Uploads under `work/uploads` and reports under `work/reports` are kept until
   deleted by hand; a sweep at startup over a `spending.retention-days` property is ten lines if
   wanted.
10. **Synchronous REST.** The POC 04 controller holds the request for the whole run, fine for a
    few hundred rows; large statements need show-off 5 (async start and poll), and the size at
    which that becomes necessary should be set once a real file has been timed.
11. **Transfer keywords.** `PIX`, `TED`, `DOC` and `MB WAY` rows are treated as transfers between
    people or accounts and never sent to the model, which keeps counterparty names in the JVM but
    also means a PIX paid to a shop shows as `TRANSFER` until a `rules.csv` line says otherwise.
    Confirm the list for the person's banks.
## Learnings

Built as designed: one `@Agent` with thirteen actions, six conditions in three producer/consumer
pairs and the exported goal `analyse_spending`, the plain Java helpers, the POC 04 wiring with the
upload and download endpoints, and the tests. `mvn verify` passes across all modules with no API
key: POC 05 runs 96 tests (unit, `FakeOperationContext`, six `EmbabelMockitoIntegrationTest` flows
and the structure test) and POC 04 runs 14, including the four-goal listing, the MCP tool list, a
REST run of `analyse_spending` with zero model calls, the 422 for a names file and the per-goal
budget on the `ProcessOptions`.

### What differs from the design above

- `Transaction` has an eleventh component, `List<String> flags`. The pair and refund links
  (`TRANSFER_PAIR:<id>`, `REFUND_OF:<id>`), `DATE_ORDER_ASSUMED` and the direction of a transfer
  row (`TRANSFER_OUT` or `TRANSFER_IN`, needed once `flow` is `TRANSFER` and the sign is gone) had
  no home in the table. They are written as the `flags` column of `transactions.csv` as the design
  says; the anomaly flags (`DUPLICATE`, `LARGE_FOR_MERCHANT`, `FEE`) are added at write time.
- `MerchantTotal` carries a `YearMonth month` so the `topMerchants(month, n)` tool can answer per
  month from the same list; the tool merges months when `month` is blank.
- `@LlmTool` methods cannot be called `anomalies()` or `cashFlow()` on a record that has components
  of those names: Java requires an explicit accessor to return the component type. The tools are
  `anomalyReport()` and `cashFlowReport()` with `@LlmTool(name = "anomalies")` and
  `@LlmTool(name = "cashFlow")`, so the model still sees the names above. `months()` is the
  annotated accessor itself.
- `collectStatements` and `sniffLayouts` take an `OperationContext` to bind `TerminationReason`;
  `analyse` and `writeReport` take the `Ledger` as a parameter (unmatched transfers, foreign rows
  and `foreign.csv` come from it). Both are blackboard reads, the POC 01 learning.
- `Deduplicator` does not need the overlap window: two rows with the same account, date, signed
  amount and raw description are inside both files' date ranges by definition, so keeping the
  largest per-file multiplicity of each key is the whole rule.
- Transfer pairing is across different source files, as designed; the checking and savings fixtures
  share a header and therefore a fingerprint and an `account` label, which is why the dedupe key
  includes the raw description and the pairing does not use the account.
- `Redactor` checks `Pii.found` before the amount check. `Amounts.isAmount` strips three-letter
  currency codes, so `NIF 123456789` parsed as an amount and would have been written as nines
  rather than `#`; either hides the number, but the design says `#`.
- The termination reason names the source exactly as the caller sent it (`<uuid>/bank.csv` for an
  upload), which is what the REST caller can match on.

### What bit

1. A listener passed through `ProcessOptions.withListener` to
   `AgentInvocation.builder(platform).options(...)` received no process events, so the planned
   assertion on `events.getProcessEvents()` was empty. `AgentPlatform.runAgentFrom` returns the
   `AgentProcess`, and `process.getHistory()` lists every `ActionInvocation` by name: the zero-LLM
   route shows exactly nine actions with `acceptRuledLedger` and `skipCoaching` and without
   `inferLayouts`, `categoriseRemainder` or `coach`.
2. `TerminateAgentException` thrown from an action does what the design says: `AbstractAgentProcess`
   catches it in `doExecuteAction`, the action ends `AGENT_TERMINATED`, the process `TERMINATED`,
   `getFailureInfo()` stays null and the bound `TerminationReason` is readable with `process.last`.
   The POC 04 handler turns it into a 422 whose `error` is the message.
3. A `RuntimeException implements NonRetryable` from `collectStatements` is not turned into a
   `FAILED` process: `ActionRunner` logs "Unexpected error invoking action" once and the exception
   comes straight out of `AgentProcess.run()`, with the process still `RUNNING` and an empty action
   history. The retry template never ran the action again, which is what the criterion asks for,
   but a REST caller sees Spring's 500 for it rather than the 422 of a `TerminateAgentException`.
4. `parallelMap` under `FakeOperationContext` works as the design says, and the Mockito stubs of
   `EmbabelMockitoIntegrationTest` are shared across the fan-out threads; one `CategoryBatch` call
   covered the four unknown merchants in one batch.
5. Spring Boot binds the `List<Layout>` entries of `spending.layouts` into the record, including
   the `char` delimiter from `","`, with no converter of its own.
6. MockMvc does not run the servlet multipart limits, so `UploadControllerTest` proves the save of
   a 1.5 MB file and the name sanitising, not the 6 MB cap; that part of the POC 04 criterion needs
   a run against the real container and stays unticked.
7. The flow tests take about 100 seconds per class because every class boots a Spring context with
   the Anthropic model registrations; the unit tests take under a second.
8. With `agent-server.show-planning: true` the log prints "formulated plan:" with the action chain
   before every step. The first plan of a run lists `inferLayouts` and `coach`, because no condition
   is settled yet and the planner takes a path that reaches the goal; after `sniffLayouts` the plan
   reads `useKnownLayouts -> parseStatements -> applyRules -> acceptRuledLedger -> ...`, and after
   `analyse` it switches to `skipCoaching`. The plan the planner chose is the last one printed, not
   the first.

### Answers to the questions

- The POC 02 shape used three times stays readable in code because each condition pair sits next
  to the action that settles it and the structure test guards the `post` arrays; the plan diagram
  in this document was still the thing a reader needs to follow the route end to end.
- Categorising distinct merchants instead of rows turned 56 statement rows into one call for four
  merchants in the fixtures; the cost of a year of statements on `claude-haiku-4-5` needs a real
  key and is still open.
- Whether the tool object keeps the coach honest cannot be answered with a mocked model; the Java
  check did drop the invented insight and the invented saving the stub returned and kept the ones
  whose amounts exist (`2100.0` matched the analysis's `2100.00` by value).
- Where a real export breaks the sniffer first is still open; the fixtures cover preambles, a
  decimal-comma file with a balance column, a signed card file with pending rows and an unknown
  header, and all of them parse.
- The bound `TerminationReason` worked for both the REST path and the shell path;
  `AgentProcess.terminateAgent(String)` was not tried.

### Still open

- A run with a real key on a real export and the cost per 1,000 rows from `agent.llm.cost`.
- The 6 MB multipart cap against the real container.
- The show-off list above, none of it started.
