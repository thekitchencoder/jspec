package uk.codery.jspec.model;

import uk.codery.jspec.builder.SpecificationBuilder;

import java.util.Collections;
import java.util.List;

/**
 * Represents a complete specification containing evaluation criteria.
 *
 * <p>A Specification is the top-level evaluation unit that contains a list of criteria.
 * Each criterion can be:
 * <ul>
 *   <li>{@link QueryCriterion} - MongoDB-style query evaluation</li>
 *   <li>{@link CompositeCriterion} - Multiple criteria combined with AND/OR logic</li>
 *   <li>{@link CriterionReference} - Reference to another criterion by ID</li>
 * </ul>
 *
 * <h2>Design Benefits</h2>
 *
 * <ul>
 *   <li><b>Unified Model:</b> Single list, no artificial separation of criteria and groups</li>
 *   <li><b>Flexible Composition:</b> Mix queries, composites, and references freely</li>
 *   <li><b>Reusable Results:</b> Define criteria once, reference many times</li>
 *   <li><b>Arbitrary Nesting:</b> Composites can contain other composites</li>
 * </ul>
 *
 * <h2>Creating Specifications</h2>
 *
 * <h3>Simple Queries</h3>
 * <pre>{@code
 * Specification spec = new Specification("user-validation", List.of(
 *     new QueryCriterion("age-check", Map.of("age", Map.of("$gte", 18))),
 *     new QueryCriterion("status-check", Map.of("status", Map.of("$eq", "active")))
 * ));
 * }</pre>
 *
 * <h3>With Composition</h3>
 * <pre>{@code
 * Specification spec = new Specification("eligibility", List.of(
 *     // Define base queries
 *     new QueryCriterion("age-check", Map.of("age", Map.of("$gte", 18))),
 *     new QueryCriterion("status-check", Map.of("status", Map.of("$eq", "active"))),
 *
 *     // Compose them with AND
 *     new CompositeCriterion("both-checks", Junction.AND, List.of(
 *         new CriterionReference("age-check"),     // Reference by ID
 *         new CriterionReference("status-check")   // Reuses cached result
 *     ))
 * ));
 * }</pre>
 *
 * <h3>With Nested Composition</h3>
 * <pre>{@code
 * Specification spec = new Specification("complex", List.of(
 *     // Base queries
 *     new QueryCriterion("age-check", Map.of("age", Map.of("$gte", 18))),
 *     new QueryCriterion("us-resident", Map.of("country", Map.of("$eq", "US"))),
 *     new QueryCriterion("uk-resident", Map.of("country", Map.of("$eq", "UK"))),
 *
 *     // First level composition
 *     new CompositeCriterion("location", Junction.OR, List.of(
 *         new CriterionReference("us-resident"),
 *         new CriterionReference("uk-resident")
 *     )),
 *
 *     // Nested composition
 *     new CompositeCriterion("eligibility", Junction.AND, List.of(
 *         new CriterionReference("age-check"),
 *         new CriterionReference("location")  // References a composite!
 *     ))
 * ));
 * }</pre>
 *
 * <h3>Using Builder</h3>
 * <pre>{@code
 * Specification spec = Specification.builder()
 *     .id("user-eligibility")
 *     .addCriterion(ageCheck)
 *     .addCriterion(statusCheck)
 *     .addCriterion(eligibilityComposite)
 *     .build();
 * }</pre>
 *
 * <h2>Evaluation</h2>
 *
 * <pre>{@code
 * SpecificationEvaluator evaluator = new SpecificationEvaluator();
 * Map<String, Object> document = Map.of("age", 25, "status", "active");
 *
 * EvaluationOutcome outcome = evaluator.evaluate(document, specification);
 *
 * System.out.println("Total criteria: " + outcome.summary().total());
 * System.out.println("Matched: " + outcome.summary().matched());
 *
 * // Inspect individual results
 * for (EvaluationResult result : outcome.results()) {
 *     System.out.println(result.id() + ": " + result.state());
 * }
 * }</pre>
 *
 * <h2>Evaluation Order</h2>
 *
 * <p>The {@link uk.codery.jspec.evaluator.SpecificationEvaluator} evaluates criteria in an order
 * that ensures references can find their cached results:
 * <ol>
 *   <li>All non-reference criteria are evaluated first (queries and composites)</li>
 *   <li>Results are cached by criterion ID</li>
 *   <li>References use the cached results (no re-evaluation)</li>
 * </ol>
 *
 * @param id the unique identifier for this specification
 * @param criteria the list of criteria (can include queries, composites, and references)
 * @see Criterion
 * @see QueryCriterion
 * @see CompositeCriterion
 * @see CriterionReference
 * @see SpecificationBuilder
 * @see uk.codery.jspec.evaluator.SpecificationEvaluator
 * @since 0.2.0
 */
public record Specification(String id, List<Criterion> criteria) {

    /**
     * Ensures the criteria list is immutable.
     */
    public Specification {
        criteria = criteria != null ? List.copyOf(criteria) : Collections.emptyList();
    }

    /**
     * Returns all criteria that are not references.
     *
     * <p>This is useful for the evaluator to determine which criteria to
     * evaluate first (before processing references).
     *
     * @return list of non-reference criteria (queries and composites)
     */
    public List<Criterion> nonReferenceCriteria() {
        return criteria.stream()
                .filter(c -> !(c instanceof CriterionReference))
                .toList();
    }

    /**
     * Returns all criteria that are references.
     *
     * <p>This is useful for debugging or understanding the reference structure.
     *
     * @return list of criterion references
     */
    public List<CriterionReference> references() {
        return criteria.stream()
                .filter(c -> c instanceof CriterionReference)
                .map(c -> (CriterionReference) c)
                .toList();
    }

    /**
     * Returns all query criteria (non-composite, non-reference).
     *
     * @return list of query criteria
     */
    public List<QueryCriterion> queries() {
        return criteria.stream()
                .filter(c -> c instanceof QueryCriterion)
                .map(c -> (QueryCriterion) c)
                .toList();
    }

    /**
     * Returns all composite criteria.
     *
     * @return list of composite criteria
     */
    public List<CompositeCriterion> composites() {
        return criteria.stream()
                .filter(c -> c instanceof CompositeCriterion)
                .map(c -> (CompositeCriterion) c)
                .toList();
    }

    /**
     * Creates a new fluent builder for constructing specifications.
     *
     * <h3>Example:</h3>
     * <pre>{@code
     * Specification spec = Specification.builder()
     *     .id("user-eligibility")
     *     .addCriterion(ageCheck)
     *     .addCriterion(statusCheck)
     *     .addCriterion(eligibilityComposite)
     *     .build();
     * }</pre>
     *
     * @return a new SpecificationBuilder instance
     * @see SpecificationBuilder
     */
    public static SpecificationBuilder builder() {
        return new SpecificationBuilder();
    }
}
