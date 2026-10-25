package io.github.vikthorvergara.playground.spending;

import java.util.Map;

public record LayoutMappings(Map<String, Layout> byFile) {

    @Override
    public String toString() {
        return "LayoutMappings" + byFile.keySet();
    }
}
