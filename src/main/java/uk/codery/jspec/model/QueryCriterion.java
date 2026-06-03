package uk.codery.jspec.model;

import uk.codery.jspec.builder.CriterionBuilder;
import uk.codery.jspec.evaluator.ContextPathResolver;
import uk.codery.jspec.evaluator.EvaluationContext;
import uk.codery.jspec.evaluator.ResolutionResult;
import uk.codery.jspec.result.EvaluationResult;
import uk.codery.jspec.result.QueryResult;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A leaf criterion that evaluates MongoDB-style query operators against a document.
 *
 * <p>This is the fundamental evaluation unit that:
 * <ul>
 *   <li>Matches document fields against query conditions</li>
 *   <li>Supports 13 MongoDB-style operators ($eq, $gt, $in, $regex, etc.)</li>
 *   <li>Uses dot notation for nested field access (e.g., "address.city")</li>
 *   <li>Returns tri-state results (MATCHED / NOT_MATCHED / UNDETERMINED)</li>
 * </ul>
 *
 * <h2>Creating Query Criteria</h2>
 *
 * <h3>Using Constructor (Map-based)</h3>
 * <pre>{@code
 * // Simple equality check
 * QueryCriterion criterion = new QueryCriterion("status-check",
 *     Map.of("status", Map.of("$eq", "active")));
 *
 * // Range check
 * QueryCriterion criterion = new QueryCriterion("age-check",
 *     Map.of("age", Map.of("$gte", 18, "$lte", 65)));
 *
 * // Nested field
 * QueryCriterion criterion = new QueryCriterion("city-check",
 *     Map.of("address.city", Map.of("$eq", "London")));
 * }</pre>
 *
 * <h3>Using Builder (Fluent API)</h3>
 * <pre>{@code
 * // Simple equality check
 * QueryCriterion criterion = QueryCriterion.builder()
 *     .id("status-check")
 *     .field("status").eq("active")
 *     .build();
 *
 * // Range check
 * QueryCriterion criterion = QueryCriterion.builder()
 *     .id("age-check")
 *     .field("age").gte(18).and().lte(65)
 *     .build();
 *
 * // Multiple fields
 * QueryCriterion criterion = QueryCriterion.builder()
 *     .id("user-validation")
 *     .field("age").gte(18)
 *     .field("status").eq("active")
 *     .field("email").exists(true)
 *     .build();
 * }</pre>
 *
 * <h2>Supported Operators</h2>
 *
 * <h3>Comparison (6)</h3>
 * <ul>
 *   <li><b>$eq</b> - Equality</li>
 *   <li><b>$ne</b> - Not equal</li>
 *   <li><b>$gt</b> - Greater than</li>
 *   <li><b>$gte</b> - Greater than or equal</li>
 *   <li><b>$lt</b> - Less than</li>
 *   <li><b>$lte</b> - Less than or equal</li>
 * </ul>
 *
 * <h3>Collection (4)</h3>
 * <ul>
 *   <li><b>$in</b> - Value in array</li>
 *   <li><b>$nin</b> - Value not in array</li>
 *   <li><b>$all</b> - Array contains all values</li>
 *   <li><b>$size</b> - Array size</li>
 * </ul>
 *
 * <h3>Advanced (3)</h3>
 * <ul>
 *   <li><b>$exists</b> - Field existence</li>
 *   <li><b>$type</b> - Type check</li>
 *   <li><b>$regex</b> - Pattern match</li>
 *   <li><b>$elemMatch</b> - Array element match</li>
 * </ul>
 *
 * @param id the unique identifier for this criterion
 * @param query the query conditions as a map (MongoDB-style operators)
 * @see CriterionBuilder
 * @see uk.codery.jspec.evaluator.CriterionEvaluator
 * @see EvaluationResult
 * @since 0.2.0
 */
public record QueryCriterion(String id, Map<String, Object> query) implements Criterion {

    /**
     * Creates a query criterion with an empty query.
     *
     * <p>Useful for creating placeholder criteria that will be populated later.
     *
     * @param id the criterion identifier
     */
    public QueryCriterion(String id) {
        this(id, Collections.emptyMap());
    }

    /**
     * Ensures the query map is immutable.
     *
     * <p>Uses {@code LinkedHashMap} + {@code Collections.unmodifiableMap} (rather than
     * {@code Map.copyOf}) so that null leaf values pass through. This matters because
     * resolved context-path references may legitimately produce {@code {$eq: null}},
     * and {@code Map.copyOf} would NPE on such entries. Consistent with the pattern
     * used by {@link uk.codery.jspec.model.SpecificationNormaliser} and
     * {@link uk.codery.jspec.evaluator.ContextPathResolver}.
     */
    public QueryCriterion {
        query = query != null
                ? Collections.unmodifiableMap(new LinkedHashMap<>(query))
                : Collections.emptyMap();
    }

    /**
     * Evaluates this query criterion against a document.
     *
     * <p>Resolves any {@link ContextPathReference} operands against the context
     * document first; if any references cannot be resolved, the criterion
     * short-circuits to {@code UNDETERMINED} with the missing paths listed.
     * Otherwise a resolved copy of this criterion is passed to the
     * {@link uk.codery.jspec.evaluator.CriterionEvaluator} for matching.
     *
     * @param document the document to evaluate against
     * @param context the evaluation context (provides evaluator, context doc, and cache)
     * @return the evaluation result
     */
    @Override
    public EvaluationResult evaluate(Object document, EvaluationContext context) {
        ResolutionResult resolution =
                ContextPathResolver.resolve(query, context.contextDoc());

        if (resolution.hasMissingPaths()) {
            return QueryResult.undetermined(
                    this,
                    "Unresolved context paths: " + String.join(", ", resolution.missingPaths()),
                    resolution.missingPaths());
        }

        // Fast path: a plain query (no $contextPath operands) resolves to the same
        // map instance, so reuse this criterion rather than reallocating a copy.
        QueryCriterion resolved = resolution.resolved() == query
                ? this
                : new QueryCriterion(id, resolution.resolved());
        return context.evaluator().evaluateQuery(document, resolved);
    }

    /**
     * Creates a new fluent builder for constructing query criteria.
     *
     * <p>The builder provides a more readable alternative to manually constructing
     * Map-based queries.
     *
     * <h3>Example:</h3>
     * <pre>{@code
     * QueryCriterion criterion = QueryCriterion.builder()
     *     .id("age-check")
     *     .field("age").gte(18)
     *     .field("status").eq("active")
     *     .build();
     * }</pre>
     *
     * @return a new CriterionBuilder instance
     * @see CriterionBuilder
     */
    public static CriterionBuilder builder() {
        return new CriterionBuilder();
    }
}
