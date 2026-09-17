package io.github.vikthorvergara.playground.server;

import com.embabel.agent.core.AgentPlatform;
import com.embabel.agent.core.Goal;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * The goals marked {@code @Export(remote = true)}: the same set Embabel publishes as MCP tools.
 */
@Component
public class ExportedGoals {

    private final AgentPlatform platform;

    public ExportedGoals(AgentPlatform platform) {
        this.platform = platform;
    }

    public List<Goal> all() {
        return platform.getGoals().stream()
                .filter(goal -> goal.getExport().getRemote() && goal.getExport().getName() != null)
                .sorted(Comparator.comparing(ExportedGoals::toolName))
                .toList();
    }

    public Optional<Goal> byToolName(String name) {
        return all().stream().filter(goal -> toolName(goal).equals(name)).findFirst();
    }

    public static String toolName(Goal goal) {
        return goal.getExport().getName();
    }

    /** Exported goals declare exactly one starting input type in this project. */
    public static Class<?> inputType(Goal goal) {
        return goal.getExport().getStartingInputTypes().iterator().next();
    }
}
