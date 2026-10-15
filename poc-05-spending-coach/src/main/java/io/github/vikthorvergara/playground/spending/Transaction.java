package io.github.vikthorvergara.playground.spending;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public record Transaction(
        String id,
        LocalDate date,
        String account,
        String sourceFile,
        Layout.Kind kind,
        String rawDescription,
        String merchant,
        BigDecimal amount,
        String currency,
        Flow flow,
        List<String> flags) {

    public enum Flow { SPEND, INCOME, TRANSFER, REFUND, CREDIT }

    public static final String TRANSFER_OUT = "TRANSFER_OUT";
    public static final String TRANSFER_IN = "TRANSFER_IN";
    public static final String TRANSFER_PAIR = "TRANSFER_PAIR:";
    public static final String REFUND_OF = "REFUND_OF:";
    public static final String DATE_ORDER_ASSUMED = "DATE_ORDER_ASSUMED";

    public Transaction {
        flags = flags == null ? List.of() : List.copyOf(flags);
    }

    public Transaction withFlow(Flow newFlow, String... extraFlags) {
        List<String> all = new ArrayList<>(flags);
        all.addAll(List.of(extraFlags));
        return new Transaction(id, date, account, sourceFile, kind, rawDescription, merchant, amount, currency, newFlow, all);
    }

    public Transaction withCurrency(String newCurrency) {
        return new Transaction(id, date, account, sourceFile, kind, rawDescription, merchant, amount, newCurrency, flow, flags);
    }

    public boolean outgoing() {
        return flow == Flow.SPEND || (flow == Flow.TRANSFER && flags.contains(TRANSFER_OUT));
    }

    public String linked(String prefix) {
        return flags.stream().filter(flag -> flag.startsWith(prefix)).map(flag -> flag.substring(prefix.length()))
                .findFirst().orElse(null);
    }

    @Override
    public String toString() {
        return id + " " + flow;
    }
}
