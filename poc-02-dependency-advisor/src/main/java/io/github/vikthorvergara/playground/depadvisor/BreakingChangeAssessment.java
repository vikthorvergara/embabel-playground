package io.github.vikthorvergara.playground.depadvisor;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.util.List;

public record BreakingChangeAssessment(List<Note> notes) {

    public record Note(
            @JsonPropertyDescription("groupId:artifactId of the dependency, exactly as given")
            String coordinates,
            @JsonPropertyDescription("Risk of the upgrade: LOW, MEDIUM or HIGH")
            String risk,
            @JsonPropertyDescription("One or two sentences on what is likely to break and what to check")
            String summary,
            @JsonPropertyDescription("Link to release notes or a migration guide, or null if unknown")
            String link) {
    }
}
