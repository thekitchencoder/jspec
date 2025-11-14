package uk.codery.rules.operators;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.codery.rules.*;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Comprehensive tests for comparison operators: $eq, $ne, $gt, $gte, $lt, $lte
 * These tests baseline the current behavior for all comparison operators.
 */
class ComparisonOperatorsTest {

    private RuleEvaluator evaluator;

    @BeforeEach
    void setUp() {
        evaluator = new RuleEvaluator();
    }

    // ========== $eq Operator Tests ==========

    @Test
    void eq_withMatchingIntegers_shouldMatch() {
        Map<String, Object> doc = Map.of("age", 25);
        Rule rule = new Rule("test", Map.of("age", Map.of("$eq", 25)));

        EvaluationResult result = evaluator.evaluateRule(doc, rule);

        assertThat(result.state()).isEqualTo(EvaluationState.MATCHED);
        assertThat(result.matched()).isTrue();
    }

    @Test
    void eq_withNonMatchingIntegers_shouldNotMatch() {
        Map<String, Object> doc = Map.of("age", 25);
        Rule rule = new Rule("test", Map.of("age", Map.of("$eq", 30)));

        EvaluationResult result = evaluator.evaluateRule(doc, rule);

        assertThat(result.state()).isEqualTo(EvaluationState.NOT_MATCHED);
        assertThat(result.matched()).isFalse();
    }

    @Test
    void eq_withMatchingStrings_shouldMatch() {
        Map<String, Object> doc = Map.of("name", "John");
        Rule rule = new Rule("test", Map.of("name", Map.of("$eq", "John")));

        EvaluationResult result = evaluator.evaluateRule(doc, rule);

        assertThat(result.state()).isEqualTo(EvaluationState.MATCHED);
    }

    @Test
    void eq_withMatchingBooleans_shouldMatch() {
        Map<String, Object> doc = Map.of("active", true);
        Rule rule = new Rule("test", Map.of("active", Map.of("$eq", true)));

        EvaluationResult result = evaluator.evaluateRule(doc, rule);

        assertThat(result.state()).isEqualTo(EvaluationState.MATCHED);
    }

    @Test
    void eq_withMatchingDoubles_shouldMatch() {
        Map<String, Object> doc = Map.of("price", 19.99);
        Rule rule = new Rule("test", Map.of("price", Map.of("$eq", 19.99)));

        EvaluationResult result = evaluator.evaluateRule(doc, rule);

        assertThat(result.state()).isEqualTo(EvaluationState.MATCHED);
    }

    @Test
    void eq_caseSensitiveStrings_shouldNotMatch() {
        Map<String, Object> doc = Map.of("name", "John");
        Rule rule = new Rule("test", Map.of("name", Map.of("$eq", "john")));

        EvaluationResult result = evaluator.evaluateRule(doc, rule);

        assertThat(result.state()).isEqualTo(EvaluationState.NOT_MATCHED);
    }

    // ========== $ne Operator Tests ==========

    @Test
    void ne_withDifferentValues_shouldMatch() {
        Map<String, Object> doc = Map.of("age", 25);
        Rule rule = new Rule("test", Map.of("age", Map.of("$ne", 30)));

        EvaluationResult result = evaluator.evaluateRule(doc, rule);

        assertThat(result.state()).isEqualTo(EvaluationState.MATCHED);
    }

    @Test
    void ne_withSameValues_shouldNotMatch() {
        Map<String, Object> doc = Map.of("age", 25);
        Rule rule = new Rule("test", Map.of("age", Map.of("$ne", 25)));

        EvaluationResult result = evaluator.evaluateRule(doc, rule);

        assertThat(result.state()).isEqualTo(EvaluationState.NOT_MATCHED);
    }

    @Test
    void ne_withDifferentTypes_shouldMatch() {
        Map<String, Object> doc = Map.of("age", 25);
        Rule rule = new Rule("test", Map.of("age", Map.of("$ne", "25")));

        EvaluationResult result = evaluator.evaluateRule(doc, rule);

        assertThat(result.state()).isEqualTo(EvaluationState.MATCHED);
    }

    @Test
    void ne_withBooleans_shouldMatch() {
        Map<String, Object> doc = Map.of("active", true);
        Rule rule = new Rule("test", Map.of("active", Map.of("$ne", false)));

        EvaluationResult result = evaluator.evaluateRule(doc, rule);

        assertThat(result.state()).isEqualTo(EvaluationState.MATCHED);
    }

    // ========== $gt Operator Tests ==========

    @Test
    void gt_withLargerValue_shouldMatch() {
        Map<String, Object> doc = Map.of("age", 30);
        Rule rule = new Rule("test", Map.of("age", Map.of("$gt", 25)));

        EvaluationResult result = evaluator.evaluateRule(doc, rule);

        assertThat(result.state()).isEqualTo(EvaluationState.MATCHED);
    }

    @Test
    void gt_withSmallerValue_shouldNotMatch() {
        Map<String, Object> doc = Map.of("age", 20);
        Rule rule = new Rule("test", Map.of("age", Map.of("$gt", 25)));

        EvaluationResult result = evaluator.evaluateRule(doc, rule);

        assertThat(result.state()).isEqualTo(EvaluationState.NOT_MATCHED);
    }

    @Test
    void gt_withEqualValue_shouldNotMatch() {
        Map<String, Object> doc = Map.of("age", 25);
        Rule rule = new Rule("test", Map.of("age", Map.of("$gt", 25)));

        EvaluationResult result = evaluator.evaluateRule(doc, rule);

        assertThat(result.state()).isEqualTo(EvaluationState.NOT_MATCHED);
    }

    @Test
    void gt_withDoubles_shouldMatch() {
        Map<String, Object> doc = Map.of("price", 29.99);
        Rule rule = new Rule("test", Map.of("price", Map.of("$gt", 19.99)));

        EvaluationResult result = evaluator.evaluateRule(doc, rule);

        assertThat(result.state()).isEqualTo(EvaluationState.MATCHED);
    }

    @Test
    void gt_withMixedIntegerAndDouble_shouldWork() {
        Map<String, Object> doc = Map.of("age", 30);
        Rule rule = new Rule("test", Map.of("age", Map.of("$gt", 25.5)));

        EvaluationResult result = evaluator.evaluateRule(doc, rule);

        assertThat(result.state()).isEqualTo(EvaluationState.MATCHED);
    }

    @Test
    void gt_withStrings_shouldWork() {
        Map<String, Object> doc = Map.of("name", "Bob");
        Rule rule = new Rule("test", Map.of("name", Map.of("$gt", "Alice")));

        EvaluationResult result = evaluator.evaluateRule(doc, rule);

        // String comparison uses Comparable
        assertThat(result.state()).isEqualTo(EvaluationState.MATCHED);
    }

    @Test
    void gt_withNonComparableTypes_shouldNotMatch() {
        Map<String, Object> doc = Map.of("value", Map.of("nested", "value"));
        Rule rule = new Rule("test", Map.of("value", Map.of("$gt", 10)));

        EvaluationResult result = evaluator.evaluateRule(doc, rule);

        // Map is not comparable to Number, returns 0 from compare()
        assertThat(result.state()).isEqualTo(EvaluationState.NOT_MATCHED);
    }

    // ========== $gte Operator Tests ==========

    @Test
    void gte_withLargerValue_shouldMatch() {
        Map<String, Object> doc = Map.of("age", 30);
        Rule rule = new Rule("test", Map.of("age", Map.of("$gte", 25)));

        EvaluationResult result = evaluator.evaluateRule(doc, rule);

        assertThat(result.state()).isEqualTo(EvaluationState.MATCHED);
    }

    @Test
    void gte_withEqualValue_shouldMatch() {
        Map<String, Object> doc = Map.of("age", 25);
        Rule rule = new Rule("test", Map.of("age", Map.of("$gte", 25)));

        EvaluationResult result = evaluator.evaluateRule(doc, rule);

        assertThat(result.state()).isEqualTo(EvaluationState.MATCHED);
    }

    @Test
    void gte_withSmallerValue_shouldNotMatch() {
        Map<String, Object> doc = Map.of("age", 20);
        Rule rule = new Rule("test", Map.of("age", Map.of("$gte", 25)));

        EvaluationResult result = evaluator.evaluateRule(doc, rule);

        assertThat(result.state()).isEqualTo(EvaluationState.NOT_MATCHED);
    }

    @Test
    void gte_withDoubles_shouldWork() {
        Map<String, Object> doc = Map.of("price", 19.99);
        Rule rule = new Rule("test", Map.of("price", Map.of("$gte", 19.99)));

        EvaluationResult result = evaluator.evaluateRule(doc, rule);

        assertThat(result.state()).isEqualTo(EvaluationState.MATCHED);
    }

    // ========== $lt Operator Tests ==========

    @Test
    void lt_withSmallerValue_shouldMatch() {
        Map<String, Object> doc = Map.of("age", 20);
        Rule rule = new Rule("test", Map.of("age", Map.of("$lt", 25)));

        EvaluationResult result = evaluator.evaluateRule(doc, rule);

        assertThat(result.state()).isEqualTo(EvaluationState.MATCHED);
    }

    @Test
    void lt_withLargerValue_shouldNotMatch() {
        Map<String, Object> doc = Map.of("age", 30);
        Rule rule = new Rule("test", Map.of("age", Map.of("$lt", 25)));

        EvaluationResult result = evaluator.evaluateRule(doc, rule);

        assertThat(result.state()).isEqualTo(EvaluationState.NOT_MATCHED);
    }

    @Test
    void lt_withEqualValue_shouldNotMatch() {
        Map<String, Object> doc = Map.of("age", 25);
        Rule rule = new Rule("test", Map.of("age", Map.of("$lt", 25)));

        EvaluationResult result = evaluator.evaluateRule(doc, rule);

        assertThat(result.state()).isEqualTo(EvaluationState.NOT_MATCHED);
    }

    @Test
    void lt_withNegativeNumbers_shouldWork() {
        Map<String, Object> doc = Map.of("temperature", -5);
        Rule rule = new Rule("test", Map.of("temperature", Map.of("$lt", 0)));

        EvaluationResult result = evaluator.evaluateRule(doc, rule);

        assertThat(result.state()).isEqualTo(EvaluationState.MATCHED);
    }

    // ========== $lte Operator Tests ==========

    @Test
    void lte_withSmallerValue_shouldMatch() {
        Map<String, Object> doc = Map.of("age", 20);
        Rule rule = new Rule("test", Map.of("age", Map.of("$lte", 25)));

        EvaluationResult result = evaluator.evaluateRule(doc, rule);

        assertThat(result.state()).isEqualTo(EvaluationState.MATCHED);
    }

    @Test
    void lte_withEqualValue_shouldMatch() {
        Map<String, Object> doc = Map.of("age", 25);
        Rule rule = new Rule("test", Map.of("age", Map.of("$lte", 25)));

        EvaluationResult result = evaluator.evaluateRule(doc, rule);

        assertThat(result.state()).isEqualTo(EvaluationState.MATCHED);
    }

    @Test
    void lte_withLargerValue_shouldNotMatch() {
        Map<String, Object> doc = Map.of("age", 30);
        Rule rule = new Rule("test", Map.of("age", Map.of("$lte", 25)));

        EvaluationResult result = evaluator.evaluateRule(doc, rule);

        assertThat(result.state()).isEqualTo(EvaluationState.NOT_MATCHED);
    }

    // ========== Combined Operators Tests ==========

    @Test
    void combined_gteAndLte_withinRange_shouldMatch() {
        Map<String, Object> doc = Map.of("age", 25);
        Rule rule = new Rule("test", Map.of("age", Map.of("$gte", 18, "$lte", 65)));

        EvaluationResult result = evaluator.evaluateRule(doc, rule);

        assertThat(result.state()).isEqualTo(EvaluationState.MATCHED);
    }

    @Test
    void combined_gteAndLte_outsideRange_shouldNotMatch() {
        Map<String, Object> doc = Map.of("age", 70);
        Rule rule = new Rule("test", Map.of("age", Map.of("$gte", 18, "$lte", 65)));

        EvaluationResult result = evaluator.evaluateRule(doc, rule);

        assertThat(result.state()).isEqualTo(EvaluationState.NOT_MATCHED);
    }

    @Test
    void combined_gtAndLt_withinRange_shouldMatch() {
        Map<String, Object> doc = Map.of("age", 25);
        Rule rule = new Rule("test", Map.of("age", Map.of("$gt", 18, "$lt", 65)));

        EvaluationResult result = evaluator.evaluateRule(doc, rule);

        assertThat(result.state()).isEqualTo(EvaluationState.MATCHED);
    }

    @Test
    void combined_gtAndLt_atBoundary_shouldNotMatch() {
        Map<String, Object> doc = Map.of("age", 18);
        Rule rule = new Rule("test", Map.of("age", Map.of("$gt", 18, "$lt", 65)));

        EvaluationResult result = evaluator.evaluateRule(doc, rule);

        assertThat(result.state()).isEqualTo(EvaluationState.NOT_MATCHED);
    }

    // ========== Edge Cases ==========

    @Test
    void comparison_withMissingField_shouldBeUndetermined() {
        Map<String, Object> doc = Map.of("name", "John");
        Rule rule = new Rule("test", Map.of("age", Map.of("$gt", 18)));

        EvaluationResult result = evaluator.evaluateRule(doc, rule);

        assertThat(result.state()).isEqualTo(EvaluationState.UNDETERMINED);
        assertThat(result.missingPaths()).contains("age");
    }

    @Test
    void comparison_withVeryLargeNumbers_shouldWork() {
        Map<String, Object> doc = Map.of("value", 999999999L);
        Rule rule = new Rule("test", Map.of("value", Map.of("$gt", 1000000)));

        EvaluationResult result = evaluator.evaluateRule(doc, rule);

        assertThat(result.state()).isEqualTo(EvaluationState.MATCHED);
    }

    @Test
    void comparison_withZero_shouldWork() {
        Map<String, Object> doc = Map.of("value", 0);
        Rule rule = new Rule("test", Map.of("value", Map.of("$eq", 0)));

        EvaluationResult result = evaluator.evaluateRule(doc, rule);

        assertThat(result.state()).isEqualTo(EvaluationState.MATCHED);
    }

    @Test
    void comparison_withDecimalPrecision_shouldWork() {
        Map<String, Object> doc = Map.of("price", 10.001);
        Rule rule = new Rule("test", Map.of("price", Map.of("$gt", 10.0)));

        EvaluationResult result = evaluator.evaluateRule(doc, rule);

        assertThat(result.state()).isEqualTo(EvaluationState.MATCHED);
    }
}
