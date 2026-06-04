/**
 * Evaluation engine for processing specifications and criteria against documents.
 *
 * <p>This package contains the core evaluation logic that processes specifications
 * and individual criteria. The evaluators are thread-safe and support parallel
 * evaluation for optimal performance.
 *
 * <h2>Core Classes</h2>
 *
 * <h3>{@link uk.codery.jspec.evaluator.SpecificationEvaluator}</h3>
 * <p>Main entry point for evaluating complete specifications. Orchestrates parallel
 * evaluation of criteria and criteria groups.
 *
 * <pre>{@code
 * SpecificationEvaluator evaluator = new SpecificationEvaluator();
 * EvaluationOutcome outcome = evaluator.evaluate(document, specification);
 *
 * if (outcome.summary().fullyDetermined()) {
 *     System.out.println("Matched: " + outcome.summary().matched());
 * }
 * }</pre>
 *
 * <h3>{@link uk.codery.jspec.evaluator.CriterionEvaluator}</h3>
 * <p>Evaluates individual criteria using query operators. Supports 23 built-in
 * operators and can be extended with custom operators via {@link uk.codery.jspec.operator.OperatorRegistry}.
 *
 * <pre>{@code
 * CriterionEvaluator evaluator = new CriterionEvaluator();
 * EvaluationResult result = evaluator.evaluateCriterion(document, criterion);
 *
 * if (result.state() == EvaluationState.MATCHED) {
 *     System.out.println("Criterion matched!");
 * }
 * }</pre>
 *
 * <h2>Key Features</h2>
 *
 * <h3>Tri-State Evaluation</h3>
 * <p>Every criterion evaluation produces one of three states:
 * <ul>
 *   <li><b>MATCHED</b> - Criterion evaluated successfully, condition is TRUE</li>
 *   <li><b>NOT_MATCHED</b> - Criterion evaluated successfully, condition is FALSE</li>
 *   <li><b>UNDETERMINED</b> - Could not evaluate (missing data, unknown operator, type mismatch)</li>
 * </ul>
 *
 * <h3>Graceful Degradation</h3>
 * <p>The evaluators never throw exceptions during evaluation. Instead:
 * <ul>
 *   <li>Unknown operators → UNDETERMINED + warning log</li>
 *   <li>Type mismatches → UNDETERMINED + warning log</li>
 *   <li>Missing data → UNDETERMINED (not an error)</li>
 *   <li>Invalid patterns → UNDETERMINED + warning log</li>
 * </ul>
 *
 * <h3>Performance Optimizations</h3>
 * <ul>
 *   <li><b>Parallel evaluation</b> - Criteria evaluated concurrently using parallel streams</li>
 *   <li><b>Result caching</b> - Criterion results cached for efficient group evaluation</li>
 *   <li><b>Regex pattern caching</b> - Thread-safe LRU cache (~10-100x faster for repeated patterns)</li>
 *   <li><b>Optimized algorithms</b> - HashSet-based $all operator for O(n) performance</li>
 * </ul>
 *
 * <h3>Thread Safety</h3>
 * <p>Both evaluators are fully thread-safe:
 * <ul>
 *   <li>No mutable shared state</li>
 *   <li>Uses parallel streams (thread-safe operations)</li>
 *   <li>Synchronized regex pattern cache</li>
 *   <li>Safe to use from multiple threads concurrently</li>
 * </ul>
 *
 * <h2>Custom Operators</h2>
 *
 * <pre>{@code
 * // Create registry with custom operator
 * OperatorRegistry registry = OperatorRegistry.withDefaults();
 * registry.register("$length", (value, operand) -> {
 *     if (!(value instanceof String) || !(operand instanceof Number)) {
 *         return false;
 *     }
 *     return ((String) value).length() == ((Number) operand).intValue();
 * });
 *
 * // Use custom registry
 * CriterionEvaluator criterionEvaluator = new CriterionEvaluator(registry);
 * SpecificationEvaluator evaluator = new SpecificationEvaluator(criterionEvaluator);
 * }</pre>
 *
 * @see uk.codery.jspec.evaluator.SpecificationEvaluator
 * @see uk.codery.jspec.evaluator.CriterionEvaluator
 * @see uk.codery.jspec.result.EvaluationOutcome
 * @see uk.codery.jspec.result.EvaluationResult
 * @since 0.1.0
 */
package uk.codery.jspec.evaluator;
