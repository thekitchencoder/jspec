package uk.codery.jspec.model;

/**
 * Boolean logic operators for combining multiple criteria in a {@link uk.codery.jspec.result.CompositeResult}.
 *
 * <p>Junctions define how multiple criteria within a group are evaluated together:
 * <ul>
 *   <li>{@link #AND} - All criteria must match (logical AND)</li>
 *   <li>{@link #OR} - At least one criterion must match (logical OR)</li>
 * </ul>
 *
 * <h2>Terminology: Junction vs Operator</h2>
 *
 * <p><b>IMPORTANT:</b> Do not confuse "Junction" with "Operator":
 * <ul>
 *   <li><b>Junction</b> - Combines multiple criteria (AND/OR) - only 2 values</li>
 *   <li><b>Operator</b> - Evaluates a single criterion ({@code $eq}, {@code $gt}, {@code $in}, etc.) - 13 operators</li>
 * </ul>
 *
 * <h2>Usage Examples</h2>
 *
 * <h3>AND Junction (All Must Match)</h3>
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
 * // Both criteria must match
 * CompositeResult group = new CompositeResult(
 *     "eligible-user",
 *     Junction.AND,
 *     List.of(ageCheck, statusCheck)
 * );
 * }</pre>
 *
 * <h3>OR Junction (Any Must Match)</h3>
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
 * // Either criterion can match
 * CompositeResult group = new CompositeResult(
 *     "special-user",
 *     Junction.OR,
 *     List.of(vipCheck, premiumCheck)
 * );
 * }</pre>
 *
 * <h3>Using Builder API</h3>
 * <pre>{@code
 * // AND group
 * CompositeResult andGroup = CompositeResult.builder()
 *     .id("employment-checks")
 *     .and()  // Sets junction to AND
 *     .criteria(employmentCheck, incomeCheck)
 *     .build();
 *
 * // OR group
 * CompositeResult orGroup = CompositeResult.builder()
 *     .id("special-access")
 *     .or()  // Sets junction to OR
 *     .criteria(adminCheck, managerCheck)
 *     .build();
 * }</pre>
 *
 * <h2>Evaluation Semantics</h2>
 *
 * <p><b>AND Junction:</b>
 * <ul>
 *   <li>Returns {@code true} if ALL criteria in the group match</li>
 *   <li>Returns {@code false} if ANY criterion does not match</li>
 *   <li>Short-circuits on first non-matching criterion (optimization)</li>
 * </ul>
 *
 * <p><b>OR Junction:</b>
 * <ul>
 *   <li>Returns {@code true} if ANY criterion in the group matches</li>
 *   <li>Returns {@code false} if ALL criteria do not match</li>
 *   <li>Short-circuits on first matching criterion (optimization)</li>
 * </ul>
 *
 * <h2>Design Note</h2>
 * <p>The library currently supports only AND and OR junctions. More complex boolean
 * logic (XOR, NAND, NOR) is not planned, as it can be expressed through combinations
 * of AND/OR groups.
 *
 * @see uk.codery.jspec.result.CompositeResult
 * @see Criterion
 * @see uk.codery.jspec.result.CompositeResult
 * @since 0.1.0
 */
public enum Junction {
    /**
     * Logical AND - all criteria in the group must match.
     *
     * <p>When used in a {@link uk.codery.jspec.result.CompositeResult}, this junction requires that
     * every criterion in the group evaluates to MATCHED for the group to match.
     *
     * <p>This is the default junction if none is specified.
     */
    AND,

    /**
     * Logical OR - at least one criterion in the group must match.
     *
     * <p>When used in a {@link uk.codery.jspec.result.CompositeResult}, this junction requires that
     * at least one criterion in the group evaluates to MATCHED for the group to match.
     */
    OR
}
