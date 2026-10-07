package io.github.vikthorvergara.playground.decisions;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * @param maxOptions     options beyond this are dropped (each one costs an LLM call)
 * @param maxCriteria    criteria beyond this are dropped
 * @param maxConcurrency how many options are assessed at the same time
 */
@ConfigurationProperties("decisions")
public record DecisionProperties(
        @DefaultValue("5") int maxOptions,
        @DefaultValue("6") int maxCriteria,
        @DefaultValue("4") int maxConcurrency) {

    public static final String ANALYST_ROLE = "analyst";
    public static final String JUDGE_ROLE = "judge";
}
