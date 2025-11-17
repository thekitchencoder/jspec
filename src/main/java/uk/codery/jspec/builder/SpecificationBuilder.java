package uk.codery.jspec.builder;

import uk.codery.jspec.evaluator.SpecificationEvaluator;
import uk.codery.jspec.model.Criterion;
import uk.codery.jspec.model.Specification;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Fluent builder for creating {@link Specification} instances.
 *
 * <p>A Specification contains a unified list of criteria, which can include:
 * <ul>
 *   <li>{@link uk.codery.jspec.model.QueryCriterion} - MongoDB-style query evaluations</li>
 *   <li>{@link uk.codery.jspec.model.CompositeCriterion} - Criteria combined with AND/OR logic</li>
 *   <li>{@link uk.codery.jspec.model.CriterionReference} - References to other criteria for result reuse</li>
 * </ul>
 *
 * <h2>Usage Examples</h2>
 *
 * <h3>Simple Specification with Query Criteria</h3>
 * <pre>{@code
 * QueryCriterion ageCheck = QueryCriterion.builder()
 *     .id("age-check")
 *     .field("age").gte(18)
 *     .build();
 *
 * QueryCriterion statusCheck = QueryCriterion.builder()
 *     .id("status-check")
 *     .field("status").eq("active")
 *     .build();
 *
 * Specification spec = Specification.builder()
 *     .id("user-eligibility")
 *     .criteria(ageCheck, statusCheck)
 *     .build();
 * }</pre>
 *
 * <h3>Specification with Composite Criteria</h3>
 * <pre>{@code
 * QueryCriterion age = QueryCriterion.builder()
 *     .id("age-check")
 *     .field("age").gte(18)
 *     .build();
 *
 * QueryCriterion status = QueryCriterion.builder()
 *     .id("status-check")
 *     .field("status").eq("active")
 *     .build();
 *
 * CompositeCriterion eligibility = CompositeCriterion.builder()
 *     .id("eligibility")
 *     .and()
 *     .criteria(age, status)
 *     .build();
 *
 * Specification spec = Specification.builder()
 *     .id("loan-eligibility")
 *     .addCriterion(age)
 *     .addCriterion(status)
 *     .addCriterion(eligibility)
 *     .build();
 * }</pre>
 *
 * <h3>Specification with References (Result Reuse)</h3>
 * <pre>{@code
 * // Define base criteria
 * QueryCriterion age = QueryCriterion.builder()
 *     .id("age-check")
 *     .field("age").gte(18)
 *     .build();
 *
 * // Reference in multiple composites (evaluated once, reused)
 * CompositeCriterion basic = CompositeCriterion.builder()
 *     .id("basic-check")
 *     .and()
 *     .addCriterion(new CriterionReference("age-check"))
 *     .build();
 *
 * Specification spec = Specification.builder()
 *     .id("validation")
 *     .addCriterion(age)      // Evaluated once
 *     .addCriterion(basic)    // Uses cached result
 *     .build();
 * }</pre>
 *
 * @see Specification
 * @see Criterion
 * @see uk.codery.jspec.model.QueryCriterion
 * @see uk.codery.jspec.model.CompositeCriterion
 * @see uk.codery.jspec.model.CriterionReference
 * @since 0.2.0
 */
public class SpecificationBuilder {

    private String id;
    private final List<Criterion> criteria = new ArrayList<>();

    /**
     * Creates a new SpecificationBuilder.
     *
     * <p>Use {@code Specification.builder()} instead of calling this constructor directly.
     */
    public SpecificationBuilder() {
        // Package-private constructor
    }

    /**
     * Sets the specification ID.
     *
     * @param id the specification identifier
     * @return this builder for method chaining
     * @throws IllegalArgumentException if id is null or empty
     */
    public SpecificationBuilder id(String id) {
        if (id == null || id.isEmpty()) {
            throw new IllegalArgumentException("Specification ID cannot be null or empty");
        }
        this.id = id;
        return this;
    }

    /**
     * Adds a single criterion to the specification.
     *
     * @param criterion the criterion to add
     * @return this builder for method chaining
     * @throws IllegalArgumentException if criterion is null
     */
    public SpecificationBuilder addCriterion(Criterion criterion) {
        if (criterion == null) {
            throw new IllegalArgumentException("Criterion cannot be null");
        }
        this.criteria.add(criterion);
        return this;
    }

    /**
     * Sets the criteria list (varargs).
     *
     * <p>This replaces any previously added criteria.
     *
     * @param criteria the criteria to include
     * @return this builder for method chaining
     */
    public SpecificationBuilder criteria(Criterion... criteria) {
        if (criteria != null) {
            this.criteria.clear();
            this.criteria.addAll(Arrays.asList(criteria));
        }
        return this;
    }

    /**
     * Sets the criteria list.
     *
     * <p>This replaces any previously added criteria.
     *
     * @param criteria the criteria to include
     * @return this builder for method chaining
     */
    public SpecificationBuilder criteria(List<Criterion> criteria) {
        if (criteria != null) {
            this.criteria.clear();
            this.criteria.addAll(criteria);
        }
        return this;
    }

    /**
     * Builds the Specification instance.
     *
     * <p>Creates a specification with all added criteria. The criteria list can contain
     * any combination of {@link uk.codery.jspec.model.QueryCriterion},
     * {@link uk.codery.jspec.model.CompositeCriterion}, and
     * {@link uk.codery.jspec.model.CriterionReference} instances.
     *
     * @return the constructed Specification
     * @throws IllegalStateException if id is not set
     */
    public Specification build() {
        if (id == null || id.isEmpty()) {
            throw new IllegalStateException("Specification ID must be set before building");
        }
        return new Specification(id, new ArrayList<>(criteria));
    }

    /**
     * Builds a {@link SpecificationEvaluator} directly from this builder.
     *
     * <p>This is a convenience method that builds the specification and immediately
     * creates an evaluator for it. Equivalent to:
     * <pre>{@code
     * Specification spec = builder.build();
     * SpecificationEvaluator evaluator = new SpecificationEvaluator(spec);
     * }</pre>
     *
     * <h3>Example:</h3>
     * <pre>{@code
     * SpecificationEvaluator evaluator = Specification.builder()
     *     .id("user-validation")
     *     .addCriterion(ageCheck)
     *     .addCriterion(statusCheck)
     *     .buildEvaluator();
     *
     * // Now ready to evaluate documents
     * EvaluationOutcome outcome = evaluator.evaluate(document);
     * }</pre>
     *
     * @return a new SpecificationEvaluator bound to the built specification
     * @throws IllegalStateException if id is not set
     * @see #build()
     * @see SpecificationEvaluator
     * @since 0.3.0
     */
    public SpecificationEvaluator buildEvaluator() {
        return new SpecificationEvaluator(build());
    }
}
