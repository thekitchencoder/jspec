package uk.codery.jspec.operator;

/**
 * Functional interface for implementing custom MongoDB-style query operators.
 *
 * <p>An operator handler evaluates a single criterion by comparing a document value
 * against an operand. The handler returns {@code true} if the criterion is satisfied,
 * {@code false} otherwise.
 *
 * <h2>Built-in Operators</h2>
 * The library provides 13 built-in operators out of the box:
 * <ul>
 *   <li><b>Comparison:</b> {@code $eq}, {@code $ne}, {@code $gt}, {@code $gte}, {@code $lt}, {@code $lte}</li>
 *   <li><b>Collection:</b> {@code $in}, {@code $nin}, {@code $all}, {@code $size}</li>
 *   <li><b>Advanced:</b> {@code $exists}, {@code $type}, {@code $regex}, {@code $elemMatch}</li>
 * </ul>
 *
 * <h2>Custom Operators</h2>
 * You can create custom operators by implementing this interface. Custom operators
 * enable domain-specific evaluation logic beyond the built-in MongoDB-style operators.
 *
 * <h3>Example: String Length Operator</h3>
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
 *
 * // Register with OperatorRegistry (future API)
 * OperatorRegistry registry = new OperatorRegistry();
 * registry.register("$length", lengthOperator);
 *
 * // Use in a criterion
 * Criterion criterion = new Criterion("username-check",
 *     Map.of("username", Map.of("$length", 8)));
 * }</pre>
 *
 * <h3>Example: Date Range Operator</h3>
 * <pre>{@code
 * // Define a custom $between operator for dates
 * OperatorHandler betweenOperator = (value, operand) -> {
 *     if (!(value instanceof LocalDate)) {
 *         return false;
 *     }
 *     if (!(operand instanceof Map)) {
 *         return false;
 *     }
 *
 *     Map<String, Object> range = (Map<String, Object>) operand;
 *     LocalDate date = (LocalDate) value;
 *     LocalDate start = (LocalDate) range.get("start");
 *     LocalDate end = (LocalDate) range.get("end");
 *
 *     return !date.isBefore(start) && !date.isAfter(end);
 * };
 *
 * // Use in a criterion
 * Criterion criterion = new Criterion("date-check",
 *     Map.of("createdAt", Map.of("$between", Map.of(
 *         "start", LocalDate.of(2024, 1, 1),
 *         "end", LocalDate.of(2024, 12, 31)
 *     ))));
 * }</pre>
 *
 * <h2>Implementation Guidelines</h2>
 *
 * <h3>Type Safety</h3>
 * Always check types before casting to prevent {@code ClassCastException}:
 * <pre>{@code
 * OperatorHandler safeOperator = (value, operand) -> {
 *     // Check value type
 *     if (!(value instanceof String)) {
 *         return false; // Type mismatch = not matched
 *     }
 *
 *     // Check operand type
 *     if (!(operand instanceof Pattern)) {
 *         return false; // Invalid operand = not matched
 *     }
 *
 *     // Safe to cast now
 *     String str = (String) value;
 *     Pattern pattern = (Pattern) operand;
 *     return pattern.matcher(str).find();
 * };
 * }</pre>
 *
 * <h3>Error Handling</h3>
 * Operators should follow graceful degradation principles:
 * <ul>
 *   <li>Type mismatches → return {@code false} (becomes NOT_MATCHED)</li>
 *   <li>Invalid operands → return {@code false} (becomes NOT_MATCHED)</li>
 *   <li>Null values → handle explicitly or return {@code false}</li>
 *   <li>Never throw exceptions from {@code evaluate()} method</li>
 * </ul>
 *
 * <pre>{@code
 * OperatorHandler resilientOperator = (value, operand) -> {
 *     try {
 *         // Your evaluation logic
 *         return someCondition;
 *     } catch (Exception e) {
 *         // Log the error (if logger available)
 *         // Return false to indicate no match
 *         return false;
 *     }
 * };
 * }</pre>
 *
 * <h3>Null Handling</h3>
 * Be explicit about null value handling:
 * <pre>{@code
 * OperatorHandler nullAwareOperator = (value, operand) -> {
 *     // Decide: should null values match?
 *     if (value == null) {
 *         return operand == null; // Or: return false;
 *     }
 *
 *     // Normal evaluation for non-null values
 *     return value.equals(operand);
 * };
 * }</pre>
 *
 * <h3>Thread Safety</h3>
 * Operator handlers must be thread-safe as they may be invoked concurrently:
 * <ul>
 *   <li>Use immutable state only</li>
 *   <li>Avoid mutable shared state</li>
 *   <li>Use thread-safe caching if needed (e.g., ConcurrentHashMap)</li>
 * </ul>
 *
 * <pre>{@code
 * // Thread-safe: no mutable state
 * OperatorHandler threadSafeOperator = (value, operand) -> {
 *     // Pure function - only uses parameters
 *     return value.equals(operand);
 * };
 *
 * // Thread-safe: immutable cache
 * class CachedOperatorHandler implements OperatorHandler {
 *     private final Map<String, Pattern> cache =
 *         new ConcurrentHashMap<>();
 *
 *     @Override
 *     public boolean evaluate(Object value, Object operand) {
 *         Pattern pattern = cache.computeIfAbsent(
 *             (String) operand,
 *             Pattern::compile
 *         );
 *         return pattern.matcher((String) value).find();
 *     }
 * }
 * }</pre>
 *
 * <h2>Operator Naming Conventions</h2>
 * Follow MongoDB conventions when naming custom operators:
 * <ul>
 *   <li>Start with {@code $} prefix (e.g., {@code $length}, {@code $between})</li>
 *   <li>Use lowercase names (e.g., {@code $startswith}, not {@code $startsWith})</li>
 *   <li>Use descriptive names that indicate the operation</li>
 *   <li>Avoid collisions with built-in operators</li>
 * </ul>
 *
 * @see uk.codery.jspec.evaluator.CriterionEvaluator
 * @see uk.codery.jspec.model.Criterion
 * @since 0.1.0
 */
@FunctionalInterface
public interface OperatorHandler {

    /**
     * Evaluates whether a document value satisfies the operator's condition.
     *
     * <p>This method is called during criterion evaluation to check if a specific
     * field value matches the operator's requirements defined by the operand.
     *
     * <p><b>Implementation Contract:</b>
     * <ul>
     *   <li>Return {@code true} if the value satisfies the condition</li>
     *   <li>Return {@code false} if the value does not satisfy the condition</li>
     *   <li>Return {@code false} for type mismatches (never throw ClassCastException)</li>
     *   <li>Return {@code false} for invalid operands (never throw exceptions)</li>
     *   <li>Handle null values explicitly</li>
     *   <li>Must be thread-safe (may be called concurrently)</li>
     * </ul>
     *
     * <h3>Examples</h3>
     *
     * <b>Equality Operator ($eq):</b>
     * <pre>{@code
     * OperatorHandler eqOperator = (value, operand) -> Objects.equals(value, operand);
     *
     * // Usage:
     * eqOperator.evaluate("active", "active") → true
     * eqOperator.evaluate("active", "inactive") → false
     * eqOperator.evaluate(null, null) → true
     * }</pre>
     *
     * <b>Greater Than Operator ($gt):</b>
     * <pre>{@code
     * OperatorHandler gtOperator = (value, operand) -> {
     *     if (!(value instanceof Number) || !(operand instanceof Number)) {
     *         return false; // Type safety
     *     }
     *     double v = ((Number) value).doubleValue();
     *     double o = ((Number) operand).doubleValue();
     *     return v > o;
     * };
     *
     * // Usage:
     * gtOperator.evaluate(25, 18) → true
     * gtOperator.evaluate(15, 18) → false
     * gtOperator.evaluate("25", 18) → false (type mismatch)
     * }</pre>
     *
     * <b>Contains Operator ($in):</b>
     * <pre>{@code
     * OperatorHandler inOperator = (value, operand) -> {
     *     if (!(operand instanceof List)) {
     *         return false; // Invalid operand
     *     }
     *     return ((List<?>) operand).contains(value);
     * };
     *
     * // Usage:
     * inOperator.evaluate("red", List.of("red", "blue", "green")) → true
     * inOperator.evaluate("yellow", List.of("red", "blue", "green")) → false
     * }</pre>
     *
     * @param value the document field value to evaluate (may be null)
     * @param operand the criterion operand to compare against (may be null)
     * @return {@code true} if the value satisfies the operator's condition,
     *         {@code false} otherwise (including type mismatches and errors)
     * @throws RuntimeException implementations should avoid throwing exceptions;
     *         return {@code false} instead to indicate no match
     */
    boolean evaluate(Object value, Object operand);
}
