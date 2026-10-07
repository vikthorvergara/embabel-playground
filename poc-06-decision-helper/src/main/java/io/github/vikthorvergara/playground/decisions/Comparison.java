package io.github.vikthorvergara.playground.decisions;

import java.util.List;

public record Comparison(List<RankedOption> ranking) {

    public RankedOption winner() {
        return ranking.getFirst();
    }
}
