package io.github.vikthorvergara.playground.spending;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

final class Rows {

    private Rows() {
    }

    static Transaction tx(String id, String date, String file, Layout.Kind kind, String raw, String amount, Transaction.Flow flow, String... flags) {
        return new Transaction(id, LocalDate.parse(date), kind + "-" + file, file, kind, raw, MerchantNormaliser.normalise(raw),
                new BigDecimal(amount), "BRL", flow, List.of(flags));
    }

    static Transaction spend(String id, String date, String raw, String amount) {
        return tx(id, date, "card.csv", Layout.Kind.CARD, raw, amount, Transaction.Flow.SPEND);
    }

    static RuledLedger.Categorised cat(Transaction tx, Category category) {
        return new RuledLedger.Categorised(tx, category, Origin.RULE, null);
    }
}
