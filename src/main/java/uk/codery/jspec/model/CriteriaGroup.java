package uk.codery.jspec.model;

import uk.codery.jspec.builder.CriteriaGroupBuilder;

import java.util.List;

/**
 * Represents a group of criteria combined with boolean logic (AND/OR).
 *
 * <p>A CriteriaGroup allows combining multiple criteria using a junction (AND or OR).
 * This enables complex evaluation logic beyond individual criteria.
 *
 * <h2>Creating Criteria Groups</h2>
 *
 * <h3>Using Constructor</h3>
 * <pre>{@code
 * Criterion age = new Criterion("age-check", Map.of("age", Map.of("$gte", 18)));
 * Criterion status = new Criterion("status-check", Map.of("status", Map.of("$eq", "active")));
 *
 * // AND group (all must match)
 * CriteriaGroup group = new CriteriaGroup("eligibility", Junction.AND, List.of(age, status));
 *
 * // OR group (any must match)
 * CriteriaGroup group = new CriteriaGroup("special-user", Junction.OR, List.of(vip, premium));
 * }</pre>
 *
 * <h3>Using Builder</h3>
 * <pre>{@code
 * CriteriaGroup group = CriteriaGroup.builder()
 *     .id("eligibility")
 *     .and()  // or .or()
 *     .criteria(ageCheck, statusCheck)
 *     .build();
 * }</pre>
 *
 * @param id the unique identifier for this group
 * @param junction the boolean logic to combine criteria (AND or OR)
 * @param criteria the list of criteria in this group
 * @see Junction
 * @see Criterion
 * @see CriteriaGroupBuilder
 * @since 0.1.0
 */
public record CriteriaGroup(String id, Junction junction, List<Criterion> criteria) {

    /**
     * Creates a criteria group with AND junction.
     *
     * <p>Convenience constructor that defaults to AND junction.
     *
     * @param id the group identifier
     * @param criteria the criteria to include
     */
    public CriteriaGroup(String id, List<Criterion> criteria){
        this(id, Junction.AND, criteria);
    }

    /**
     * Creates a new fluent builder for constructing criteria groups.
     *
     * <h3>Example:</h3>
     * <pre>{@code
     * CriteriaGroup group = CriteriaGroup.builder()
     *     .id("user-checks")
     *     .and()
     *     .criteria(ageCheck, statusCheck)
     *     .build();
     * }</pre>
     *
     * @return a new CriteriaGroupBuilder instance
     * @see CriteriaGroupBuilder
     */
    public static CriteriaGroupBuilder builder() {
        return new CriteriaGroupBuilder();
    }
}
