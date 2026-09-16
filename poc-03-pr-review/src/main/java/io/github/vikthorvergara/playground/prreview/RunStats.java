package io.github.vikthorvergara.playground.prreview;

import com.embabel.agent.core.LlmInvocation;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.stream.Collectors;

public record RunStats(int llmCalls, int promptTokens, int completionTokens, double costUsd,
                       Map<String, Long> callsByModel) {

    static RunStats from(List<LlmInvocation> invocations) {
        return new RunStats(
                invocations.size(),
                invocations.stream().map(i -> i.getUsage().getPromptTokens()).filter(Objects::nonNull).mapToInt(Integer::intValue).sum(),
                invocations.stream().map(i -> i.getUsage().getCompletionTokens()).filter(Objects::nonNull).mapToInt(Integer::intValue).sum(),
                invocations.stream().mapToDouble(LlmInvocation::cost).sum(),
                invocations.stream().collect(Collectors.groupingBy(i -> i.getLlmMetadata().getName(), TreeMap::new, Collectors.counting())));
    }

    @Override
    public String toString() {
        return "%d LLM call(s) %s, %d prompt + %d completion tokens, $%.4f".formatted(
                llmCalls, callsByModel, promptTokens, completionTokens, costUsd);
    }
}
