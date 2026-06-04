/**
 * JSON Specification Evaluator - MongoDB-style query evaluation for Java.
 *
 * <p>This library provides a lightweight, Spring-independent framework for evaluating
 * business criteria against JSON/YAML documents using MongoDB-style query operators.
 * It supports 23 built-in query operators and can be extended with custom operators.
 *
 * <h2>Core Concepts</h2>
 *
 * <h3>Tri-State Evaluation Model</h3>
 * <p>Every criterion evaluation produces one of three states:
 * <ul>
 *   <li><b>MATCHED</b> - Criterion evaluated successfully, condition is TRUE</li>
 *   <li><b>NOT_MATCHED</b> - Criterion evaluated successfully, condition is FALSE</li>
 *   <li><b>UNDETERMINED</b> - Could not evaluate (missing data, invalid criterion, type mismatch)</li>
 * </ul>
 *
 * <p>This tri-state model enables graceful degradation - one bad criterion never stops
 * the overall specification evaluation.
 *
 * <h3>Main Entry Points</h3>
 * <ul>
 *   <li>{@link uk.codery.jspec.evaluator.SpecificationEvaluator} - Evaluate complete specifications</li>
 *   <li>{@link uk.codery.jspec.evaluator.CriterionEvaluator} - Evaluate individual criteria</li>
 *   <li>{@link uk.codery.jspec.model.Criterion} - Define evaluation criteria</li>
 *   <li>{@link uk.codery.jspec.model.Specification} - Define complete specifications</li>
 * </ul>
 *
 * <h2>Quick Start</h2>
 *
 * <h3>Basic Evaluation</h3>
 * <pre>{@code
 * // Create evaluator
 * SpecificationEvaluator evaluator = new SpecificationEvaluator();
 *
 * // Define document
 * Map<String, Object> document = Map.of(
 *     "age", 25,
 *     "status", "active"
 * );
 *
 * // Define criterion
 * Criterion criterion = Criterion.builder()
 *     .id("age-check")
 *     .field("age").gte(18)
 *     .build();
 *
 * // Create specification
 * Specification spec = Specification.builder()
 *     .id("eligibility")
 *     .addCriterion(criterion)
 *     .build();
 *
 * // Evaluate
 * EvaluationOutcome outcome = evaluator.evaluate(document, spec);
 *
 * // Check results
 * if (outcome.summary().fullyDetermined()) {
 *     System.out.println("Matched: " + outcome.summary().matched());
 * }
 * }</pre>
 *
 * <h3>Using Criteria Groups (AND/OR Logic)</h3>
 * <pre>{@code
 * CriteriaGroup group = CriteriaGroup.builder()
 *     .id("employment-checks")
 *     .and()  // All must match
 *     .criteria(employmentCheck, incomeCheck)
 *     .build();
 *
 * Specification spec = Specification.builder()
 *     .id("loan-eligibility")
 *     .addCriterion(ageCheck)
 *     .addGroup(group)
 *     .build();
 * }</pre>
 *
 * <h3>Custom Operators</h3>
 * <pre>{@code
 * OperatorRegistry registry = OperatorRegistry.withDefaults();
 * registry.register("$length", (value, operand) -> {
 *     return value instanceof String &&
 *            ((String) value).length() == ((Number) operand).intValue();
 * });
 *
 * CriterionEvaluator criterionEvaluator = new CriterionEvaluator(registry);
 * SpecificationEvaluator evaluator = new SpecificationEvaluator(criterionEvaluator);
 * }</pre>
 *
 * <h2>Supported Operators</h2>
 *
 * <h3>Comparison (6 operators)</h3>
 * <ul>
 *   <li>{@code $eq} - Equal to</li>
 *   <li>{@code $ne} - Not equal to</li>
 *   <li>{@code $gt} - Greater than</li>
 *   <li>{@code $gte} - Greater than or equal</li>
 *   <li>{@code $lt} - Less than</li>
 *   <li>{@code $lte} - Less than or equal</li>
 * </ul>
 *
 * <h3>Collection (4 operators)</h3>
 * <ul>
 *   <li>{@code $in} - Value in array</li>
 *   <li>{@code $nin} - Value not in array</li>
 *   <li>{@code $all} - Array contains all values</li>
 *   <li>{@code $size} - Array size equals</li>
 * </ul>
 *
 * <h3>Advanced (3 operators)</h3>
 * <ul>
 *   <li>{@code $exists} - Field existence check</li>
 *   <li>{@code $type} - Type checking</li>
 *   <li>{@code $regex} - Regular expression match</li>
 *   <li>{@code $elemMatch} - Array element matching</li>
 * </ul>
 *
 * <h2>Package Organization</h2>
 * <ul>
 *   <li>{@link uk.codery.jspec.model} - Domain models (Criterion, Specification, Junction)</li>
 *   <li>{@link uk.codery.jspec.evaluator} - Evaluation engine</li>
 *   <li>{@link uk.codery.jspec.result} - Result types (EvaluationState, EvaluationResult, etc.)</li>
 *   <li>{@link uk.codery.jspec.operator} - Operator extensibility (OperatorHandler, OperatorRegistry)</li>
 *   <li>{@link uk.codery.jspec.builder} - Fluent builder APIs</li>
 * </ul>
 *
 * <h2>Key Features</h2>
 * <ul>
 *   <li><b>Zero Spring coupling</b> - Works standalone or with Spring</li>
 *   <li><b>Thread-safe</b> - Safe for concurrent evaluation</li>
 *   <li><b>Parallel evaluation</b> - Criteria evaluated concurrently</li>
 *   <li><b>Graceful degradation</b> - Partial failures don't stop evaluation</li>
 *   <li><b>Performance optimized</b> - Regex caching, optimized algorithms</li>
 *   <li><b>Extensible</b> - Add custom operators via OperatorRegistry</li>
 *   <li><b>Modern Java 21</b> - Uses records, pattern matching, switch expressions</li>
 * </ul>
 *
 * @see uk.codery.jspec.evaluator.SpecificationEvaluator
 * @see uk.codery.jspec.evaluator.CriterionEvaluator
 * @see uk.codery.jspec.model.Criterion
 * @see uk.codery.jspec.model.Specification
 * @since 0.1.0
 */
package uk.codery.jspec;
