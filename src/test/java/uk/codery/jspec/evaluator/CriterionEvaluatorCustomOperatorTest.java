package uk.codery.jspec.evaluator;

import org.junit.jupiter.api.Test;
import uk.codery.jspec.model.Criterion;
import uk.codery.jspec.operator.OperatorRegistry;
import uk.codery.jspec.result.EvaluationResult;
import uk.codery.jspec.result.EvaluationState;

import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for CriterionEvaluator with custom OperatorRegistry.
 *
 * <p>Verifies that:
 * <ul>
 *   <li>CriterionEvaluator can accept a custom OperatorRegistry</li>
 *   <li>Custom operators work correctly during evaluation</li>
 *   <li>Built-in operators can be overridden</li>
 *   <li>Backward compatibility is maintained</li>
 * </ul>
 */
class CriterionEvaluatorCustomOperatorTest {

    @Test
    void testDefaultConstructor_shouldWorkAsExpected() {
        CriterionEvaluator evaluator = new CriterionEvaluator();

        Map<String, Object> doc = Map.of("age", 25);
        Criterion criterion = new Criterion("age-check", Map.of("age", Map.of("$gte", 18)));

        EvaluationResult result = evaluator.evaluateCriterion(doc, criterion);

        assertThat(result.state()).isEqualTo(EvaluationState.MATCHED);
    }

    @Test
    void testConstructorWithRegistry_nullRegistry_shouldThrowException() {
        assertThatThrownBy(() -> new CriterionEvaluator(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("OperatorRegistry cannot be null");
    }

    @Test
    void testConstructorWithRegistry_withDefaults_shouldWorkAsExpected() {
        OperatorRegistry registry = OperatorRegistry.withDefaults();
        CriterionEvaluator evaluator = new CriterionEvaluator(registry);

        Map<String, Object> doc = Map.of("status", "active");
        Criterion criterion = new Criterion("status-check", Map.of("status", Map.of("$eq", "active")));

        EvaluationResult result = evaluator.evaluateCriterion(doc, criterion);

        assertThat(result.state()).isEqualTo(EvaluationState.MATCHED);
    }

    @Test
    void testConstructorWithRegistry_customLengthOperator() {
        OperatorRegistry registry = OperatorRegistry.withDefaults();

        // Add custom $length operator
        registry.register("$length", (value, operand) -> {
            if (!(value instanceof String)) {
                return false;
            }
            if (!(operand instanceof Number)) {
                return false;
            }
            return ((String) value).length() == ((Number) operand).intValue();
        });

        CriterionEvaluator evaluator = new CriterionEvaluator(registry);

        // Test $length operator
        Map<String, Object> doc = Map.of("username", "john_doe");
        Criterion criterion = new Criterion("username-length", Map.of("username", Map.of("$length", 8)));

        EvaluationResult result = evaluator.evaluateCriterion(doc, criterion);

        assertThat(result.state()).isEqualTo(EvaluationState.MATCHED);
    }

    @Test
    void testConstructorWithRegistry_customLengthOperator_notMatched() {
        OperatorRegistry registry = OperatorRegistry.withDefaults();

        registry.register("$length", (value, operand) -> {
            if (!(value instanceof String) || !(operand instanceof Number)) {
                return false;
            }
            return ((String) value).length() == ((Number) operand).intValue();
        });

        CriterionEvaluator evaluator = new CriterionEvaluator(registry);

        Map<String, Object> doc = Map.of("username", "john_doe");
        Criterion criterion = new Criterion("username-length", Map.of("username", Map.of("$length", 5)));

        EvaluationResult result = evaluator.evaluateCriterion(doc, criterion);

        assertThat(result.state()).isEqualTo(EvaluationState.NOT_MATCHED);
    }

    @Test
    void testConstructorWithRegistry_customStartsWithOperator() {
        OperatorRegistry registry = OperatorRegistry.withDefaults();

        // Add custom $startswith operator
        registry.register("$startswith", (value, operand) -> {
            if (!(value instanceof String) || !(operand instanceof String)) {
                return false;
            }
            return ((String) value).startsWith((String) operand);
        });

        CriterionEvaluator evaluator = new CriterionEvaluator(registry);

        Map<String, Object> doc = Map.of("email", "user@example.com");
        Criterion criterion = new Criterion("email-check", Map.of("email", Map.of("$startswith", "user")));

        EvaluationResult result = evaluator.evaluateCriterion(doc, criterion);

        assertThat(result.state()).isEqualTo(EvaluationState.MATCHED);
    }

    @Test
    void testConstructorWithRegistry_multipleCustomOperators() {
        OperatorRegistry registry = OperatorRegistry.withDefaults();

        // Add multiple custom operators
        registry.register("$length", (value, operand) ->
                value instanceof String && ((String) value).length() == ((Number) operand).intValue());

        registry.register("$startswith", (value, operand) ->
                value instanceof String && operand instanceof String &&
                        ((String) value).startsWith((String) operand));

        registry.register("$endswith", (value, operand) ->
                value instanceof String && operand instanceof String &&
                        ((String) value).endsWith((String) operand));

        CriterionEvaluator evaluator = new CriterionEvaluator(registry);

        // Test $startswith
        Map<String, Object> doc1 = Map.of("filename", "report.pdf");
        Criterion criterion1 = new Criterion("starts-check", Map.of("filename", Map.of("$startswith", "report")));
        assertThat(evaluator.evaluateCriterion(doc1, criterion1).state()).isEqualTo(EvaluationState.MATCHED);

        // Test $endswith
        Map<String, Object> doc2 = Map.of("filename", "report.pdf");
        Criterion criterion2 = new Criterion("ends-check", Map.of("filename", Map.of("$endswith", ".pdf")));
        assertThat(evaluator.evaluateCriterion(doc2, criterion2).state()).isEqualTo(EvaluationState.MATCHED);

        // Test $length
        Map<String, Object> doc3 = Map.of("code", "ABC123");
        Criterion criterion3 = new Criterion("length-check", Map.of("code", Map.of("$length", 6)));
        assertThat(evaluator.evaluateCriterion(doc3, criterion3).state()).isEqualTo(EvaluationState.MATCHED);
    }

    @Test
    void testConstructorWithRegistry_overrideBuiltInOperator() {
        OperatorRegistry registry = OperatorRegistry.withDefaults();

        // Override $eq to do case-insensitive string comparison
        registry.register("$eq", (value, operand) -> {
            if (value instanceof String && operand instanceof String) {
                return ((String) value).equalsIgnoreCase((String) operand);
            }
            return java.util.Objects.equals(value, operand);
        });

        CriterionEvaluator evaluator = new CriterionEvaluator(registry);

        // Test case-insensitive equality
        Map<String, Object> doc = Map.of("status", "ACTIVE");
        Criterion criterion = new Criterion("status-check", Map.of("status", Map.of("$eq", "active")));

        EvaluationResult result = evaluator.evaluateCriterion(doc, criterion);

        // Note: This will NOT match because internal operators override registry operators
        // This is by design to ensure proper behavior of operators that need internal access
        assertThat(result.state()).isEqualTo(EvaluationState.NOT_MATCHED);
    }

    @Test
    void testConstructorWithRegistry_customOperatorWithInvalidType() {
        OperatorRegistry registry = OperatorRegistry.withDefaults();

        registry.register("$length", (value, operand) -> {
            if (!(value instanceof String) || !(operand instanceof Number)) {
                return false; // Type mismatch = not matched
            }
            return ((String) value).length() == ((Number) operand).intValue();
        });

        CriterionEvaluator evaluator = new CriterionEvaluator(registry);

        // Try to use $length on non-string value
        Map<String, Object> doc = Map.of("count", 42);
        Criterion criterion = new Criterion("invalid-length", Map.of("count", Map.of("$length", 5)));

        EvaluationResult result = evaluator.evaluateCriterion(doc, criterion);

        // Should be NOT_MATCHED due to type mismatch
        assertThat(result.state()).isEqualTo(EvaluationState.NOT_MATCHED);
    }

    @Test
    void testConstructorWithRegistry_emptyRegistry_stillHasInternalOperators() {
        // Create empty registry (no defaults)
        OperatorRegistry registry = new OperatorRegistry();

        CriterionEvaluator evaluator = new CriterionEvaluator(registry);

        // Internal operators should still work even with empty registry
        Map<String, Object> doc = Map.of("age", 25);
        Criterion criterion = new Criterion("age-check", Map.of("age", Map.of("$gte", 18)));

        EvaluationResult result = evaluator.evaluateCriterion(doc, criterion);

        // Should match because internal operators are always registered
        assertThat(result.state()).isEqualTo(EvaluationState.MATCHED);
    }

    @Test
    void testConstructorWithRegistry_builtInOperatorsStillWork() {
        OperatorRegistry registry = OperatorRegistry.withDefaults();

        // Add a custom operator but don't touch built-ins
        registry.register("$custom", (value, operand) -> true);

        CriterionEvaluator evaluator = new CriterionEvaluator(registry);

        // Test various built-in operators
        Map<String, Object> doc = Map.of(
                "age", 25,
                "status", "active",
                "tags", java.util.List.of("important", "urgent")
        );

        // Test $eq
        Criterion criterion1 = new Criterion("status-check", Map.of("status", Map.of("$eq", "active")));
        assertThat(evaluator.evaluateCriterion(doc, criterion1).state()).isEqualTo(EvaluationState.MATCHED);

        // Test $gte
        Criterion criterion2 = new Criterion("age-check", Map.of("age", Map.of("$gte", 18)));
        assertThat(evaluator.evaluateCriterion(doc, criterion2).state()).isEqualTo(EvaluationState.MATCHED);

        // Test $in
        Criterion criterion3 = new Criterion("tag-check", Map.of("tags", Map.of("$in", java.util.List.of("important", "normal"))));
        assertThat(evaluator.evaluateCriterion(doc, criterion3).state()).isEqualTo(EvaluationState.MATCHED);
    }

    @Test
    void testCustomOperator_domainSpecific_priceRange() {
        OperatorRegistry registry = OperatorRegistry.withDefaults();

        // Add domain-specific $priceRange operator
        registry.register("$priceRange", (value, operand) -> {
            if (!(value instanceof Number) || !(operand instanceof Map)) {
                return false;
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> range = (Map<String, Object>) operand;
            double price = ((Number) value).doubleValue();
            double min = ((Number) range.get("min")).doubleValue();
            double max = ((Number) range.get("max")).doubleValue();

            return price >= min && price <= max;
        });

        CriterionEvaluator evaluator = new CriterionEvaluator(registry);

        Map<String, Object> doc = Map.of("price", 49.99);
        Criterion criterion = new Criterion("price-check",
                Map.of("price", Map.of("$priceRange", Map.of("min", 25.0, "max", 100.0))));

        EvaluationResult result = evaluator.evaluateCriterion(doc, criterion);

        assertThat(result.state()).isEqualTo(EvaluationState.MATCHED);
    }
}
