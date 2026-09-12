package io.github.vikthorvergara.playground.depadvisor;

public record Dependency(String groupId, String artifactId, String version) {

    public String coordinates() {
        return groupId + ":" + artifactId;
    }
}
