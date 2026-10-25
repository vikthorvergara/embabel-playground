package io.github.vikthorvergara.playground.spending;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.List;

@ConfigurationProperties("spending")
public record SpendingProperties(
        @DefaultValue("./work") String workDir,
        @DefaultValue("40") int batchSize,
        @DefaultValue("4") int parallelism,
        @DefaultValue("400") int maxMerchants,
        @DefaultValue("0.7") BigDecimal lowConfidence,
        @DefaultValue("DAY_FIRST") Dates.Order dateOrder,
        @DefaultValue("TRANSACTION_DATE") MonthBy monthBy,
        @DefaultValue("BRL") String currency,
        @DefaultValue("COMMA_DOT") CsvWriter.Format csvFormat,
        @DefaultValue("5242880") long maxFileBytes,
        List<String> transferKeywords,
        List<Layout> layouts) {

    public enum MonthBy { TRANSACTION_DATE, POSTING_DATE }

    public static final String LAYOUT_ROLE = "layout";
    public static final String CATEGORISER_ROLE = "categoriser";
    public static final String COACH_ROLE = "coach";

    public SpendingProperties {
        transferKeywords = transferKeywords == null ? List.of() : List.copyOf(transferKeywords);
        layouts = layouts == null ? List.of() : List.copyOf(layouts);
    }

    public Path work() {
        return Path.of(workDir);
    }

    public Path uploads() {
        return work().resolve("uploads");
    }

    public Path reports() {
        return work().resolve("reports");
    }

    public Path rulesFile() {
        return work().resolve("rules.csv");
    }

    public Path learnedMerchantsFile() {
        return work().resolve("merchants-learned.csv");
    }

    public Path learnedLayoutsFile() {
        return work().resolve("layouts-learned.csv");
    }
}
