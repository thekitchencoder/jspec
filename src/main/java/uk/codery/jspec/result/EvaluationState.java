package uk.codery.jspec.result;

/**
 * Represents the three possible states of a criterion evaluation.
 *
 * <p>This tri-state model allows the system to distinguish between:
 * <ul>
 *   <li>Successful evaluation that matched (MATCHED)</li>
 *   <li>Successful evaluation that did not match (NOT_MATCHED)</li>
 *   <li>Failed evaluation due to errors or missing data (UNDETERMINED)</li>
 * </ul>
 *
 * <p>The UNDETERMINED state ensures graceful degradation - errors in one criterion
 * never prevent evaluation of other criteria or stop the overall specification evaluation.
 *
 * <h2>Kleene Three-Valued Logic</h2>
 *
 * <p>This enum implements <b>Strong Kleene Logic (K3)</b> for combining evaluation states
 * using {@link #and(EvaluationState)} and {@link #or(EvaluationState)} methods. This logic
 * is more powerful than conservative logic because it can still make definitive conclusions
 * even when some values are UNDETERMINED.
 *
 * <h3>AND Logic (Conjunction):</h3>
 * <pre>
 *             | MATCHED | NOT_MATCHED | UNDETERMINED
 * ------------|---------|-------------|-------------
 * MATCHED     | MATCHED | NOT_MATCHED | UNDETERMINED
 * NOT_MATCHED | NOT_MATCHED | NOT_MATCHED | NOT_MATCHED
 * UNDETERMINED| UNDETERMINED | NOT_MATCHED | UNDETERMINED
 * </pre>
 *
 * <p><b>Key Insight:</b> {@code NOT_MATCHED AND anything = NOT_MATCHED} because
 * one false value makes the entire AND expression false.
 *
 * <h3>OR Logic (Disjunction):</h3>
 * <pre>
 *             | MATCHED | NOT_MATCHED | UNDETERMINED
 * ------------|---------|-------------|-------------
 * MATCHED     | MATCHED | MATCHED     | MATCHED
 * NOT_MATCHED | MATCHED | NOT_MATCHED | UNDETERMINED
 * UNDETERMINED| MATCHED | UNDETERMINED | UNDETERMINED
 * </pre>
 *
 * <p><b>Key Insight:</b> {@code MATCHED OR anything = MATCHED} because
 * one true value makes the entire OR expression true.
 *
 * <h2>Usage Examples</h2>
 *
 * <h3>Checking State</h3>
 * <pre>{@code
 * EvaluationState state = result.state();
 *
 * if (state.matched()) {
 *     System.out.println("Criterion matched!");
 * }
 *
 * if (state.undetermined()) {
 *     System.out.println("Could not evaluate: " + result.reason());
 * }
 * }</pre>
 *
 * <h3>Combining States</h3>
 * <pre>{@code
 * EvaluationState result1 = MATCHED;
 * EvaluationState result2 = UNDETERMINED;
 *
 * // AND: MATCHED AND UNDETERMINED = UNDETERMINED
 * EvaluationState andResult = result1.and(result2);
 * System.out.println(andResult);  // UNDETERMINED
 *
 * // OR: MATCHED OR UNDETERMINED = MATCHED
 * EvaluationState orResult = result1.or(result2);
 * System.out.println(orResult);  // MATCHED
 * }</pre>
 *
 * <h3>Composite Criteria</h3>
 * <pre>{@code
 * // Reduce multiple child states with AND
 * EvaluationState andComposite = childResults.stream()
 *     .map(EvaluationResult::state)
 *     .reduce(MATCHED, EvaluationState::and);
 *
 * // Reduce multiple child states with OR
 * EvaluationState orComposite = childResults.stream()
 *     .map(EvaluationResult::state)
 *     .reduce(NOT_MATCHED, EvaluationState::or);
 * }</pre>
 *
 * @see <a href="https://en.wikipedia.org/wiki/Three-valued_logic#Kleene_and_Priest_logics">Kleene Logic</a>
 */
public enum EvaluationState {
    /**
     * Criterion evaluated successfully and the condition is TRUE.
     * All required data was present and valid.
     */
    MATCHED,

    /**
     * Criterion evaluated successfully and the condition is FALSE.
     * All required data was present and valid.
     */
    NOT_MATCHED,

    /**
     * Criterion could not be evaluated definitively.
     * Reasons include:
     * <ul>
     *   <li>Missing data in the input document</li>
     *   <li>Unknown operator in the criterion</li>
     *   <li>Type mismatch (operator expects different type)</li>
     *   <li>Invalid query (e.g., malformed regex pattern)</li>
     * </ul>
     */
    UNDETERMINED;

    /**
     * Returns true if this state is MATCHED.
     *
     * @return true if this state represents a successful match
     */
    public boolean matched() {
        return this == MATCHED;
    }

    /**
     * Returns true if this state is NOT_MATCHED.
     *
     * @return true if this state represents a definitive non-match
     */
    public boolean notMatched() {
        return this == NOT_MATCHED;
    }

    /**
     * Returns true if this state is UNDETERMINED.
     *
     * @return true if the evaluation could not be completed
     */
    public boolean undetermined() {
        return this == UNDETERMINED;
    }

    /**
     * Returns true if the evaluation was deterministic (MATCHED or NOT_MATCHED).
     *
     * @return true if this state is not UNDETERMINED
     */
    public boolean determined() {
        return this != UNDETERMINED;
    }

    /**
     * Combines this state with another using AND logic (Kleene conjunction).
     *
     * <p><b>Truth Table:</b>
     * <pre>
     *             | MATCHED | NOT_MATCHED | UNDETERMINED
     * ------------|---------|-------------|-------------
     * MATCHED     | MATCHED | NOT_MATCHED | UNDETERMINED
     * NOT_MATCHED | NOT_MATCHED | NOT_MATCHED | NOT_MATCHED
     * UNDETERMINED| UNDETERMINED | NOT_MATCHED | UNDETERMINED
     * </pre>
     *
     * <p><b>Key Property:</b> {@code NOT_MATCHED AND anything = NOT_MATCHED}
     * <br>This is because one false value makes the entire AND expression false.
     *
     * <h3>Example:</h3>
     * <pre>{@code
     * MATCHED.and(MATCHED)           // → MATCHED
     * MATCHED.and(NOT_MATCHED)       // → NOT_MATCHED
     * MATCHED.and(UNDETERMINED)      // → UNDETERMINED
     * NOT_MATCHED.and(UNDETERMINED)  // → NOT_MATCHED (we know it's false!)
     * }</pre>
     *
     * @param other the other state to combine with
     * @return the combined state using AND logic
     */
    public EvaluationState and(EvaluationState other) {
        // NOT_MATCHED dominates (false AND anything = false)
        if (this == NOT_MATCHED || other == NOT_MATCHED) {
            return NOT_MATCHED;
        }

        // If both are MATCHED, result is MATCHED
        if (this == MATCHED && other == MATCHED) {
            return MATCHED;
        }

        // Otherwise, at least one is UNDETERMINED
        return UNDETERMINED;
    }

    /**
     * Combines this state with another using OR logic (Kleene disjunction).
     *
     * <p><b>Truth Table:</b>
     * <pre>
     *             | MATCHED | NOT_MATCHED | UNDETERMINED
     * ------------|---------|-------------|-------------
     * MATCHED     | MATCHED | MATCHED     | MATCHED
     * NOT_MATCHED | MATCHED | NOT_MATCHED | UNDETERMINED
     * UNDETERMINED| MATCHED | UNDETERMINED | UNDETERMINED
     * </pre>
     *
     * <p><b>Key Property:</b> {@code MATCHED OR anything = MATCHED}
     * <br>This is because one true value makes the entire OR expression true.
     *
     * <h3>Example:</h3>
     * <pre>{@code
     * MATCHED.or(NOT_MATCHED)       // → MATCHED
     * MATCHED.or(UNDETERMINED)      // → MATCHED (we know it's true!)
     * NOT_MATCHED.or(NOT_MATCHED)   // → NOT_MATCHED
     * NOT_MATCHED.or(UNDETERMINED)  // → UNDETERMINED
     * }</pre>
     *
     * @param other the other state to combine with
     * @return the combined state using OR logic
     */
    public EvaluationState or(EvaluationState other) {
        // MATCHED dominates (true OR anything = true)
        if (this == MATCHED || other == MATCHED) {
            return MATCHED;
        }

        // If both are NOT_MATCHED, result is NOT_MATCHED
        if (this == NOT_MATCHED && other == NOT_MATCHED) {
            return NOT_MATCHED;
        }

        // Otherwise, at least one is UNDETERMINED
        return UNDETERMINED;
    }
}
