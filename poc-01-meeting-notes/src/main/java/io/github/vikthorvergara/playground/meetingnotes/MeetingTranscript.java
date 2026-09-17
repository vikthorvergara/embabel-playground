package io.github.vikthorvergara.playground.meetingnotes;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

/**
 * Input for remote callers. UserInput works in the shell, but its generated JSON schema makes
 * "timestamp" a required property, which MCP clients then have to invent.
 */
public record MeetingTranscript(
        @JsonPropertyDescription("Raw meeting notes or transcript")
        String text) {
}
