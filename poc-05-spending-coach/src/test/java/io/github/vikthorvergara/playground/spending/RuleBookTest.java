package io.github.vikthorvergara.playground.spending;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class RuleBookTest {

    @Test
    void userRulesMatchBySubstringOrRegex(@TempDir Path work) throws Exception {
        Files.writeString(work.resolve("rules.csv"), "pattern,category\ncafé do centro,DINING\nre:\\bFEE\\b,FEES_AND_INTEREST\n");
        RuleBook.Rules rules = new RuleBook(Fixtures.properties(work)).load();

        assertThat(rules.userRule("CAFE DO CENTRO")).contains(Category.DINING);
        assertThat(rules.userRule("BANK FEE")).contains(Category.FEES_AND_INTEREST);
        assertThat(rules.userRule("COFFEE SHOP")).isEmpty();
    }

    @Test
    void builtInRulesCoverCommonMerchantsAndNothingElse(@TempDir Path work) {
        RuleBook.Rules rules = new RuleBook(Fixtures.properties(work)).load();

        assertThat(rules.builtInRule("UBER TRIP")).contains(Category.TRANSPORT);
        assertThat(rules.builtInRule("NETFLIX COM")).contains(Category.SUBSCRIPTIONS);
        assertThat(rules.builtInRule("MERCADO LIVRE")).contains(Category.SHOPPING);
        assertThat(rules.builtInRule("SUPERMERCADO BOM PRECO")).contains(Category.GROCERIES);
        assertThat(rules.builtInRule("TARIFA MANUTENCAO CONTA")).contains(Category.FEES_AND_INTEREST);
        assertThat(rules.builtInRule("COFFEE HOUSE")).isEmpty();
        assertThat(rules.builtInRule("CAFE DO CENTRO")).isEmpty();
        assertThat(rules.builtInRule("ACADEMIA FORMA")).isEmpty();
        assertThat(rules.userRule("UBER TRIP")).isEmpty();
    }

    @Test
    void learnedMerchantsAreReloadedPerRunAndMergedWithoutDuplicates(@TempDir Path work) throws Exception {
        RuleBook book = new RuleBook(Fixtures.properties(work));
        assertThat(book.load().learnedRule("CAFE DO CENTRO")).isNull();

        book.learn(Map.of("CAFE DO CENTRO", new RuleBook.Learned(Category.DINING, new BigDecimal("0.9"))));
        book.learn(Map.of("CAFE DO CENTRO", new RuleBook.Learned(Category.DINING, new BigDecimal("0.95")),
                "ACADEMIA FORMA", new RuleBook.Learned(Category.HEALTH, new BigDecimal("0.8"))));

        assertThat(book.load().learnedRule("CAFE DO CENTRO")).isEqualTo(new RuleBook.Learned(Category.DINING, new BigDecimal("0.95")));
        assertThat(book.load().learnedRule("ACADEMIA FORMA").category()).isEqualTo(Category.HEALTH);
        assertThat(Files.readAllLines(work.resolve("merchants-learned.csv"))).hasSize(3);
    }

    @Test
    void twoThreadsLearningAtOnceLeaveAParseableFile(@TempDir Path work) throws Exception {
        RuleBook book = new RuleBook(Fixtures.properties(work));
        ExecutorService pool = Executors.newFixedThreadPool(2);
        for (int i = 0; i < 40; i++) {
            String merchant = "MERCHANT %02d".formatted(i);
            pool.submit(() -> book.learn(Map.of(merchant, new RuleBook.Learned(Category.OTHER, new BigDecimal("0.75")))));
        }
        pool.shutdown();
        assertThat(pool.awaitTermination(30, TimeUnit.SECONDS)).isTrue();

        assertThat(book.load().learned()).hasSize(40);
    }
}
