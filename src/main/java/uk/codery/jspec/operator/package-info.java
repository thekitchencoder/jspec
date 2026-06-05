/**
 * Operator extensibility support for custom MongoDB-style query operators.
 *
 * <p>This package provides the infrastructure for extending the library with custom
 * operators beyond the built-in set. Users can register custom operators to handle
 * domain-specific evaluation logic.
 *
 * <h2>Core Classes</h2>
 *
 * <h3>{@link uk.codery.jspec.operator.OperatorHandler}</h3>
 * <p>Functional interface for implementing custom operators.
 *
 * <pre>{@code
 * // Define a custom $length operator
 * OperatorHandler lengthOperator = (value, operand) -> {
 *     if (!(value instanceof String)) {
 *         return false; // Type mismatch
 *     }
 *     if (!(operand instanceof Number)) {
 *         return false; // Invalid operand
 *     }
 *     String str = (String) value;
 *     int expectedLength = ((Number) operand).intValue();
 *     return str.length() == expectedLength;
 * };
 * }</pre>
 *
 * <h3>{@link uk.codery.jspec.operator.OperatorRegistry}</h3>
 * <p>Thread-safe registry for managing both built-in and custom operators.
 *
 * <pre>{@code
 * // Create registry with defaults
 * OperatorRegistry registry = OperatorRegistry.withDefaults();
 *
 * // Add custom operator
 * registry.register("$length", lengthOperator);
 *
 * // Use in evaluator
 * CriterionEvaluator evaluator = new CriterionEvaluator(registry);
 * }</pre>
 *
 * <h2>Built-in Operators</h2>
 *
 * <p>A {@link uk.codery.jspec.evaluator.CriterionEvaluator} supports 23 operators in total.
 * {@link uk.codery.jspec.operator.OperatorRegistry#withDefaults()} seeds only the six
 * <em>overridable</em> comparison operators below; the {@code CriterionEvaluator} registers
 * every other operator itself (collection, advanced, string, range/date, and the
 * {@code $not}/{@code $and}/{@code $or} logical operators), because those need evaluator
 * internals. {@code CriterionEvaluator.supportedOperators()} is the canonical list.
 *
 * <h3>Comparison (6 operators — the registry defaults)</h3>
 * <ul>
 *   <li>{@code $eq} - Equality</li>
 *   <li>{@code $ne} - Not equal</li>
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
 * <h3>Advanced (4 operators)</h3>
 * <ul>
 *   <li>{@code $exists} - Field existence</li>
 *   <li>{@code $type} - Type checking</li>
 *   <li>{@code $regex} - Pattern matching</li>
 *   <li>{@code $elemMatch} - Array element matching</li>
 * </ul>
 *
 * <h3>String (3 operators)</h3>
 * <ul>
 *   <li>{@code $contains} - Substring or collection element check</li>
 *   <li>{@code $startsWith} - String prefix match</li>
 *   <li>{@code $endsWith} - String suffix match</li>
 * </ul>
 *
 * <h3>Range/Date (3 operators)</h3>
 * <ul>
 *   <li>{@code $between} - Inclusive numeric range</li>
 *   <li>{@code $dateBefore} - Date/time before</li>
 *   <li>{@code $dateAfter} - Date/time after</li>
 * </ul>
 *
 * <h2>Custom Operator Examples</h2>
 *
 * <h3>String Length Operator</h3>
 * <pre>{@code
 * registry.register("$length", (value, operand) -> {
 *     if (!(value instanceof String) || !(operand instanceof Number)) {
 *         return false;
 *     }
 *     return ((String) value).length() == ((Number) operand).intValue();
 * });
 *
 * // Use in criterion
 * Criterion criterion = Criterion.builder()
 *     .id("username-length")
 *     .field("username").operator("$length", 8)
 *     .build();
 * }</pre>
 *
 * <h3>String Starts With Operator</h3>
 * <pre>{@code
 * registry.register("$startswith", (value, operand) -> {
 *     if (!(value instanceof String) || !(operand instanceof String)) {
 *         return false;
 *     }
 *     return ((String) value).startsWith((String) operand);
 * });
 * }</pre>
 *
 * <h3>Date Range Operator</h3>
 * <pre>{@code
 * registry.register("$between", (value, operand) -> {
 *     if (!(value instanceof LocalDate) || !(operand instanceof Map)) {
 *         return false;
 *     }
 *     Map<String, Object> range = (Map<String, Object>) operand;
 *     LocalDate date = (LocalDate) value;
 *     LocalDate start = (LocalDate) range.get("start");
 *     LocalDate end = (LocalDate) range.get("end");
 *     return !date.isBefore(start) && !date.isAfter(end);
 * });
 * }</pre>
 *
 * <h2>Implementation Guidelines</h2>
 *
 * <h3>Type Safety</h3>
 * <p>Always check types before casting to prevent ClassCastException:
 * <pre>{@code
 * if (!(value instanceof String)) {
 *     return false; // Type mismatch = not matched
 * }
 * if (!(operand instanceof Pattern)) {
 *     return false; // Invalid operand = not matched
 * }
 * }</pre>
 *
 * <h3>Error Handling</h3>
 * <p>Follow graceful degradation principles:
 * <ul>
 *   <li>Type mismatches → return {@code false}</li>
 *   <li>Invalid operands → return {@code false}</li>
 *   <li>Null values → handle explicitly</li>
 *   <li>Never throw exceptions</li>
 * </ul>
 *
 * <h3>Thread Safety</h3>
 * <p>Operator handlers must be thread-safe:
 * <ul>
 *   <li>Use immutable state only</li>
 *   <li>Avoid mutable shared state</li>
 *   <li>Use thread-safe caching if needed (e.g., ConcurrentHashMap)</li>
 * </ul>
 *
 * <h3>Naming Conventions</h3>
 * <p>Follow MongoDB conventions:
 * <ul>
 *   <li>Start with {@code $} prefix</li>
 *   <li>Use lowercase names</li>
 *   <li>Use descriptive names</li>
 *   <li>Avoid collisions with built-in operators</li>
 * </ul>
 *
 * @see uk.codery.jspec.operator.OperatorHandler
 * @see uk.codery.jspec.operator.OperatorRegistry
 * @see uk.codery.jspec.evaluator.CriterionEvaluator
 * @since 0.1.0
 */
package uk.codery.jspec.operator;
