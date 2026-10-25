package io.github.vikthorvergara.playground.spending;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.regex.Pattern;

@Component
public class RuleBook {

    static final String BUILT_IN = "/category-rules.csv";
    static final String REGEX_PREFIX = "re:";
    static final List<String> LEARNED_HEADER = List.of("merchant", "category", "confidence");

    public record Rule(String pattern, Category category, Pattern regex) {

        static Rule of(String pattern, Category category) {
            String trimmed = pattern.strip();
            if (trimmed.toLowerCase(Locale.ROOT).startsWith(REGEX_PREFIX)) {
                return new Rule(trimmed, category, Pattern.compile(trimmed.substring(REGEX_PREFIX.length()), Pattern.CASE_INSENSITIVE));
            }
            return new Rule(MerchantNormaliser.plain(trimmed), category, null);
        }

        boolean matches(String merchant) {
            return regex != null ? regex.matcher(merchant).find() : merchant.contains(pattern);
        }
    }

    public record Learned(Category category, BigDecimal confidence) {
    }

    public record Rules(List<Rule> user, List<Rule> builtIn, Map<String, Learned> learned) {

        public Optional<Category> userRule(String merchant) {
            return firstMatch(user, merchant);
        }

        public Optional<Category> builtInRule(String merchant) {
            return firstMatch(builtIn, merchant);
        }

        public Learned learnedRule(String merchant) {
            return learned.get(merchant);
        }

        private static Optional<Category> firstMatch(List<Rule> rules, String merchant) {
            return rules.stream().filter(rule -> rule.matches(merchant)).map(Rule::category).findFirst();
        }
    }

    private final SpendingProperties properties;

    public RuleBook(SpendingProperties properties) {
        this.properties = properties;
    }

    public Rules load() {
        try (InputStream builtIn = RuleBook.class.getResourceAsStream(BUILT_IN)) {
            return new Rules(parseRules(readIfExists(properties.rulesFile())),
                    parseRules(CsvTable.decode(builtIn.readAllBytes()).text()),
                    parseLearned(readIfExists(properties.learnedMerchantsFile())));
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read the rule files", e);
        }
    }

    public synchronized void learn(Map<String, Learned> merchants) {
        if (merchants.isEmpty()) {
            return;
        }
        Map<String, Learned> merged = new TreeMap<>(parseLearned(readIfExists(properties.learnedMerchantsFile())));
        merged.putAll(merchants);
        List<List<Object>> rows = new ArrayList<>();
        rows.add(new ArrayList<>(LEARNED_HEADER));
        merged.forEach((merchant, learned) -> rows.add(Arrays.asList(merchant, learned.category(), learned.confidence())));
        try {
            CsvWriter.writeAtomically(properties.learnedMerchantsFile(), new CsvWriter(CsvWriter.Format.COMMA_DOT).render(rows));
        } catch (IOException e) {
            throw new UncheckedIOException("Could not write " + properties.learnedMerchantsFile(), e);
        }
    }

    static List<Rule> parseRules(String text) {
        List<Rule> rules = new ArrayList<>();
        for (CsvTable.Row row : CsvTable.tokenise(text, ',')) {
            List<String> cells = row.cells();
            if (cells.size() < 2 || cells.getFirst().isBlank() || cells.getFirst().strip().equalsIgnoreCase("pattern")) {
                continue;
            }
            rules.add(Rule.of(cells.get(0), Category.valueOf(cells.get(1).strip().toUpperCase(Locale.ROOT))));
        }
        return rules;
    }

    static Map<String, Learned> parseLearned(String text) {
        Map<String, Learned> learned = new LinkedHashMap<>();
        for (CsvTable.Row row : CsvTable.tokenise(text, ',')) {
            List<String> cells = row.cells();
            if (cells.size() < 3 || cells.getFirst().isBlank() || cells.getFirst().strip().equalsIgnoreCase("merchant")) {
                continue;
            }
            learned.put(cells.get(0), new Learned(Category.valueOf(cells.get(1).strip()), new BigDecimal(cells.get(2).strip())));
        }
        return learned;
    }

    private static String readIfExists(Path path) {
        if (!Files.exists(path)) {
            return "";
        }
        try {
            return CsvTable.decode(Files.readAllBytes(path)).text();
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read " + path, e);
        }
    }
}
