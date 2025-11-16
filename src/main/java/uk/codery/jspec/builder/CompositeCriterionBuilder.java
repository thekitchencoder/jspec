package uk.codery.jspec.builder;

import lombok.NonNull;
import uk.codery.jspec.model.Criterion;
import uk.codery.jspec.model.CompositeCriterion;
import uk.codery.jspec.model.Junction;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Fluent builder for creating {@link CompositeCriterion} instances.
 *
 * <p>A CompositeCriterion combines multiple criteria using AND or OR logic (junction).
 * This builder provides a fluent API for constructing composite criteria.
 *
 * <h2>Usage Examples</h2>
 *
 * <h3>AND Group (All Criteria Must Match)</h3>
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
 * CompositeCriterion group = CompositeCriterion.builder()
 *     .id("eligible-user")
 *     .and()
 *     .criteria(ageCheck, statusCheck)
 *     .build();
 * }</pre>
 *
 * <h3>OR Group (Any Criterion Must Match)</h3>
 * <pre>{@code
 * QueryCriterion vipCheck = QueryCriterion.builder()
 *     .id("vip-check")
 *     .field("vip").eq(true)
 *     .build();
 *
 * QueryCriterion premiumCheck = QueryCriterion.builder()
 *     .id("premium-check")
 *     .field("subscription").eq("premium")
 *     .build();
 *
 * CompositeCriterion group = CompositeCriterion.builder()
 *     .id("special-user")
 *     .or()
 *     .criteria(vipCheck, premiumCheck)
 *     .build();
 * }</pre>
 *
 * <h3>Adding Criteria Individually</h3>
 * <pre>{@code
 * CompositeCriterion group = CompositeCriterion.builder()
 *     .id("user-checks")
 *     .and()
 *     .addCriterion(ageCheck)
 *     .addCriterion(statusCheck)
 *     .addCriterion(emailCheck)
 *     .build();
 * }</pre>
 *
 * @see CompositeCriterion
 * @see Criterion
 * @see Junction
 * @since 0.2.0
 */
public class CompositeCriterionBuilder {

    private String id;
    private Junction junction = Junction.AND; // Default to AND
    private final List<Criterion> criteria = new ArrayList<>();

    /**
     * Creates a new CompositeCriterionBuilder.
     *
     * <p>Use {@code CompositeCriterion.builder()} instead of calling this constructor directly.
     */
    public CompositeCriterionBuilder() {
        // Package-private constructor
    }

    /**
     * Sets the composite criterion ID.
     *
     * @param id the criterion identifier
     * @return this builder for method chaining
     * @throws IllegalArgumentException if id is null or empty
     */
    public CompositeCriterionBuilder id(String id) {
        if (id == null || id.isEmpty()) {
            throw new IllegalArgumentException("CompositeCriterion ID cannot be null or empty");
        }
        this.id = id;
        return this;
    }

    /**
     * Sets the junction to AND (all criteria must match).
     *
     * <p>This is the default junction if not specified.
     *
     * @return this builder for method chaining
     */
    public CompositeCriterionBuilder and() {
        this.junction = Junction.AND;
        return this;
    }

    /**
     * Sets the junction to OR (any criterion must match).
     *
     * @return this builder for method chaining
     */
    public CompositeCriterionBuilder or() {
        this.junction = Junction.OR;
        return this;
    }

    /**
     * Sets the junction explicitly.
     *
     * @param junction the junction to use (AND or OR)
     * @return this builder for method chaining
     * @throws IllegalArgumentException if junction is null
     */
    public CompositeCriterionBuilder junction(Junction junction) {
        if (junction == null) {
            throw new IllegalArgumentException("Junction cannot be null");
        }
        this.junction = junction;
        return this;
    }

    /**
     * Adds a single criterion to the group.
     *
     * @param criterion the criterion to add
     * @return this builder for method chaining
     * @throws IllegalArgumentException if criterion is null
     */
    public CompositeCriterionBuilder addCriterion(Criterion criterion) {
        if (criterion == null) {
            throw new IllegalArgumentException("Criterion cannot be null");
        }
        this.criteria.add(criterion);
        return this;
    }

    public CompositeCriterionBuilder addReference(@NonNull Criterion criterion){
        return this.addCriterion(criterion.ref());
    }

    /**
     * Sets the criteria list (varargs).
     *
     * <p>This replaces any previously added criteria.
     *
     * @param criteria the criteria to include in the group
     * @return this builder for method chaining
     * @throws IllegalArgumentException if criteria is null or empty
     */
    public CompositeCriterionBuilder criteria(Criterion... criteria) {
        if (criteria == null || criteria.length == 0) {
            throw new IllegalArgumentException("At least one criterion must be provided");
        }
        this.criteria.clear();
        this.criteria.addAll(Arrays.asList(criteria));
        return this;
    }

    /**
     * Sets the criteria list.
     *
     * <p>This replaces any previously added criteria.
     *
     * @param criteria the criteria to include in the group
     * @return this builder for method chaining
     * @throws IllegalArgumentException if criteria is null or empty
     */
    public CompositeCriterionBuilder criteria(List<Criterion> criteria) {
        if (criteria == null || criteria.isEmpty()) {
            throw new IllegalArgumentException("At least one criterion must be provided");
        }
        this.criteria.clear();
        this.criteria.addAll(criteria);
        return this;
    }

    /**
     * Builds the CompositeCriterion instance.
     *
     * @return the constructed CompositeCriterion
     * @throws IllegalStateException if id is not set or no criteria are defined
     */
    public CompositeCriterion build() {
        if (id == null || id.isEmpty()) {
            throw new IllegalStateException("CompositeCriterion ID must be set before building");
        }
        if (criteria.isEmpty()) {
            throw new IllegalStateException("At least one criterion must be added");
        }
        return new CompositeCriterion(id, junction, new ArrayList<>(criteria));
    }
}
