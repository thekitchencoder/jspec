package uk.codery.jspec.model;

import uk.codery.jspec.builder.CompositeCriterionBuilder;
import uk.codery.jspec.evaluator.EvaluationContext;
import uk.codery.jspec.result.CompositeResult;
import uk.codery.jspec.result.EvaluationResult;
import uk.codery.jspec.result.EvaluationState;

import java.util.Collections;
import java.util.List;

/**
 * A composite criterion that combines multiple criteria with boolean logic (AND/OR).
 *
 * <p>This enables:
 * <ul>
 *   <li><b>Arbitrary Nesting:</b> Composites can contain other composites</li>
 *   <li><b>Flexible Composition:</b> Mix inline criteria and references</li>
 *   <li><b>Tri-State Propagation:</b> UNDETERMINED states propagate through junctions</li>
 *   <li><b>Result Reuse:</b> Referenced criteria use cached results</li>
 * </ul>
 *
 * <h2>Junction Logic</h2>
 *
 * <h3>AND Junction</h3>
 * <ul>
 *   <li><b>MATCHED:</b> All children are MATCHED</li>
 *   <li><b>NOT_MATCHED:</b> Any child is NOT_MATCHED</li>
 *   <li><b>UNDETERMINED:</b> No NOT_MATCHED children, but at least one UNDETERMINED</li>
 * </ul>
 *
 * <h3>OR Junction</h3>
 * <ul>
 *   <li><b>MATCHED:</b> Any child is MATCHED</li>
 *   <li><b>NOT_MATCHED:</b> All children are NOT_MATCHED</li>
 *   <li><b>UNDETERMINED:</b> No MATCHED children, but at least one UNDETERMINED</li>
 * </ul>
 *
 * <h2>Usage Examples</h2>
 *
 * <h3>Simple AND Group</h3>
 * <pre>{@code
 * CompositeCriterion eligibility = new CompositeCriterion(
 *     "eligibility",
 *     Junction.AND,
 *     List.of(
 *         new QueryCriterion("age-check", Map.of("age", Map.of("$gte", 18))),
 *         new QueryCriterion("status-check", Map.of("status", Map.of("$eq", "active")))
 *     )
 * );
 * }</pre>
 *
 * <h3>Nested Composition</h3>
 * <pre>{@code
 * CompositeCriterion complex = new CompositeCriterion(
 *     "complex",
 *     Junction.AND,
 *     List.of(
 *         new QueryCriterion("base", Map.of("age", Map.of("$gte", 18))),
 *         new CompositeCriterion(  // Nest composite inside composite!
 *             "location",
 *             Junction.OR,
 *             List.of(
 *                 new QueryCriterion("us", Map.of("country", Map.of("$eq", "US"))),
 *                 new QueryCriterion("uk", Map.of("country", Map.of("$eq", "UK")))
 *             )
 *         )
 *     )
 * );
 * }</pre>
 *
 * <h3>Reference-Based Reuse</h3>
 * <pre>{@code
 * // Define base criteria
 * QueryCriterion age = new QueryCriterion("age-check", Map.of(...));
 * QueryCriterion status = new QueryCriterion("status-check", Map.of(...));
 *
 * // Reference them in multiple groups
 * CompositeCriterion group1 = new CompositeCriterion(
 *     "group1",
 *     Junction.AND,
 *     List.of(
 *         new CriterionReference("age-check"),    // Reference by ID
 *         new CriterionReference("status-check")  // Result reused from cache
 *     )
 * );
 *
 * CompositeCriterion group2 = new CompositeCriterion(
 *     "group2",
 *     Junction.OR,
 *     List.of(
 *         new CriterionReference("age-check"),  // Same reference, cached result
 *         new QueryCriterion("other", Map.of(...))
 *     )
 * );
 * }</pre>
 *
 * @param id the unique identifier for this composite criterion
 * @param junction the boolean logic to combine criteria (AND or OR)
 * @param criteria the child criteria (can include QueryCriterion, CompositeCriterion, CriterionReference)
 * @see Junction
 * @see QueryCriterion
 * @see CriterionReference
 * @see CompositeResult
 * @since 0.2.0
 */
public record CompositeCriterion(
        String id,
        Junction junction,
        List<Criterion> criteria) implements Criterion {

    /**
     * Creates a composite criterion with AND junction.
     *
     * <p>Convenience constructor that defaults to AND junction.
     *
     * @param id the criterion identifier
     * @param criteria the child criteria
     */
    public CompositeCriterion(String id, List<Criterion> criteria) {
        this(id, Junction.AND, criteria);
    }

    /**
     * Ensures the criteria list is immutable.
     */
    public CompositeCriterion {
        criteria = criteria != null ? List.copyOf(criteria) : Collections.emptyList();
    }

    /**
     * Evaluates this composite criterion against a document.
     *
     * <p>Process:
     * <ol>
     *   <li>Recursively evaluate all child criteria (uses cache for references)</li>
     *   <li>Apply junction logic to determine composite state</li>
     *   <li>Return CompositeResult with child results and computed state</li>
     * </ol>
     *
     * @param document the document to evaluate against
     * @param context the evaluation context (provides evaluator and cache)
     * @return the composite evaluation result
     */
    @Override
    public EvaluationResult evaluate(Object document, EvaluationContext context) {
        // Recursively evaluate all children (context handles caching)
        List<EvaluationResult> childResults = criteria.stream()
                .map(criterion -> context.getOrEvaluate(criterion, document))
                .toList();

        // Calculate composite state based on junction logic
        EvaluationState compositeState = calculateCompositeState(childResults);

        // Create composite result with child results
        return new CompositeResult(this, compositeState, childResults);
    }

    /**
     * Calculates the composite state based on junction logic and child states.
     *
     * <h3>AND Logic:</h3>
     * <ul>
     *   <li>MATCHED - All children MATCHED</li>
     *   <li>NOT_MATCHED - Any child NOT_MATCHED</li>
     *   <li>UNDETERMINED - No NOT_MATCHED, but at least one UNDETERMINED</li>
     * </ul>
     *
     * <h3>OR Logic:</h3>
     * <ul>
     *   <li>MATCHED - Any child MATCHED</li>
     *   <li>NOT_MATCHED - All children NOT_MATCHED</li>
     *   <li>UNDETERMINED - No MATCHED, but at least one UNDETERMINED</li>
     * </ul>
     */
    private EvaluationState calculateCompositeState(List<EvaluationResult> childResults) {
        if (childResults.isEmpty()) {
            return EvaluationState.UNDETERMINED;
        }

        long matchedCount = childResults.stream()
                .filter(r -> r.state() == EvaluationState.MATCHED)
                .count();

        long notMatchedCount = childResults.stream()
                .filter(r -> r.state() == EvaluationState.NOT_MATCHED)
                .count();

        return switch (junction) {
            case AND -> {
                // All must match for AND
                if (matchedCount == childResults.size()) {
                    yield EvaluationState.MATCHED;
                } else if (notMatchedCount > 0) {
                    // Any not-matched fails the AND
                    yield EvaluationState.NOT_MATCHED;
                } else {
                    // Must have undetermined children (no not-matched, but not all matched)
                    yield EvaluationState.UNDETERMINED;
                }
            }
            case OR -> {
                // Any match succeeds for OR
                if (matchedCount > 0) {
                    yield EvaluationState.MATCHED;
                } else if (notMatchedCount == childResults.size()) {
                    // All not-matched fails the OR
                    yield EvaluationState.NOT_MATCHED;
                } else {
                    // Must have undetermined children (no matches, but not all not-matched)
                    yield EvaluationState.UNDETERMINED;
                }
            }
        };
    }

    /**
     * Creates a new fluent builder for constructing composite criteria.
     *
     * <h3>Example:</h3>
     * <pre>{@code
     * CompositeCriterion composite = CompositeCriterion.builder()
     *     .id("user-checks")
     *     .and()
     *     .criteria(ageCheck, statusCheck)
     *     .build();
     * }</pre>
     *
     * @return a new CompositeCriterionBuilder instance
     * @see CompositeCriterionBuilder
     */
    public static CompositeCriterionBuilder builder() {
        return new CompositeCriterionBuilder();
    }
}
