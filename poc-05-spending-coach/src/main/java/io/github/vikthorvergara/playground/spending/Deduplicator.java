package io.github.vikthorvergara.playground.spending;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class Deduplicator {

    private Deduplicator() {
    }

    public static List<Transaction> dedupe(List<Transaction> rows, List<String> warnings) {
        Map<String, Map<String, List<Transaction>>> byKeyAndFile = new LinkedHashMap<>();
        for (Transaction tx : rows) {
            byKeyAndFile.computeIfAbsent(key(tx), key -> new LinkedHashMap<>())
                    .computeIfAbsent(tx.sourceFile(), file -> new ArrayList<>())
                    .add(tx);
        }
        Set<String> kept = new HashSet<>();
        int dropped = 0;
        for (Map<String, List<Transaction>> perFile : byKeyAndFile.values()) {
            int keep = perFile.values().stream().mapToInt(List::size).max().orElse(0);
            for (List<Transaction> copies : perFile.values()) {
                for (Transaction copy : copies) {
                    if (keep > 0) {
                        kept.add(copy.id());
                        keep--;
                    } else {
                        dropped++;
                    }
                }
            }
        }
        if (dropped > 0) {
            warnings.add(dropped + " duplicate row(s) dropped from overlapping exports");
        }
        return rows.stream().filter(tx -> kept.contains(tx.id())).toList();
    }

    private static String key(Transaction tx) {
        return tx.account() + "|" + tx.date() + "|" + (tx.flow() == Transaction.Flow.SPEND ? "-" : "+") + tx.amount().toPlainString()
                + "|" + tx.rawDescription();
    }
}
