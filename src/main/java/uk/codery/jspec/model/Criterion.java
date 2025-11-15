package uk.codery.jspec.model;

import uk.codery.jspec.builder.CriterionBuilder;

import java.util.Collections;
import java.util.Map;

/**
 * Represents a single evaluation criterion with an ID and query conditions.
 *
 * <p>A criterion defines a set of conditions that a document must satisfy.
 * Conditions are expressed as MongoDB-style queries using operators like
 * {@code $eq}, {@code $gt}, {@code $in}, {@code $regex}, etc.
 *
 * <h2>Creating Criteria</h2>
 *
 * <h3>Using Constructor (Map-based)</h3>
 * <pre>{@code
 * // Simple equality check
 * Criterion criterion = new Criterion("status-check",
 *     Map.of("status", Map.of("$eq", "active")));
 *
 * // Range check
 * Criterion criterion = new Criterion("age-check",
 *     Map.of("age", Map.of("$gte", 18, "$lte", 65)));
 *
 * // Nested field
 * Criterion criterion = new Criterion("city-check",
 *     Map.of("address.city", Map.of("$eq", "London")));
 * }</pre>
 *
 * <h3>Using Builder (Fluent API)</h3>
 * <pre>{@code
 * // Simple equality check
 * Criterion criterion = Criterion.builder()
 *     .id("status-check")
 *     .field("status").eq("active")
 *     .build();
 *
 * // Range check
 * Criterion criterion = Criterion.builder()
 *     .id("age-check")
 *     .field("age").gte(18).and().lte(65)
 *     .build();
 *
 * // Multiple fields
 * Criterion criterion = Criterion.builder()
 *     .id("user-validation")
 *     .field("age").gte(18)
 *     .field("status").eq("active")
 *     .field("email").exists(true)
 *     .build();
 * }</pre>
 *
 * <h2>Evaluation</h2>
 * <pre>{@code
 * CriterionEvaluator evaluator = new CriterionEvaluator();
 * Map<String, Object> document = Map.of("age", 25, "status", "active");
 *
 * EvaluationResult result = evaluator.evaluateCriterion(document, criterion);
 *
 * if (result.state() == EvaluationState.MATCHED) {
 *     System.out.println("Document matches criterion");
 * }
 * }</pre>
 *
 * @param id the unique identifier for this criterion
 * @param query the query conditions as a map (MongoDB-style operators)
 * @see CriterionBuilder
 * @see uk.codery.jspec.evaluator.CriterionEvaluator
 * @see uk.codery.jspec.result.EvaluationResult
 * @since 0.1.0
 */
public record Criterion(String id, Map<String, Object> query) {

    /**
     * Creates a criterion with an empty query.
     *
     * <p>Useful for creating placeholder criteria that will be populated later.
     *
     * @param id the criterion identifier
     */
    public Criterion(String id) {
        this(id, Collections.emptyMap());
    }

    /**
     * Creates a new fluent builder for constructing criteria.
     *
     * <p>The builder provides a more readable alternative to manually constructing
     * Map-based queries.
     *
     * <h3>Example:</h3>
     * <pre>{@code
     * Criterion criterion = Criterion.builder()
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
