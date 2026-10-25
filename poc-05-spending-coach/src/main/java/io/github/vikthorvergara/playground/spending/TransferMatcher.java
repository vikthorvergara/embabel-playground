package io.github.vikthorvergara.playground.spending;

import java.math.BigDecimal;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public final class TransferMatcher {

    static final int WINDOW_DAYS = 3;

    public record Result(List<Transaction> rows, BigDecimal unmatchedTransfers) {
    }

    private TransferMatcher() {
    }

    public static Result mark(List<Transaction> rows, List<String> keywords) {
        List<Pattern> patterns = keywords.stream()
                .map(keyword -> Pattern.compile("(?<![A-Z0-9])" + Pattern.quote(MerchantNormaliser.plain(keyword)) + "(?![A-Z0-9])"))
                .toList();
        Map<String, String> pairs = pair(rows);
        List<Transaction> marked = new ArrayList<>();
        BigDecimal unmatched = BigDecimal.ZERO.setScale(2);
        for (Transaction tx : rows) {
            boolean keyword = matchesKeyword(tx, patterns);
            String partner = pairs.get(tx.id());
            if (partner == null && !keyword) {
                marked.add(tx);
                continue;
            }
            String direction = tx.flow() == Transaction.Flow.SPEND ? Transaction.TRANSFER_OUT : Transaction.TRANSFER_IN;
            if (partner != null) {
                marked.add(tx.withFlow(Transaction.Flow.TRANSFER, direction, Transaction.TRANSFER_PAIR + partner));
            } else {
                marked.add(tx.withFlow(Transaction.Flow.TRANSFER, direction));
                if (tx.flow() == Transaction.Flow.SPEND) {
                    unmatched = unmatched.add(tx.amount());
                }
            }
        }
        return new Result(marked, unmatched);
    }

    private static boolean matchesKeyword(Transaction tx, List<Pattern> patterns) {
        String description = MerchantNormaliser.plain(tx.rawDescription());
        return patterns.stream().anyMatch(pattern -> pattern.matcher(description).find());
    }

    private static Map<String, String> pair(List<Transaction> rows) {
        List<Transaction> debits = rows.stream().filter(tx -> tx.flow() == Transaction.Flow.SPEND)
                .sorted(Comparator.comparing(Transaction::date).thenComparing(Transaction::id)).toList();
        List<Transaction> credits = new ArrayList<>(rows.stream().filter(tx -> tx.flow() == Transaction.Flow.INCOME)
                .sorted(Comparator.comparing(Transaction::date).thenComparing(Transaction::id)).toList());
        Map<String, String> pairs = new HashMap<>();
        for (Transaction debit : debits) {
            for (Transaction credit : credits) {
                if (credit.sourceFile().equals(debit.sourceFile()) || credit.amount().compareTo(debit.amount()) != 0
                        || Math.abs(ChronoUnit.DAYS.between(debit.date(), credit.date())) > WINDOW_DAYS) {
                    continue;
                }
                pairs.put(debit.id(), credit.id());
                pairs.put(credit.id(), debit.id());
                credits.remove(credit);
                break;
            }
        }
        return pairs;
    }
}
