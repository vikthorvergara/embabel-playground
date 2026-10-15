package io.github.vikthorvergara.playground.spending;

import java.util.List;
import java.util.Map;

public record LayoutDetection(Map<String, Layout> known, List<String> unknown) {

    @Override
    public String toString() {
        return "LayoutDetection[known=" + known.keySet() + " unknown=" + unknown + "]";
    }
}
