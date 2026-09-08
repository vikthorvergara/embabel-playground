package io.github.vikthorvergara.playground.meetingnotes;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.time.LocalDate;

public record ActionItem(
        @JsonPropertyDescription("What needs to be done, as a short imperative sentence")
        String description,
        @JsonPropertyDescription("Name of the person responsible, exactly as written in the notes, or null if nobody was assigned")
        String owner,
        @JsonPropertyDescription("Due date if one was mentioned, otherwise null")
        LocalDate dueDate,
        Priority priority) {

    public static final String UNASSIGNED = "Unassigned";

    ActionItem withOwner(String newOwner) {
        return new ActionItem(description, newOwner, dueDate, priority);
    }
}
