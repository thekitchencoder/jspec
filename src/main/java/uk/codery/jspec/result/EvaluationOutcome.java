package uk.codery.jspec.result;

import lombok.NonNull;

import java.util.Collections;
import java.util.List;

/**
 * The outcome of evaluating a specification against a document.
 *
 * <p>Contains:
 * <ul>
 *   <li>The specification ID</li>
 *   <li>All evaluation results (queries, composites, references) in a unified list</li>
 *   <li>A summary with statistics (matched, not matched, undetermined counts)</li>
 * </ul>
 *
 * <h2>Unified Result Model</h2>
 *
 * <p>Unlike the previous API with separate lists for criteria and groups,
 * this version uses a single unified list of {@link EvaluationResult}.
 * Each result can be:
 * <ul>
 *   <li>{@link QueryResult} - Result of evaluating a query criterion</li>
 *   <li>{@link CompositeResult} - Result of evaluating a composite criterion (AND/OR)</li>
 *   <li>{@link ReferenceResult} - Result of evaluating a criterion reference</li>
 * </ul>
 *
 * <h2>Usage Examples</h2>
 *
 * <h3>Basic Outcome Inspection</h3>
 * <pre>{@code
 * EvaluationOutcome outcome = evaluator.evaluate(document, specification);
 *
 * System.out.println("Specification: " + outcome.specificationId());
 * System.out.println("Total criteria: " + outcome.summary().total());
 * System.out.println("Matched: " + outcome.summary().matched());
 * System.out.println("Not Matched: " + outcome.summary().notMatched());
 * System.out.println("Undetermined: " + outcome.summary().undetermined());
 * }</pre>
 *
 * <h3>Iterating All Results</h3>
 * <pre>{@code
 * for (EvaluationResult result : outcome.results()) {
 *     System.out.printf("%s: %s%n", result.id(), result.state());
 *
 *     if (!result.matched()) {
 *         System.out.println("  Reason: " + result.reason());
 *     }
 * }
 * }</pre>
 *
 * <h3>Filtering by Result Type</h3>
 * <pre>{@code
 * // Get only query results
 * List<QueryResult> queries = outcome.queryResults();
 *
 * // Get only composite results
 * List<CompositeResult> composites = outcome.compositeResults();
 *
 * // Get only reference results
 * List<ReferenceResult> references = outcome.referenceResults();
 * }</pre>
 *
 * <h3>Pattern Matching</h3>
 * <pre>{@code
 * for (EvaluationResult result : outcome.results()) {
 *     switch (result) {
 *         case QueryResult query ->
 *             System.out.println("Query: " + query.criterion().query());
 *
 *         case CompositeResult composite ->
 *             System.out.println("Composite: " + composite.junction() +
 *                              " with " + composite.childResults().size() + " children");
 *
 *         case ReferenceResult ref ->
 *             System.out.println("Reference: " + ref.reference().id());
 *     }
 * }
 * }</pre>
 *
 * <h3>Checking Overall Success</h3>
 * <pre>{@code
 * if (outcome.summary().fullyDetermined()) {
 *     System.out.println("All criteria were determined (no UNDETERMINED states)");
 * }
 *
 * if (outcome.summary().matched() > 0) {
 *     System.out.println("At least one criterion matched");
 * }
 *
 * long failedCount = outcome.summary().notMatched() + outcome.summary().undetermined();
 * if (failedCount > 0) {
 *     System.out.println(failedCount + " criteria failed or were undetermined");
 * }
 * }</pre>
 *
 * @param specificationId the ID of the specification that was evaluated
 * @param results all evaluation results (queries, composites, references) in a single list
 * @param summary statistical summary of the evaluation
 * @see EvaluationResult
 * @see QueryResult
 * @see CompositeResult
 * @see ReferenceResult
 * @see EvaluationSummary
 * @since 0.2.0
 */
public record EvaluationOutcome(
        String specificationId,
        List<EvaluationResult> results,
        EvaluationSummary summary) {

    /**
     * Ensures the results list is immutable.
     */
    public EvaluationOutcome {
        results = results != null ? List.copyOf(results) : Collections.emptyList();
    }

    /**
     * Returns only the query results.
     *
     * <p>Convenience method to filter results by type.
     *
     * @return list of query results
     */
    public List<QueryResult> queryResults() {
        return results.stream()
                .filter(r -> r instanceof QueryResult)
                .map(r -> (QueryResult) r)
                .toList();
    }

    /**
     * Returns only the composite results.
     *
     * <p>Convenience method to filter results by type.
     *
     * @return list of composite results
     */
    public List<CompositeResult> compositeResults() {
        return results.stream()
                .filter(r -> r instanceof CompositeResult)
                .map(r -> (CompositeResult) r)
                .toList();
    }

    /**
     * Returns only the reference results.
     *
     * <p>Convenience method to filter results by type.
     *
     * @return list of reference results
     */
    public List<ReferenceResult> referenceResults() {
        return results.stream()
                .filter(r -> r instanceof ReferenceResult)
                .map(r -> (ReferenceResult) r)
                .toList();
    }

    /**
     * Returns all results that matched.
     *
     * @return list of matched results
     */
    public List<EvaluationResult> matchedResults() {
        return results.stream()
                .filter(EvaluationResult::matched)
                .toList();
    }

    /**
     * Returns all results that did not match.
     *
     * @return list of not-matched results
     */
    public List<EvaluationResult> notMatchedResults() {
        return results.stream()
                .filter(r -> r.state() == EvaluationState.NOT_MATCHED)
                .toList();
    }

    /**
     * Returns all results that were undetermined.
     *
     * @return list of undetermined results
     */
    public List<EvaluationResult> undeterminedResults() {
        return results.stream()
                .filter(r -> r.state() == EvaluationState.UNDETERMINED)
                .toList();
    }

    public EvaluationResult get(@NonNull String id) {
        return results.stream().filter(r -> id.equals(r.id())).findFirst().orElse(null);
    }
}
