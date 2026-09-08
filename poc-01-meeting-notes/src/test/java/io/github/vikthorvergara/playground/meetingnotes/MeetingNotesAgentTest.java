package io.github.vikthorvergara.playground.meetingnotes;

import com.embabel.agent.domain.io.UserInput;
import com.embabel.agent.test.unit.FakeOperationContext;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MeetingNotesAgentTest {

    private final MeetingNotesAgent agent = new MeetingNotesAgent();

    private final MeetingNotes notes = new MeetingNotes(
            "Sprint 42 planning",
            List.of("Ana Souza", "Bruno Lima", "Carla Mendes"),
            "Bruno: I'll take the checkout bug.");

    @Test
    void parseNotesDelegatesToParser() {
        MeetingNotes parsed = agent.parseNotes(new UserInput("# Retro\nAttendees: Ana, Bruno\nAna: ship it"));

        assertThat(parsed.title()).isEqualTo("Retro");
        assertThat(parsed.participants()).containsExactly("Ana", "Bruno");
    }

    @Test
    void extractActionItemsSendsNotesAndParticipantsToTheLlm() {
        var context = FakeOperationContext.create();
        var expected = new ActionItemDraft(List.of(
                new ActionItem("Fix the checkout bug", "Bruno", LocalDate.of(2026, 9, 11), Priority.HIGH)));
        context.expectResponse(expected);

        ActionItemDraft draft = agent.extractActionItems(notes, context);

        assertThat(draft).isEqualTo(expected);
        assertThat(context.getLlmInvocations()).hasSize(1);
        String prompt = context.getLlmInvocations().getFirst().getPrompt();
        assertThat(prompt)
                .contains("Participants: Ana Souza, Bruno Lima, Carla Mendes")
                .contains("Meeting: Sprint 42 planning")
                .contains("I'll take the checkout bug");
    }

    @Test
    void prioritiseOrdersByPriorityThenDueDateAndResolvesOwners() {
        var draft = new ActionItemDraft(List.of(
                new ActionItem("Update onboarding docs", "Carla", null, Priority.LOW),
                new ActionItem("Look at flaky payment test", null, null, Priority.MEDIUM),
                new ActionItem("Fix the checkout bug", "bruno", LocalDate.of(2026, 9, 11), Priority.HIGH),
                new ActionItem("Book the retro room", "Someone Else", LocalDate.of(2026, 9, 10), Priority.HIGH)));

        ActionItemReport report = agent.prioritise(draft, notes);

        assertThat(report.meetingTitle()).isEqualTo("Sprint 42 planning");
        assertThat(report.items()).extracting(ActionItem::description).containsExactly(
                "Book the retro room",
                "Fix the checkout bug",
                "Look at flaky payment test",
                "Update onboarding docs");
        assertThat(report.items()).extracting(ActionItem::owner).containsExactly(
                ActionItem.UNASSIGNED,
                "Bruno Lima",
                ActionItem.UNASSIGNED,
                "Carla Mendes");
    }

    @Test
    void ownersAreKeptAsIsWhenParticipantsAreUnknown() {
        assertThat(MeetingNotesAgent.resolveOwner(" Dani ", List.of())).isEqualTo("Dani");
        assertThat(MeetingNotesAgent.resolveOwner("", List.of())).isEqualTo(ActionItem.UNASSIGNED);
    }

    @Test
    void reportRendersAsMarkdown() {
        var report = new ActionItemReport("Retro", List.of(
                new ActionItem("Fix the checkout bug", "Bruno Lima", LocalDate.of(2026, 9, 11), Priority.HIGH)));

        assertThat(report.toMarkdown()).isEqualTo("""
                ## Retro

                - [HIGH] Fix the checkout bug (owner: Bruno Lima, due 2026-09-11)""");
        assertThat(new ActionItemReport("Empty", List.of()).toMarkdown()).isEqualTo("## Empty\n\nNo action items.");
    }
}
