package io.github.vikthorvergara.playground.spending;

import com.embabel.agent.api.invocation.AgentInvocation;
import com.embabel.agent.core.ActionInvocation;
import com.embabel.agent.core.Agent;
import com.embabel.agent.core.AgentProcess;
import com.embabel.agent.core.AgentProcessStatusCode;
import com.embabel.agent.core.NonRetryable;
import com.embabel.agent.core.ProcessOptions;
import com.embabel.agent.test.integration.EmbabelMockitoIntegrationTest;
import com.embabel.common.ai.model.ByRoleModelSelectionCriteria;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@TestPropertySource(properties = {
        "ANTHROPIC_API_KEY=test-key",
        "embabel.agent.shell.interactive.enabled=false"
})
class SpendingCoachFlowTest extends EmbabelMockitoIntegrationTest {

    private static final Path WORK = createWorkDir();
    private static final Path FIXTURES = Path.of("src/test/resources/statements");
    private static final ColumnMapping UNKNOWN_BANK_MAPPING = new ColumnMapping(0, "dd/MM/yyyy", null, 1, 2, null, null, 3, "D", null,
            List.of(), null, Layout.Kind.BANK, false);

    @DynamicPropertySource
    static void workDir(DynamicPropertyRegistry registry) {
        registry.add("spending.work-dir", WORK::toString);
    }

    @BeforeEach
    void resetWork() throws IOException {
        Files.copy(FIXTURES.resolve("rules.csv"), WORK.resolve("rules.csv"), StandardCopyOption.REPLACE_EXISTING);
        Files.deleteIfExists(WORK.resolve("merchants-learned.csv"));
        Files.deleteIfExists(WORK.resolve("layouts-learned.csv"));
    }

    @Test
    void rulesCoveringEveryMerchantAndNoCoachingMakeNoModelCall() throws IOException {
        AgentProcess process = start(upload(false, Fixtures.ALL));
        SpendingReport report = process.last(SpendingReport.class);

        verify(llmOperations, never()).createObject(any(), any(), any(), any(), any());
        assertThat(process.getStatus()).isEqualTo(AgentProcessStatusCode.COMPLETED);
        assertThat(report.route()).isEqualTo(SpendingReport.ROUTE_RULES_ONLY);
        assertThat(report.summaryCsv()).isEqualTo(Files.readString(FIXTURES.resolve("summary-golden.csv")).replace("\r\n", "\n"));
        assertThat(report.cost().llmCalls()).isZero();
        assertThat(report.notes().isEmpty()).isTrue();
        List<String> actions = process.getHistory().stream().map(ActionInvocation::getActionName).toList();
        assertThat(actions).hasSize(9)
                .anyMatch(name -> name.endsWith("acceptRuledLedger")).anyMatch(name -> name.endsWith("skipCoaching"))
                .noneMatch(name -> name.endsWith("categoriseRemainder")).noneMatch(name -> name.endsWith("coach"))
                .noneMatch(name -> name.endsWith("inferLayouts"));
        assertThat(Files.readString(report(report, "transactions.csv"))).contains("TRANSFER_PAIR:").doesNotContain(",UNCATEGORISED,");
    }

    @Test
    void aMissingSourceFailsWithoutRetries() {
        AgentProcess process = agentPlatform.createAgentProcessFrom(spendingCoachAgent(), new ProcessOptions(),
                new StatementUpload(List.of("nowhere/missing.csv"), List.of(), false));

        assertThatThrownBy(process::run).isInstanceOf(StatementReadException.class).isInstanceOf(NonRetryable.class)
                .hasMessage("Could not read nowhere/missing.csv");

        assertThat(process.getStatus()).isNotEqualTo(AgentProcessStatusCode.COMPLETED);
        assertThat(process.getHistory()).isEmpty();
        verify(llmOperations, never()).createObject(any(), any(), any(), any(), any());
    }

    @Test
    void partialRulesUseTheCategoriserOnceAndTheCoachOnceThenLearnedMerchantsCoverTheSecondUpload() throws IOException {
        Files.copy(FIXTURES.resolve("rules-partial.csv"), WORK.resolve("rules.csv"), StandardCopyOption.REPLACE_EXISTING);
        whenCreateObject(prompt -> prompt.contains("m1:"), CategoryBatch.class,
                interaction -> selectsRole(interaction.getLlm().getCriteria(), SpendingProperties.CATEGORISER_ROLE))
                .thenReturn(new CategoryBatch(List.of(
                        new CategoryBatch.MerchantCategory(1, Category.HEALTH, new BigDecimal("0.95"), "gym"),
                        new CategoryBatch.MerchantCategory(2, Category.DINING, new BigDecimal("0.9"), "cafe"),
                        new CategoryBatch.MerchantCategory(3, Category.UTILITIES, new BigDecimal("0.9"), "power"),
                        new CategoryBatch.MerchantCategory(4, Category.SHOPPING, new BigDecimal("0.8"), "books"))));
        whenCreateObject(prompt -> prompt.contains("coaching note"), CoachingNotes.class,
                interaction -> selectsRole(interaction.getLlm().getCriteria(), SpendingProperties.COACH_ROLE))
                .thenReturn(new CoachingNotes("Rent dominates.", List.of(
                        new CoachingNotes.Insight("Rent", "Rent is the biggest line.", Category.HOUSING, new BigDecimal("2100.0")),
                        new CoachingNotes.Insight("Invented", "Not a real figure.", Category.DINING, new BigDecimal("123.45"))),
                        List.of(new CoachingNotes.SavingOpportunity("Cancel Spotify", new BigDecimal("21.9"), "Cancel it."))));

        SpendingReport first = AgentInvocation.create(agentPlatform, SpendingReport.class).invoke(upload(true, Fixtures.ALL));

        assertThat(first.route()).isEqualTo(SpendingReport.ROUTE_MODEL);
        assertThat(llmCalls(CategoryBatch.class)).isEqualTo(1);
        assertThat(llmCalls(CoachingNotes.class)).isEqualTo(1);
        String transactions = Files.readString(report(first, "transactions.csv"));
        for (String merchant : List.of("ACADEMIA FORMA", "CAFE DO CENTRO", "ENEL ENERGIA", "LIVRARIA CULTURA")) {
            assertThat(transactions).contains(merchant + ",").containsPattern(merchant + ",.*,LLM,");
        }
        String coach = Files.readString(report(first, "coach.md"));
        assertThat(coach).contains("2100.00").contains("21.90").doesNotContain("123.45");
        assertThat(first.notes().insights()).hasSize(1);
        assertThat(Files.readString(WORK.resolve("merchants-learned.csv"))).contains("ACADEMIA FORMA,HEALTH,0.95");

        SpendingReport second = AgentInvocation.create(agentPlatform, SpendingReport.class).invoke(upload(true, Fixtures.ALL));

        assertThat(llmCalls(CategoryBatch.class)).isEqualTo(1);
        assertThat(second.route()).isEqualTo(SpendingReport.ROUTE_RULES_ONLY);
        assertThat(Files.readString(report(second, "transactions.csv"))).containsPattern("ACADEMIA FORMA,.*,LEARNED,");
    }

    @Test
    void unknownLayoutIsInferredVerifiedAndLearned() throws IOException {
        whenCreateObject(prompt -> prompt.contains("Header: 0=Dt. Mov."), ColumnMapping.class,
                interaction -> selectsRole(interaction.getLlm().getCriteria(), SpendingProperties.LAYOUT_ROLE))
                .thenReturn(UNKNOWN_BANK_MAPPING);

        SpendingReport report = AgentInvocation.create(agentPlatform, SpendingReport.class).invoke(upload(false, List.of("unknown-bank.csv")));

        assertThat(llmCalls(ColumnMapping.class)).isEqualTo(1);
        assertThat(Files.readAllLines(WORK.resolve("layouts-learned.csv"))).hasSize(2);
        List<String> rows = Files.readAllLines(report(report, "transactions.csv"));
        assertThat(rows).hasSize(13);
        assertThat(rows.subList(1, rows.size())).allMatch(row -> row.contains(Transaction.DATE_ORDER_ASSUMED));
        assertThat(report.analysis().months()).hasSize(2);
    }

    @Test
    void aFileThatIsNotAStatementTerminatesBeforeAnyModelCall() {
        AgentProcess process = start(upload(false, List.of("bank-q3.csv", "not-a-statement.csv")));

        assertThat(process.getStatus()).isEqualTo(AgentProcessStatusCode.TERMINATED);
        assertThat(process.last(TerminationReason.class).file()).endsWith("/not-a-statement.csv");
        verify(llmOperations, never()).createObject(any(), any(), any(), any(), any());
    }

    @Test
    void aMappingWithTheWrongColumnsTerminatesAfterExactlyOneCall() {
        whenCreateObject(prompt -> prompt.contains("Header: 0=Dt. Mov."), ColumnMapping.class)
                .thenReturn(new ColumnMapping(1, null, null, 0, 2, null, null, 3, "D", null, List.of(), null, Layout.Kind.BANK, false));

        AgentProcess process = start(upload(false, List.of("unknown-bank.csv")));

        assertThat(process.getStatus()).isEqualTo(AgentProcessStatusCode.TERMINATED);
        assertThat(process.last(TerminationReason.class).message()).startsWith("Could not read ").contains("/unknown-bank.csv: header [Dt. Mov.");
        assertThat(llmCalls(ColumnMapping.class)).isEqualTo(1);
        assertThat(WORK.resolve("layouts-learned.csv")).doesNotExist();
    }

    private static StatementUpload upload(boolean coach, List<String> names) {
        Path dir = WORK.resolve("uploads").resolve(UUID.randomUUID().toString());
        List<String> sources = new ArrayList<>();
        try {
            Files.createDirectories(dir);
            for (String name : names) {
                Files.write(dir.resolve(name), Fixtures.bytes(name));
                sources.add(dir.getFileName() + "/" + name);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return new StatementUpload(sources, List.of(), coach);
    }

    private AgentProcess start(StatementUpload upload) {
        return agentPlatform.runAgentFrom(spendingCoachAgent(), new ProcessOptions(), Map.of("it", upload));
    }

    private Agent spendingCoachAgent() {
        return agentPlatform.agents().stream()
                .filter(a -> a.getName().equals("SpendingCoachAgent"))
                .findFirst()
                .orElseThrow();
    }

    private static Path report(SpendingReport report, String file) {
        return WORK.resolve("reports").resolve(report.runId()).resolve(file);
    }

    private long llmCalls(Class<?> outputClass) {
        return Mockito.mockingDetails(llmOperations).getInvocations().stream()
                .filter(i -> i.getMethod().getName().equals("createObject"))
                .filter(i -> outputClass.equals(i.getArgument(2)))
                .count();
    }

    private static boolean selectsRole(Object criteria, String role) {
        return criteria instanceof ByRoleModelSelectionCriteria byRole && byRole.getRole().equals(role);
    }

    private static Path createWorkDir() {
        try {
            return Files.createTempDirectory("spending-flow");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
