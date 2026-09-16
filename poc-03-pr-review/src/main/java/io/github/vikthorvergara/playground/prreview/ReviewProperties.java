package io.github.vikthorvergara.playground.prreview;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * @param criticEnabled  false skips the critic entirely: one draft, no loop (for comparing runs)
 * @param maxIterations  hard cap on draft/critique rounds
 * @param scoreThreshold critic score at which a draft is accepted
 */
@ConfigurationProperties("review")
public record ReviewProperties(
        @DefaultValue("true") boolean criticEnabled,
        @DefaultValue("3") int maxIterations,
        @DefaultValue("0.8") double scoreThreshold) {

    public static final String DRAFTER_ROLE = "drafter";
    public static final String CRITIC_ROLE = "critic";

    public ReviewProperties {
        if (maxIterations < 1) {
            throw new IllegalArgumentException("review.max-iterations must be at least 1");
        }
    }
}
