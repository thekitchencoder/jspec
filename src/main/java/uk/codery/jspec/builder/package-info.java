/**
 * Fluent builder APIs for constructing specifications, criteria, and groups.
 *
 * <p>This package provides builder pattern implementations that offer a more
 * readable alternative to manual Map construction. Builders provide type-safe,
 * fluent APIs for creating domain model objects.
 *
 * <h2>Core Classes</h2>
 *
 * <h3>{@link uk.codery.jspec.builder.CriterionBuilder}</h3>
 * <p>Fluent builder for creating {@link uk.codery.jspec.model.Criterion} instances.
 *
 * <pre>{@code
 * // Instead of:
 * Criterion criterion = new Criterion("age-check",
 *     Map.of("age", Map.of("$gte", 18)));
 *
 * // Use builder:
 * Criterion criterion = Criterion.builder()
 *     .id("age-check")
 *     .field("age").gte(18)
 *     .build();
 * }</pre>
 *
 * <h3>{@link uk.codery.jspec.builder.CompositeCriterionBuilder}</h3>
 * <p>Fluent builder for creating {@link uk.codery.jspec.model.CompositeCriterion} instances.
 *
 * <pre>{@code
 * CriteriaGroup group = CriteriaGroup.builder()
 *     .id("employment-checks")
 *     .and()  // or .or()
 *     .criteria(employmentCheck, incomeCheck)
 *     .build();
 * }</pre>
 *
 * <h3>{@link uk.codery.jspec.builder.SpecificationBuilder}</h3>
 * <p>Fluent builder for creating {@link uk.codery.jspec.model.Specification} instances.
 *
 * <pre>{@code
 * Specification spec = Specification.builder()
 *     .id("loan-eligibility")
 *     .addCriterion(ageCheck)
 *     .addGroup(employmentGroup)
 *     .build();
 * }</pre>
 *
 * <h2>Supported Operators</h2>
 *
 * <p>CriterionBuilder provides fluent methods for all 13 MongoDB-style operators:
 *
 * <h3>Comparison Operators</h3>
 * <pre>{@code
 * field("age").eq(25)          // $eq
 * field("age").ne(25)          // $ne
 * field("age").gt(18)          // $gt
 * field("age").gte(18)         // $gte
 * field("age").lt(65)          // $lt
 * field("age").lte(65)         // $lte
 * }</pre>
 *
 * <h3>Collection Operators</h3>
 * <pre>{@code
 * field("status").in("active", "pending")           // $in
 * field("status").nin("deleted", "archived")        // $nin
 * field("tags").all("important", "urgent")          // $all
 * field("items").size(5)                            // $size
 * }</pre>
 *
 * <h3>Advanced Operators</h3>
 * <pre>{@code
 * field("email").exists(true)                       // $exists
 * field("count").type("number")                     // $type
 * field("email").regex("^[\\w.]+@[\\w.]+\\.[a-z]+$")  // $regex
 * field("items").elemMatch(subQuery)                // $elemMatch
 * }</pre>
 *
 * <h3>Custom Operators</h3>
 * <pre>{@code
 * field("username").operator("$length", 8)          // Custom operator
 * }</pre>
 *
 * <h2>Builder Features</h2>
 *
 * <h3>Multiple Conditions on Same Field</h3>
 * <pre>{@code
 * // Age between 18 and 65
 * Criterion criterion = Criterion.builder()
 *     .id("age-range")
 *     .field("age").gte(18).and().lte(65)
 *     .build();
 * }</pre>
 *
 * <h3>Multiple Fields</h3>
 * <pre>{@code
 * Criterion criterion = Criterion.builder()
 *     .id("user-validation")
 *     .field("age").gte(18)
 *     .field("status").eq("active")
 *     .field("email").exists(true)
 *     .build();
 * }</pre>
 *
 * <h3>Nested Fields (Dot Notation)</h3>
 * <pre>{@code
 * Criterion criterion = Criterion.builder()
 *     .id("address-check")
 *     .field("address.city").eq("London")
 *     .field("address.postalCode").regex("^[A-Z]{1,2}\\d")
 *     .build();
 * }</pre>
 *
 * <h3>Criteria Groups with AND/OR</h3>
 * <pre>{@code
 * // AND group (all must match)
 * CriteriaGroup andGroup = CriteriaGroup.builder()
 *     .id("employment-checks")
 *     .and()
 *     .criteria(employmentCheck, incomeCheck)
 *     .build();
 *
 * // OR group (any must match)
 * CriteriaGroup orGroup = CriteriaGroup.builder()
 *     .id("special-access")
 *     .or()
 *     .criteria(adminCheck, managerCheck)
 *     .build();
 * }</pre>
 *
 * <h2>Complete Example</h2>
 *
 * <pre>{@code
 * // Build criteria
 * Criterion ageCheck = Criterion.builder()
 *     .id("age-check")
 *     .field("age").gte(18).and().lte(65)
 *     .build();
 *
 * Criterion statusCheck = Criterion.builder()
 *     .id("status-check")
 *     .field("status").eq("active")
 *     .build();
 *
 * Criterion employmentCheck = Criterion.builder()
 *     .id("employment-check")
 *     .field("employment.status").eq("employed")
 *     .field("employment.income").gte(30000)
 *     .build();
 *
 * // Build group
 * CriteriaGroup employmentGroup = CriteriaGroup.builder()
 *     .id("employment-validation")
 *     .and()
 *     .addCriterion(employmentCheck)
 *     .build();
 *
 * // Build specification
 * Specification spec = Specification.builder()
 *     .id("loan-eligibility")
 *     .addCriterion(ageCheck)
 *     .addCriterion(statusCheck)
 *     .addGroup(employmentGroup)
 *     .build();
 * }</pre>
 *
 * <h2>Design Principles</h2>
 * <ul>
 *   <li><b>Fluent API</b> - Method chaining for readable code</li>
 *   <li><b>Type safety</b> - Compile-time checking of builder calls</li>
 *   <li><b>Immutability</b> - Builders produce immutable domain objects</li>
 *   <li><b>Validation</b> - Builders validate inputs and state</li>
 * </ul>
 *
 * <h2>Thread Safety</h2>
 * <p><b>Not thread-safe:</b> Builder instances should not be shared across threads.
 * Each thread should create its own builder instance. The domain objects produced
 * by builders are immutable and thread-safe.
 *
 * @see uk.codery.jspec.builder.CriterionBuilder
 * @see uk.codery.jspec.builder.CompositeCriterionBuilder
 * @see uk.codery.jspec.builder.SpecificationBuilder
 * @see uk.codery.jspec.model.Criterion
 * @see uk.codery.jspec.model.CompositeCriterion
 * @see uk.codery.jspec.model.Specification
 * @since 0.1.0
 */
package uk.codery.jspec.builder;
