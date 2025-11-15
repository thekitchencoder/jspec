package uk.codery.jspec.result;

import java.util.List;

/**
 * The outcome of evaluating a specification against a document.
 *
 * <p>Contains the results of evaluating individual criteria, criterion sets,
 * and a summary showing evaluation completeness.
 */
public record EvaluationOutcome(
        String specificationId,
        List<EvaluationResult> evaluationResults,
        List<CriteriaGroupResult> criteriaGroupResults,
        EvaluationSummary summary) {
}
