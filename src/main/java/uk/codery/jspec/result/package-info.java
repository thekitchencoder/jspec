/**
 * Result types returned from specification and criterion evaluation.
 *
 * <p>This package contains all result types that represent evaluation outcomes.
 * All result classes are immutable and provide comprehensive information about
 * what matched, what didn't, and why.
 *
 * <h2>Core Classes</h2>
 *
 * <h3>{@link uk.codery.jspec.result.EvaluationOutcome}</h3>
 * <p>Top-level result from evaluating a complete specification.
 *
 * <pre>{@code
 * EvaluationOutcome outcome = evaluator.evaluate(document, specification);
 *
 * // Access individual criterion results
 * for (EvaluationResult result : outcome.evaluationResults()) {
 *     System.out.println(result.id() + ": " + result.state());
 * }
 *
 * // Access criteria group results
 * for (CompositeResult group : outcome.CompositeResults()) {
 *     System.out.println(group.id() + ": " + group.matched());
 * }
 *
 * // Check summary
 * System.out.println("Fully Determined: " + outcome.summary().fullyDetermined());
 * }</pre>
 *
 * <h3>{@link uk.codery.jspec.result.EvaluationResult}</h3>
 * <p>Result from evaluating a single criterion.
 *
 * <pre>{@code
 * EvaluationResult result = evaluator.evaluateCriterion(document, criterion);
 *
 * switch (result.state()) {
 *     case MATCHED -> System.out.println("Matched!");
 *     case NOT_MATCHED -> System.out.println("Not matched: " + result.reason());
 *     case UNDETERMINED -> System.out.println("Could not evaluate: " + result.reason());
 * }
 *
 * // Check for missing data
 * if (!result.missingPaths().isEmpty()) {
 *     System.out.println("Missing: " + result.missingPaths());
 * }
 * }</pre>
 *
 * <h3>{@link uk.codery.jspec.result.CompositeResult}</h3>
 * <p>Result from evaluating a criteria group (multiple criteria with AND/OR logic).
 *
 * <pre>{@code
 * CompositeResult groupResult = // from outcome
 *
 * System.out.println("Group: " + groupResult.id());
 * System.out.println("Junction: " + groupResult.junction());
 * System.out.println("Matched: " + groupResult.matched());
 *
 * // Inspect individual criteria in group
 * for (EvaluationResult result : groupResult.evaluationResults()) {
 *     System.out.println("  - " + result.id() + ": " + result.matched());
 * }
 * }</pre>
 *
 * <h3>{@link uk.codery.jspec.result.EvaluationState}</h3>
 * <p>Enum representing the three possible evaluation states.
 *
 * <pre>{@code
 * MATCHED       // Evaluated successfully, condition is TRUE
 * NOT_MATCHED   // Evaluated successfully, condition is FALSE
 * UNDETERMINED  // Could not evaluate (missing data, invalid criterion, etc.)
 * }</pre>
 *
 * <h3>{@link uk.codery.jspec.result.EvaluationSummary}</h3>
 * <p>Summary statistics for specification evaluation.
 *
 * <pre>{@code
 * EvaluationSummary summary = outcome.summary();
 *
 * System.out.println("Total: " + summary.total());
 * System.out.println("Matched: " + summary.matched());
 * System.out.println("Not Matched: " + summary.notMatched());
 * System.out.println("Undetermined: " + summary.undetermined());
 * System.out.println("Fully Determined: " + summary.fullyDetermined());
 * }</pre>
 *
 * <h3>{@link uk.codery.jspec.result.EvaluationResult}</h3>
 * <p>Common interface for all result types, enabling polymorphic handling.
 *
 * <pre>{@code
 * Result result = // EvaluationResult or CompositeResult
 *
 * if (!result.matched()) {
 *     System.out.println(result.id() + " failed: " + result.reason());
 * }
 * }</pre>
 *
 * <h2>Tri-State Model</h2>
 *
 * <p>The tri-state evaluation model is the core innovation that enables graceful degradation:
 * <ul>
 *   <li><b>MATCHED</b> - Criterion evaluated successfully, condition is TRUE</li>
 *   <li><b>NOT_MATCHED</b> - Criterion evaluated successfully, condition is FALSE</li>
 *   <li><b>UNDETERMINED</b> - Could not evaluate (missing data, unknown operator, type mismatch)</li>
 * </ul>
 *
 * <p>This ensures that:
 * <ul>
 *   <li>One bad criterion never stops the overall evaluation</li>
 *   <li>Partial evaluation results are usable</li>
 *   <li>Clear visibility into what couldn't be evaluated</li>
 *   <li>Production-ready resilience</li>
 * </ul>
 *
 * <h2>Design Principles</h2>
 * <ul>
 *   <li><b>Immutability</b> - All result classes are immutable records</li>
 *   <li><b>Thread-safety</b> - Safe to share across threads</li>
 *   <li><b>Transparency</b> - Comprehensive information about evaluation outcomes</li>
 *   <li><b>Debuggability</b> - Detailed failure reasons and missing paths</li>
 * </ul>
 *
 * @see uk.codery.jspec.result.EvaluationOutcome
 * @see uk.codery.jspec.result.EvaluationResult
 * @see uk.codery.jspec.result.CompositeResult
 * @see uk.codery.jspec.result.EvaluationState
 * @see uk.codery.jspec.result.EvaluationSummary
 * @since 0.1.0
 */
package uk.codery.jspec.result;
