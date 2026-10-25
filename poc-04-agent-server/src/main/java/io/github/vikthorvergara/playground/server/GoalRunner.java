package io.github.vikthorvergara.playground.server;

import com.embabel.agent.api.common.autonomy.AgentProcessExecution;
import com.embabel.agent.api.common.autonomy.Autonomy;
import com.embabel.agent.core.Agent;
import com.embabel.agent.core.AgentPlatform;
import com.embabel.agent.core.Budget;
import com.embabel.agent.core.Goal;
import com.embabel.agent.core.ProcessOptions;
import com.embabel.agent.core.Verbosity;
import org.springframework.stereotype.Service;

/**
 * Runs an exported goal the same way Embabel's MCP goal tools do: a single-goal agent built from
 * every action on the platform, started from the goal's input type.
 */
@Service
public class GoalRunner {

    static final int BUDGET_ACTIONS = 40;
    static final int BUDGET_TOKENS = 300_000;

    private final Autonomy autonomy;
    private final AgentPlatform platform;
    private final ServerProperties properties;

    public GoalRunner(Autonomy autonomy, AgentPlatform platform, ServerProperties properties) {
        this.autonomy = autonomy;
        this.platform = platform;
        this.properties = properties;
    }

    public AgentProcessExecution run(Goal goal, Object input) {
        Agent agent = autonomy.createGoalAgent(input, platform, goal, false);
        return autonomy.runAgent(input, options(goal), agent);
    }

    ProcessOptions options(Goal goal) {
        ProcessOptions options = new ProcessOptions();
        Double budgetUsd = properties.budgets().get(ExportedGoals.toolName(goal));
        if (budgetUsd != null) {
            options = options.withBudget(new Budget(budgetUsd, BUDGET_ACTIONS, BUDGET_TOKENS));
        }
        if (properties.showPlanning()) {
            options = options.withVerbosity(new Verbosity().showPlanning());
        }
        return options;
    }
}
