package io.github.vikthorvergara.playground.server;

import com.embabel.agent.api.event.ActionExecutionResultEvent;
import com.embabel.agent.api.event.AgentProcessCompletedEvent;
import com.embabel.agent.api.event.AgentProcessEvent;
import com.embabel.agent.api.event.AgentProcessFailedEvent;
import com.embabel.agent.api.event.AgentProcessStuckEvent;
import com.embabel.agent.api.event.AgenticEventListener;
import com.embabel.agent.core.AgentProcess;
import com.embabel.agent.core.Usage;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Turns Embabel process events into Micrometer metrics. Every AgenticEventListener bean is picked up
 * by the platform automatically.
 */
@Component
class AgentMetrics implements AgenticEventListener {

    private static final Logger log = LoggerFactory.getLogger(AgentMetrics.class);

    private final MeterRegistry registry;

    AgentMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    @Override
    public void onProcessEvent(AgentProcessEvent event) {
        switch (event) {
            case ActionExecutionResultEvent e -> Timer.builder("agent.action")
                    .description("Time spent in each agent action")
                    .tag("action", shortName(e.getAction().getName()))
                    .tag("status", e.getActionStatus().getStatus().name().toLowerCase())
                    .register(registry)
                    .record(e.getRunningTime());
            case AgentProcessCompletedEvent e -> finished(e.getAgentProcess(), "completed");
            case AgentProcessFailedEvent e -> finished(e.getAgentProcess(), "failed");
            case AgentProcessStuckEvent e -> finished(e.getAgentProcess(), "stuck");
            default -> {
            }
        }
    }

    private void finished(AgentProcess process, String outcome) {
        // Goal agents built for MCP/REST runs are named "goal-<goal name>"
        String agent = shortName(process.getAgent().getName().replaceFirst("^goal-", ""));
        registry.counter("agent.runs", "agent", agent, "outcome", outcome).increment();
        Usage usage = process.usage();
        registry.counter("agent.llm.tokens", "agent", agent, "type", "prompt").increment(orZero(usage.getPromptTokens()));
        registry.counter("agent.llm.tokens", "agent", agent, "type", "completion").increment(orZero(usage.getCompletionTokens()));
        registry.counter("agent.llm.cost", "agent", agent).increment(process.cost());
        log.info("[{}] {} {}: {} LLM call(s), {} tokens, ${}", process.getId(), agent, outcome,
                process.getLlmInvocations().size(), (long) orZero(usage.getTotalTokens()), "%.4f".formatted(process.cost()));
    }

    /** "io.github...PrReviewAgent.review" becomes "PrReviewAgent.review". */
    static String shortName(String actionName) {
        int method = actionName.lastIndexOf('.');
        int type = method > 0 ? actionName.lastIndexOf('.', method - 1) : -1;
        return type >= 0 ? actionName.substring(type + 1) : actionName;
    }

    private static double orZero(Integer value) {
        return value == null ? 0 : value;
    }
}
