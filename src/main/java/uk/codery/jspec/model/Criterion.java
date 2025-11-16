package uk.codery.jspec.model;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import uk.codery.jspec.evaluator.EvaluationContext;
import uk.codery.jspec.result.EvaluationResult;

/**
 * A criterion defines a condition that can be evaluated against a document.
 *
 * <p>This sealed interface supports three types of criteria:
 * <ul>
 *   <li>{@link QueryCriterion} - Evaluates MongoDB-style query operators against document fields</li>
 *   <li>{@link CompositeCriterion} - Combines multiple criteria with AND/OR logic (enables nesting)</li>
 *   <li>{@link CriterionReference} - References another criterion by ID for result reuse</li>
 * </ul>
 *
 * <h2>Design Benefits</h2>
 * <ul>
 *   <li><b>Unified Model:</b> No artificial distinction between criteria and groups</li>
 *   <li><b>Composable:</b> Criteria can nest arbitrarily deep via CompositeCriterion</li>
 *   <li><b>Reusable:</b> Evaluate once, reference many times via CriterionReference</li>
 *   <li><b>Type-Safe:</b> Sealed interface enables exhaustive pattern matching</li>
 * </ul>
 *
 * <h2>Usage Examples</h2>
 *
 * <h3>Simple Query Criterion</h3>
 * <pre>{@code
 * Criterion ageCriterion = new QueryCriterion(
 *     "age-check",
 *     Map.of("age", Map.of("$gte", 18))
 * );
 * }</pre>
 *
 * <h3>Composite Criterion (AND/OR Logic)</h3>
 * <pre>{@code
 * Criterion eligibility = new CompositeCriterion(
 *     "eligibility",
 *     Junction.AND,
 *     List.of(
 *         new QueryCriterion("age-check", Map.of("age", Map.of("$gte", 18))),
 *         new QueryCriterion("status-check", Map.of("status", Map.of("$eq", "active")))
 *     )
 * );
 * }</pre>
 *
 * <h3>Reference-Based Reuse</h3>
 * <pre>{@code
 * // Define once
 * Criterion ageCheck = new QueryCriterion("age-check", Map.of("age", Map.of("$gte", 18)));
 *
 * // Reference multiple times (result reused from cache)
 * Criterion group1 = new CompositeCriterion("group1", Junction.AND, List.of(
 *     new CriterionReference("age-check"),  // References cached result
 *     new QueryCriterion("other", Map.of(...))
 * ));
 *
 * Criterion group2 = new CompositeCriterion("group2", Junction.OR, List.of(
 *     new CriterionReference("age-check"),  // Reuses same cached result
 *     new QueryCriterion("another", Map.of(...))
 * ));
 * }</pre>
 *
 * <h3>Arbitrary Nesting</h3>
 * <pre>{@code
 * Criterion nested = new CompositeCriterion(
 *     "complex",
 *     Junction.AND,
 *     List.of(
 *         new QueryCriterion("base", Map.of(...)),
 *         new CompositeCriterion(  // Nest composite inside composite!
 *             "inner",
 *             Junction.OR,
 *             List.of(
 *                 new QueryCriterion("opt1", Map.of(...)),
 *                 new QueryCriterion("opt2", Map.of(...))
 *             )
 *         )
 *     )
 * );
 * }</pre>
 *
 * @see QueryCriterion
 * @see CompositeCriterion
 * @see CriterionReference
 * @see Junction
 * @since 0.2.0
 */
@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.PROPERTY,
        property = "type"
)
@JsonSubTypes({
        @JsonSubTypes.Type(value = QueryCriterion.class, name = "query"),
        @JsonSubTypes.Type(value = CompositeCriterion.class, name = "composite"),
        @JsonSubTypes.Type(value = CriterionReference.class, name = "reference")
})
public sealed interface Criterion
        permits QueryCriterion, CompositeCriterion, CriterionReference {

    /**
     * Returns the unique identifier for this criterion.
     *
     * @return the criterion ID (never null)
     */
    String id();

    /**
     * Evaluates this criterion against a document.
     *
     * <p>The evaluation context provides:
     * <ul>
     *   <li>Access to the {@link uk.codery.jspec.evaluator.CriterionEvaluator}</li>
     *   <li>Result caching for referenced criteria (evaluate once, reuse many times)</li>
     * </ul>
     *
     * <p><b>Implementation Notes:</b>
     * <ul>
     *   <li>{@link QueryCriterion} - Delegates to evaluator for MongoDB-style query matching</li>
     *   <li>{@link CompositeCriterion} - Recursively evaluates children, applies junction logic</li>
     *   <li>{@link CriterionReference} - Looks up cached result by ID</li>
     * </ul>
     *
     * @param document the document to evaluate against
     * @param context the evaluation context (provides evaluator and result cache)
     * @return the evaluation result (never null)
     */
    EvaluationResult evaluate(Object document, EvaluationContext context);
}
