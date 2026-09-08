package io.github.vikthorvergara.playground.meetingnotes;

import com.embabel.agent.api.annotation.AchievesGoal;
import com.embabel.agent.api.annotation.Action;
import com.embabel.agent.api.annotation.Agent;
import com.embabel.agent.api.common.OperationContext;
import com.embabel.agent.domain.io.UserInput;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@Agent(description = "Turns raw meeting notes into a prioritised list of action items with owners and due dates")
public class MeetingNotesAgent {

    static final Comparator<ActionItem> REPORT_ORDER = Comparator
            .comparing(ActionItem::priority)
            .thenComparing(ActionItem::dueDate, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(ActionItem::description);

    @Action(description = "Parse raw meeting notes into title, participants and body")
    public MeetingNotes parseNotes(UserInput input) {
        return MeetingNotesParser.parse(input.getContent());
    }

    @Action(description = "Extract action items from the meeting notes")
    public ActionItemDraft extractActionItems(MeetingNotes notes, OperationContext context) {
        return context.ai()
                .withDefaultLlm()
                .createObject("""
                        Extract every action item from the meeting notes below.

                        Rules:
                        - Only include work someone committed to or was asked to do. Skip discussion and decisions.
                        - Owner must be one of the participants when possible. Use null when nobody was assigned.
                        - Resolve relative dates ("next Friday", "end of month") against today's date.
                        - Priority: HIGH if it blocks others or has a date within a week, LOW if it is optional
                          or "nice to have", otherwise MEDIUM.

                        Participants: %s

                        Meeting: %s
                        ---
                        %s
                        """.formatted(
                        notes.participants().isEmpty() ? "unknown" : String.join(", ", notes.participants()),
                        notes.title(),
                        notes.body()),
                        ActionItemDraft.class);
    }

    @AchievesGoal(description = "A prioritised action item report has been produced from meeting notes")
    @Action(description = "Validate owners and order action items by priority and due date")
    public ActionItemReport prioritise(ActionItemDraft draft, MeetingNotes notes) {
        List<ActionItem> items = draft.items().stream()
                .map(item -> item.withOwner(resolveOwner(item.owner(), notes.participants())))
                .sorted(REPORT_ORDER)
                .toList();
        return new ActionItemReport(notes.title(), items);
    }

    static String resolveOwner(String owner, List<String> participants) {
        if (owner == null || owner.isBlank()) {
            return ActionItem.UNASSIGNED;
        }
        if (participants.isEmpty()) {
            return owner.strip();
        }
        String wanted = owner.strip().toLowerCase(Locale.ROOT);
        return participants.stream()
                .filter(p -> p.toLowerCase(Locale.ROOT).equals(wanted)
                        || p.toLowerCase(Locale.ROOT).startsWith(wanted + " "))
                .findFirst()
                .orElse(ActionItem.UNASSIGNED);
    }
}
