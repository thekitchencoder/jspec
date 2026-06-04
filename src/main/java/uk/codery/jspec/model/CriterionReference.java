package uk.codery.jspec.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import uk.codery.jspec.evaluator.EvaluationContext;
import uk.codery.jspec.result.EvaluationResult;
import uk.codery.jspec.result.EvaluationState;
import uk.codery.jspec.result.ReferenceResult;

/**
 * A reference to another criterion by ID, enabling result reuse.
 *
 * <p>This is the key to "evaluate once, reference many times" pattern:
 * <ul>
 *   <li><b>Define Once:</b> Create a criterion and add it to the specification</li>
 *   <li><b>Reference Many:</b> Reference it by ID in multiple composites</li>
 *   <li><b>Cached Results:</b> The criterion evaluates once, all references use the cached result</li>
 *   <li><b>Performance:</b> Avoid redundant evaluations of shared criteria</li>
 * </ul>
 *
 * <h2>Why Use References?</h2>
 *
 * <p><b>Without References (redundant evaluation):</b>
 * <pre>{@code
 * QueryCriterion age1 = new QueryCriterion("age-check-1", Map.of("age", Map.of("$gte", 18)));
 * QueryCriterion age2 = new QueryCriterion("age-check-2", Map.of("age", Map.of("$gte", 18)));
 *
 * CompositeCriterion group1 = new CompositeCriterion("g1", Junction.AND, List.of(age1, ...));
 * CompositeCriterion group2 = new CompositeCriterion("g2", Junction.OR, List.of(age2, ...));
 *
 * // Problem: Same query evaluated twice!
 * }</pre>
 *
 * <p><b>With References (evaluate once, reuse):</b>
 * <pre>{@code
 * // Define once
 * QueryCriterion age = new QueryCriterion("age-check", Map.of("age", Map.of("$gte", 18)));
 *
 * // Reference multiple times
 * CompositeCriterion group1 = new CompositeCriterion("g1", Junction.AND, List.of(
 *     new CriterionReference("age-check"),  // Uses cached result
 *     ...
 * ));
 *
 * CompositeCriterion group2 = new CompositeCriterion("g2", Junction.OR, List.of(
 *     new CriterionReference("age-check"),  // Uses same cached result!
 *     ...
 * ));
 *
 * // Benefit: Query evaluated once, result reused!
 * }</pre>
 *
 * <h2>Usage Examples</h2>
 *
 * <h3>Basic Reference</h3>
 * <pre>{@code
 * // Define shared criteria
 * QueryCriterion ageCheck = new QueryCriterion("age-check", Map.of(...));
 * QueryCriterion statusCheck = new QueryCriterion("status-check", Map.of(...));
 *
 * // Create composite using references
 * CompositeCriterion eligibility = new CompositeCriterion(
 *     "eligibility",
 *     Junction.AND,
 *     List.of(
 *         new CriterionReference("age-check"),     // Reference by ID
 *         new CriterionReference("status-check")   // Reference by ID
 *     )
 * );
 *
 * // Specification includes both definitions and references
 * Specification spec = new Specification("spec", List.of(
 *     ageCheck,        // Evaluated once
 *     statusCheck,     // Evaluated once
 *     eligibility      // Uses cached results
 * ));
 * }</pre>
 *
 * <h3>Multiple Groups Sharing Criteria</h3>
 * <pre>{@code
 * QueryCriterion age = new QueryCriterion("age-check", Map.of(...));
 * QueryCriterion email = new QueryCriterion("email-check", Map.of(...));
 * QueryCriterion status = new QueryCriterion("status-check", Map.of(...));
 *
 * CompositeCriterion basicGroup = new CompositeCriterion("basic", Junction.AND, List.of(
 *     new CriterionReference("age-check"),     // Cached
 *     new CriterionReference("status-check")   // Cached
 * ));
 *
 * CompositeCriterion fullGroup = new CompositeCriterion("full", Junction.AND, List.of(
 *     new CriterionReference("age-check"),     // Same cached result!
 *     new CriterionReference("email-check"),   // Cached
 *     new CriterionReference("status-check")   // Same cached result!
 * ));
 *
 * // All three base criteria evaluated once, results reused in both groups
 * }</pre>
 *
 * <h3>YAML/JSON Serialization</h3>
 * <p>In YAML/JSON, use the "ref" property to create a reference:
 * <pre>{@code
 * # YAML example
 * - ref: age-check
 *
 * # JSON example
 * {"ref": "age-check"}
 * }</pre>
 *
 * <h2>Error Handling</h2>
 *
 * <p>If the referenced criterion is not found in the cache (not evaluated yet or doesn't exist):
 * <ul>
 *   <li>Returns {@link EvaluationState#UNDETERMINED}</li>
 *   <li>Provides clear error message: "Referenced criterion 'X' not found or not yet evaluated"</li>
 *   <li>Never throws exceptions (graceful degradation)</li>
 * </ul>
 *
 * @param ref the unique identifier of the referenced criteria
 * @see CompositeCriterion
 * @see QueryCriterion
 * @see EvaluationContext
 * @see ReferenceResult
 * @since 0.2.0
 */
public record CriterionReference(String ref) implements Criterion {

    @JsonIgnore
    @Override
    public String id() {
        return ref;
    }

    /**
     * Cache-only fallback for resolving this reference.
     *
     * <p><b>This method is the no-index fallback only.</b> The primary resolution path is
     * {@link EvaluationContext#getOrEvaluate(Criterion, Object)}, which special-cases
     * references and resolves them centrally (including on-demand evaluation of the
     * target and cycle detection). Crucially, that special-casing means
     * {@code getOrEvaluate} <em>never</em> calls {@code reference.evaluate(...)} — so
     * this method cannot recurse into itself, and a reference wrapper is never cached
     * under its own id (which equals the target id).
     *
     * <p>This body is therefore reached only when a {@code CriterionReference} is
     * evaluated directly (not via {@code getOrEvaluate}). It preserves the historical
     * cache-only semantics:
     * <ol>
     *   <li>Look up the referenced criterion's result in the context cache</li>
     *   <li>If found, wrap it in a {@link ReferenceResult}</li>
     *   <li>If not found, return UNDETERMINED via {@link ReferenceResult#missing}</li>
     * </ol>
     *
     * @param document the document being evaluated (unused by references)
     * @param context the evaluation context containing the result cache
     * @return the referenced result (if cached) or UNDETERMINED (if not)
     */
    @Override
    public EvaluationResult evaluate(Object document, EvaluationContext context) {
        // Cache-only fallback. The on-demand / cycle-aware path lives in
        // EvaluationContext.getOrEvaluate, which does NOT call this method for
        // references — so there is no recursion here.
        EvaluationResult referencedResult = context.getCached(ref);

        if (referencedResult == null) {
            // Referenced criterion not found or not yet evaluated
            return ReferenceResult.missing(this);
        }

        // Return reference result wrapping the cached result
        return new ReferenceResult(this, referencedResult);
    }
}
