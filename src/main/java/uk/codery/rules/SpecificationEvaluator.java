package uk.codery.rules;

import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
public record SpecificationEvaluator(RuleEvaluator evaluator) {

    public SpecificationEvaluator(){
        this(new RuleEvaluator());
    }

    public EvaluationOutcome evaluate(Object doc, Specification specification) {
        log.info("Starting evaluation of specification '{}'", specification.id());

        // FIX: Use this.evaluator instead of creating new instance
        Map<String, EvaluationResult> ruleResults =
                specification.rules().parallelStream()
                        .map(rule -> this.evaluator.evaluateRule(doc, rule))
                        .collect(Collectors.toMap(result -> result.rule().id(), Function.identity()));

        log.debug("Evaluated {} rules for specification '{}'", ruleResults.size(), specification.id());

        List<RuleSetResult> results = specification.ruleSets().parallelStream().map(ruleSet -> {
            List<EvaluationResult> ruleSetResults = ruleSet.rules().parallelStream()
                    .map(rule -> ruleResults.getOrDefault(rule.id(), this.evaluator.evaluateRule(doc, rule)))
                    .toList();

            boolean match = (Operator.AND == ruleSet.operator())
                    ? ruleSetResults.stream().allMatch(EvaluationResult::matched)
                    : ruleSetResults.stream().anyMatch(EvaluationResult::matched);
            return new RuleSetResult(ruleSet.id(), ruleSet.operator(), ruleSetResults, match);
        }).toList();

        // Create summary
        EvaluationSummary summary = EvaluationSummary.from(ruleResults.values());

        log.info("Completed evaluation of specification '{}' - Total: {}, Matched: {}, Not Matched: {}, Undetermined: {}, Fully Determined: {}",
                   specification.id(), summary.totalRules(), summary.matchedRules(),
                   summary.notMatchedRules(), summary.undeterminedRules(), summary.fullyDetermined());

        return new EvaluationOutcome(specification.id(), new ArrayList<>(ruleResults.values()), results, summary);
    }
}