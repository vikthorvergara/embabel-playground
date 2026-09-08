package io.github.vikthorvergara.playground.meetingnotes;

import com.embabel.agent.api.annotation.support.AgentMetadataReader;
import com.embabel.agent.core.Action;
import com.embabel.agent.core.AgentScope;
import com.embabel.agent.core.Goal;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MeetingNotesAgentStructureTest {

    @Test
    void plannerSeesThreeActionsAndOneGoal() {
        AgentScope scope = new AgentMetadataReader().createAgentMetadata(new MeetingNotesAgent());

        assertThat(scope).isNotNull();
        assertThat(scope.getActions()).extracting(Action::getName)
                .anySatisfy(name -> assertThat(name).endsWith("parseNotes"))
                .anySatisfy(name -> assertThat(name).endsWith("extractActionItems"))
                .anySatisfy(name -> assertThat(name).endsWith("prioritise"))
                .hasSize(3);
        assertThat(scope.getGoals()).extracting(Goal::getDescription)
                .containsExactly("A prioritised action item report has been produced from meeting notes");
    }
}
