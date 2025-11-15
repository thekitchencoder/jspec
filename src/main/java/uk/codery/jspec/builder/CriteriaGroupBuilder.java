package uk.codery.jspec.builder;

import uk.codery.jspec.model.Criterion;
import uk.codery.jspec.model.CriteriaGroup;
import uk.codery.jspec.model.Junction;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Fluent builder for creating {@link CriteriaGroup} instances.
 *
 * <p>A CriteriaGroup combines multiple criteria using AND or OR logic (junction).
 * This builder provides a fluent API for constructing groups.
 *
 * <h2>Usage Examples</h2>
 *
 * <h3>AND Group (All Criteria Must Match)</h3>
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
 * CriteriaGroup group = CriteriaGroup.builder()
 *     .id("eligible-user")
 *     .and()
 *     .criteria(ageCheck, statusCheck)
 *     .build();
 * }</pre>
 *
 * <h3>OR Group (Any Criterion Must Match)</h3>
 * <pre>{@code
 * Criterion vipCheck = Criterion.builder()
 *     .id("vip-check")
 *     .field("vip").eq(true)
 *     .build();
 *
 * Criterion premiumCheck = Criterion.builder()
 *     .id("premium-check")
 *     .field("subscription").eq("premium")
 *     .build();
 *
 * CriteriaGroup group = CriteriaGroup.builder()
 *     .id("special-user")
 *     .or()
 *     .criteria(vipCheck, premiumCheck)
 *     .build();
 * }</pre>
 *
 * <h3>Adding Criteria Individually</h3>
 * <pre>{@code
 * CriteriaGroup group = CriteriaGroup.builder()
 *     .id("user-checks")
 *     .and()
 *     .addCriterion(ageCheck)
 *     .addCriterion(statusCheck)
 *     .addCriterion(emailCheck)
 *     .build();
 * }</pre>
 *
 * @see CriteriaGroup
 * @see Criterion
 * @see Junction
 * @since 0.1.0
 */
public class CriteriaGroupBuilder {

    private String id;
    private Junction junction = Junction.AND; // Default to AND
    private final List<Criterion> criteria = new ArrayList<>();

    /**
     * Creates a new CriteriaGroupBuilder.
     *
     * <p>Use {@code CriteriaGroup.builder()} instead of calling this constructor directly.
     */
    public CriteriaGroupBuilder() {
        // Package-private constructor
    }

    /**
     * Sets the group ID.
     *
     * @param id the group identifier
     * @return this builder for method chaining
     * @throws IllegalArgumentException if id is null or empty
     */
    public CriteriaGroupBuilder id(String id) {
        if (id == null || id.isEmpty()) {
            throw new IllegalArgumentException("CriteriaGroup ID cannot be null or empty");
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
    public CriteriaGroupBuilder and() {
        this.junction = Junction.AND;
        return this;
    }

    /**
     * Sets the junction to OR (any criterion must match).
     *
     * @return this builder for method chaining
     */
    public CriteriaGroupBuilder or() {
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
    public CriteriaGroupBuilder junction(Junction junction) {
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
    public CriteriaGroupBuilder addCriterion(Criterion criterion) {
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
     * @param criteria the criteria to include in the group
     * @return this builder for method chaining
     * @throws IllegalArgumentException if criteria is null or empty
     */
    public CriteriaGroupBuilder criteria(Criterion... criteria) {
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
    public CriteriaGroupBuilder criteria(List<Criterion> criteria) {
        if (criteria == null || criteria.isEmpty()) {
            throw new IllegalArgumentException("At least one criterion must be provided");
        }
        this.criteria.clear();
        this.criteria.addAll(criteria);
        return this;
    }

    /**
     * Builds the CriteriaGroup instance.
     *
     * @return the constructed CriteriaGroup
     * @throws IllegalStateException if id is not set or no criteria are defined
     */
    public CriteriaGroup build() {
        if (id == null || id.isEmpty()) {
            throw new IllegalStateException("CriteriaGroup ID must be set before building");
        }
        if (criteria.isEmpty()) {
            throw new IllegalStateException("At least one criterion must be added");
        }
        return new CriteriaGroup(id, junction, new ArrayList<>(criteria));
    }
}
