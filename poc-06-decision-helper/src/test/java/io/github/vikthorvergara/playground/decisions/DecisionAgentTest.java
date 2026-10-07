package io.github.vikthorvergara.playground.decisions;

import com.embabel.agent.domain.io.UserInput;
import com.embabel.agent.test.unit.FakeOperationContext;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DecisionAgentTest {

    private final DecisionAgent agent = new DecisionAgent(new DecisionProperties(3, 2, 4));

    private final List<Criterion> criteria = List.of(
            new Criterion("Performance", 5, "Fast under load"),
            new Criterion("Learning curve", 1, "Easy to pick up"));
    private final DecisionRequest request = new DecisionRequest("Which queue?", List.of("Kafka", "RabbitMQ", "SQS"), criteria);

    @Test
    void frameDecisionNormalisesWhatTheLlmReturns() {
        var context = FakeOperationContext.create();
        context.expectResponse(new DecisionRequest("Which queue?",
                List.of(" Kafka", "RabbitMQ", "Kafka", "SQS", "Pulsar"),
                List.of(new Criterion("Performance", 9, "Fast"), new Criterion("Performance", 2, "dup"),
                        new Criterion("Cost", 0, "Cheap"), new Criterion("Ops", 3, "Easy to run"))));

        DecisionRequest framed = agent.frameDecision(new UserInput("Kafka, RabbitMQ or SQS for our events?"), context);

        assertThat(framed.options()).containsExactly("Kafka", "RabbitMQ", "SQS");
        assertThat(framed.criteria()).extracting(Criterion::name).containsExactly("Performance", "Cost");
        assertThat(framed.criteria()).extracting(Criterion::weight).containsExactly(5, 1);
        assertThat(context.getLlmInvocations().getFirst().getPrompt()).contains("Kafka, RabbitMQ or SQS for our events?");
    }

    @Test
    void framingNeedsAtLeastTwoOptions() {
        var context = FakeOperationContext.create();
        context.expectResponse(new DecisionRequest("Q", List.of("Kafka", " Kafka "), criteria));

        assertThatThrownBy(() -> agent.frameDecision(new UserInput("Kafka?"), context))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void assessUsesTheAnalystAndKeepsTheLlmInsideTheFrame() {
        var context = FakeOperationContext.create();
        context.expectResponse(new OptionAssessment("Apache Kafka", List.of(
                new CriterionScore("performance", 9, "Very fast"),
                new CriterionScore("Made up", 5, "?")), null));

        OptionAssessment assessment = agent.assess("Kafka", request, context);

        assertThat(assessment.option()).isEqualTo("Kafka");
        assertThat(assessment.scores()).containsExactly(
                new CriterionScore("Performance", 5, "Very fast"),
                new CriterionScore("Learning curve", 1, "Not assessed"));
        assertThat(assessment.risks()).isEmpty();
        var invocation = context.getLlmInvocations().getFirst();
        assertThat(Roles.selectsRole(invocation.getInteraction().getLlm(), DecisionProperties.ANALYST_ROLE)).isTrue();
        assertThat(invocation.getPrompt()).contains("Option: Kafka").contains("- Learning curve: Easy to pick up");
    }

    @Test
    void rankUsesWeightedAverageAndKeepsInputOrderOnTies() {
        Comparison comparison = DecisionAgent.rank(request, List.of(
                assessment("SQS", 3, 5),        // (15 + 5) / 6 = 3.33
                assessment("Kafka", 5, 1),      // (25 + 1) / 6 = 4.33
                assessment("RabbitMQ", 3, 5))); // tie with SQS, given earlier

        assertThat(comparison.ranking()).extracting(RankedOption::option).containsExactly("Kafka", "RabbitMQ", "SQS");
        assertThat(comparison.ranking()).extracting(RankedOption::weightedScore).containsExactly(4.33, 3.33, 3.33);
        assertThat(comparison.ranking()).extracting(RankedOption::rank).containsExactly(1, 2, 3);
    }

    @Test
    void recommendTellsTheJudgeTheWinnerAndRendersATable() {
        var context = FakeOperationContext.create();
        context.expectResponse(new Explanation("Kafka wins on performance.", "If the team is small, RabbitMQ."));
        Comparison comparison = DecisionAgent.rank(request, List.of(
                assessment("Kafka", 5, 1), assessment("RabbitMQ", 3, 5), assessment("SQS", 2, 4)));

        Recommendation recommendation = agent.recommend(comparison, request, context);

        assertThat(recommendation.winner()).isEqualTo("Kafka");
        var invocation = context.getLlmInvocations().getFirst();
        assertThat(Roles.selectsRole(invocation.getInteraction().getLlm(), DecisionProperties.JUDGE_ROLE)).isTrue();
        assertThat(invocation.getPrompt()).contains("The recommendation is Kafka.").contains("1. Kafka (4.33)");
        assertThat(recommendation.toMarkdown())
                .contains("**Recommendation: Kafka**")
                .contains("| # | Option | Score | Performance (x5) | Learning curve (x1) |")
                .contains("| 1 | Kafka | 4.33 | 5 | 1 |")
                .contains("When to pick RabbitMQ instead: If the team is small, RabbitMQ.");
    }

    private OptionAssessment assessment(String option, int performance, int learning) {
        return new OptionAssessment(option, List.of(
                new CriterionScore("Performance", performance, "p"),
                new CriterionScore("Learning curve", learning, "l")), List.of("risk"));
    }
}
