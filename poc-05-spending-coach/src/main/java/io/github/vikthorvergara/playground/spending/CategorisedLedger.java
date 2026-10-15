package io.github.vikthorvergara.playground.spending;

import java.util.List;

public record CategorisedLedger(List<RuledLedger.Categorised> rows, List<String> warnings) {

    public List<RuledLedger.Categorised> rowsWithOrigin(Origin origin) {
        return rows.stream().filter(row -> row.origin() == origin).toList();
    }

    @Override
    public String toString() {
        return "CategorisedLedger[" + rows.size() + " rows, " + warnings.size() + " warnings]";
    }
}
