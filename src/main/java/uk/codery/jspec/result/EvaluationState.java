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
    UNDETERMINED
}
