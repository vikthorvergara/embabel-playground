package io.github.vikthorvergara.playground.spending;

import java.math.BigDecimal;
import java.util.List;

public record Ledger(
        List<Transaction> transactions,
        String currency,
        List<Transaction> foreign,
        BigDecimal unmatchedTransfers,
        List<String> warnings) {

    @Override
    public String toString() {
        return "Ledger[" + transactions.size() + " rows in " + currency + ", " + foreign.size() + " foreign, "
                + warnings.size() + " warnings]";
    }
}
