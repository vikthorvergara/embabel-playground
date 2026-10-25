package io.github.vikthorvergara.playground.spending;

import java.util.List;

public record RawStatements(List<RawStatement> files) {

    public record RawStatement(String name, String text, String charset, String fingerprint) {

        @Override
        public String toString() {
            return name + " (" + charset + ", " + fingerprint + ")";
        }
    }

    public RawStatement named(String name) {
        return files.stream().filter(file -> file.name().equals(name)).findFirst().orElseThrow();
    }

    public List<String> names() {
        return files.stream().map(RawStatement::name).toList();
    }

    @Override
    public String toString() {
        return "RawStatements" + names();
    }
}
