package io.github.vikthorvergara.playground.spending;

import com.embabel.agent.api.annotation.AchievesGoal;
import com.embabel.agent.api.annotation.Action;
import com.embabel.agent.api.annotation.Agent;
import com.embabel.agent.api.annotation.Condition;
import com.embabel.agent.api.annotation.Export;
import com.embabel.agent.api.common.OperationContext;
import com.embabel.agent.api.tool.TerminateAgentException;
import com.embabel.agent.core.ActionRetryPolicy;
import com.embabel.agent.core.CoreToolGroups;
import com.embabel.agent.domain.io.UserInput;
import com.embabel.common.ai.model.LlmOptions;
import com.embabel.common.ai.prompt.CurrentDate;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

@Agent(description = "Turns bank and card CSV exports into categorised spending spreadsheets and a short coaching note")
public class SpendingCoachAgent {

    static final String ALL_LAYOUTS_KNOWN = "allLayoutsKnown";
    static final String HAS_UNKNOWN_LAYOUT = "hasUnknownLayout";
    static final String ALL_CATEGORISED = "allCategorised";
    static final String HAS_UNCATEGORISED = "hasUncategorised";
    static final String WANTS_COACHING = "wantsCoaching";
    static final String NO_COACHING = "noCoaching";

    static final int MIN_LINES = 2;
    static final int SAMPLE_ROWS = 3;
    static final int VERIFY_ROWS = 5;

    private final SpendingProperties properties;
    private final RuleBook ruleBook;
    private final LayoutStore layoutStore;

    public SpendingCoachAgent(SpendingProperties properties, RuleBook ruleBook, LayoutStore layoutStore) {
        this.properties = properties;
        this.ruleBook = ruleBook;
        this.layoutStore = layoutStore;
    }

    @Action(description = "Read every CSV in a folder, or the file paths listed one per line, from the shell")
    public StatementUpload fromUserInput(UserInput input) {
        List<Path> paths = new ArrayList<>();
        String text = input.getContent().strip();
        if (Files.isDirectory(Path.of(text))) {
            try (Stream<Path> listing = Files.list(Path.of(text))) {
                listing.filter(path -> path.getFileName().toString().toLowerCase().endsWith(".csv")).sorted().forEach(paths::add);
            } catch (IOException e) {
                throw new StatementReadException("Could not list " + text, e);
            }
        } else {
            text.lines().map(String::strip).filter(line -> !line.isEmpty()).map(Path::of).forEach(paths::add);
        }
        List<StatementUpload.InlineStatement> inline = new ArrayList<>();
        for (Path path : paths) {
            try {
                inline.add(new StatementUpload.InlineStatement(path.getFileName().toString(), CsvTable.decode(Files.readAllBytes(path)).text()));
            } catch (IOException e) {
                throw new StatementReadException("Could not read " + path, e);
            }
        }
        return new StatementUpload(List.of(), inline, true);
    }

    @Action(description = "Decode the uploaded statements, check their size and fingerprint their headers")
    public RawStatements collectStatements(StatementUpload upload, OperationContext context) {
        List<RawStatements.RawStatement> files = new ArrayList<>();
        Path uploads = properties.uploads().toAbsolutePath().normalize();
        for (String source : upload.sources()) {
            Path path = uploads.resolve(source).normalize();
            if (!path.startsWith(uploads)) {
                return terminate(context, source, "Source " + source + " is outside the upload directory");
            }
            byte[] bytes;
            try {
                bytes = Files.readAllBytes(path);
            } catch (IOException e) {
                throw new StatementReadException("Could not read " + source, e);
            }
            if (bytes.length > properties.maxFileBytes()) {
                return terminate(context, source, source + " is larger than " + properties.maxFileBytes() + " bytes");
            }
            CsvTable.Decoded decoded = CsvTable.decode(bytes);
            files.add(statement(source, decoded.text(), decoded.charset(), context));
        }
        for (StatementUpload.InlineStatement statement : upload.inline()) {
            String text = statement.csv() == null ? "" : statement.csv();
            if (text.getBytes(StandardCharsets.UTF_8).length > properties.maxFileBytes()) {
                return terminate(context, statement.name(), statement.name() + " is larger than " + properties.maxFileBytes() + " bytes");
            }
            files.add(statement(statement.name(), text.startsWith("﻿") ? text.substring(1) : text, "inline", context));
        }
        if (files.isEmpty()) {
            return terminate(context, "", "No statements were given");
        }
        return new RawStatements(files);
    }

    private RawStatements.RawStatement statement(String name, String text, String charset, OperationContext context) {
        if (text.strip().lines().count() < MIN_LINES) {
            return terminate(context, name, name + " has fewer than two lines");
        }
        return new RawStatements.RawStatement(name, text, charset, CsvTable.parse(text).fingerprint());
    }

    @Action(post = {ALL_LAYOUTS_KNOWN, HAS_UNKNOWN_LAYOUT},
            description = "Recognise the layout of each file from the synonym table, the configured layouts and the learned layouts")
    public LayoutDetection sniffLayouts(RawStatements statements, OperationContext context) {
        LayoutSniffer sniffer = new LayoutSniffer(properties.layouts(), layoutStore.load());
        Map<String, Layout> known = new LinkedHashMap<>();
        List<String> unknown = new ArrayList<>();
        for (RawStatements.RawStatement file : statements.files()) {
            CsvTable table = CsvTable.parse(file.text());
            if (!LayoutSniffer.looksLikeStatement(table)) {
                return terminate(context, file.name(), file.name() + " does not look like a bank or card statement");
            }
            Optional<Layout> layout = sniffer.sniff(file.name(), table, file.fingerprint());
            if (layout.isPresent()) {
                known.put(file.name(), layout.get());
            } else {
                unknown.add(file.name());
            }
        }
        return new LayoutDetection(known, unknown);
    }

    @Condition(name = ALL_LAYOUTS_KNOWN)
    public boolean allLayoutsKnown(LayoutDetection detection) {
        return detection.unknown().isEmpty();
    }

    @Condition(name = HAS_UNKNOWN_LAYOUT)
    public boolean hasUnknownLayout(LayoutDetection detection) {
        return !detection.unknown().isEmpty();
    }

    @Action(pre = ALL_LAYOUTS_KNOWN, readOnly = true, description = "Use the recognised layouts as they are")
    public LayoutMappings useKnownLayouts(LayoutDetection detection) {
        return new LayoutMappings(detection.known());
    }

    @Action(pre = HAS_UNKNOWN_LAYOUT, description = "Ask the model for a column mapping for files no layout recognises")
    public LayoutMappings inferLayouts(LayoutDetection detection, RawStatements statements, OperationContext context) {
        Map<String, Layout> byFile = new LinkedHashMap<>(detection.known());
        for (String name : detection.unknown()) {
            RawStatements.RawStatement file = statements.named(name);
            CsvTable table = CsvTable.parse(file.text());
            ColumnMapping mapping = context.ai()
                    .withLlm(LlmOptions.withLlmForRole(SpendingProperties.LAYOUT_ROLE).withTemperature(0.0))
                    .withGuardRails(new PiiGuardRail())
                    .createObject(Prompts.columnMapping(table.header(), Redactor.sample(table.rows(SAMPLE_ROWS))), ColumnMapping.class);
            Layout layout = mapping.toLayout(name, table.delimiter(), Amounts.formatOf(table, mapping));
            if (layout.width() > table.header().size() || !StatementParser.parses(table.rows(VERIFY_ROWS), layout)) {
                return terminate(context, name, "Could not read " + name + ": header " + table.header());
            }
            byFile.put(name, layout);
            layoutStore.learn(file.fingerprint(), layout);
        }
        return new LayoutMappings(byFile);
    }

    @Action(readOnly = true, description = "Parse every file into one ledger: duplicates removed, transfers paired, refunds matched")
    public Ledger parseStatements(RawStatements statements, LayoutMappings mappings) {
        List<Transaction> all = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        List<RawStatements.RawStatement> files = statements.files();
        for (int i = 0; i < files.size(); i++) {
            RawStatements.RawStatement file = files.get(i);
            StatementParser.Parsed parsed = StatementParser.parse(i + 1, file, mappings.byFile().get(file.name()),
                    properties.dateOrder(), properties.monthBy());
            all.addAll(parsed.rows());
            warnings.addAll(parsed.warnings());
            if (parsed.pending() > 0) {
                warnings.add(file.name() + ": " + parsed.pending() + " pending row(s) skipped");
            }
            if (parsed.dateOrderAssumed()) {
                warnings.add(file.name() + ": day/month order could not be settled, " + properties.dateOrder() + " assumed");
            }
        }
        String currency = mainCurrency(all);
        List<Transaction> main = new ArrayList<>();
        List<Transaction> foreign = new ArrayList<>();
        for (Transaction tx : all) {
            if (tx.currency() == null) {
                main.add(tx.withCurrency(currency));
            } else if (tx.currency().equals(currency)) {
                main.add(tx);
            } else {
                foreign.add(tx);
            }
        }
        List<Transaction> unique = Deduplicator.dedupe(main, warnings);
        TransferMatcher.Result transfers = TransferMatcher.mark(unique, properties.transferKeywords());
        return new Ledger(RefundMatcher.match(transfers.rows()), currency, foreign, transfers.unmatchedTransfers(), warnings);
    }

    private String mainCurrency(List<Transaction> rows) {
        Map<String, Integer> counts = new HashMap<>();
        rows.stream().filter(tx -> tx.currency() != null).forEach(tx -> counts.merge(tx.currency(), 1, Integer::sum));
        return counts.entrySet().stream()
                .max(Map.Entry.<String, Integer>comparingByValue().thenComparing(Map.Entry.comparingByKey()))
                .map(Map.Entry::getKey)
                .orElse(properties.currency());
    }

    @Action(post = {ALL_CATEGORISED, HAS_UNCATEGORISED},
            description = "Categorise every row from the person's rules, the money flow, the built-in rules and the learned merchants")
    public RuledLedger applyRules(Ledger ledger) {
        RuleBook.Rules rules = ruleBook.load();
        Map<String, RuledLedger.Categorised> byId = new LinkedHashMap<>();
        List<Transaction> refunds = new ArrayList<>();
        for (Transaction tx : ledger.transactions()) {
            Optional<Category> user = rules.userRule(tx.merchant());
            if (user.isPresent()) {
                byId.put(tx.id(), new RuledLedger.Categorised(tx, user.get(), Origin.USER_RULE, null));
                continue;
            }
            switch (tx.flow()) {
                case TRANSFER -> byId.put(tx.id(), new RuledLedger.Categorised(tx, Category.TRANSFER, Origin.FLOW, null));
                case INCOME -> byId.put(tx.id(), new RuledLedger.Categorised(tx, Category.INCOME, Origin.FLOW, null));
                case CREDIT -> byId.put(tx.id(), new RuledLedger.Categorised(tx, Category.OTHER, Origin.FLOW, null));
                case REFUND -> refunds.add(tx);
                case SPEND -> byId.put(tx.id(), categoriseSpend(tx, rules));
            }
        }
        for (Transaction refund : refunds) {
            RuledLedger.Categorised debit = byId.get(refund.linked(Transaction.REFUND_OF));
            byId.put(refund.id(), new RuledLedger.Categorised(refund, debit == null ? Category.OTHER : debit.category(), Origin.FLOW, null));
        }
        return new RuledLedger(ledger.transactions().stream().map(tx -> byId.get(tx.id())).toList());
    }

    private static RuledLedger.Categorised categoriseSpend(Transaction tx, RuleBook.Rules rules) {
        Optional<Category> builtIn = rules.builtInRule(tx.merchant());
        if (builtIn.isPresent()) {
            return new RuledLedger.Categorised(tx, builtIn.get(), Origin.RULE, null);
        }
        RuleBook.Learned learned = rules.learnedRule(tx.merchant());
        if (learned != null) {
            return new RuledLedger.Categorised(tx, learned.category(), Origin.LEARNED, learned.confidence());
        }
        return new RuledLedger.Categorised(tx, Category.UNCATEGORISED, Origin.NONE, null);
    }

    @Condition(name = ALL_CATEGORISED)
    public boolean allCategorised(RuledLedger ledger) {
        return ledger.uncategorisedMerchants().isEmpty();
    }

    @Condition(name = HAS_UNCATEGORISED)
    public boolean hasUncategorised(RuledLedger ledger) {
        return !ledger.uncategorisedMerchants().isEmpty();
    }

    @Action(pre = ALL_CATEGORISED, description = "Accept the rule-based categories, no model needed")
    public CategorisedLedger acceptRuledLedger(RuledLedger ruled) {
        return new CategorisedLedger(ruled.rows(), List.of());
    }

    @Action(pre = HAS_UNCATEGORISED, description = "Ask the categoriser model about merchants the rule book does not know")
    public CategorisedLedger categoriseRemainder(RuledLedger ruled, OperationContext context) {
        List<String> warnings = new ArrayList<>();
        List<RuledLedger.Merchant> merchants = ruled.uncategorisedMerchants();
        if (merchants.size() > properties.maxMerchants()) {
            warnings.add((merchants.size() - properties.maxMerchants()) + " merchant(s) left uncategorised: cap of "
                    + properties.maxMerchants() + " merchants per run");
            merchants = merchants.subList(0, properties.maxMerchants());
        }
        List<List<RuledLedger.Merchant>> batches = batches(merchants, properties.batchSize());
        List<BatchResult> results = context.parallelMap(batches, properties.parallelism(), batch -> categorise(batch, context));
        Map<String, CategoryBatch.MerchantCategory> answers = new HashMap<>();
        for (BatchResult result : results) {
            if (result.failure() != null) {
                warnings.add("A batch of " + result.batch().size() + " merchant(s) stayed uncategorised: " + result.failure());
                continue;
            }
            Map<Integer, RuledLedger.Merchant> asked = new HashMap<>();
            result.batch().forEach(merchant -> asked.put(merchant.merchantId(), merchant));
            for (CategoryBatch.MerchantCategory answer : result.answer().merchants()) {
                RuledLedger.Merchant merchant = asked.get(answer.merchantId());
                if (merchant != null && answer.category() != null) {
                    answers.put(merchant.name(), answer);
                }
            }
        }
        CategorisedLedger ledger = ruled.apply(answers, properties.lowConfidence(), warnings);
        Map<String, RuleBook.Learned> learned = new LinkedHashMap<>();
        ledger.rowsWithOrigin(Origin.LLM).forEach(row ->
                learned.put(row.tx().merchant(), new RuleBook.Learned(row.category(), row.confidence())));
        ruleBook.learn(learned);
        return ledger;
    }

    record BatchResult(List<RuledLedger.Merchant> batch, CategoryBatch answer, String failure) {
    }

    BatchResult categorise(List<RuledLedger.Merchant> batch, OperationContext context) {
        try {
            CategoryBatch answer = context.ai()
                    .withLlm(LlmOptions.withLlmForRole(SpendingProperties.CATEGORISER_ROLE).withTemperature(0.0))
                    .withGuardRails(new PiiGuardRail())
                    .withGenerateExamples(false)
                    .createObject(Prompts.categorise(batch), CategoryBatch.class);
            return answer == null ? new BatchResult(batch, null, "no answer") : new BatchResult(batch, answer, null);
        } catch (RuntimeException e) {
            return new BatchResult(batch, null, e.getMessage());
        }
    }

    static <T> List<List<T>> batches(List<T> items, int size) {
        List<List<T>> batches = new ArrayList<>();
        for (int from = 0; from < items.size(); from += size) {
            batches.add(items.subList(from, Math.min(items.size(), from + size)));
        }
        return batches;
    }

    @Action(post = {WANTS_COACHING, NO_COACHING}, readOnly = true,
            description = "Compute totals by category and month, recurring charges, anomalies, cash flow and budgets")
    public SpendingAnalysis analyse(CategorisedLedger categorised, Ledger ledger, StatementUpload upload) {
        return Aggregator.analyse(categorised, ledger, upload.coach());
    }

    @Condition(name = WANTS_COACHING)
    public boolean wantsCoaching(SpendingAnalysis analysis) {
        return analysis.coachable();
    }

    @Condition(name = NO_COACHING)
    public boolean noCoaching(SpendingAnalysis analysis) {
        return !analysis.coachable();
    }

    @Action(pre = NO_COACHING, description = "Skip the coaching note")
    public CoachingNotes skipCoaching(SpendingAnalysis analysis) {
        return CoachingNotes.empty();
    }

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

    @AchievesGoal(description = "Produced a categorised spending report from bank and card statements; sources and inline are arrays, send an empty one when unused",
            export = @Export(name = "analyse_spending", remote = true, startingInputTypes = StatementUpload.class))
    @Action(actionRetryPolicy = ActionRetryPolicy.FIRE_ONCE, description = "Write the spreadsheets and the coaching note under work/reports")
    public SpendingReport writeReport(SpendingAnalysis analysis, CoachingNotes notes, CategorisedLedger categorised, Ledger ledger,
                                      OperationContext context) {
        String runId = context.getAgentProcess().getId();
        boolean usedModel = categorised.rows().stream()
                .anyMatch(row -> row.origin() == Origin.LLM || row.origin() == Origin.LLM_LOW_CONFIDENCE);
        String route = usedModel ? SpendingReport.ROUTE_MODEL : SpendingReport.ROUTE_RULES_ONLY;
        RunCost cost = RunCost.from(context.getAgentProcess());
        ReportWriter.Written written;
        try {
            written = new ReportWriter(properties.reports(), properties.csvFormat())
                    .write(runId, analysis, notes, categorised, ledger, cost, route);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not write the report for " + runId, e);
        }
        written.artifacts().forEach(context::addObject);
        return new SpendingReport(runId, written.files(), written.summaryCsv(), analysis, notes, cost, route, written.artifacts());
    }

    private static <T> T terminate(OperationContext context, String file, String message) {
        context.addObject(new TerminationReason(file, message));
        throw new TerminateAgentException(message);
    }
}
