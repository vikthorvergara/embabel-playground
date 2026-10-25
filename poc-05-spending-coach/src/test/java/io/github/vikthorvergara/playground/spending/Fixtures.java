package io.github.vikthorvergara.playground.spending;

import com.embabel.agent.test.unit.FakeOperationContext;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.List;

final class Fixtures {

    static final List<String> KEYWORDS = List.of("PAGAMENTO FATURA", "PAYMENT RECEIVED", "PIX", "TED", "DOC", "TRANSF", "MB WAY");
    static final Layout GENERIC_SIGNED = new Layout("generic-signed", Layout.Kind.BANK, ',', 0, "yyyy-MM-dd", null, 1, 2,
            null, null, null, null, null, List.of(), null, false, null);
    static final List<String> ALL = List.of("bank-q3.csv", "savings-q3.csv", "card-q3.csv", "card-q3-overlap.csv");

    record Run(SpendingProperties properties, SpendingCoachAgent agent, FakeOperationContext context, Ledger ledger,
               CategorisedLedger categorised, SpendingAnalysis analysis) {
    }

    private Fixtures() {
    }

    static byte[] bytes(String name) {
        try (InputStream in = Fixtures.class.getResourceAsStream("/statements/" + name)) {
            return in.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static String text(String name) {
        return CsvTable.decode(bytes(name)).text();
    }

    static CsvTable table(String name) {
        return CsvTable.parse(text(name));
    }

    static RawStatements.RawStatement raw(String name) {
        CsvTable.Decoded decoded = CsvTable.decode(bytes(name));
        return new RawStatements.RawStatement(name, decoded.text(), decoded.charset(), CsvTable.parse(decoded.text()).fingerprint());
    }

    static StatementUpload.InlineStatement inline(String name) {
        return new StatementUpload.InlineStatement(name, text(name));
    }

    static SpendingProperties properties(Path work) {
        return properties(work, 400);
    }

    static SpendingProperties properties(Path work, int maxMerchants) {
        return new SpendingProperties(work.toString(), 40, 4, maxMerchants, new BigDecimal("0.7"), Dates.Order.DAY_FIRST,
                SpendingProperties.MonthBy.TRANSACTION_DATE, "BRL", CsvWriter.Format.COMMA_DOT, 5_242_880L, KEYWORDS, List.of(GENERIC_SIGNED));
    }

    static SpendingCoachAgent agent(SpendingProperties properties) {
        return new SpendingCoachAgent(properties, new RuleBook(properties), new LayoutStore(properties));
    }

    static Run run(Path work, boolean coach, List<String> names) {
        SpendingProperties properties = properties(work);
        SpendingCoachAgent agent = agent(properties);
        FakeOperationContext context = FakeOperationContext.create();
        StatementUpload upload = new StatementUpload(List.of(), names.stream().map(Fixtures::inline).toList(), coach);
        RawStatements statements = agent.collectStatements(upload, context);
        LayoutMappings mappings = agent.useKnownLayouts(agent.sniffLayouts(statements, context));
        Ledger ledger = agent.parseStatements(statements, mappings);
        CategorisedLedger categorised = agent.acceptRuledLedger(agent.applyRules(ledger));
        SpendingAnalysis analysis = agent.analyse(categorised, ledger, upload);
        return new Run(properties, agent, context, ledger, categorised, analysis);
    }
}
