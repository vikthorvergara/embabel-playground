package io.github.vikthorvergara.playground.spending;

import com.embabel.agent.api.tool.TerminateAgentException;
import com.embabel.agent.domain.io.UserInput;
import com.embabel.agent.test.unit.FakeOperationContext;
import com.embabel.common.ai.model.ByRoleModelSelectionCriteria;
import com.embabel.common.ai.model.LlmOptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static io.github.vikthorvergara.playground.spending.Rows.spend;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SpendingCoachAgentTest {

    private static final ColumnMapping GOOD_MAPPING = new ColumnMapping(0, "dd/MM/yyyy", null, 1, 2, null, null, 3, "D", null,
            List.of(), null, Layout.Kind.BANK, false);

    @Test
    void categoriseRemainderSendsDistinctSpendMerchantsOnlyAndLearnsConfidentAnswers(@TempDir Path work) throws Exception {
        SpendingCoachAgent agent = Fixtures.agent(Fixtures.properties(work));
        FakeOperationContext context = FakeOperationContext.create();
        context.expectResponse(new CategoryBatch(List.of(
                new CategoryBatch.MerchantCategory(1, Category.DINING, new BigDecimal("0.9"), "cafe"),
                new CategoryBatch.MerchantCategory(2, Category.HEALTH, new BigDecimal("0.5"), "gym"),
                new CategoryBatch.MerchantCategory(9, Category.TRAVEL, new BigDecimal("0.9"), "made up"))));

        CategorisedLedger ledger = agent.categoriseRemainder(ruled(), context);

        assertThat(ledger.rows()).extracting(RuledLedger.Categorised::category, RuledLedger.Categorised::origin)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(Category.DINING, Origin.LLM),
                        org.assertj.core.groups.Tuple.tuple(Category.DINING, Origin.LLM),
                        org.assertj.core.groups.Tuple.tuple(Category.HEALTH, Origin.LLM_LOW_CONFIDENCE),
                        org.assertj.core.groups.Tuple.tuple(Category.INCOME, Origin.FLOW),
                        org.assertj.core.groups.Tuple.tuple(Category.TRANSFER, Origin.FLOW));
        var invocation = context.getLlmInvocations().getFirst();
        assertThat(invocation.getPrompt())
                .contains("m1: CAFE DO CENTRO (typical 18.50, seen 2 times)")
                .contains("m2: ACADEMIA FORMA (typical 129.90, seen 1 times)")
                .doesNotContain("SALARIO").doesNotContain("PIX");
        assertThat(Pii.found(invocation.getPrompt())).isFalse();
        assertThat(selectsRole(invocation.getInteraction().getLlm(), SpendingProperties.CATEGORISER_ROLE)).isTrue();
        assertThat(invocation.getInteraction().getLlm().getTemperature()).isEqualTo(0.0);
        assertThat(Files.readString(work.resolve("merchants-learned.csv"))).contains("CAFE DO CENTRO,DINING,0.9").doesNotContain("ACADEMIA");
    }

    @Test
    void unknownIdIsDroppedAndMissingMerchantStaysUncategorised(@TempDir Path work) {
        SpendingCoachAgent agent = Fixtures.agent(Fixtures.properties(work));
        FakeOperationContext context = FakeOperationContext.create();
        context.expectResponse(new CategoryBatch(List.of(
                new CategoryBatch.MerchantCategory(1, Category.DINING, new BigDecimal("0.9"), "cafe"),
                new CategoryBatch.MerchantCategory(9, Category.TRAVEL, new BigDecimal("0.9"), "made up"))));

        CategorisedLedger ledger = agent.categoriseRemainder(ruled(), context);

        assertThat(ledger.rows().get(2).category()).isEqualTo(Category.UNCATEGORISED);
        assertThat(ledger.rows().get(2).origin()).isEqualTo(Origin.NONE);
        assertThat(ledger.rows()).noneMatch(row -> row.category() == Category.TRAVEL);
        assertThat(ledger.warnings()).isEmpty();
    }

    @Test
    void merchantCapAndBatchFailuresDegradeWithWarnings(@TempDir Path work) {
        SpendingCoachAgent capped = Fixtures.agent(Fixtures.properties(work, 1));
        FakeOperationContext context = FakeOperationContext.create();
        context.expectResponse(new CategoryBatch(List.of(new CategoryBatch.MerchantCategory(1, Category.DINING, new BigDecimal("0.9"), ""))));

        CategorisedLedger ledger = capped.categoriseRemainder(ruled(), context);

        assertThat(context.getLlmInvocations().getFirst().getPrompt()).contains("m1:").doesNotContain("m2:");
        assertThat(ledger.warnings()).singleElement().asString().contains("cap of 1");
        assertThat(ledger.rows().get(2).category()).isEqualTo(Category.UNCATEGORISED);

        CategorisedLedger failed = Fixtures.agent(Fixtures.properties(work)).categoriseRemainder(ruled(), FakeOperationContext.create());
        assertThat(failed.warnings()).singleElement().asString().contains("stayed uncategorised");
        assertThat(failed.rows()).noneMatch(row -> row.origin() == Origin.LLM);
    }

    @Test
    void coachOffersTheAnalysisToolsAndThePersonaAndKeepsOnlyAmountsFromTheAnalysis(@TempDir Path work) throws Exception {
        Files.copy(Path.of("src/test/resources/statements/rules.csv"), work.resolve("rules.csv"));
        Fixtures.Run run = Fixtures.run(work, true, Fixtures.ALL);
        FakeOperationContext context = FakeOperationContext.create();
        context.expectResponse(new CoachingNotes("Rent is the big one.", List.of(
                new CoachingNotes.Insight("Rent", "Rent is 2100 a month.", Category.HOUSING, new BigDecimal("2100.0")),
                new CoachingNotes.Insight("Invented", "Nothing costs this.", Category.DINING, new BigDecimal("123.45")),
                new CoachingNotes.Insight("No amount", "Keep this.", Category.OTHER, null)),
                List.of(new CoachingNotes.SavingOpportunity("Drop Spotify", new BigDecimal("21.9"), "Cancel it."),
                        new CoachingNotes.SavingOpportunity("Made up", new BigDecimal("77.77"), "Nope."))));

        CoachingNotes notes = run.agent().coach(run.analysis(), context);

        assertThat(notes.insights()).extracting(CoachingNotes.Insight::title).containsExactly("Rent", "No amount");
        assertThat(notes.savings()).extracting(CoachingNotes.SavingOpportunity::what).containsExactly("Drop Spotify");
        var interaction = context.getLlmInvocations().getFirst().getInteraction();
        assertThat(interaction.getTools()).extracting(tool -> tool.getDefinition().getName())
                .contains("spendByCategory", "topMerchants", "months", "anomalies", "cashFlow");
        assertThat(interaction.getPromptContributors()).contains(CoachPersona.CALM);
        assertThat(selectsRole(interaction.getLlm(), SpendingProperties.COACH_ROLE)).isTrue();
        assertThat(context.getLlmInvocations().getFirst().getPrompt()).contains("2026-07, 2026-08, 2026-09").doesNotContain("ALUGUEL");
    }

    @Test
    void inferLayoutsWithAMappingThatDoesNotParseTerminatesWithAReason(@TempDir Path work) {
        SpendingCoachAgent agent = Fixtures.agent(Fixtures.properties(work));
        FakeOperationContext context = FakeOperationContext.create();
        context.expectResponse(new ColumnMapping(1, null, null, 0, 2, null, null, 3, "D", null, List.of(), null, Layout.Kind.BANK, false));
        RawStatements statements = new RawStatements(List.of(Fixtures.raw("unknown-bank.csv")));

        assertThatThrownBy(() -> agent.inferLayouts(new LayoutDetection(Map.of(), List.of("unknown-bank.csv")), statements, context))
                .isInstanceOf(TerminateAgentException.class);

        assertThat(context.last(TerminationReason.class).file()).isEqualTo("unknown-bank.csv");
        assertThat(context.getLlmInvocations()).hasSize(1);
        assertThat(work.resolve("layouts-learned.csv")).doesNotExist();
    }

    @Test
    void inferLayoutsWithAGoodMappingLearnsTheLayoutAndSendsAMaskedSample(@TempDir Path work) throws Exception {
        SpendingCoachAgent agent = Fixtures.agent(Fixtures.properties(work));
        FakeOperationContext context = FakeOperationContext.create();
        context.expectResponse(GOOD_MAPPING);
        RawStatements statements = new RawStatements(List.of(Fixtures.raw("unknown-bank.csv")));

        LayoutMappings mappings = agent.inferLayouts(new LayoutDetection(Map.of(), List.of("unknown-bank.csv")), statements, context);

        assertThat(mappings.byFile().get("unknown-bank.csv").amounts()).isEqualTo(Layout.AmountFormat.DECIMAL_COMMA);
        assertThat(Files.readAllLines(work.resolve("layouts-learned.csv"))).hasSize(2);
        var invocation = context.getLlmInvocations().getFirst();
        assertThat(invocation.getPrompt()).contains("Header: 0=Dt. Mov.").contains("9.999,99").doesNotContain("5.200,00").doesNotContain("unknown-bank");
        assertThat(Pii.found(invocation.getPrompt())).isFalse();
        assertThat(selectsRole(invocation.getInteraction().getLlm(), SpendingProperties.LAYOUT_ROLE)).isTrue();
    }

    @Test
    void sniffLayoutsOnTheNamesFileTerminatesWithoutAnyInvocation(@TempDir Path work) {
        SpendingCoachAgent agent = Fixtures.agent(Fixtures.properties(work));
        FakeOperationContext context = FakeOperationContext.create();
        RawStatements statements = new RawStatements(List.of(Fixtures.raw("bank-q3.csv"), Fixtures.raw("not-a-statement.csv")));

        assertThatThrownBy(() -> agent.sniffLayouts(statements, context)).isInstanceOf(TerminateAgentException.class);

        assertThat(context.last(TerminationReason.class).file()).isEqualTo("not-a-statement.csv");
        assertThat(context.getLlmInvocations()).isEmpty();
    }

    @Test
    void sniffLayoutsSettlesTheConditionPair(@TempDir Path work) {
        SpendingCoachAgent agent = Fixtures.agent(Fixtures.properties(work));
        RawStatements statements = new RawStatements(List.of(Fixtures.raw("bank-q3.csv"), Fixtures.raw("unknown-bank.csv")));

        LayoutDetection detection = agent.sniffLayouts(statements, FakeOperationContext.create());

        assertThat(detection.known()).containsOnlyKeys("bank-q3.csv");
        assertThat(detection.unknown()).containsExactly("unknown-bank.csv");
        assertThat(agent.hasUnknownLayout(detection)).isTrue();
        assertThat(agent.allLayoutsKnown(detection)).isFalse();
    }

    @Test
    void collectStatementsRejectsBadSourcesAndSizes(@TempDir Path work) throws Exception {
        SpendingProperties small = new SpendingProperties(work.toString(), 40, 4, 400, new BigDecimal("0.7"), Dates.Order.DAY_FIRST,
                SpendingProperties.MonthBy.TRANSACTION_DATE, "BRL", CsvWriter.Format.COMMA_DOT, 100L, Fixtures.KEYWORDS, List.of());
        SpendingCoachAgent agent = Fixtures.agent(Fixtures.properties(work));
        FakeOperationContext context = FakeOperationContext.create();
        Files.createDirectories(work.resolve("uploads").resolve("abc"));
        Files.write(work.resolve("uploads").resolve("abc").resolve("bank.csv"), Fixtures.bytes("bank-q3.csv"));

        assertThatThrownBy(() -> agent.collectStatements(new StatementUpload(List.of("../rules.csv"), List.of(), false), context))
                .isInstanceOf(TerminateAgentException.class);
        assertThat(context.last(TerminationReason.class).file()).isEqualTo("../rules.csv");
        assertThatThrownBy(() -> agent.collectStatements(new StatementUpload(List.of("abc/missing.csv"), List.of(), false), context))
                .isInstanceOf(StatementReadException.class);
        assertThatThrownBy(() -> Fixtures.agent(small).collectStatements(new StatementUpload(List.of("abc/bank.csv"), List.of(), false), context))
                .isInstanceOf(TerminateAgentException.class);
        assertThatThrownBy(() -> agent.collectStatements(new StatementUpload(List.of(), List.of(new StatementUpload.InlineStatement("one.csv", "a,b")), false), context))
                .isInstanceOf(TerminateAgentException.class);

        RawStatements statements = agent.collectStatements(new StatementUpload(List.of("abc/bank.csv"), List.of(Fixtures.inline("card-q3.csv")), false), context);
        assertThat(statements.files()).extracting(RawStatements.RawStatement::charset).containsExactly("windows-1252", "inline");
        assertThat(statements.names()).containsExactly("abc/bank.csv", "card-q3.csv");
    }

    @Test
    void fromUserInputReadsEveryCsvInAFolder(@TempDir Path folder) throws Exception {
        Files.write(folder.resolve("bank.csv"), Fixtures.bytes("bank-q3.csv"));
        Files.write(folder.resolve("card.csv"), Fixtures.bytes("card-q3.csv"));
        Files.writeString(folder.resolve("notes.txt"), "ignored");
        SpendingCoachAgent agent = Fixtures.agent(Fixtures.properties(folder));

        StatementUpload upload = agent.fromUserInput(new UserInput(folder.toString()));

        assertThat(upload.coach()).isTrue();
        assertThat(upload.inline()).extracting(StatementUpload.InlineStatement::name).containsExactly("bank.csv", "card.csv");
        assertThat(upload.inline().getFirst().csv()).contains("Histórico");
        assertThat(agent.fromUserInput(new UserInput(folder.resolve("card.csv").toString())).inline()).hasSize(1);
        assertThatThrownBy(() -> agent.fromUserInput(new UserInput(folder.resolve("nope.csv").toString())))
                .isInstanceOf(StatementReadException.class);
    }

    @Test
    void writeReportProducesTheFilesAndTheRoute(@TempDir Path work) throws Exception {
        Files.copy(Path.of("src/test/resources/statements/rules.csv"), work.resolve("rules.csv"));
        Fixtures.Run run = Fixtures.run(work, false, Fixtures.ALL);

        SpendingReport report = run.agent().writeReport(run.analysis(), CoachingNotes.empty(), run.categorised(), run.ledger(), run.context());

        assertThat(report.route()).isEqualTo(SpendingReport.ROUTE_RULES_ONLY);
        assertThat(report.runId()).isNotBlank();
        assertThat(report.files()).containsExactly("summary.csv", "transactions.csv", "recurring.csv", "anomalies.csv", "cashflow.csv", "foreign.csv", "coach.md");
        Path dir = work.resolve("reports").resolve(report.runId());
        assertThat(Files.readString(dir.resolve("summary.csv"))).isEqualTo(CsvWriter.BOM + report.summaryCsv());
        assertThat(report.summaryCsv()).startsWith("category,2026-07,2026-08,2026-09,total,monthly_average,suggested_budget,last_month_vs_average_pct\n")
                .contains("\nSHOPPING,189.00,-89.00,140.00,240.00,80.00,").contains("\nTOTAL_SPEND,");
        assertThat(Files.readString(dir.resolve("transactions.csv"))).contains("TRANSFER_PAIR:").contains("REFUND_OF:").contains("DUPLICATE").contains("FEE");
        assertThat(Files.readString(dir.resolve("foreign.csv"))).contains("STEAM GAMES");
        assertThat(Files.readString(dir.resolve("coach.md"))).contains("Coaching was not requested").contains("120.00");
        assertThat(report.toMarkdown()).contains("```csv").contains("Route: rules-only").contains("A negative cell means a refund");
        assertThat(run.context().objectsOfType(com.embabel.agent.domain.io.FileArtifact.class)).hasSize(7);
    }

    private static RuledLedger ruled() {
        Transaction salary = Rows.tx("b1", "2026-07-05", "bank.csv", Layout.Kind.BANK, "SALARIO EMPRESA", "6500.00", Transaction.Flow.INCOME);
        Transaction pix = Rows.tx("b2", "2026-07-12", "bank.csv", Layout.Kind.BANK, "PIX JOAO PEREIRA", "120.00", Transaction.Flow.TRANSFER, Transaction.TRANSFER_OUT);
        return new RuledLedger(List.of(
                uncategorised(spend("c1", "2026-07-12", "CAFE DO CENTRO 0023", "18.50")),
                uncategorised(spend("c2", "2026-07-13", "CAFE DO CENTRO 0023", "18.50")),
                uncategorised(spend("c3", "2026-07-15", "ACADEMIA FORMA", "129.90")),
                new RuledLedger.Categorised(salary, Category.INCOME, Origin.FLOW, null),
                new RuledLedger.Categorised(pix, Category.TRANSFER, Origin.FLOW, null)));
    }

    private static RuledLedger.Categorised uncategorised(Transaction tx) {
        return new RuledLedger.Categorised(tx, Category.UNCATEGORISED, Origin.NONE, null);
    }

    private static boolean selectsRole(LlmOptions options, String role) {
        return options.getCriteria() instanceof ByRoleModelSelectionCriteria criteria && criteria.getRole().equals(role);
    }
}
