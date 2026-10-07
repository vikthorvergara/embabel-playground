package io.github.vikthorvergara.playground.decisions;

import com.embabel.agent.api.invocation.AgentInvocation;
import com.embabel.agent.domain.io.UserInput;
import com.embabel.agent.test.integration.EmbabelMockitoIntegrationTest;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.context.TestPropertySource;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Real planner and ScatterGather sub-process, mocked LLM.
 */
@TestPropertySource(properties = {
        "ANTHROPIC_API_KEY=test-key",
        "embabel.agent.shell.interactive.enabled=false"
})
class DecisionFlowTest extends EmbabelMockitoIntegrationTest {

    private static final List<Criterion> CRITERIA = List.of(
            new Criterion("Performance", 5, "Fast under load"),
            new Criterion("Operations", 3, "Easy to run"));

    @Test
    void everyOptionIsAssessedInParallelAndTheBestWeightedScoreWins() {
        Set<String> analystThreads = ConcurrentHashMap.newKeySet();
        // Each analyst call waits until all three are in flight: this only passes if they run concurrently
        CyclicBarrier allInFlight = new CyclicBarrier(3);
        whenCreateObject(prompt -> prompt.contains("Frame this technical decision"), DecisionRequest.class)
                .thenReturn(new DecisionRequest("Which queue for order events?", List.of("Kafka", "RabbitMQ", "SQS"), CRITERIA));
        analyst("Kafka", 5, 2, analystThreads, allInFlight);    // (25 + 6) / 8 = 3.88
        analyst("RabbitMQ", 3, 4, analystThreads, allInFlight); // (15 + 12) / 8 = 3.38
        analyst("SQS", 3, 5, analystThreads, allInFlight);      // (15 + 15) / 8 = 3.75
        whenCreateObject(prompt -> prompt.contains("The ranking is final"), Explanation.class,
                interaction -> Roles.selectsRole(interaction.getLlm(), DecisionProperties.JUDGE_ROLE))
                .thenReturn(new Explanation("Kafka has the best performance.", "SQS if nobody wants to run a cluster."));

        Recommendation recommendation = AgentInvocation.create(agentPlatform, Recommendation.class)
                .invoke(new UserInput("Kafka, RabbitMQ or SQS for order events? Throughput matters most."));

        assertThat(recommendation.winner()).isEqualTo("Kafka");
        assertThat(recommendation.ranking()).extracting(RankedOption::option).containsExactly("Kafka", "SQS", "RabbitMQ");
        assertThat(recommendation.ranking()).extracting(RankedOption::weightedScore).containsExactly(3.88, 3.75, 3.38);
        assertThat(llmCalls(OptionAssessment.class)).isEqualTo(3);
        assertThat(recommendation.toMarkdown()).contains("When to pick SQS instead: SQS if nobody wants to run a cluster.");
        assertThat(analystThreads).hasSize(3);
    }

    private void analyst(String option, int performance, int operations, Set<String> threads, CyclicBarrier barrier) {
        whenCreateObject(prompt -> prompt.contains("Option: " + option + "\n"), OptionAssessment.class,
                interaction -> Roles.selectsRole(interaction.getLlm(), DecisionProperties.ANALYST_ROLE))
                .thenAnswer(invocation -> {
                    threads.add(Thread.currentThread().getName());
                    barrier.await(10, TimeUnit.SECONDS);
                    return new OptionAssessment(option, List.of(
                            new CriterionScore("Performance", performance, "p"),
                            new CriterionScore("Operations", operations, "o")), List.of("risk"));
                });
    }

    private long llmCalls(Class<?> outputClass) {
        return Mockito.mockingDetails(llmOperations).getInvocations().stream()
                .filter(i -> i.getMethod().getName().equals("createObject"))
                .filter(i -> outputClass.equals(i.getArgument(2)))
                .count();
    }
}
