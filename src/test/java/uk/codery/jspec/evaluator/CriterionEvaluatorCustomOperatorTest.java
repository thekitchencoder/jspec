package uk.codery.jspec.evaluator;

import org.junit.jupiter.api.Test;
import uk.codery.jspec.model.QueryCriterion;
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
        QueryCriterion criterion = QueryCriterion.builder()
                .id("age-check")
                .field("age").gte(18)
                .build();

        EvaluationResult result = evaluator.evaluateQuery(doc, criterion);

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
        QueryCriterion criterion = QueryCriterion.builder()
                .id("status-check")
                .field("status").eq("active")
                .build();

        EvaluationResult result = evaluator.evaluateQuery(doc, criterion);

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

        // Test $length operator using builder with custom operator
        Map<String, Object> doc = Map.of("username", "john_doe");
        QueryCriterion criterion = QueryCriterion.builder()
                .id("username-length")
                .field("username").operator("$length", 8)
                .build();

        EvaluationResult result = evaluator.evaluateQuery(doc, criterion);

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
        QueryCriterion criterion = QueryCriterion.builder()
                .id("username-length")
                .field("username").operator("$length", 5)
                .build();

        EvaluationResult result = evaluator.evaluateQuery(doc, criterion);

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
        QueryCriterion criterion = QueryCriterion.builder()
                .id("email-check")
                .field("email").operator("$startswith", "user")
                .build();

        EvaluationResult result = evaluator.evaluateQuery(doc, criterion);

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
        QueryCriterion criterion1 = QueryCriterion.builder()
                .id("starts-check")
                .field("filename").operator("$startswith", "report")
                .build();
        assertThat(evaluator.evaluateQuery(doc1, criterion1).state()).isEqualTo(EvaluationState.MATCHED);

        // Test $endswith
        Map<String, Object> doc2 = Map.of("filename", "report.pdf");
        QueryCriterion criterion2 = QueryCriterion.builder()
                .id("ends-check")
                .field("filename").operator("$endswith", ".pdf")
                .build();
        assertThat(evaluator.evaluateQuery(doc2, criterion2).state()).isEqualTo(EvaluationState.MATCHED);

        // Test $length
        Map<String, Object> doc3 = Map.of("code", "ABC123");
        QueryCriterion criterion3 = QueryCriterion.builder()
                .id("length-check")
                .field("code").operator("$length", 6)
                .build();
        assertThat(evaluator.evaluateQuery(doc3, criterion3).state()).isEqualTo(EvaluationState.MATCHED);
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
        QueryCriterion criterion = QueryCriterion.builder()
                .id("status-check")
                .field("status").eq("active")
                .build();

        EvaluationResult result = evaluator.evaluateQuery(doc, criterion);

        // Should MATCH because $eq is not an internal operator, so the custom version is used
        // Comparison operators ($eq, $ne, $gt, etc.) can be overridden via registry
        assertThat(result.state()).isEqualTo(EvaluationState.MATCHED);
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
        QueryCriterion criterion = QueryCriterion.builder()
                .id("invalid-length")
                .field("count").operator("$length", 5)
                .build();

        EvaluationResult result = evaluator.evaluateQuery(doc, criterion);

        // Should be NOT_MATCHED due to type mismatch
        assertThat(result.state()).isEqualTo(EvaluationState.NOT_MATCHED);
    }

    @Test
    void testConstructorWithRegistry_emptyRegistry_stillHasInternalOperators() {
        // Create empty registry (no defaults)
        OperatorRegistry registry = new OperatorRegistry();

        CriterionEvaluator evaluator = new CriterionEvaluator(registry);

        // Internal operators ($in, $nin, $exists, $type, $regex, $size, $elemMatch, $all)
        // are always registered even with empty registry
        Map<String, Object> doc = Map.of("status", "active");
        QueryCriterion criterion = QueryCriterion.builder()
                .id("status-check")
                .field("status").exists(true)
                .build();

        EvaluationResult result = evaluator.evaluateQuery(doc, criterion);

        // Should match because $exists is an internal operator that's always registered
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
        QueryCriterion criterion1 = QueryCriterion.builder()
                .id("status-check")
                .field("status").eq("active")
                .build();
        assertThat(evaluator.evaluateQuery(doc, criterion1).state()).isEqualTo(EvaluationState.MATCHED);

        // Test $gte
        QueryCriterion criterion2 = QueryCriterion.builder()
                .id("age-check")
                .field("age").gte(18)
                .build();
        assertThat(evaluator.evaluateQuery(doc, criterion2).state()).isEqualTo(EvaluationState.MATCHED);

        // Test $in
        QueryCriterion criterion3 = QueryCriterion.builder()
                .id("tag-check")
                .field("tags").in("important", "normal")
                .build();
        assertThat(evaluator.evaluateQuery(doc, criterion3).state()).isEqualTo(EvaluationState.MATCHED);
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
        QueryCriterion criterion = QueryCriterion.builder()
                .id("price-check")
                .field("price").operator("$priceRange", Map.of("min", 25.0, "max", 100.0))
                .build();

        EvaluationResult result = evaluator.evaluateQuery(doc, criterion);

        assertThat(result.state()).isEqualTo(EvaluationState.MATCHED);
    }
}
