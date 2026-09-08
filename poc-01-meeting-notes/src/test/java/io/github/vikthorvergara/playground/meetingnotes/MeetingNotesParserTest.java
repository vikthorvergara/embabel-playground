package io.github.vikthorvergara.playground.meetingnotes;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class MeetingNotesParserTest {

    @Test
    void readsTitleAndAttendeesLine() throws IOException {
        MeetingNotes notes = MeetingNotesParser.parse(sample("sprint-planning.md"));

        assertThat(notes.title()).isEqualTo("Sprint 42 planning");
        assertThat(notes.participants()).containsExactly("Ana Souza", "Bruno Lima", "Carla Mendes", "Ana", "Bruno", "Carla");
        assertThat(notes.body()).startsWith("Ana: we need the checkout bug fixed");
        assertThat(notes.body()).doesNotContain("Attendees");
    }

    @Test
    void detectsSpeakersWhenThereIsNoAttendeesLine() {
        MeetingNotes notes = MeetingNotesParser.parse("""
                Title: Infra sync
                Marcos: I'll rotate the certificates.
                Action: renew the domain
                Julia Rocha: I'll review the Terraform PR.
                """);

        assertThat(notes.title()).isEqualTo("Infra sync");
        assertThat(notes.participants()).containsExactly("Marcos", "Julia Rocha");
    }

    @Test
    void fallsBackToDefaultTitle() {
        MeetingNotes notes = MeetingNotesParser.parse("just some notes without structure");

        assertThat(notes.title()).isEqualTo("Meeting");
        assertThat(notes.participants()).isEmpty();
        assertThat(notes.body()).isEqualTo("just some notes without structure");
    }

    private static String sample(String name) throws IOException {
        try (InputStream in = MeetingNotesParserTest.class.getResourceAsStream("/samples/" + name)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
