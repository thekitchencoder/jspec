/**
 * Domain model classes for defining specifications and criteria.
 *
 * <p>This package contains the core domain models that users interact with when
 * creating specifications. All classes are immutable Java records following the
 * "data as code" philosophy.
 *
 * <h2>Core Classes</h2>
 *
 * <h3>{@link uk.codery.jspec.model.Criterion}</h3>
 * <p>Represents a single evaluation criterion with MongoDB-style query operators.
 *
 * <pre>{@code
 * Criterion criterion = Criterion.builder()
 *     .id("age-check")
 *     .field("age").gte(18)
 *     .build();
 * }</pre>
 *
 * <h3>{@link uk.codery.jspec.model.CompositeCriterion}</h3>
 * <p>Combines multiple criteria using AND/OR logic (junction).
 *
 * <pre>{@code
 * CompositeCriterion group = CompositeCriterion.builder()
 *     .id("employment")
 *     .and()  // All must match
 *     .criteria(employmentCheck, incomeCheck)
 *     .build();
 * }</pre>
 *
 * <h3>{@link uk.codery.jspec.model.Specification}</h3>
 * <p>Top-level container for criteria and criteria groups.
 *
 * <pre>{@code
 * Specification spec = Specification.builder()
 *     .id("loan-eligibility")
 *     .addCriterion(ageCheck)
 *     .addGroup(employmentGroup)
 *     .build();
 * }</pre>
 *
 * <h3>{@link uk.codery.jspec.model.Junction}</h3>
 * <p>Enum defining boolean logic for criteria groups (AND/OR).
 *
 * <pre>{@code
 * Junction.AND  // All criteria must match
 * Junction.OR   // Any criterion must match
 * }</pre>
 *
 * <h2>Design Principles</h2>
 * <ul>
 *   <li><b>Immutability</b> - All classes are Java records (immutable by default)</li>
 *   <li><b>Thread-safety</b> - Safe to share across threads</li>
 *   <li><b>Structural equality</b> - Records provide equals/hashCode for free</li>
 *   <li><b>Builder pattern</b> - Fluent APIs for easy construction</li>
 * </ul>
 *
 * <h2>Important Terminology</h2>
 *
 * <p><b>Junction vs Operator:</b> Do not confuse these two concepts:
 * <ul>
 *   <li><b>Junction</b> - Combines multiple criteria (AND/OR) - only 2 values</li>
 *   <li><b>Operator</b> - Evaluates a single criterion ($eq, $gt, $in, etc.) - 13 operators</li>
 * </ul>
 *
 * @see uk.codery.jspec.model.Criterion
 * @see uk.codery.jspec.model.CompositeCriterion
 * @see uk.codery.jspec.model.Specification
 * @see uk.codery.jspec.model.Junction
 * @since 0.1.0
 */
package uk.codery.jspec.model;
