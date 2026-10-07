package io.github.vikthorvergara.playground.decisions;

import com.embabel.agent.api.annotation.AchievesGoal;
import com.embabel.agent.api.annotation.Action;
import com.embabel.agent.api.annotation.Agent;
import com.embabel.agent.api.common.ActionContext;
import com.embabel.agent.api.common.OperationContext;
import com.embabel.agent.api.common.SupplierActionContext;
import com.embabel.agent.api.common.workflow.control.ScatterGatherBuilder;
import com.embabel.agent.domain.io.UserInput;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Agent(description = "Compares options for a technical decision against weighted criteria and recommends one")
public class DecisionAgent {

    private final DecisionProperties properties;

    public DecisionAgent(DecisionProperties properties) {
        this.properties = properties;
    }

    @Action(description = "Turn a free-text question into options and weighted criteria")
    public DecisionRequest frameDecision(UserInput input, OperationContext context) {
        DecisionRequest framed = context.ai()
                .withDefaultLlm()
                .createObject("""
                        Frame this technical decision for a structured comparison.

                        - options: the alternatives being compared, as short names. Use the ones the user names;
                          only suggest options if none are given. At most %d.
                        - criteria: 3 to %d things that matter for this decision, each with a weight from 1 to 5.
                          Take any priorities the user states into account in the weights.

                        Decision: %s
                        """.formatted(properties.maxOptions(), properties.maxCriteria(), input.getContent()),
                        DecisionRequest.class);
        return normalise(framed);
    }

    @Action(description = "Assess every option in parallel and rank them by weighted score")
    public Comparison compareOptions(DecisionRequest request, ActionContext context) {
        List<Function<SupplierActionContext<OptionAssessment>, OptionAssessment>> assessors = request.options().stream()
                .<Function<SupplierActionContext<OptionAssessment>, OptionAssessment>>map(
                        option -> ctx -> assess(option, request, ctx))
                .toList();
        var fanOut = new ScatterGatherBuilder<>(OptionAssessment.class, Comparison.class, properties.maxConcurrency())
                .withGenerators(assessors)
                .consolidatedBy(ctx -> rank(request, ctx.getInput().getResults()))
                .build();
        return context.asSubProcess(Comparison.class, fanOut);
    }

    @AchievesGoal(description = "A technical decision has a ranked comparison and a recommendation")
    @Action(description = "Explain the recommendation; the ranking itself is not up to the LLM")
    public Recommendation recommend(Comparison comparison, DecisionRequest request, OperationContext context) {
        String table = comparison.ranking().stream()
                .map(r -> "%d. %s (%.2f): %s. Risks: %s".formatted(r.rank(), r.option(), r.weightedScore(),
                        r.assessment().scores().stream()
                                .map(s -> s.criterion() + " " + s.score() + "/5 (" + s.rationale() + ")")
                                .collect(Collectors.joining("; ")),
                        String.join("; ", r.assessment().risks())))
                .collect(Collectors.joining("\n"));
        Explanation explanation = context.ai()
                .withLlmByRole(DecisionProperties.JUDGE_ROLE)
                .createObject("""
                        A team asked: %s

                        The options were scored against weighted criteria. The ranking is final.
                        The recommendation is %s. Explain why it came out on top, referring to the scores,
                        and say in which situation the runner-up would be the better choice.

                        Ranking:
                        %s
                        """.formatted(request.question(), comparison.winner().option(), table),
                        Explanation.class);
        return new Recommendation(request.question(), request.criteria(), comparison.ranking(), explanation);
    }

    OptionAssessment assess(String option, DecisionRequest request, OperationContext context) {
        String criteria = request.criteria().stream()
                .map(c -> "- %s: %s".formatted(c.name(), c.description()))
                .collect(Collectors.joining("\n"));
        OptionAssessment raw = context.ai()
                .withLlmByRole(DecisionProperties.ANALYST_ROLE)
                .createObject("""
                        Assess one option for this decision: %s

                        Option: %s

                        Score it from 1 to 5 on each criterion below, with a one-sentence rationale, and list its
                        main risks. Judge this option on its own merits; the other options are assessed separately.

                        Criteria:
                        %s
                        """.formatted(request.question(), option, criteria),
                        OptionAssessment.class);
        return clean(option, request.criteria(), raw);
    }

    /** Keep the LLM inside the frame: our option name, our criteria, scores clamped to 1..5. */
    static OptionAssessment clean(String option, List<Criterion> criteria, OptionAssessment raw) {
        Map<String, CriterionScore> byName = raw.scores() == null ? Map.of() : raw.scores().stream()
                .filter(s -> s.criterion() != null)
                .collect(Collectors.toMap(s -> s.criterion().strip().toLowerCase(), s -> s, (a, b) -> a));
        List<CriterionScore> scores = criteria.stream()
                .map(c -> {
                    CriterionScore s = byName.get(c.name().toLowerCase());
                    return s == null
                            ? new CriterionScore(c.name(), 1, "Not assessed")
                            : new CriterionScore(c.name(), clamp(s.score()), s.rationale());
                })
                .toList();
        return new OptionAssessment(option, scores, raw.risks() == null ? List.of() : raw.risks());
    }

    /** Weighted average per option, highest first. Ties keep the order the options were given in. */
    static Comparison rank(DecisionRequest request, List<OptionAssessment> assessments) {
        Map<String, OptionAssessment> byOption = assessments.stream()
                .collect(Collectors.toMap(OptionAssessment::option, a -> a, (a, b) -> a));
        int totalWeight = request.criteria().stream().mapToInt(Criterion::weight).sum();
        List<OptionAssessment> ordered = request.options().stream()
                .map(byOption::get)
                .filter(a -> a != null)
                .sorted(Comparator.comparingDouble((OptionAssessment a) -> weightedScore(request, a, totalWeight)).reversed())
                .toList();
        return new Comparison(IntStream.range(0, ordered.size())
                .mapToObj(i -> new RankedOption(i + 1, ordered.get(i).option(),
                        weightedScore(request, ordered.get(i), totalWeight), ordered.get(i)))
                .toList());
    }

    private static double weightedScore(DecisionRequest request, OptionAssessment assessment, int totalWeight) {
        Map<String, Integer> scores = assessment.scores().stream()
                .collect(Collectors.toMap(CriterionScore::criterion, CriterionScore::score, (a, b) -> a));
        double sum = request.criteria().stream()
                .mapToDouble(c -> c.weight() * scores.getOrDefault(c.name(), 1))
                .sum();
        return Math.round(sum / totalWeight * 100) / 100.0;
    }

    /** Dedupe, trim and cap what the LLM framed, and clamp weights to 1..5. */
    DecisionRequest normalise(DecisionRequest framed) {
        List<String> options = new ArrayList<>(new LinkedHashSet<>(framed.options().stream()
                .map(String::strip).filter(o -> !o.isEmpty()).toList()));
        if (options.size() < 2) {
            throw new IllegalArgumentException("Need at least two options to compare, got " + options);
        }
        List<Criterion> criteria = framed.criteria().stream()
                .filter(c -> c.name() != null && !c.name().isBlank())
                .collect(Collectors.toMap(c -> c.name().strip(), c -> c, (a, b) -> a, java.util.LinkedHashMap::new))
                .values().stream()
                .limit(properties.maxCriteria())
                .map(c -> new Criterion(c.name().strip(), clamp(c.weight()), c.description()))
                .toList();
        if (criteria.isEmpty()) {
            throw new IllegalArgumentException("Need at least one criterion");
        }
        return new DecisionRequest(framed.question(), options.subList(0, Math.min(options.size(), properties.maxOptions())), criteria);
    }

    private static int clamp(int value) {
        return Math.max(1, Math.min(5, value));
    }
}
