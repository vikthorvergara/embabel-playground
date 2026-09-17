package io.github.vikthorvergara.playground.server;

import com.embabel.agent.api.common.autonomy.AgentProcessExecution;
import com.embabel.agent.api.common.autonomy.Autonomy;
import com.embabel.agent.core.Agent;
import com.embabel.agent.core.AgentPlatform;
import com.embabel.agent.core.Goal;
import com.embabel.agent.core.ProcessOptions;
import org.springframework.stereotype.Service;

/**
 * Runs an exported goal the same way Embabel's MCP goal tools do: a single-goal agent built from
 * every action on the platform, started from the goal's input type.
 */
@Service
public class GoalRunner {

    private final Autonomy autonomy;
    private final AgentPlatform platform;

    public GoalRunner(Autonomy autonomy, AgentPlatform platform) {
        this.autonomy = autonomy;
        this.platform = platform;
    }

    public AgentProcessExecution run(Goal goal, Object input) {
        Agent agent = autonomy.createGoalAgent(input, platform, goal, false);
        return autonomy.runAgent(input, new ProcessOptions(), agent);
    }
}
