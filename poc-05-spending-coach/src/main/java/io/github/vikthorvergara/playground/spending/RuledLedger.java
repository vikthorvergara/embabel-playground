package io.github.vikthorvergara.playground.spending;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record RuledLedger(List<Categorised> rows) {

    public record Categorised(Transaction tx, Category category, Origin origin, BigDecimal confidence) {

        public Categorised with(Category newCategory, Origin newOrigin, BigDecimal newConfidence) {
            return new Categorised(tx, newCategory, newOrigin, newConfidence);
        }

        @Override
        public String toString() {
            return tx.id() + " " + category + " " + origin;
        }
    }

    public record Merchant(int merchantId, String name, BigDecimal typicalAmount, int occurrences) {
    }

    public List<Merchant> uncategorisedMerchants() {
        Map<String, List<BigDecimal>> amounts = new LinkedHashMap<>();
        for (Categorised row : rows) {
            if (row.category() == Category.UNCATEGORISED && row.tx().flow() == Transaction.Flow.SPEND) {
                amounts.computeIfAbsent(row.tx().merchant(), name -> new ArrayList<>()).add(row.tx().amount());
            }
        }
        List<Map.Entry<String, List<BigDecimal>>> ordered = new ArrayList<>(amounts.entrySet());
        ordered.sort(Comparator.<Map.Entry<String, List<BigDecimal>>>comparingInt(entry -> entry.getValue().size()).reversed()
                .thenComparing(Map.Entry::getKey));
        List<Merchant> merchants = new ArrayList<>();
        for (Map.Entry<String, List<BigDecimal>> entry : ordered) {
            merchants.add(new Merchant(merchants.size() + 1, entry.getKey(), Amounts.median(entry.getValue()), entry.getValue().size()));
        }
        return merchants;
    }

    public CategorisedLedger apply(Map<String, CategoryBatch.MerchantCategory> answers, BigDecimal lowConfidence, List<String> warnings) {
        List<Categorised> result = new ArrayList<>();
        for (Categorised row : rows) {
            CategoryBatch.MerchantCategory answer = row.category() == Category.UNCATEGORISED ? answers.get(row.tx().merchant()) : null;
            if (answer == null) {
                result.add(row);
                continue;
            }
            boolean confident = answer.confidence() != null && answer.confidence().compareTo(lowConfidence) >= 0;
            result.add(row.with(answer.category(), confident ? Origin.LLM : Origin.LLM_LOW_CONFIDENCE, answer.confidence()));
        }
        return new CategorisedLedger(result, warnings);
    }

    @Override
    public String toString() {
        return "RuledLedger[" + rows.size() + " rows, " + uncategorisedMerchants().size() + " merchants uncategorised]";
    }
}
