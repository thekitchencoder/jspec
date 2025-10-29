package uk.codery.rules;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public record SpecificationEvaluator(RuleEvaluator evaluator) {
    public SpecificationEvaluator(){
        this(new RuleEvaluator());
    }

    public EvaluationOutcome evaluate(Object doc, Specification specification) {
        RuleEvaluator evaluator = new RuleEvaluator();

        Map<String, EvaluationResult> ruleResults =
                specification.rules().parallelStream()
                        .map(rule -> evaluator.evaluateRule(doc, rule))
                        .collect(Collectors.toMap(result -> result.rule().id(), Function.identity()));

        List<RuleSetResult> results = specification.ruleSets().parallelStream().map(ruleSet -> {
            List<EvaluationResult> ruleSetResults = ruleSet.rules().parallelStream()
                    .map(rule -> ruleResults.getOrDefault(rule.id(), evaluator.evaluateRule(doc, rule)))
                    .toList();

            boolean match = (Operator.AND == ruleSet.operator())
                    ? ruleSetResults.stream().allMatch(EvaluationResult::matched)
                    : ruleSetResults.stream().anyMatch(EvaluationResult::matched);
            return new RuleSetResult(ruleSet.id(), ruleSet.operator(), ruleSetResults, match);
        }).toList();

        return new EvaluationOutcome(specification.id(), new ArrayList<>(ruleResults.values()), results);
    }
}