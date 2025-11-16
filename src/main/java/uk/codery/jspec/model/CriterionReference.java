package uk.codery.jspec.model;

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
 * <h3>Convenience Constructor</h3>
 * <pre>{@code
 * // Reference uses same ID as target
 * new CriterionReference("age-check")  // id = "age-check", refId = "age-check"
 *
 * // Or use different ID for the reference
 * new CriterionReference("my-ref-id", "age-check")  // id = "my-ref-id", refId = "age-check"
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
 * @param id the unique identifier of the referenced criteria
 * @see CompositeCriterion
 * @see QueryCriterion
 * @see EvaluationContext
 * @see ReferenceResult
 * @since 0.2.0
 */
public record CriterionReference(String id) implements Criterion {

    /**
     * Evaluates this reference by looking up the cached result.
     *
     * <p>Process:
     * <ol>
     *   <li>Look up referenced criterion's result in the context cache</li>
     *   <li>If found, return ReferenceResult wrapping the cached result</li>
     *   <li>If not found, return UNDETERMINED with error message</li>
     * </ol>
     *
     * <p><b>Important:</b> Referenced criteria must be evaluated BEFORE references.
     * The {@link uk.codery.jspec.evaluator.SpecificationEvaluator} ensures this by:
     * <ul>
     *   <li>Evaluating all non-reference criteria first</li>
     *   <li>Then evaluating composites that may contain references</li>
     * </ul>
     *
     * @param document the document being evaluated (unused by references)
     * @param context the evaluation context containing the result cache
     * @return the referenced result (if found) or UNDETERMINED (if not found)
     */
    @Override
    public EvaluationResult evaluate(Object document, EvaluationContext context) {
        // Look up in cache - if not found, this is an error
        EvaluationResult referencedResult = context.getCached(id);

        if (referencedResult == null) {
            // Referenced criterion not found or not yet evaluated
            return ReferenceResult.missing(this);
        }

        // Return reference result wrapping the cached result
        return new ReferenceResult(this, referencedResult);
    }
}
