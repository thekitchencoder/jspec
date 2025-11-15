package uk.codery.jspec.builder;

import uk.codery.jspec.model.Criterion;
import uk.codery.jspec.model.CriteriaGroup;
import uk.codery.jspec.model.Specification;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Fluent builder for creating {@link Specification} instances.
 *
 * <p>A Specification contains criteria and optionally criteria groups.
 * This builder provides a fluent API for constructing specifications.
 *
 * <h2>Usage Examples</h2>
 *
 * <h3>Simple Specification with Criteria</h3>
 * <pre>{@code
 * Criterion ageCheck = Criterion.builder()
 *     .id("age-check")
 *     .field("age").gte(18)
 *     .build();
 *
 * Criterion statusCheck = Criterion.builder()
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
 * <h3>Specification with Criteria Groups</h3>
 * <pre>{@code
 * CriteriaGroup employmentGroup = CriteriaGroup.builder()
 *     .id("employment-checks")
 *     .and()
 *     .criteria(employmentCheck, incomeCheck)
 *     .build();
 *
 * CriteriaGroup residencyGroup = CriteriaGroup.builder()
 *     .id("residency-checks")
 *     .and()
 *     .criteria(addressCheck, citizenshipCheck)
 *     .build();
 *
 * Specification spec = Specification.builder()
 *     .id("loan-eligibility")
 *     .addCriterion(ageCheck)
 *     .addGroup(employmentGroup)
 *     .addGroup(residencyGroup)
 *     .build();
 * }</pre>
 *
 * <h3>Building Inline</h3>
 * <pre>{@code
 * Specification spec = Specification.builder()
 *     .id("complete-validation")
 *     .addCriterion(
 *         Criterion.builder()
 *             .id("age-check")
 *             .field("age").gte(18)
 *             .build()
 *     )
 *     .addCriterion(
 *         Criterion.builder()
 *             .id("status-check")
 *             .field("status").eq("active")
 *             .build()
 *     )
 *     .build();
 * }</pre>
 *
 * @see Specification
 * @see Criterion
 * @see CriteriaGroup
 * @since 0.1.0
 */
public class SpecificationBuilder {

    private String id;
    private final List<Criterion> criteria = new ArrayList<>();
    private final List<CriteriaGroup> criteriaGroups = new ArrayList<>();

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
     * Adds a single criteria group to the specification.
     *
     * @param group the criteria group to add
     * @return this builder for method chaining
     * @throws IllegalArgumentException if group is null
     */
    public SpecificationBuilder addGroup(CriteriaGroup group) {
        if (group == null) {
            throw new IllegalArgumentException("CriteriaGroup cannot be null");
        }
        this.criteriaGroups.add(group);
        return this;
    }

    /**
     * Sets the criteria groups list (varargs).
     *
     * <p>This replaces any previously added groups.
     *
     * @param groups the criteria groups to include
     * @return this builder for method chaining
     */
    public SpecificationBuilder groups(CriteriaGroup... groups) {
        if (groups != null) {
            this.criteriaGroups.clear();
            this.criteriaGroups.addAll(Arrays.asList(groups));
        }
        return this;
    }

    /**
     * Sets the criteria groups list.
     *
     * <p>This replaces any previously added groups.
     *
     * @param groups the criteria groups to include
     * @return this builder for method chaining
     */
    public SpecificationBuilder groups(List<CriteriaGroup> groups) {
        if (groups != null) {
            this.criteriaGroups.clear();
            this.criteriaGroups.addAll(groups);
        }
        return this;
    }

    /**
     * Builds the Specification instance.
     *
     * @return the constructed Specification
     * @throws IllegalStateException if id is not set
     */
    public Specification build() {
        if (id == null || id.isEmpty()) {
            throw new IllegalStateException("Specification ID must be set before building");
        }
        return new Specification(
                id,
                new ArrayList<>(criteria),
                new ArrayList<>(criteriaGroups)
        );
    }
}
