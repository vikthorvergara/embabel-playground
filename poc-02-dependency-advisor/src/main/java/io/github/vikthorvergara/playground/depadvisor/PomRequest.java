package io.github.vikthorvergara.playground.depadvisor;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

/**
 * Input for remote callers (see MeetingTranscript in POC 01 for why not UserInput).
 */
public record PomRequest(
        @JsonPropertyDescription("Contents of a pom.xml, or a path to one on the server")
        String pom) {
}
