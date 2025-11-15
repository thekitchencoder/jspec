package uk.codery.jspec.operator;

import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe registry for MongoDB-style query operators.
 *
 * <p>The {@code OperatorRegistry} manages both built-in and custom operators,
 * providing a centralized mechanism for operator lookup during criterion evaluation.
 * All operations are thread-safe and can be called concurrently from multiple threads.
 *
 * <h2>Built-in Operators</h2>
 * The registry comes pre-configured with 13 MongoDB-style operators:
 * <ul>
 *   <li><b>Comparison:</b> {@code $eq}, {@code $ne}, {@code $gt}, {@code $gte}, {@code $lt}, {@code $lte}</li>
 *   <li><b>Collection:</b> {@code $in}, {@code $nin}, {@code $all}, {@code $size}</li>
 *   <li><b>Advanced:</b> {@code $exists}, {@code $type}, {@code $regex}, {@code $elemMatch}</li>
 * </ul>
 *
 * <h2>Thread Safety</h2>
 * All methods in this class are thread-safe:
 * <ul>
 *   <li>Uses {@link ConcurrentHashMap} for operator storage</li>
 *   <li>Safe to register operators from multiple threads</li>
 *   <li>Safe to retrieve operators during concurrent evaluations</li>
 *   <li>Immutable views returned for operator collections</li>
 * </ul>
 *
 * <h2>Usage Examples</h2>
 *
 * <h3>Using Default Registry</h3>
 * <pre>{@code
 * // Create registry with built-in operators
 * OperatorRegistry registry = OperatorRegistry.withDefaults();
 *
 * // Use in CriterionEvaluator
 * CriterionEvaluator evaluator = new CriterionEvaluator(registry);
 * }</pre>
 *
 * <h3>Custom Registry with Additional Operators</h3>
 * <pre>{@code
 * // Start with defaults
 * OperatorRegistry registry = OperatorRegistry.withDefaults();
 *
 * // Add custom $length operator
 * registry.register("$length", (value, operand) -> {
 *     if (!(value instanceof String) || !(operand instanceof Number)) {
 *         return false;
 *     }
 *     return ((String) value).length() == ((Number) operand).intValue();
 * });
 *
 * // Add custom $startswith operator
 * registry.register("$startswith", (value, operand) -> {
 *     if (!(value instanceof String) || !(operand instanceof String)) {
 *         return false;
 *     }
 *     return ((String) value).startsWith((String) operand);
 * });
 *
 * // Use the enhanced registry
 * CriterionEvaluator evaluator = new CriterionEvaluator(registry);
 * }</pre>
 *
 * <h3>Empty Registry (Only Custom Operators)</h3>
 * <pre>{@code
 * // Create empty registry
 * OperatorRegistry registry = new OperatorRegistry();
 *
 * // Register only the operators you need
 * registry.register("$eq", Objects::equals);
 * registry.register("$custom", customHandler);
 *
 * CriterionEvaluator evaluator = new CriterionEvaluator(registry);
 * }</pre>
 *
 * <h3>Checking Available Operators</h3>
 * <pre>{@code
 * OperatorRegistry registry = OperatorRegistry.withDefaults();
 *
 * // Get all available operator names
 * Set<String> operators = registry.availableOperators();
 * // Returns: [$eq, $ne, $gt, $gte, $lt, $lte, $in, $nin, $all, $size, $exists, $type, $regex, $elemMatch]
 *
 * // Check if specific operator exists
 * if (registry.contains("$regex")) {
 *     System.out.println("Regex operator is available");
 * }
 *
 * // Get count of registered operators
 * int count = registry.size();
 * System.out.println("Total operators: " + count);
 * }</pre>
 *
 * <h3>Overriding Built-in Operators</h3>
 * <pre>{@code
 * OperatorRegistry registry = OperatorRegistry.withDefaults();
 *
 * // Override the $eq operator with custom behavior
 * registry.register("$eq", (value, operand) -> {
 *     // Custom equality logic (e.g., case-insensitive string comparison)
 *     if (value instanceof String && operand instanceof String) {
 *         return ((String) value).equalsIgnoreCase((String) operand);
 *     }
 *     return Objects.equals(value, operand);
 * });
 * }</pre>
 *
 * <h2>Design Principles</h2>
 * <ul>
 *   <li><b>Immutability:</b> Returned collections are unmodifiable views</li>
 *   <li><b>Thread-safety:</b> Safe concurrent access from multiple threads</li>
 *   <li><b>Extensibility:</b> Easy to add custom operators without modifying library code</li>
 *   <li><b>Backward compatibility:</b> Built-in operators always available via {@code withDefaults()}</li>
 * </ul>
 *
 * @see OperatorHandler
 * @see uk.codery.jspec.evaluator.CriterionEvaluator
 * @since 0.1.0
 */
@Slf4j
public class OperatorRegistry {

    /**
     * Thread-safe map of operator name → handler.
     * Uses ConcurrentHashMap for lock-free concurrent reads and writes.
     */
    private final Map<String, OperatorHandler> operators = new ConcurrentHashMap<>();

    /**
     * Creates an empty operator registry with no operators registered.
     *
     * <p>Use this constructor when you want to register only custom operators
     * without the built-in MongoDB-style operators.
     *
     * <p><b>Note:</b> Most users should use {@link #withDefaults()} instead
     * to get the standard set of built-in operators.
     *
     * @see #withDefaults()
     */
    public OperatorRegistry() {
        // Empty registry - no operators registered
        log.debug("Created empty OperatorRegistry");
    }

    /**
     * Creates an operator registry pre-populated with built-in operators.
     *
     * <p>This factory method returns a new registry containing all 13 built-in
     * MongoDB-style operators. This is the recommended way to create a registry
     * for most use cases.
     *
     * <h3>Built-in Operators Included:</h3>
     * <ul>
     *   <li><b>Comparison:</b> $eq, $ne, $gt, $gte, $lt, $lte</li>
     *   <li><b>Collection:</b> $in, $nin, $all, $size</li>
     *   <li><b>Advanced:</b> $exists, $type, $regex, $elemMatch</li>
     * </ul>
     *
     * <h3>Example:</h3>
     * <pre>{@code
     * // Create registry with all built-in operators
     * OperatorRegistry registry = OperatorRegistry.withDefaults();
     *
     * // Optionally add custom operators
     * registry.register("$length", lengthHandler);
     *
     * // Use in evaluator
     * CriterionEvaluator evaluator = new CriterionEvaluator(registry);
     * }</pre>
     *
     * @return a new {@code OperatorRegistry} with all built-in operators registered
     * @see #OperatorRegistry()
     */
    public static OperatorRegistry withDefaults() {
        OperatorRegistry registry = new OperatorRegistry();
        registry.registerDefaultOperators();
        log.debug("Created OperatorRegistry with {} default operators", registry.size());
        return registry;
    }

    /**
     * Registers a custom operator handler with the given name.
     *
     * <p>If an operator with the same name already exists, it will be overridden
     * with the new handler. This allows customization of built-in operators.
     *
     * <p>Thread-safe: Can be called concurrently from multiple threads.
     *
     * <h3>Example:</h3>
     * <pre>{@code
     * OperatorRegistry registry = OperatorRegistry.withDefaults();
     *
     * // Register custom $length operator
     * registry.register("$length", (value, operand) -> {
     *     if (!(value instanceof String) || !(operand instanceof Number)) {
     *         return false;
     *     }
     *     return ((String) value).length() == ((Number) operand).intValue();
     * });
     * }</pre>
     *
     * @param name the operator name (should start with '$' by convention)
     * @param handler the operator handler implementation
     * @throws IllegalArgumentException if name or handler is null
     * @see #unregister(String)
     */
    public void register(String name, OperatorHandler handler) {
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("Operator name cannot be null or empty");
        }
        if (handler == null) {
            throw new IllegalArgumentException("Operator handler cannot be null");
        }

        OperatorHandler previous = operators.put(name, handler);
        if (previous != null) {
            log.info("Overriding operator '{}' with new handler", name);
        } else {
            log.debug("Registered new operator '{}'", name);
        }
    }

    /**
     * Removes an operator from the registry.
     *
     * <p>Thread-safe: Can be called concurrently from multiple threads.
     *
     * <h3>Example:</h3>
     * <pre>{@code
     * OperatorRegistry registry = OperatorRegistry.withDefaults();
     *
     * // Remove the $regex operator if not needed
     * registry.unregister("$regex");
     * }</pre>
     *
     * @param name the operator name to remove
     * @return {@code true} if the operator was removed, {@code false} if it didn't exist
     */
    public boolean unregister(String name) {
        boolean removed = operators.remove(name) != null;
        if (removed) {
            log.debug("Unregistered operator '{}'", name);
        }
        return removed;
    }

    /**
     * Retrieves an operator handler by name.
     *
     * <p>Thread-safe: Can be called concurrently from multiple threads.
     *
     * <p><b>Note:</b> Returns {@code null} if the operator is not found.
     * Use {@link #contains(String)} to check if an operator exists.
     *
     * <h3>Example:</h3>
     * <pre>{@code
     * OperatorRegistry registry = OperatorRegistry.withDefaults();
     *
     * OperatorHandler handler = registry.get("$eq");
     * if (handler != null) {
     *     boolean result = handler.evaluate(value, operand);
     * }
     * }</pre>
     *
     * @param name the operator name
     * @return the operator handler, or {@code null} if not found
     * @see #contains(String)
     */
    public OperatorHandler get(String name) {
        return operators.get(name);
    }

    /**
     * Checks if an operator is registered.
     *
     * <p>Thread-safe: Can be called concurrently from multiple threads.
     *
     * <h3>Example:</h3>
     * <pre>{@code
     * OperatorRegistry registry = OperatorRegistry.withDefaults();
     *
     * if (registry.contains("$regex")) {
     *     System.out.println("Regex operator is available");
     * }
     * }</pre>
     *
     * @param name the operator name to check
     * @return {@code true} if the operator exists, {@code false} otherwise
     * @see #get(String)
     */
    public boolean contains(String name) {
        return operators.containsKey(name);
    }

    /**
     * Returns the names of all registered operators.
     *
     * <p>Thread-safe: Can be called concurrently from multiple threads.
     *
     * <p>The returned set is an unmodifiable snapshot of the current operators.
     * Changes to the registry after this call will not be reflected in the returned set.
     *
     * <h3>Example:</h3>
     * <pre>{@code
     * OperatorRegistry registry = OperatorRegistry.withDefaults();
     *
     * Set<String> operators = registry.availableOperators();
     * System.out.println("Available operators: " + operators);
     * // Output: Available operators: [$eq, $ne, $gt, $gte, $lt, $lte, $in, $nin, ...]
     * }</pre>
     *
     * @return unmodifiable set of operator names
     */
    public Set<String> availableOperators() {
        return Collections.unmodifiableSet(new HashSet<>(operators.keySet()));
    }

    /**
     * Returns the number of registered operators.
     *
     * <p>Thread-safe: Can be called concurrently from multiple threads.
     *
     * <h3>Example:</h3>
     * <pre>{@code
     * OperatorRegistry registry = OperatorRegistry.withDefaults();
     * System.out.println("Total operators: " + registry.size());
     * // Output: Total operators: 14
     * }</pre>
     *
     * @return the count of registered operators
     */
    public int size() {
        return operators.size();
    }

    /**
     * Checks if the registry is empty (no operators registered).
     *
     * <p>Thread-safe: Can be called concurrently from multiple threads.
     *
     * @return {@code true} if no operators are registered, {@code false} otherwise
     */
    public boolean isEmpty() {
        return operators.isEmpty();
    }

    /**
     * Returns an unmodifiable map of all registered operators.
     *
     * <p>Thread-safe: Can be called concurrently from multiple threads.
     *
     * <p>The returned map is an unmodifiable snapshot. Changes to the registry
     * after this call will not be reflected in the returned map.
     *
     * <p><b>Note:</b> This method is primarily for internal use by
     * {@code CriterionEvaluator}. Most users should use {@link #availableOperators()}
     * or {@link #get(String)} instead.
     *
     * @return unmodifiable map of operator name → handler
     */
    public Map<String, OperatorHandler> getAll() {
        return Collections.unmodifiableMap(new HashMap<>(operators));
    }

    /**
     * Registers all built-in MongoDB-style operators.
     *
     * <p>This method is called automatically by {@link #withDefaults()}.
     *
     * <p><b>Built-in Operators:</b>
     * <ul>
     *   <li>{@code $eq} - Equality comparison</li>
     *   <li>{@code $ne} - Not equal comparison</li>
     *   <li>{@code $gt} - Greater than comparison</li>
     *   <li>{@code $gte} - Greater than or equal comparison</li>
     *   <li>{@code $lt} - Less than comparison</li>
     *   <li>{@code $lte} - Less than or equal comparison</li>
     *   <li>{@code $in} - Value in array</li>
     *   <li>{@code $nin} - Value not in array</li>
     *   <li>{@code $all} - Array contains all values</li>
     *   <li>{@code $size} - Array size match</li>
     *   <li>{@code $exists} - Field existence check</li>
     *   <li>{@code $type} - Type check</li>
     *   <li>{@code $regex} - Regular expression match</li>
     *   <li>{@code $elemMatch} - Array element match</li>
     * </ul>
     */
    private void registerDefaultOperators() {
        // Comparison operators
        register("$eq", Objects::equals);
        register("$ne", (val, operand) -> !Objects.equals(val, operand));
        register("$gt", this::greaterThan);
        register("$gte", this::greaterThanOrEqual);
        register("$lt", this::lessThan);
        register("$lte", this::lessThanOrEqual);

        // Collection operators - placeholders (actual implementations in CriterionEvaluator)
        // These will be overridden by CriterionEvaluator with its internal implementations
        // that have access to evaluate() and other internal methods
        register("$in", this::evaluateIn);
        register("$nin", this::evaluateNotIn);
        register("$all", this::evaluateAll);
        register("$size", this::evaluateSize);

        // Advanced operators - placeholders
        register("$exists", this::evaluateExists);
        register("$type", this::evaluateType);
        register("$regex", this::evaluateRegex);
        register("$elemMatch", this::evaluateElemMatch);

        log.debug("Registered {} default operators", operators.size());
    }

    // Comparison operator implementations

    @SuppressWarnings({"rawtypes", "unchecked"})
    private boolean greaterThan(Object a, Object b) {
        if (a instanceof Number && b instanceof Number) {
            double aNum = ((Number) a).doubleValue();
            double bNum = ((Number) b).doubleValue();
            return aNum > bNum;
        }
        if (a instanceof Comparable && b instanceof Comparable) {
            try {
                return ((Comparable) a).compareTo(b) > 0;
            } catch (ClassCastException e) {
                return false;
            }
        }
        return false;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private boolean greaterThanOrEqual(Object a, Object b) {
        if (a instanceof Number && b instanceof Number) {
            double aNum = ((Number) a).doubleValue();
            double bNum = ((Number) b).doubleValue();
            return aNum >= bNum;
        }
        if (a instanceof Comparable && b instanceof Comparable) {
            try {
                return ((Comparable) a).compareTo(b) >= 0;
            } catch (ClassCastException e) {
                return false;
            }
        }
        return false;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private boolean lessThan(Object a, Object b) {
        if (a instanceof Number && b instanceof Number) {
            double aNum = ((Number) a).doubleValue();
            double bNum = ((Number) b).doubleValue();
            return aNum < bNum;
        }
        if (a instanceof Comparable && b instanceof Comparable) {
            try {
                return ((Comparable) a).compareTo(b) < 0;
            } catch (ClassCastException e) {
                return false;
            }
        }
        return false;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private boolean lessThanOrEqual(Object a, Object b) {
        if (a instanceof Number && b instanceof Number) {
            double aNum = ((Number) a).doubleValue();
            double bNum = ((Number) b).doubleValue();
            return aNum <= bNum;
        }
        if (a instanceof Comparable && b instanceof Comparable) {
            try {
                return ((Comparable) a).compareTo(b) <= 0;
            } catch (ClassCastException e) {
                return false;
            }
        }
        return false;
    }

    // Collection operator implementations

    private boolean evaluateIn(Object val, Object operand) {
        if (!(operand instanceof List<?> list)) {
            return false;
        }
        if (val instanceof List<?> valList) {
            for (Object item : valList) {
                if (list.contains(item)) {
                    return true;
                }
            }
            return false;
        }
        return list.contains(val);
    }

    private boolean evaluateNotIn(Object val, Object operand) {
        if (!(operand instanceof List<?> list)) {
            return false;
        }
        if (val instanceof List<?> valList) {
            for (Object item : valList) {
                if (list.contains(item)) {
                    return false;
                }
            }
            return true;
        }
        return !list.contains(val);
    }

    private boolean evaluateAll(Object val, Object operand) {
        if (!(val instanceof List<?> valList)) {
            return false;
        }
        if (!(operand instanceof List<?> queryList)) {
            return false;
        }
        return new HashSet<>(valList).containsAll(queryList);
    }

    private boolean evaluateSize(Object val, Object operand) {
        if (!(val instanceof List<?> list)) {
            return false;
        }
        if (!(operand instanceof Number number)) {
            return false;
        }
        return list.size() == number.intValue();
    }

    // Advanced operator implementations

    private boolean evaluateExists(Object val, Object operand) {
        if (!(operand instanceof Boolean query)) {
            return false;
        }
        return query == (val != null);
    }

    private boolean evaluateType(Object val, Object operand) {
        String type = switch (val) {
            case null -> "null";
            case List<?> ignored -> "array";
            case String ignored -> "string";
            case Number ignored -> "number";
            case Boolean ignored -> "boolean";
            case Map<?,?> ignored -> "object";
            default -> val.getClass().getSimpleName().toLowerCase();
        };
        return type.equals(operand);
    }

    private boolean evaluateRegex(Object val, Object operand) {
        if (!(operand instanceof String pattern)) {
            return false;
        }
        try {
            return java.util.regex.Pattern.compile(pattern).matcher(String.valueOf(val)).find();
        } catch (Exception e) {
            return false;
        }
    }

    private boolean evaluateElemMatch(Object val, Object operand) {
        // This is a simplified implementation
        // The actual CriterionEvaluator will override this with its full implementation
        if (!(val instanceof List<?> list)) {
            return false;
        }
        if (!(operand instanceof Map<?, ?>)) {
            return false;
        }
        // Simplified: just check if list is not empty
        return !list.isEmpty();
    }
}
