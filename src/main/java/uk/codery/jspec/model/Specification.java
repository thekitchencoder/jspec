package uk.codery.jspec.model;

import uk.codery.jspec.builder.SpecificationBuilder;

import java.util.List;

/**
 * Represents a complete specification with criteria and criteria groups.
 *
 * <p>A Specification is the top-level evaluation unit that contains:
 * <ul>
 *   <li>Individual criteria - evaluated independently</li>
 *   <li>Criteria groups - multiple criteria combined with AND/OR logic</li>
 * </ul>
 *
 * <h2>Creating Specifications</h2>
 *
 * <h3>Using Constructor</h3>
 * <pre>{@code
 * List<Criterion> criteria = List.of(
 *     new Criterion("age-check", Map.of("age", Map.of("$gte", 18))),
 *     new Criterion("status-check", Map.of("status", Map.of("$eq", "active")))
 * );
 *
 * List<CriteriaGroup> groups = List.of(
 *     new CriteriaGroup("employment", Junction.AND, List.of(employmentCheck, incomeCheck))
 * );
 *
 * Specification spec = new Specification("eligibility", criteria, groups);
 * }</pre>
 *
 * <h3>Using Builder</h3>
 * <pre>{@code
 * Specification spec = Specification.builder()
 *     .id("eligibility")
 *     .addCriterion(ageCheck)
 *     .addCriterion(statusCheck)
 *     .addGroup(employmentGroup)
 *     .build();
 * }</pre>
 *
 * <h2>Evaluation</h2>
 * <pre>{@code
 * SpecificationEvaluator evaluator = new SpecificationEvaluator();
 * Map<String, Object> document = Map.of("age", 25, "status", "active");
 *
 * EvaluationOutcome outcome = evaluator.evaluate(document, specification);
 *
 * if (outcome.summary().matchedCriteria() > 0) {
 *     System.out.println("Document matches specification");
 * }
 * }</pre>
 *
 * @param id the unique identifier for this specification
 * @param criteria the list of individual criteria
 * @param criteriaGroups the list of criteria groups
 * @see Criterion
 * @see CriteriaGroup
 * @see SpecificationBuilder
 * @see uk.codery.jspec.evaluator.SpecificationEvaluator
 * @since 0.1.0
 */
public record Specification(String id, List<Criterion> criteria, List<CriteriaGroup> criteriaGroups) {

    /**
     * Creates a new fluent builder for constructing specifications.
     *
     * <h3>Example:</h3>
     * <pre>{@code
     * Specification spec = Specification.builder()
     *     .id("user-eligibility")
     *     .addCriterion(ageCheck)
     *     .addGroup(employmentGroup)
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
