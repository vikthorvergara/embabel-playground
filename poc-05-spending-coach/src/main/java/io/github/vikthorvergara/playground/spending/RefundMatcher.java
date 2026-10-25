package io.github.vikthorvergara.playground.spending;

import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class RefundMatcher {

    static final int WINDOW_DAYS = 60;

    private RefundMatcher() {
    }

    public static List<Transaction> match(List<Transaction> rows) {
        List<Transaction> result = new ArrayList<>();
        for (Transaction tx : rows) {
            if (tx.flow() != Transaction.Flow.INCOME) {
                result.add(tx);
                continue;
            }
            Transaction debit = rows.stream()
                    .filter(candidate -> candidate.flow() == Transaction.Flow.SPEND
                            && candidate.account().equals(tx.account())
                            && candidate.merchant().equals(tx.merchant())
                            && !candidate.date().isAfter(tx.date())
                            && ChronoUnit.DAYS.between(candidate.date(), tx.date()) <= WINDOW_DAYS
                            && candidate.amount().compareTo(tx.amount()) >= 0)
                    .max(Comparator.comparing(Transaction::date).thenComparing(Transaction::id))
                    .orElse(null);
            if (debit != null) {
                result.add(tx.withFlow(Transaction.Flow.REFUND, Transaction.REFUND_OF + debit.id()));
            } else if (tx.kind() == Layout.Kind.CARD) {
                result.add(tx.withFlow(Transaction.Flow.CREDIT));
            } else {
                result.add(tx);
            }
        }
        return result;
    }
}
