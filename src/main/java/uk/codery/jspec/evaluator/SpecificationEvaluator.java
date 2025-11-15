package uk.codery.jspec.evaluator;

import lombok.extern.slf4j.Slf4j;
import uk.codery.jspec.model.Junction;
import uk.codery.jspec.model.Specification;
import uk.codery.jspec.result.EvaluationOutcome;
import uk.codery.jspec.result.EvaluationResult;
import uk.codery.jspec.result.EvaluationSummary;
import uk.codery.jspec.result.CriteriaGroupResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
public record SpecificationEvaluator(CriterionEvaluator evaluator) {

    public SpecificationEvaluator(){
        this(new CriterionEvaluator());
    }

    public EvaluationOutcome evaluate(Object doc, Specification specification) {
        log.info("Starting evaluation of specification '{}'", specification.id());

        // FIX: Use this.evaluator instead of creating new instance
        Map<String, EvaluationResult> criteriaResultMap =
                specification.criteria().parallelStream()
                        .map(criterion -> this.evaluator.evaluateCriterion(doc, criterion))
                        .collect(Collectors.toMap(result -> result.criterion().id(), Function.identity()));

        log.debug("Evaluated {} criteria for specification '{}'", criteriaResultMap.size(), specification.id());

        List<CriteriaGroupResult> results = specification.criteriaGroups().parallelStream().map(criteriaGroup -> {
            List<EvaluationResult> criteriaGroupResults = criteriaGroup.criteria().parallelStream()
                    .map(criterion -> criteriaResultMap.getOrDefault(criterion.id(), this.evaluator.evaluateCriterion(doc, criterion)))
                    .toList();

            boolean match = (Junction.AND == criteriaGroup.junction())
                    ? criteriaGroupResults.stream().allMatch(EvaluationResult::matched)
                    : criteriaGroupResults.stream().anyMatch(EvaluationResult::matched);
            return new CriteriaGroupResult(criteriaGroup.id(), criteriaGroup.junction(), criteriaGroupResults, match);
        }).toList();

        // Create summary
        EvaluationSummary summary = EvaluationSummary.from(criteriaResultMap.values());

        log.info("Completed evaluation of specification '{}' - Total: {}, Matched: {}, Not Matched: {}, Undetermined: {}, Fully Determined: {}",
                   specification.id(), summary.total(), summary.matched(),
                   summary.notMatched(), summary.undetermined(), summary.fullyDetermined());

        return new EvaluationOutcome(specification.id(), new ArrayList<>(criteriaResultMap.values()), results, summary);
    }
}
