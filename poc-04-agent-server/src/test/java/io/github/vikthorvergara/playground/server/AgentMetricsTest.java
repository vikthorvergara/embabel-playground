package io.github.vikthorvergara.playground.server;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AgentMetricsTest {

    @Test
    void shortNameKeepsClassAndMethod() {
        assertThat(AgentMetrics.shortName("io.github.vikthorvergara.playground.prreview.PrReviewAgent.review"))
                .isEqualTo("PrReviewAgent.review");
        assertThat(AgentMetrics.shortName("Agent.action")).isEqualTo("Agent.action");
        assertThat(AgentMetrics.shortName("plain")).isEqualTo("plain");
    }
}
