package io.github.vikthorvergara.playground.meetingnotes;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Plain parsing, no LLM involved. Pulls out the title and the participant list
 * so the LLM step gets a cleaner input and the owners can be validated later.
 */
final class MeetingNotesParser {

    private static final String DEFAULT_TITLE = "Meeting";
    private static final Pattern PARTICIPANTS_LINE =
            Pattern.compile("^(?:attendees|participants|present)\\s*:\\s*(.+)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern SPEAKER_PREFIX = Pattern.compile("^([A-Z][\\p{L}'-]+(?: [A-Z][\\p{L}'-]+)?)\\s*:");

    private MeetingNotesParser() {
    }

    static MeetingNotes parse(String raw) {
        List<String> lines = raw.strip().lines().map(String::strip).toList();
        String title = DEFAULT_TITLE;
        Set<String> participants = new LinkedHashSet<>();
        List<String> body = new ArrayList<>();

        for (String line : lines) {
            if (title.equals(DEFAULT_TITLE) && body.isEmpty() && isTitle(line)) {
                title = line.replaceFirst("^#+\\s*", "").replaceFirst("(?i)^title\\s*:\\s*", "").strip();
                continue;
            }
            Matcher participantsLine = PARTICIPANTS_LINE.matcher(line);
            if (participantsLine.matches()) {
                Arrays.stream(participantsLine.group(1).split("[,;]| and "))
                        .map(String::strip)
                        .filter(name -> !name.isEmpty())
                        .forEach(participants::add);
                continue;
            }
            Matcher speaker = SPEAKER_PREFIX.matcher(line);
            if (speaker.find() && !isKeyword(speaker.group(1))) {
                participants.add(speaker.group(1));
            }
            body.add(line);
        }
        return new MeetingNotes(title, List.copyOf(participants), String.join("\n", body).strip());
    }

    private static boolean isTitle(String line) {
        return line.startsWith("#") || line.toLowerCase(Locale.ROOT).startsWith("title:");
    }

    private static boolean isKeyword(String word) {
        return switch (word.toLowerCase(Locale.ROOT)) {
            case "action", "actions", "todo", "note", "notes", "decision", "decisions", "agenda", "date" -> true;
            default -> false;
        };
    }
}
