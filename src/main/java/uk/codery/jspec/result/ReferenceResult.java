package uk.codery.jspec.result;

import uk.codery.jspec.model.CriterionReference;
import uk.codery.jspec.model.QueryCriterion;

import java.util.Collections;

/**
 * Result of evaluating a {@link CriterionReference}.
 *
 * <p>A reference result is a wrapper around the actual result of the referenced criterion.
 * It provides:
 * <ul>
 *   <li>The reference criterion that was evaluated</li>
 *   <li>The actual result from the referenced criterion (cached)</li>
 *   <li>Delegation of state, matched, and reason to the referenced result</li>
 * </ul>
 *
 * <h2>How It Works</h2>
 *
 * <pre>{@code
 * // 1. Define a criterion
 * QueryCriterion age = new QueryCriterion("age-check", Map.of("age", Map.of("$gte", 18)));
 *
 * // 2. Evaluate it
 * EvaluationResult ageResult = evaluator.evaluate(age, document);  // Cached
 *
 * // 3. Reference it
 * CriterionReference ref = new CriterionReference("age-check");
 * EvaluationResult refResult = ref.evaluate(document, context);
 *
 * // 4. Reference result wraps the cached result
 * assert refResult instanceof ReferenceResult;
 * assert ((ReferenceResult) refResult).referencedResult() == ageResult;  // Same object
 * assert refResult.state() == ageResult.state();  // Delegates to cached result
 * }</pre>
 *
 * <h2>Benefits</h2>
 *
 * <ul>
 *   <li><b>Zero Overhead:</b> No re-evaluation, just wraps cached result</li>
 *   <li><b>Transparent:</b> Delegates all methods to referenced result</li>
 *   <li><b>Traceable:</b> Maintains reference ID for debugging</li>
 *   <li><b>Type-Safe:</b> Pattern matching distinguishes references from originals</li>
 * </ul>
 *
 * <h2>Usage Examples</h2>
 *
 * <h3>Pattern Matching</h3>
 * <pre>{@code
 * switch (result) {
 *     case ReferenceResult ref ->
 *         System.out.println("Reference " + ref.id() +
 *                          " -> " + ref.referencedResult().id());
 *
 *     case QueryResult query ->
 *         System.out.println("Direct query evaluation");
 *
 *     case CompositeResult composite ->
 *         System.out.println("Composite evaluation");
 * }
 * }</pre>
 *
 * <h3>Unwrapping References</h3>
 * <pre>{@code
 * // Get the original result (unwrap reference)
 * if (result instanceof ReferenceResult ref) {
 *     EvaluationResult original = ref.referencedResult();
 *
 *     // Can recursively unwrap nested references
 *     while (original instanceof ReferenceResult nestedRef) {
 *         original = nestedRef.referencedResult();
 *     }
 *
 *     // Now original is QueryResult or CompositeResult
 * }
 * }</pre>
 *
 * <h3>Tracing Reference Chain</h3>
 * <pre>{@code
 * void traceResult(EvaluationResult result) {
 *     if (result instanceof ReferenceResult ref) {
 *         System.out.println("Reference: " + ref.reference().id() +
 *                          " -> " + ref.reference().id());
 *         traceResult(ref.referencedResult());  // Follow the chain
 *     } else {
 *         System.out.println("Original: " + result.id() + " = " + result.state());
 *     }
 * }
 * }</pre>
 *
 * @param reference the criterion reference
 * @param referencedResult the actual result from the referenced criterion
 * @see CriterionReference
 * @see QueryResult
 * @see CompositeResult
 * @since 0.2.0
 */
public record ReferenceResult(
        CriterionReference reference,
        EvaluationResult referencedResult) implements EvaluationResult {

    /**
     * Creates a missing reference result (referenced criterion not found).
     */
    public static ReferenceResult missing(CriterionReference reference) {
        QueryResult missingResult = QueryResult.undetermined(
                new QueryCriterion(reference.id()),
                "Referenced criterion '" + reference.id() + "' not found or not yet evaluated",
                Collections.singletonList("criterion-reference")
        );
        return new ReferenceResult(reference, missingResult);
    }

    @Override
    public String id() {
        return reference.id();
    }

    @Override
    public EvaluationState state() {
        return referencedResult.state();
    }

    @Override
    public String reason() {
        return referencedResult.reason();
    }

    /**
     * Returns true if the evaluation was deterministic (not UNDETERMINED).
     *
     * @return true if state is MATCHED or NOT_MATCHED
     */
    public boolean isDetermined() {
        return state().determined();
    }

    /**
     * Unwraps nested references to get the original result.
     *
     * <p>If the referenced result is itself a ReferenceResult, recursively
     * unwraps until reaching a non-reference result (QueryResult or CompositeResult).
     *
     * <h3>Example:</h3>
     * <pre>{@code
     * // ref1 -> ref2 -> ref3 -> queryResult
     * ReferenceResult ref1 = ...;
     * EvaluationResult original = ref1.unwrap();  // Returns queryResult
     * }</pre>
     *
     * @return the original non-reference result
     */
    public EvaluationResult unwrap() {
        EvaluationResult current = referencedResult;
        while (current instanceof ReferenceResult nested) {
            current = nested.referencedResult();
        }
        return current;
    }

    // TODO external formatters (YAML,JSON,Text,etc) rather than YAML embedded in the toString method
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(reference.id()).append(" (reference):\n");
        sb.append("  references: ").append(reference.id()).append("\n");
        sb.append("  match: ").append(state().matched()).append("\n");
        sb.append("  state: ").append(state()).append("\n");

        if (reason() != null) {
            sb.append("  reason: \"").append(reason()).append("\"\n");
        }

        return sb.toString();
    }
}
