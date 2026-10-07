package io.github.vikthorvergara.playground.docsqa;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

public record Citation(
        @JsonPropertyDescription("Path of the document the quote comes from, exactly as shown in the search results")
        String source,
        @JsonPropertyDescription("A short passage copied word for word from that document")
        String quote) {
}
