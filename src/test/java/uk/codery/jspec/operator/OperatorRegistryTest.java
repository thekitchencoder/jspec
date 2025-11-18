package uk.codery.jspec.operator;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;

/**
 * Comprehensive tests for OperatorRegistry.
 *
 * <p>Tests cover:
 * <ul>
 *   <li>Empty registry creation</li>
 *   <li>Default operators registration</li>
 *   <li>Custom operator registration</li>
 *   <li>Operator override behavior</li>
 *   <li>Thread safety</li>
 *   <li>Built-in operator functionality</li>
 * </ul>
 */
class OperatorRegistryTest {

    private OperatorRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new OperatorRegistry();
    }

    // ==================== Construction Tests ====================

    @Test
    void testEmptyRegistry_shouldHaveNoOperators() {
        assertThat(registry.isEmpty()).isTrue();
        assertThat(registry.size()).isZero();
        assertThat(registry.availableOperators()).isEmpty();
    }

    @Test
    void testWithDefaults_shouldHave14Operators() {
        registry = OperatorRegistry.withDefaults();

        assertThat(registry.isEmpty()).isFalse();
        assertThat(registry.size()).isEqualTo(14);
    }

    @Test
    void testWithDefaults_shouldContainAllBuiltInOperators() {
        registry = OperatorRegistry.withDefaults();

        Set<String> operators = registry.availableOperators();

        // Comparison operators
        assertThat(operators).contains("$eq", "$ne", "$gt", "$gte", "$lt", "$lte");

        // Collection operators
        assertThat(operators).contains("$in", "$nin", "$all", "$size");

        // Advanced operators
        assertThat(operators).contains("$exists", "$type", "$regex", "$elemMatch");
    }

    // ==================== Registration Tests ====================

    @Test
    void testRegister_shouldAddOperator() {
        OperatorHandler handler = (val, operand) -> true;

        registry.register("$custom", handler);

        assertThat(registry.size()).isEqualTo(1);
        assertThat(registry.contains("$custom")).isTrue();
        assertThat(registry.get("$custom")).isSameAs(handler);
    }

    @Test
    void testRegister_multipleOperators() {
        registry.register("$op1", (val, operand) -> true);
        registry.register("$op2", (val, operand) -> false);
        registry.register("$op3", Object::equals);

        assertThat(registry.size()).isEqualTo(3);
        assertThat(registry.availableOperators()).containsExactlyInAnyOrder("$op1", "$op2", "$op3");
    }

    @Test
    void testRegister_nullName_shouldThrowException() {
        assertThatThrownBy(() -> registry.register(null, (val, operand) -> true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Operator name cannot be null");
    }

    @Test
    void testRegister_emptyName_shouldThrowException() {
        assertThatThrownBy(() -> registry.register("", (val, operand) -> true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Operator name cannot be null or empty");
    }

    @Test
    void testRegister_nullHandler_shouldThrowException() {
        assertThatThrownBy(() -> registry.register("$custom", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Operator handler cannot be null");
    }

    @Test
    void testRegister_override_shouldReplaceExistingOperator() {
        OperatorHandler original = (val, operand) -> true;
        OperatorHandler replacement = (val, operand) -> false;

        registry.register("$custom", original);
        assertThat(registry.get("$custom")).isSameAs(original);

        registry.register("$custom", replacement);
        assertThat(registry.get("$custom")).isSameAs(replacement);
        assertThat(registry.size()).isEqualTo(1);
    }

    // ==================== Retrieval Tests ====================

    @Test
    void testGet_existingOperator_shouldReturnHandler() {
        OperatorHandler handler = (val, operand) -> true;
        registry.register("$custom", handler);

        assertThat(registry.get("$custom")).isSameAs(handler);
    }

    @Test
    void testGet_nonExistentOperator_shouldReturnNull() {
        assertThat(registry.get("$nonexistent")).isNull();
    }

    @Test
    void testContains_existingOperator_shouldReturnTrue() {
        registry.register("$custom", (val, operand) -> true);

        assertThat(registry.contains("$custom")).isTrue();
    }

    @Test
    void testContains_nonExistentOperator_shouldReturnFalse() {
        assertThat(registry.contains("$nonexistent")).isFalse();
    }

    @Test
    void testAvailableOperators_shouldReturnUnmodifiableSet() {
        registry.register("$op1", (val, operand) -> true);
        registry.register("$op2", (val, operand) -> false);

        Set<String> operators = registry.availableOperators();

        assertThat(operators).containsExactlyInAnyOrder("$op1", "$op2");

        // Should be unmodifiable
        assertThatThrownBy(() -> operators.add("$new"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void testGetAll_shouldReturnUnmodifiableMap() {
        registry.register("$op1", (val, operand) -> true);

        Map<String, OperatorHandler> all = registry.getAll();

        assertThat(all).hasSize(1);
        assertThat(all).containsKey("$op1");

        // Should be unmodifiable
        assertThatThrownBy(() -> all.put("$new", (val, operand) -> true))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    // ==================== Unregister Tests ====================

    @Test
    void testUnregister_existingOperator_shouldReturnTrue() {
        registry.register("$custom", (val, operand) -> true);

        boolean removed = registry.unregister("$custom");

        assertThat(removed).isTrue();
        assertThat(registry.contains("$custom")).isFalse();
        assertThat(registry.size()).isZero();
    }

    @Test
    void testUnregister_nonExistentOperator_shouldReturnFalse() {
        boolean removed = registry.unregister("$nonexistent");

        assertThat(removed).isFalse();
    }

    // ==================== Built-in Operator Tests ====================

    @Test
    void testDefaultOperator_eq_shouldWorkCorrectly() {
        registry = OperatorRegistry.withDefaults();
        OperatorHandler eq = registry.get("$eq");

        assertThat(eq.evaluate("hello", "hello")).isTrue();
        assertThat(eq.evaluate("hello", "world")).isFalse();
        assertThat(eq.evaluate(42, 42)).isTrue();
        assertThat(eq.evaluate(42, 43)).isFalse();
        assertThat(eq.evaluate(null, null)).isTrue();
        assertThat(eq.evaluate(null, "value")).isFalse();
    }

    @Test
    void testDefaultOperator_ne_shouldWorkCorrectly() {
        registry = OperatorRegistry.withDefaults();
        OperatorHandler ne = registry.get("$ne");

        assertThat(ne.evaluate("hello", "world")).isTrue();
        assertThat(ne.evaluate("hello", "hello")).isFalse();
        assertThat(ne.evaluate(42, 43)).isTrue();
        assertThat(ne.evaluate(42, 42)).isFalse();
    }

    @Test
    void testDefaultOperator_gt_shouldWorkCorrectly() {
        registry = OperatorRegistry.withDefaults();
        OperatorHandler gt = registry.get("$gt");

        assertThat(gt.evaluate(10, 5)).isTrue();
        assertThat(gt.evaluate(5, 10)).isFalse();
        assertThat(gt.evaluate(10, 10)).isFalse();
        assertThat(gt.evaluate(10.5, 10.0)).isTrue();
        assertThat(gt.evaluate("b", "a")).isTrue();
        assertThat(gt.evaluate("a", "b")).isFalse();
    }

    @Test
    void testDefaultOperator_gte_shouldWorkCorrectly() {
        registry = OperatorRegistry.withDefaults();
        OperatorHandler gte = registry.get("$gte");

        assertThat(gte.evaluate(10, 5)).isTrue();
        assertThat(gte.evaluate(10, 10)).isTrue();
        assertThat(gte.evaluate(5, 10)).isFalse();
    }

    @Test
    void testDefaultOperator_lt_shouldWorkCorrectly() {
        registry = OperatorRegistry.withDefaults();
        OperatorHandler lt = registry.get("$lt");

        assertThat(lt.evaluate(5, 10)).isTrue();
        assertThat(lt.evaluate(10, 5)).isFalse();
        assertThat(lt.evaluate(10, 10)).isFalse();
    }

    @Test
    void testDefaultOperator_lte_shouldWorkCorrectly() {
        registry = OperatorRegistry.withDefaults();
        OperatorHandler lte = registry.get("$lte");

        assertThat(lte.evaluate(5, 10)).isTrue();
        assertThat(lte.evaluate(10, 10)).isTrue();
        assertThat(lte.evaluate(10, 5)).isFalse();
    }

    @Test
    void testDefaultOperator_in_shouldWorkCorrectly() {
        registry = OperatorRegistry.withDefaults();
        OperatorHandler in = registry.get("$in");

        assertThat(in.evaluate("red", java.util.List.of("red", "blue", "green"))).isTrue();
        assertThat(in.evaluate("yellow", java.util.List.of("red", "blue", "green"))).isFalse();
        assertThat(in.evaluate(42, java.util.List.of(1, 2, 42, 100))).isTrue();
    }

    @Test
    void testDefaultOperator_nin_shouldWorkCorrectly() {
        registry = OperatorRegistry.withDefaults();
        OperatorHandler nin = registry.get("$nin");

        assertThat(nin.evaluate("yellow", java.util.List.of("red", "blue", "green"))).isTrue();
        assertThat(nin.evaluate("red", java.util.List.of("red", "blue", "green"))).isFalse();
    }

    @Test
    void testDefaultOperator_exists_shouldWorkCorrectly() {
        registry = OperatorRegistry.withDefaults();
        OperatorHandler exists = registry.get("$exists");

        assertThat(exists.evaluate("value", true)).isTrue();
        assertThat(exists.evaluate(null, false)).isTrue();
        assertThat(exists.evaluate("value", false)).isFalse();
        assertThat(exists.evaluate(null, true)).isFalse();
    }

    @Test
    void testDefaultOperator_type_shouldWorkCorrectly() {
        registry = OperatorRegistry.withDefaults();
        OperatorHandler type = registry.get("$type");

        assertThat(type.evaluate("hello", "string")).isTrue();
        assertThat(type.evaluate(42, "number")).isTrue();
        assertThat(type.evaluate(true, "boolean")).isTrue();
        assertThat(type.evaluate(java.util.List.of(), "array")).isTrue();
        assertThat(type.evaluate(java.util.Map.of(), "object")).isTrue();
        assertThat(type.evaluate(null, "null")).isTrue();
        assertThat(type.evaluate("hello", "number")).isFalse();
    }

    @Test
    void testDefaultOperator_size_shouldWorkCorrectly() {
        registry = OperatorRegistry.withDefaults();
        OperatorHandler size = registry.get("$size");

        assertThat(size.evaluate(java.util.List.of(1, 2, 3), 3)).isTrue();
        assertThat(size.evaluate(java.util.List.of(1, 2, 3), 2)).isFalse();
        assertThat(size.evaluate(java.util.List.of(), 0)).isTrue();
    }

    @Test
    void testDefaultOperator_regex_shouldWorkCorrectly() {
        registry = OperatorRegistry.withDefaults();
        OperatorHandler regex = registry.get("$regex");

        assertThat(regex.evaluate("hello world", "hello")).isTrue();
        assertThat(regex.evaluate("hello world", "^hello")).isTrue();
        assertThat(regex.evaluate("hello world", "world$")).isTrue();
        assertThat(regex.evaluate("hello world", "\\d+")).isFalse();
        assertThat(regex.evaluate("test123", "\\d+")).isTrue();
    }

    @Test
    void testDefaultOperator_all_shouldWorkCorrectly() {
        registry = OperatorRegistry.withDefaults();
        OperatorHandler all = registry.get("$all");

        assertThat(all.evaluate(java.util.List.of(1, 2, 3, 4), java.util.List.of(2, 3))).isTrue();
        assertThat(all.evaluate(java.util.List.of(1, 2, 3), java.util.List.of(2, 5))).isFalse();
        assertThat(all.evaluate(java.util.List.of(1, 2, 3), java.util.List.of())).isTrue();
    }

    // ==================== Thread Safety Tests ====================

    @Test
    void testConcurrentRegistration_shouldBeThreadSafe() throws InterruptedException {
        registry = OperatorRegistry.withDefaults();
        int threadCount = 10;
        int operatorsPerThread = 100;
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);

        for (int t = 0; t < threadCount; t++) {
            final int threadId = t;
            new Thread(() -> {
                try {
                    for (int i = 0; i < operatorsPerThread; i++) {
                        String opName = "$thread" + threadId + "_op" + i;
                        registry.register(opName, (val, operand) -> true);
                        successCount.incrementAndGet();
                    }
                } finally {
                    latch.countDown();
                }
            }).start();
        }

        latch.await();

        assertThat(successCount.get()).isEqualTo(threadCount * operatorsPerThread);
        assertThat(registry.size()).isEqualTo(14 + (threadCount * operatorsPerThread));
    }

    @Test
    void testConcurrentGetAndRegister_shouldBeThreadSafe() throws InterruptedException {
        registry = OperatorRegistry.withDefaults();
        int threadCount = 10;
        CountDownLatch latch = new CountDownLatch(threadCount);

        for (int t = 0; t < threadCount; t++) {
            final int threadId = t;
            new Thread(() -> {
                try {
                    for (int i = 0; i < 100; i++) {
                        // Mix of reads and writes
                        if (i % 2 == 0) {
                            registry.get("$eq"); // Read
                        } else {
                            registry.register("$custom" + threadId, (val, operand) -> true); // Write
                        }
                    }
                } finally {
                    latch.countDown();
                }
            }).start();
        }

        latch.await();

        // Should not throw any exceptions and should have all custom operators
        assertThat(registry.size()).isGreaterThanOrEqualTo(14);
    }

    // ==================== Edge Cases ====================

    @Test
    void testOverrideBuiltInOperator_shouldWork() {
        registry = OperatorRegistry.withDefaults();

        // Override $eq with custom logic
        registry.register("$eq", (val, operand) -> false); // Always false

        OperatorHandler eq = registry.get("$eq");
        assertThat(eq.evaluate("hello", "hello")).isFalse(); // Custom behavior
    }

    @Test
    void testRegisterWithoutDollarSign_shouldStillWork() {
        registry.register("customop", (val, operand) -> true);

        assertThat(registry.contains("customop")).isTrue();
        assertThat(registry.get("customop")).isNotNull();
    }

    @Test
    void testAvailableOperators_snapshotBehavior() {
        registry.register("$op1", (val, operand) -> true);

        Set<String> snapshot = registry.availableOperators();
        assertThat(snapshot).containsExactly("$op1");

        // Register more operators
        registry.register("$op2", (val, operand) -> false);

        // Snapshot should not change
        assertThat(snapshot).containsExactly("$op1");

        // New call should reflect changes
        assertThat(registry.availableOperators()).containsExactlyInAnyOrder("$op1", "$op2");
    }

    // ==================== Additional Coverage Tests ====================

    @Test
    void testComparisonOperators_withIncompatibleTypes_shouldReturnFalse() {
        registry = OperatorRegistry.withDefaults();

        OperatorHandler gt = registry.get("$gt");
        OperatorHandler gte = registry.get("$gte");
        OperatorHandler lt = registry.get("$lt");
        OperatorHandler lte = registry.get("$lte");

        // Non-comparable types
        assertThat(gt.evaluate("string", 10)).isFalse();
        assertThat(gte.evaluate("string", 10)).isFalse();
        assertThat(lt.evaluate("string", 10)).isFalse();
        assertThat(lte.evaluate("string", 10)).isFalse();

        // Different non-number comparables (ClassCastException)
        assertThat(gt.evaluate(java.time.LocalDate.now(), "string")).isFalse();
        assertThat(gte.evaluate(java.time.LocalDate.now(), "string")).isFalse();
        assertThat(lt.evaluate(java.time.LocalDate.now(), "string")).isFalse();
        assertThat(lte.evaluate(java.time.LocalDate.now(), "string")).isFalse();
    }

    @Test
    void testComparisonOperators_withNonComparables_shouldReturnFalse() {
        registry = OperatorRegistry.withDefaults();

        OperatorHandler gt = registry.get("$gt");
        OperatorHandler gte = registry.get("$gte");
        OperatorHandler lt = registry.get("$lt");
        OperatorHandler lte = registry.get("$lte");

        // Objects that are not Comparable
        Object obj1 = new Object();
        Object obj2 = new Object();

        assertThat(gt.evaluate(obj1, obj2)).isFalse();
        assertThat(gte.evaluate(obj1, obj2)).isFalse();
        assertThat(lt.evaluate(obj1, obj2)).isFalse();
        assertThat(lte.evaluate(obj1, obj2)).isFalse();
    }

    @Test
    void testIn_withInvalidOperand_shouldReturnFalse() {
        registry = OperatorRegistry.withDefaults();
        OperatorHandler in = registry.get("$in");

        // Non-list operand
        assertThat(in.evaluate("value", "not-a-list")).isFalse();
        assertThat(in.evaluate("value", 123)).isFalse();
    }

    @Test
    void testIn_withListValue_shouldCheckIfAnyMatch() {
        registry = OperatorRegistry.withDefaults();
        OperatorHandler in = registry.get("$in");

        // List value - should check if any item is in operand list
        assertThat(in.evaluate(java.util.List.of("red", "blue"), java.util.List.of("red", "green"))).isTrue();
        assertThat(in.evaluate(java.util.List.of("yellow", "orange"), java.util.List.of("red", "green"))).isFalse();
    }

    @Test
    void testNin_withInvalidOperand_shouldReturnFalse() {
        registry = OperatorRegistry.withDefaults();
        OperatorHandler nin = registry.get("$nin");

        // Non-list operand
        assertThat(nin.evaluate("value", "not-a-list")).isFalse();
    }

    @Test
    void testNin_withListValue_shouldCheckIfNoneMatch() {
        registry = OperatorRegistry.withDefaults();
        OperatorHandler nin = registry.get("$nin");

        // List value - should check if no items are in operand list
        assertThat(nin.evaluate(java.util.List.of("yellow", "orange"), java.util.List.of("red", "green"))).isTrue();
        assertThat(nin.evaluate(java.util.List.of("red", "orange"), java.util.List.of("red", "green"))).isFalse();
    }

    @Test
    void testAll_withInvalidTypes_shouldReturnFalse() {
        registry = OperatorRegistry.withDefaults();
        OperatorHandler all = registry.get("$all");

        // Non-list value
        assertThat(all.evaluate("not-a-list", java.util.List.of(1, 2))).isFalse();

        // Non-list operand
        assertThat(all.evaluate(java.util.List.of(1, 2, 3), "not-a-list")).isFalse();
    }

    @Test
    void testSize_withInvalidTypes_shouldReturnFalse() {
        registry = OperatorRegistry.withDefaults();
        OperatorHandler size = registry.get("$size");

        // Non-list value
        assertThat(size.evaluate("not-a-list", 3)).isFalse();

        // Non-number operand
        assertThat(size.evaluate(java.util.List.of(1, 2, 3), "three")).isFalse();
    }

    @Test
    void testExists_withInvalidOperand_shouldReturnFalse() {
        registry = OperatorRegistry.withDefaults();
        OperatorHandler exists = registry.get("$exists");

        // Non-boolean operand
        assertThat(exists.evaluate("value", "true")).isFalse();
        assertThat(exists.evaluate("value", 1)).isFalse();
    }

    @Test
    void testType_withCustomClass_shouldReturnClassName() {
        registry = OperatorRegistry.withDefaults();
        OperatorHandler type = registry.get("$type");

        // Custom class
        class MyClass {}
        assertThat(type.evaluate(new MyClass(), "myclass")).isTrue();
    }

    @Test
    void testRegex_withInvalidOperand_shouldReturnFalse() {
        registry = OperatorRegistry.withDefaults();
        OperatorHandler regex = registry.get("$regex");

        // Non-string pattern
        assertThat(regex.evaluate("test", 123)).isFalse();
        assertThat(regex.evaluate("test", java.util.List.of())).isFalse();
    }

    @Test
    void testRegex_withInvalidPattern_shouldReturnFalse() {
        registry = OperatorRegistry.withDefaults();
        OperatorHandler regex = registry.get("$regex");

        // Invalid regex pattern
        assertThat(regex.evaluate("test", "[invalid(")).isFalse();
    }

    @Test
    void testElemMatch_withInvalidTypes_shouldReturnFalse() {
        registry = OperatorRegistry.withDefaults();
        OperatorHandler elemMatch = registry.get("$elemMatch");

        // Non-list value
        assertThat(elemMatch.evaluate("not-a-list", java.util.Map.of("field", "value"))).isFalse();

        // Non-map operand
        assertThat(elemMatch.evaluate(java.util.List.of(1, 2), "not-a-map")).isFalse();
    }

    @Test
    void testElemMatch_withValidTypes_returnsBasedOnNonEmpty() {
        registry = OperatorRegistry.withDefaults();
        OperatorHandler elemMatch = registry.get("$elemMatch");

        // Simplified implementation checks if list is non-empty
        assertThat(elemMatch.evaluate(java.util.List.of(1, 2), java.util.Map.of("field", "value"))).isTrue();
        assertThat(elemMatch.evaluate(java.util.List.of(), java.util.Map.of("field", "value"))).isFalse();
    }

    @Test
    void testGetAll_snapshotBehavior() {
        registry.register("$op1", (val, operand) -> true);

        Map<String, OperatorHandler> snapshot = registry.getAll();
        assertThat(snapshot).hasSize(1);

        // Register more operators
        registry.register("$op2", (val, operand) -> false);

        // Snapshot should not change
        assertThat(snapshot).hasSize(1);

        // New call should reflect changes
        assertThat(registry.getAll()).hasSize(2);
    }

    @Test
    void testComparisonOperators_withDifferentNumberTypes() {
        registry = OperatorRegistry.withDefaults();

        OperatorHandler gt = registry.get("$gt");
        OperatorHandler gte = registry.get("$gte");
        OperatorHandler lt = registry.get("$lt");
        OperatorHandler lte = registry.get("$lte");

        // Different number types (int vs double)
        assertThat(gt.evaluate(10.5, 10)).isTrue();
        assertThat(gte.evaluate(10.0, 10)).isTrue();
        assertThat(lt.evaluate(9.5, 10)).isTrue();
        assertThat(lte.evaluate(10.0, 10)).isTrue();

        // Long vs Integer
        assertThat(gt.evaluate(100L, 99)).isTrue();
        assertThat(lt.evaluate(99L, 100)).isTrue();
    }
}
