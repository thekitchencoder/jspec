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
 * <h2>Default Operators</h2>
 * {@link #withDefaults()} seeds only the six <em>overridable</em> comparison/equality
 * operators:
 * <ul>
 *   <li><b>Comparison (6):</b> {@code $eq}, {@code $ne}, {@code $gt}, {@code $gte}, {@code $lt}, {@code $lte}</li>
 * </ul>
 *
 * <p>These are the operators a {@link uk.codery.jspec.evaluator.CriterionEvaluator} does not
 * replace, so a custom handler registered here for one of them takes effect. The remaining
 * built-in operators (collection, advanced, string, range/date, and the {@code $and}/{@code $or}/
 * {@code $not} logical operators) are owned by {@code CriterionEvaluator} and registered there,
 * not in this registry — they need evaluator internals (recursion, the regex cache, rich date
 * parsing). The full set of operators supported during evaluation is reported by
 * {@link uk.codery.jspec.evaluator.CriterionEvaluator#supportedOperators()}. This registry's
 * job is to (a) seed those overridable comparison defaults and (b) let callers register their
 * own custom operators.
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
 * // Returns the 6 comparison defaults: [$eq, $ne, $gt, $gte, $lt, $lte]
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
     * Creates an operator registry seeded with the six overridable comparison defaults
     * ({@code $eq}, {@code $ne}, {@code $gt}, {@code $gte}, {@code $lt}, {@code $lte}).
     *
     * <p>This is the recommended starting point when backing a
     * {@link uk.codery.jspec.evaluator.CriterionEvaluator}: the evaluator registers all other
     * built-in operators itself, so the resulting evaluator supports the full operator set
     * regardless of which constructor is used. The registry deliberately does <em>not</em>
     * carry the collection/advanced/string/date operators — those are evaluator-owned (see the
     * class documentation and {@code CriterionEvaluator.supportedOperators()}).
     *
     * <h3>Example:</h3>
     * <pre>{@code
     * // Seed the overridable comparison defaults, then add custom operators
     * OperatorRegistry registry = OperatorRegistry.withDefaults();
     * registry.register("$length", lengthHandler);
     *
     * // The evaluator adds its built-in operators on top of the registry's defaults
     * CriterionEvaluator evaluator = new CriterionEvaluator(registry);
     * }</pre>
     *
     * @return a new {@code OperatorRegistry} seeded with the six comparison defaults
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
     * // Output: Total operators: 6
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
     * Registers the default <em>overridable</em> comparison operators.
     *
     * <p>This method is called automatically by {@link #withDefaults()}.
     *
     * <p>The registry intentionally seeds only the six comparison/equality operators. These
     * are the operators a {@link uk.codery.jspec.evaluator.CriterionEvaluator} leaves untouched,
     * so a custom handler registered for one of them via this registry takes effect (see
     * {@code CriterionEvaluatorCustomOperatorTest}). Every other built-in operator
     * (collection, advanced, string, range/date, and the logical combinators) is owned and
     * registered by {@code CriterionEvaluator} itself — those implementations require evaluator
     * internals (recursion, the regex cache, rich date parsing) or simply live there. The
     * registry does not carry shadow copies of them.
     *
     * <p><b>Default operators (6):</b>
     * <ul>
     *   <li>{@code $eq} - Equality comparison</li>
     *   <li>{@code $ne} - Not equal comparison</li>
     *   <li>{@code $gt} - Greater than comparison</li>
     *   <li>{@code $gte} - Greater than or equal comparison</li>
     *   <li>{@code $lt} - Less than comparison</li>
     *   <li>{@code $lte} - Less than or equal comparison</li>
     * </ul>
     */
    private void registerDefaultOperators() {
        register("$eq", Objects::equals);
        register("$ne", (val, operand) -> !Objects.equals(val, operand));
        register("$gt", this::greaterThan);
        register("$gte", this::greaterThanOrEqual);
        register("$lt", this::lessThan);
        register("$lte", this::lessThanOrEqual);

        log.debug("Registered {} default comparison operators", operators.size());
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

}
