package uk.codery.jspec.result;

import lombok.NonNull;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
 * EvaluationOutcome outcome = evaluator.evaluate(document);
 *
 * System.out.println("Specification: " + outcome.specificationId());
 * System.out.println("Total criteria: " + outcome.summary().total());
 * System.out.println("Matched: " + outcome.summary().matched());
 * System.out.println("Not Matched: " + outcome.summary().notMatched());
 * System.out.println("Undetermined: " + outcome.summary().undetermined());
 * }</pre>
 *
 * <h3>Null-Safe Result Lookup</h3>
 * <pre>{@code
 * // Type-safe Optional-based lookup
 * outcome.find("my-criterion")
 *     .ifPresent(r -> System.out.println(r.state()));
 *
 * // Type-specific lookup with casting
 * outcome.findComposite("eligibility")
 *     .ifPresent(comp -> {
 *         System.out.println("Junction: " + comp.junction());
 *         System.out.println("Children: " + comp.childResults().size());
 *     });
 *
 * // Convenient state checks (null-safe!)
 * if (outcome.matched("eligibility")) {
 *     System.out.println("Eligible!");
 * }
 * }</pre>
 *
 * <h3>Business Logic Helpers</h3>
 * <pre>{@code
 * // High-level outcome checks
 * if (outcome.allMatched()) {
 *     System.out.println("Perfect score!");
 * }
 *
 * if (outcome.hasMatches()) {
 *     System.out.println("At least one criterion matched");
 * }
 *
 * if (outcome.anyFailed()) {
 *     System.out.println("Some criteria failed or were undetermined");
 *     Map<String, String> failures = outcome.getFailureReasons();
 *     failures.forEach((id, reason) ->
 *         System.out.printf("❌ %s: %s%n", id, reason));
 * }
 * }</pre>
 *
 * <h3>Stream-Based Processing</h3>
 * <pre>{@code
 * // Direct stream access
 * List<String> matchedIds = outcome.stream()
 *     .filter(r -> r.state().matched())
 *     .map(EvaluationResult::id)
 *     .toList();
 *
 * // Count by state
 * long undeterminedCount = outcome.stream()
 *     .filter(r -> r.state().undetermined())
 *     .count();
 * }</pre>
 *
 * <h3>Iterating All Results</h3>
 * <pre>{@code
 * for (EvaluationResult result : outcome.results()) {
 *     System.out.printf("%s: %s%n", result.id(), result.state());
 *
 *     if (!result.state().matched()) {
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
 * <h3>Overall State Computation (Kleene Logic)</h3>
 * <pre>{@code
 * // AND-based: all must match for MATCHED state
 * EvaluationState overallState = outcome.overallState();
 * if (overallState.matched()) {
 *     System.out.println("All criteria matched!");
 * }
 *
 * // OR-based: any match yields MATCHED state
 * EvaluationState anyMatch = outcome.anyMatchState();
 * if (anyMatch.matched()) {
 *     System.out.println("At least one criterion matched!");
 * }
 * }</pre>
 *
 * <h3>Performance - Map-Based Lookup</h3>
 * <pre>{@code
 * // For frequent lookups, convert to Map (O(1) access)
 * Map<String, EvaluationResult> lookup = outcome.asMap();
 * EvaluationResult result = lookup.get("my-id");
 * Set<String> allIds = lookup.keySet();
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

    // ========== NULL-SAFE LOOKUPS ==========

    /**
     * Finds a result by ID, returning an Optional.
     *
     * <p>This is the recommended way to lookup results as it's null-safe
     * and encourages proper Optional handling.
     *
     * <pre>{@code
     * outcome.find("my-criterion")
     *     .ifPresent(r -> System.out.println(r.state()));
     *
     * boolean matched = outcome.find("my-criterion")
     *     .map(r -> r.state().matched())
     *     .orElse(false);
     * }</pre>
     *
     * @param id the criterion ID to find
     * @return Optional containing the result if found, empty otherwise
     * @since 0.4.0
     */
    public Optional<EvaluationResult> find(@NonNull String id) {
        return results.stream()
                .filter(r -> id.equals(r.id()))
                .findFirst();
    }

    /**
     * Finds a query result by ID.
     *
     * <p>Type-safe variant of {@link #find(String)} that automatically
     * filters and casts to {@link QueryResult}.
     *
     * <pre>{@code
     * outcome.findQuery("age-check")
     *     .ifPresent(query -> {
     *         System.out.println(query.criterion().query());
     *         System.out.println(query.missingPaths());
     *     });
     * }</pre>
     *
     * @param id the criterion ID to find
     * @return Optional containing the query result if found and is a QueryResult, empty otherwise
     * @since 0.4.0
     */
    public Optional<QueryResult> findQuery(@NonNull String id) {
        return find(id)
                .filter(r -> r instanceof QueryResult)
                .map(r -> (QueryResult) r);
    }

    /**
     * Finds a composite result by ID.
     *
     * <p>Type-safe variant of {@link #find(String)} that automatically
     * filters and casts to {@link CompositeResult}.
     *
     * <pre>{@code
     * outcome.findComposite("eligibility")
     *     .ifPresent(comp -> {
     *         System.out.println("Junction: " + comp.junction());
     *         System.out.println("Children: " + comp.childResults().size());
     *     });
     * }</pre>
     *
     * @param id the criterion ID to find
     * @return Optional containing the composite result if found and is a CompositeResult, empty otherwise
     * @since 0.4.0
     */
    public Optional<CompositeResult> findComposite(@NonNull String id) {
        return find(id)
                .filter(r -> r instanceof CompositeResult)
                .map(r -> (CompositeResult) r);
    }

    /**
     * Finds a reference result by ID.
     *
     * <p>Type-safe variant of {@link #find(String)} that automatically
     * filters and casts to {@link ReferenceResult}.
     *
     * <pre>{@code
     * outcome.findReference("my-ref")
     *     .ifPresent(ref -> {
     *         System.out.println("Points to: " + ref.reference().id());
     *         EvaluationResult original = ref.unwrap();
     *     });
     * }</pre>
     *
     * @param id the criterion ID to find
     * @return Optional containing the reference result if found and is a ReferenceResult, empty otherwise
     * @since 0.4.0
     */
    public Optional<ReferenceResult> findReference(@NonNull String id) {
        return find(id)
                .filter(r -> r instanceof ReferenceResult)
                .map(r -> (ReferenceResult) r);
    }

    // ========== CONVENIENCE STATE CHECKS ==========

    /**
     * Checks if the specified criterion matched.
     *
     * <p>Null-safe convenience method that returns false if the criterion
     * is not found or didn't match.
     *
     * <pre>{@code
     * if (outcome.matched("eligibility")) {
     *     System.out.println("User is eligible!");
     * }
     * }</pre>
     *
     * @param id the criterion ID to check
     * @return true if found and state is MATCHED, false otherwise
     * @since 0.4.0
     */
    public boolean matched(@NonNull String id) {
        return find(id)
                .map(r -> r.state().matched())
                .orElse(false);
    }

    /**
     * Checks if the specified criterion did not match.
     *
     * <p>Null-safe convenience method that returns false if the criterion
     * is not found or is in any state other than NOT_MATCHED.
     *
     * @param id the criterion ID to check
     * @return true if found and state is NOT_MATCHED, false otherwise
     * @since 0.4.0
     */
    public boolean notMatched(@NonNull String id) {
        return find(id)
                .map(r -> r.state().notMatched())
                .orElse(false);
    }

    /**
     * Checks if the specified criterion was undetermined.
     *
     * <p>Null-safe convenience method that returns true if the criterion
     * is not found (missing = undetermined) or is UNDETERMINED.
     *
     * @param id the criterion ID to check
     * @return true if not found or state is UNDETERMINED, false otherwise
     * @since 0.4.0
     */
    public boolean undetermined(@NonNull String id) {
        return find(id)
                .map(r -> r.state().undetermined())
                .orElse(true); // Missing criterion = undetermined
    }

    // ========== BUSINESS LOGIC HELPERS ==========

    /**
     * Checks if at least one criterion matched.
     *
     * <pre>{@code
     * if (outcome.hasMatches()) {
     *     System.out.println("At least one criterion matched");
     * }
     * }</pre>
     *
     * @return true if matched count > 0
     * @since 0.4.0
     */
    public boolean hasMatches() {
        return summary.matched() > 0;
    }

    /**
     * Checks if all criteria matched.
     *
     * <pre>{@code
     * if (outcome.allMatched()) {
     *     System.out.println("Perfect score!");
     * }
     * }</pre>
     *
     * @return true if matched count equals total count
     * @since 0.4.0
     */
    public boolean allMatched() {
        return summary.matched() == summary.total();
    }

    /**
     * Checks if no criteria matched.
     *
     * @return true if matched count is 0
     * @since 0.4.0
     */
    public boolean noneMatched() {
        return summary.matched() == 0;
    }

    /**
     * Checks if any criteria failed or were undetermined.
     *
     * <pre>{@code
     * if (outcome.anyFailed()) {
     *     outcome.getFailureReasons().forEach((id, reason) ->
     *         System.out.printf("❌ %s: %s%n", id, reason));
     * }
     * }</pre>
     *
     * @return true if not-matched count or undetermined count > 0
     * @since 0.4.0
     */
    public boolean anyFailed() {
        return summary.notMatched() > 0 || summary.undetermined() > 0;
    }

    /**
     * Checks if all criteria were determined (no UNDETERMINED states).
     *
     * <p>Convenience wrapper around {@link EvaluationSummary#fullyDetermined()}.
     *
     * @return true if undetermined count is 0
     * @since 0.4.0
     */
    public boolean isFullyDetermined() {
        return summary.fullyDetermined();
    }

    // ========== STREAM API ==========

    /**
     * Returns a stream of all evaluation results.
     *
     * <p>Enables functional-style processing of results:
     *
     * <pre>{@code
     * List<String> matchedIds = outcome.stream()
     *     .filter(r -> r.state().matched())
     *     .map(EvaluationResult::id)
     *     .toList();
     *
     * long undeterminedCount = outcome.stream()
     *     .filter(r -> r.state().undetermined())
     *     .count();
     * }</pre>
     *
     * @return stream of evaluation results
     * @since 0.4.0
     */
    public Stream<EvaluationResult> stream() {
        return results.stream();
    }

    // ========== PERFORMANCE ==========

    /**
     * Returns an immutable Map view of results by ID.
     *
     * <p>Useful for frequent lookups (O(1) access) or bulk operations:
     *
     * <pre>{@code
     * Map<String, EvaluationResult> lookup = outcome.asMap();
     * EvaluationResult result = lookup.get("my-id");
     * Set<String> allIds = lookup.keySet();
     * boolean exists = lookup.containsKey("my-id");
     * }</pre>
     *
     * <p><b>Note:</b> This creates a new map on each call. For repeated
     * access, store the returned map in a variable.
     *
     * @return immutable map of criterion ID to evaluation result
     * @since 0.4.0
     */
    public Map<String, EvaluationResult> asMap() {
        return results.stream()
                .collect(Collectors.toUnmodifiableMap(
                        EvaluationResult::id,
                        Function.identity()
                ));
    }

    // ========== SAFE ACCESSORS ==========

    /**
     * Returns the first query result, if any.
     *
     * <pre>{@code
     * outcome.firstQuery()
     *     .ifPresent(q -> System.out.println("First query: " + q.id()));
     * }</pre>
     *
     * @return Optional containing the first QueryResult, or empty if none exist
     * @since 0.4.0
     */
    public Optional<QueryResult> firstQuery() {
        return queryResults().stream().findFirst();
    }

    /**
     * Returns the first composite result, if any.
     *
     * <pre>{@code
     * outcome.firstComposite()
     *     .ifPresent(c -> System.out.println("First composite: " + c.id()));
     * }</pre>
     *
     * @return Optional containing the first CompositeResult, or empty if none exist
     * @since 0.4.0
     */
    public Optional<CompositeResult> firstComposite() {
        return compositeResults().stream().findFirst();
    }

    /**
     * Returns the first reference result, if any.
     *
     * @return Optional containing the first ReferenceResult, or empty if none exist
     * @since 0.4.0
     */
    public Optional<ReferenceResult> firstReference() {
        return referenceResults().stream().findFirst();
    }

    // ========== KLEENE LOGIC ==========

    /**
     * Computes the overall evaluation state using AND-based Kleene logic.
     *
     * <p>Combines all result states using {@link EvaluationState#and(EvaluationState)}.
     * This means all criteria must be MATCHED for the overall state to be MATCHED.
     *
     * <p>Uses Strong Kleene Logic (K3):
     * <ul>
     *   <li>All MATCHED → MATCHED</li>
     *   <li>Any NOT_MATCHED → NOT_MATCHED (short-circuit)</li>
     *   <li>No NOT_MATCHED but some UNDETERMINED → UNDETERMINED</li>
     * </ul>
     *
     * <pre>{@code
     * EvaluationState overall = outcome.overallState();
     * if (overall.matched()) {
     *     System.out.println("All criteria matched!");
     * } else if (overall.undetermined()) {
     *     System.out.println("Some criteria could not be evaluated");
     * } else {
     *     System.out.println("Some criteria did not match");
     * }
     * }</pre>
     *
     * @return overall state using AND logic
     * @since 0.4.0
     */
    public EvaluationState overallState() {
        return results.stream()
                .map(EvaluationResult::state)
                .reduce(EvaluationState.MATCHED, EvaluationState::and);
    }

    /**
     * Computes the overall evaluation state using OR-based Kleene logic.
     *
     * <p>Combines all result states using {@link EvaluationState#or(EvaluationState)}.
     * This means any criterion being MATCHED yields an overall MATCHED state.
     *
     * <p>Uses Strong Kleene Logic (K3):
     * <ul>
     *   <li>Any MATCHED → MATCHED (short-circuit)</li>
     *   <li>All NOT_MATCHED → NOT_MATCHED</li>
     *   <li>No MATCHED but some UNDETERMINED → UNDETERMINED</li>
     * </ul>
     *
     * <pre>{@code
     * EvaluationState anyMatch = outcome.anyMatchState();
     * if (anyMatch.matched()) {
     *     System.out.println("At least one criterion matched!");
     * }
     * }</pre>
     *
     * @return overall state using OR logic
     * @since 0.4.0
     */
    public EvaluationState anyMatchState() {
        return results.stream()
                .map(EvaluationResult::state)
                .reduce(EvaluationState.NOT_MATCHED, EvaluationState::or);
    }

    // ========== DIAGNOSTICS ==========

    /**
     * Returns a map of criterion IDs to failure reasons.
     *
     * <p>Only includes results that didn't match (NOT_MATCHED or UNDETERMINED)
     * and have a non-null reason.
     *
     * <pre>{@code
     * Map<String, String> failures = outcome.getFailureReasons();
     * if (!failures.isEmpty()) {
     *     System.out.println("Failures:");
     *     failures.forEach((id, reason) ->
     *         System.out.printf("  ❌ %s: %s%n", id, reason));
     * }
     * }</pre>
     *
     * @return immutable map of criterion ID to failure reason
     * @since 0.4.0
     */
    public Map<String, String> getFailureReasons() {
        return results.stream()
                .filter(r -> !r.state().matched())
                .filter(r -> r.reason() != null)
                .collect(Collectors.toUnmodifiableMap(
                        EvaluationResult::id,
                        EvaluationResult::reason,
                        (r1, r2) -> r1 // Keep first if duplicate IDs (shouldn't happen)
                ));
    }

    /**
     * Returns the set of criterion IDs that were undetermined.
     *
     * <pre>{@code
     * Set<String> undetermined = outcome.getUndeterminedIds();
     * if (!undetermined.isEmpty()) {
     *     System.out.println("Could not evaluate: " + undetermined);
     * }
     * }</pre>
     *
     * @return immutable set of undetermined criterion IDs
     * @since 0.4.0
     */
    public Set<String> getUndeterminedIds() {
        return undeterminedResults().stream()
                .map(EvaluationResult::id)
                .collect(Collectors.toUnmodifiableSet());
    }

    // ========== LEGACY TYPE FILTERING METHODS ==========

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
                .filter(r -> r.state().matched())
                .toList();
    }

    /**
     * Returns all results that did not match.
     *
     * @return list of not-matched results
     */
    public List<EvaluationResult> notMatchedResults() {
        return results.stream()
                .filter(r -> r.state().notMatched())
                .toList();
    }

    /**
     * Returns all results that were undetermined.
     *
     * @return list of undetermined results
     */
    public List<EvaluationResult> undeterminedResults() {
        return results.stream()
                .filter(r -> r.state().undetermined())
                .toList();
    }
}
