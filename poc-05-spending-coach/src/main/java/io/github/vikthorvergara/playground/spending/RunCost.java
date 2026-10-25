package io.github.vikthorvergara.playground.spending;

import com.embabel.agent.core.AgentProcess;
import com.embabel.agent.core.Usage;
import com.embabel.common.ai.model.LlmMetadata;

import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

public record RunCost(int llmCalls, int promptTokens, int completionTokens, double costUsd, Set<String> models) {

    static RunCost from(AgentProcess process) {
        if (process == null) {
            return new RunCost(0, 0, 0, 0.0, Set.of());
        }
        Usage usage = process.usage();
        return new RunCost(
                process.llmInvocationCount(),
                orZero(usage.getPromptTokens()),
                orZero(usage.getCompletionTokens()),
                process.cost(),
                process.modelsUsed().stream().map(LlmMetadata::getName).collect(Collectors.toCollection(TreeSet::new)));
    }

    private static int orZero(Integer value) {
        return value == null ? 0 : value;
    }

    @Override
    public String toString() {
        return "%d LLM call(s) %s, %d prompt + %d completion tokens, $%.4f".formatted(
                llmCalls, models, promptTokens, completionTokens, costUsd);
    }
}
