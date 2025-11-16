package uk.codery.jspec.result;

/**
 * Result of evaluating a criterion against a document.
 *
 * <p>This sealed interface supports three types of evaluation results:
 * <ul>
 *   <li>{@link QueryResult} - Result of evaluating a QueryCriterion</li>
 *   <li>{@link CompositeResult} - Result of evaluating a CompositeCriterion</li>
 *   <li>{@link ReferenceResult} - Result of evaluating a CriterionReference</li>
 * </ul>
 *
 * <h2>Tri-State Model</h2>
 *
 * <p>All evaluation results use a tri-state model:
 * <ul>
 *   <li><b>MATCHED:</b> Criterion evaluated successfully and condition is true</li>
 *   <li><b>NOT_MATCHED:</b> Criterion evaluated successfully and condition is false</li>
 *   <li><b>UNDETERMINED:</b> Criterion could not be evaluated (missing data, invalid operators, etc.)</li>
 * </ul>
 *
 * <h2>Common Operations</h2>
 *
 * <pre>{@code
 * // All results have these methods
 * String id = result.id();                    // Criterion ID
 * EvaluationState state = result.state();     // Tri-state result
 * boolean matched = result.matched();         // Convenience (state == MATCHED)
 * String reason = result.reason();            // Explanation (null if matched)
 * }</pre>
 *
 * <h2>Pattern Matching</h2>
 *
 * <pre>{@code
 * switch (result) {
 *     case QueryResult query ->
 *         System.out.println("Query: " + query.criterion().query());
 *
 *     case CompositeResult composite ->
 *         System.out.println("Composite: " + composite.childResults().size() + " children");
 *
 *     case ReferenceResult ref ->
 *         System.out.println("Reference: " + ref.reference().id());
 * }
 * }</pre>
 *
 * <h2>Type-Specific Access</h2>
 *
 * <pre>{@code
 * if (result instanceof CompositeResult composite) {
 *     // Access composite-specific fields
 *     Junction junction = composite.junction();
 *     List<EvaluationResult> children = composite.childResults();
 *     Statistics stats = composite.statistics();
 * }
 *
 * if (result instanceof ReferenceResult ref) {
 *     // Unwrap reference to get original result
 *     EvaluationResult original = ref.unwrap();
 * }
 *
 * if (result instanceof QueryResult query) {
 *     // Access query-specific fields
 *     Map<String, Object> queryMap = query.criterion().query();
 *     List<String> missing = query.missingPaths();
 * }
 * }</pre>
 *
 * <h2>Usage Examples</h2>
 *
 * <h3>Checking Results</h3>
 * <pre>{@code
 * EvaluationOutcome outcome = evaluator.evaluate(document, specification);
 *
 * for (EvaluationResult result : outcome.results()) {
 *     System.out.printf("%s: %s%n", result.id(), result.state());
 *
 *     if (!result.matched()) {
 *         System.out.println("  Reason: " + result.reason());
 *     }
 * }
 * }</pre>
 *
 * <h3>Filtering by Type</h3>
 * <pre>{@code
 * List<QueryResult> queries = outcome.results().stream()
 *     .filter(r -> r instanceof QueryResult)
 *     .map(r -> (QueryResult) r)
 *     .toList();
 *
 * List<CompositeResult> composites = outcome.results().stream()
 *     .filter(r -> r instanceof CompositeResult)
 *     .map(r -> (CompositeResult) r)
 *     .toList();
 * }</pre>
 *
 * <h3>Recursive Analysis</h3>
 * <pre>{@code
 * void analyzeResult(EvaluationResult result, int depth) {
 *     String indent = "  ".repeat(depth);
 *     System.out.println(indent + result.id() + ": " + result.state());
 *
 *     if (result instanceof CompositeResult composite) {
 *         for (EvaluationResult child : composite.childResults()) {
 *             analyzeResult(child, depth + 1);  // Recursive
 *         }
 *     }
 * }
 * }</pre>
 *
 * @see QueryResult
 * @see CompositeResult
 * @see ReferenceResult
 * @see EvaluationState
 * @since 0.2.0
 */
public sealed interface EvaluationResult
        permits QueryResult, CompositeResult, ReferenceResult {

    /**
     * Returns the unique identifier of the criterion that was evaluated.
     *
     * @return the criterion ID (never null)
     */
    String id();

    /**
     * Returns the tri-state evaluation result.
     *
     * @return MATCHED, NOT_MATCHED, or UNDETERMINED
     */
    EvaluationState state();

    /**
     * Returns whether this result represents a match.
     *
     * <p>This is a convenience method equivalent to {@code state() == EvaluationState.MATCHED}.
     *
     * @return true if state is MATCHED, false otherwise
     */
    boolean matched();

    /**
     * Returns a human-readable explanation of why this result did not match.
     *
     * <p>Returns null if the result matched.
     * For non-matched results, provides details about:
     * <ul>
     *   <li>Missing data paths</li>
     *   <li>Invalid operators or types</li>
     *   <li>Composite junction failures</li>
     *   <li>Missing referenced criteria</li>
     * </ul>
     *
     * <h3>Example Output:</h3>
     * <ul>
     *   <li>"Missing data at: age, email"</li>
     *   <li>"Unknown operator: $custom"</li>
     *   <li>"AND composite failed: 2 matched, 1 not matched, 0 undetermined"</li>
     *   <li>"Referenced criterion 'age-check' not found or not yet evaluated"</li>
     * </ul>
     *
     * @return explanation of non-match, or null if matched
     */
    String reason();
}
