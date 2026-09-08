package io.github.vikthorvergara.playground.meetingnotes;

import java.util.List;

public record MeetingNotes(String title, List<String> participants, String body) {

    public MeetingNotes {
        participants = List.copyOf(participants);
    }
}
