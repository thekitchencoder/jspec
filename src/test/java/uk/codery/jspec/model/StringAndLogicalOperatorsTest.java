package uk.codery.jspec.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.codery.jspec.evaluator.CriterionEvaluator;
import uk.codery.jspec.result.EvaluationResult;
import uk.codery.jspec.result.EvaluationState;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Comprehensive tests for string operators ($contains, $startsWith, $endsWith)
 * and logical operators ($not).
 */
class StringAndLogicalOperatorsTest {

    private CriterionEvaluator evaluator;

    @BeforeEach
    void setUp() {
        evaluator = new CriterionEvaluator();
    }

    // ========== $contains Operator Tests (String) ==========

    @Test
    void contains_withMatchingSubstring_shouldMatch() {
        Map<String, Object> doc = Map.of("description", "This is urgent");
        QueryCriterion criterion = new QueryCriterion("test", Map.of("description", Map.of("$contains", "urgent")));

        EvaluationResult result = evaluator.evaluateQuery(doc, criterion);

        assertThat(result.state()).isEqualTo(EvaluationState.MATCHED);
    }

    @Test
    void contains_withNonMatchingSubstring_shouldNotMatch() {
        Map<String, Object> doc = Map.of("description", "This is normal");
        QueryCriterion criterion = new QueryCriterion("test", Map.of("description", Map.of("$contains", "urgent")));

        EvaluationResult result = evaluator.evaluateQuery(doc, criterion);

        assertThat(result.state()).isEqualTo(EvaluationState.NOT_MATCHED);
    }

    @Test
    void contains_caseSensitive_shouldNotMatch() {
        Map<String, Object> doc = Map.of("description", "This is URGENT");
        QueryCriterion criterion = new QueryCriterion("test", Map.of("description", Map.of("$contains", "urgent")));

        EvaluationResult result = evaluator.evaluateQuery(doc, criterion);

        assertThat(result.state()).isEqualTo(EvaluationState.NOT_MATCHED);
    }

    @Test
    void contains_withEmptySubstring_shouldMatch() {
        Map<String, Object> doc = Map.of("description", "Any string");
        QueryCriterion criterion = new QueryCriterion("test", Map.of("description", Map.of("$contains", "")));

        EvaluationResult result = evaluator.evaluateQuery(doc, criterion);

        assertThat(result.state()).isEqualTo(EvaluationState.MATCHED);
    }

    @Test
    void contains_withEmptyString_shouldNotMatch() {
        Map<String, Object> doc = Map.of("description", "");
        QueryCriterion criterion = new QueryCriterion("test", Map.of("description", Map.of("$contains", "text")));

        EvaluationResult result = evaluator.evaluateQuery(doc, criterion);

        assertThat(result.state()).isEqualTo(EvaluationState.NOT_MATCHED);
    }

    @Test
    void contains_atBeginning_shouldMatch() {
        Map<String, Object> doc = Map.of("email", "admin@example.com");
        QueryCriterion criterion = new QueryCriterion("test", Map.of("email", Map.of("$contains", "admin")));

        EvaluationResult result = evaluator.evaluateQuery(doc, criterion);

        assertThat(result.state()).isEqualTo(EvaluationState.MATCHED);
    }

    @Test
    void contains_atEnd_shouldMatch() {
        Map<String, Object> doc = Map.of("filename", "document.pdf");
        QueryCriterion criterion = new QueryCriterion("test", Map.of("filename", Map.of("$contains", ".pdf")));

        EvaluationResult result = evaluator.evaluateQuery(doc, criterion);

        assertThat(result.state()).isEqualTo(EvaluationState.MATCHED);
    }

    // ========== $contains Operator Tests (Collection) ==========

    @Test
    void contains_collectionWithMatchingElement_shouldMatch() {
        Map<String, Object> doc = Map.of("tags", List.of("urgent", "vip", "priority"));
        QueryCriterion criterion = new QueryCriterion("test", Map.of("tags", Map.of("$contains", "vip")));

        EvaluationResult result = evaluator.evaluateQuery(doc, criterion);

        assertThat(result.state()).isEqualTo(EvaluationState.MATCHED);
    }

    @Test
    void contains_collectionWithNonMatchingElement_shouldNotMatch() {
        Map<String, Object> doc = Map.of("tags", List.of("urgent", "vip", "priority"));
        QueryCriterion criterion = new QueryCriterion("test", Map.of("tags", Map.of("$contains", "normal")));

        EvaluationResult result = evaluator.evaluateQuery(doc, criterion);

        assertThat(result.state()).isEqualTo(EvaluationState.NOT_MATCHED);
    }

    @Test
    void contains_emptyCollection_shouldNotMatch() {
        Map<String, Object> doc = Map.of("tags", List.of());
        QueryCriterion criterion = new QueryCriterion("test", Map.of("tags", Map.of("$contains", "any")));

        EvaluationResult result = evaluator.evaluateQuery(doc, criterion);

        assertThat(result.state()).isEqualTo(EvaluationState.NOT_MATCHED);
    }

    @Test
    void contains_collectionWithNumberElement_shouldMatch() {
        Map<String, Object> doc = Map.of("numbers", List.of(1, 2, 3, 4, 5));
        QueryCriterion criterion = new QueryCriterion("test", Map.of("numbers", Map.of("$contains", 3)));

        EvaluationResult result = evaluator.evaluateQuery(doc, criterion);

        assertThat(result.state()).isEqualTo(EvaluationState.MATCHED);
    }

    @Test
    void contains_withNonStringOrCollection_shouldNotMatch() {
        Map<String, Object> doc = Map.of("count", 42);
        QueryCriterion criterion = new QueryCriterion("test", Map.of("count", Map.of("$contains", "4")));

        EvaluationResult result = evaluator.evaluateQuery(doc, criterion);

        assertThat(result.state()).isEqualTo(EvaluationState.NOT_MATCHED);
    }

    // ========== $startsWith Operator Tests ==========

    @Test
    void startsWith_withMatchingPrefix_shouldMatch() {
        Map<String, Object> doc = Map.of("email", "admin@example.com");
        QueryCriterion criterion = new QueryCriterion("test", Map.of("email", Map.of("$startsWith", "admin")));

        EvaluationResult result = evaluator.evaluateQuery(doc, criterion);

        assertThat(result.state()).isEqualTo(EvaluationState.MATCHED);
    }

    @Test
    void startsWith_withNonMatchingPrefix_shouldNotMatch() {
        Map<String, Object> doc = Map.of("email", "user@example.com");
        QueryCriterion criterion = new QueryCriterion("test", Map.of("email", Map.of("$startsWith", "admin")));

        EvaluationResult result = evaluator.evaluateQuery(doc, criterion);

        assertThat(result.state()).isEqualTo(EvaluationState.NOT_MATCHED);
    }

    @Test
    void startsWith_caseSensitive_shouldNotMatch() {
        Map<String, Object> doc = Map.of("email", "Admin@example.com");
        QueryCriterion criterion = new QueryCriterion("test", Map.of("email", Map.of("$startsWith", "admin")));

        EvaluationResult result = evaluator.evaluateQuery(doc, criterion);

        assertThat(result.state()).isEqualTo(EvaluationState.NOT_MATCHED);
    }

    @Test
    void startsWith_withEmptyPrefix_shouldMatch() {
        Map<String, Object> doc = Map.of("name", "John");
        QueryCriterion criterion = new QueryCriterion("test", Map.of("name", Map.of("$startsWith", "")));

        EvaluationResult result = evaluator.evaluateQuery(doc, criterion);

        assertThat(result.state()).isEqualTo(EvaluationState.MATCHED);
    }

    @Test
    void startsWith_withFullString_shouldMatch() {
        Map<String, Object> doc = Map.of("name", "John");
        QueryCriterion criterion = new QueryCriterion("test", Map.of("name", Map.of("$startsWith", "John")));

        EvaluationResult result = evaluator.evaluateQuery(doc, criterion);

        assertThat(result.state()).isEqualTo(EvaluationState.MATCHED);
    }

    @Test
    void startsWith_withPathPrefix_shouldMatch() {
        Map<String, Object> doc = Map.of("filename", "/var/log/app.log");
        QueryCriterion criterion = new QueryCriterion("test", Map.of("filename", Map.of("$startsWith", "/var/log/")));

        EvaluationResult result = evaluator.evaluateQuery(doc, criterion);

        assertThat(result.state()).isEqualTo(EvaluationState.MATCHED);
    }

    @Test
    void startsWith_withNonStringValue_shouldNotMatch() {
        Map<String, Object> doc = Map.of("count", 42);
        QueryCriterion criterion = new QueryCriterion("test", Map.of("count", Map.of("$startsWith", "4")));

        EvaluationResult result = evaluator.evaluateQuery(doc, criterion);

        assertThat(result.state()).isEqualTo(EvaluationState.NOT_MATCHED);
    }

    // ========== $endsWith Operator Tests ==========

    @Test
    void endsWith_withMatchingSuffix_shouldMatch() {
        Map<String, Object> doc = Map.of("filename", "document.pdf");
        QueryCriterion criterion = new QueryCriterion("test", Map.of("filename", Map.of("$endsWith", ".pdf")));

        EvaluationResult result = evaluator.evaluateQuery(doc, criterion);

        assertThat(result.state()).isEqualTo(EvaluationState.MATCHED);
    }

    @Test
    void endsWith_withNonMatchingSuffix_shouldNotMatch() {
        Map<String, Object> doc = Map.of("filename", "document.txt");
        QueryCriterion criterion = new QueryCriterion("test", Map.of("filename", Map.of("$endsWith", ".pdf")));

        EvaluationResult result = evaluator.evaluateQuery(doc, criterion);

        assertThat(result.state()).isEqualTo(EvaluationState.NOT_MATCHED);
    }

    @Test
    void endsWith_caseSensitive_shouldNotMatch() {
        Map<String, Object> doc = Map.of("filename", "document.PDF");
        QueryCriterion criterion = new QueryCriterion("test", Map.of("filename", Map.of("$endsWith", ".pdf")));

        EvaluationResult result = evaluator.evaluateQuery(doc, criterion);

        assertThat(result.state()).isEqualTo(EvaluationState.NOT_MATCHED);
    }

    @Test
    void endsWith_withEmptySuffix_shouldMatch() {
        Map<String, Object> doc = Map.of("name", "John");
        QueryCriterion criterion = new QueryCriterion("test", Map.of("name", Map.of("$endsWith", "")));

        EvaluationResult result = evaluator.evaluateQuery(doc, criterion);

        assertThat(result.state()).isEqualTo(EvaluationState.MATCHED);
    }

    @Test
    void endsWith_withFullString_shouldMatch() {
        Map<String, Object> doc = Map.of("name", "John");
        QueryCriterion criterion = new QueryCriterion("test", Map.of("name", Map.of("$endsWith", "John")));

        EvaluationResult result = evaluator.evaluateQuery(doc, criterion);

        assertThat(result.state()).isEqualTo(EvaluationState.MATCHED);
    }

    @Test
    void endsWith_withDomainSuffix_shouldMatch() {
        Map<String, Object> doc = Map.of("email", "user@company.com");
        QueryCriterion criterion = new QueryCriterion("test", Map.of("email", Map.of("$endsWith", "@company.com")));

        EvaluationResult result = evaluator.evaluateQuery(doc, criterion);

        assertThat(result.state()).isEqualTo(EvaluationState.MATCHED);
    }

    @Test
    void endsWith_withNonStringValue_shouldNotMatch() {
        Map<String, Object> doc = Map.of("count", 42);
        QueryCriterion criterion = new QueryCriterion("test", Map.of("count", Map.of("$endsWith", "2")));

        EvaluationResult result = evaluator.evaluateQuery(doc, criterion);

        assertThat(result.state()).isEqualTo(EvaluationState.NOT_MATCHED);
    }

    // ========== $not Operator Tests ==========

    @Test
    void not_invertEquality_shouldMatch() {
        Map<String, Object> doc = Map.of("status", "ACTIVE");
        QueryCriterion criterion = new QueryCriterion("test", Map.of("status", Map.of("$not", Map.of("$eq", "BANNED"))));

        EvaluationResult result = evaluator.evaluateQuery(doc, criterion);

        assertThat(result.state()).isEqualTo(EvaluationState.MATCHED);
    }

    @Test
    void not_invertEquality_shouldNotMatch() {
        Map<String, Object> doc = Map.of("status", "BANNED");
        QueryCriterion criterion = new QueryCriterion("test", Map.of("status", Map.of("$not", Map.of("$eq", "BANNED"))));

        EvaluationResult result = evaluator.evaluateQuery(doc, criterion);

        assertThat(result.state()).isEqualTo(EvaluationState.NOT_MATCHED);
    }

    @Test
    void not_invertIn_shouldMatch() {
        Map<String, Object> doc = Map.of("status", "ACTIVE");
        QueryCriterion criterion = new QueryCriterion("test",
            Map.of("status", Map.of("$not", Map.of("$in", List.of("BANNED", "SUSPENDED")))));

        EvaluationResult result = evaluator.evaluateQuery(doc, criterion);

        assertThat(result.state()).isEqualTo(EvaluationState.MATCHED);
    }

    @Test
    void not_invertIn_shouldNotMatch() {
        Map<String, Object> doc = Map.of("status", "BANNED");
        QueryCriterion criterion = new QueryCriterion("test",
            Map.of("status", Map.of("$not", Map.of("$in", List.of("BANNED", "SUSPENDED")))));

        EvaluationResult result = evaluator.evaluateQuery(doc, criterion);

        assertThat(result.state()).isEqualTo(EvaluationState.NOT_MATCHED);
    }

    @Test
    void not_invertGreaterThan_shouldMatch() {
        Map<String, Object> doc = Map.of("age", 15);
        QueryCriterion criterion = new QueryCriterion("test", Map.of("age", Map.of("$not", Map.of("$gte", 18))));

        EvaluationResult result = evaluator.evaluateQuery(doc, criterion);

        assertThat(result.state()).isEqualTo(EvaluationState.MATCHED);
    }

    @Test
    void not_invertGreaterThan_shouldNotMatch() {
        Map<String, Object> doc = Map.of("age", 25);
        QueryCriterion criterion = new QueryCriterion("test", Map.of("age", Map.of("$not", Map.of("$gte", 18))));

        EvaluationResult result = evaluator.evaluateQuery(doc, criterion);

        assertThat(result.state()).isEqualTo(EvaluationState.NOT_MATCHED);
    }

    @Test
    void not_invertRegex_shouldMatch() {
        Map<String, Object> doc = Map.of("email", "user@example.com");
        QueryCriterion criterion = new QueryCriterion("test",
            Map.of("email", Map.of("$not", Map.of("$regex", "^admin"))));

        EvaluationResult result = evaluator.evaluateQuery(doc, criterion);

        assertThat(result.state()).isEqualTo(EvaluationState.MATCHED);
    }

    @Test
    void not_invertRegex_shouldNotMatch() {
        Map<String, Object> doc = Map.of("email", "admin@example.com");
        QueryCriterion criterion = new QueryCriterion("test",
            Map.of("email", Map.of("$not", Map.of("$regex", "^admin"))));

        EvaluationResult result = evaluator.evaluateQuery(doc, criterion);

        assertThat(result.state()).isEqualTo(EvaluationState.NOT_MATCHED);
    }

    @Test
    void not_invertContains_shouldMatch() {
        Map<String, Object> doc = Map.of("description", "Normal task");
        QueryCriterion criterion = new QueryCriterion("test",
            Map.of("description", Map.of("$not", Map.of("$contains", "urgent"))));

        EvaluationResult result = evaluator.evaluateQuery(doc, criterion);

        assertThat(result.state()).isEqualTo(EvaluationState.MATCHED);
    }

    @Test
    void not_invertContains_shouldNotMatch() {
        Map<String, Object> doc = Map.of("description", "This is urgent");
        QueryCriterion criterion = new QueryCriterion("test",
            Map.of("description", Map.of("$not", Map.of("$contains", "urgent"))));

        EvaluationResult result = evaluator.evaluateQuery(doc, criterion);

        assertThat(result.state()).isEqualTo(EvaluationState.NOT_MATCHED);
    }

    @Test
    void not_withMultipleConditions_shouldMatch() {
        Map<String, Object> doc = Map.of("value", 5);
        // $not with multiple conditions: NOT (value >= 10 AND value <= 20)
        QueryCriterion criterion = new QueryCriterion("test",
            Map.of("value", Map.of("$not", Map.of("$gte", 10, "$lte", 20))));

        EvaluationResult result = evaluator.evaluateQuery(doc, criterion);

        assertThat(result.state()).isEqualTo(EvaluationState.MATCHED);
    }

    @Test
    void not_withMultipleConditions_shouldNotMatch() {
        Map<String, Object> doc = Map.of("value", 15);
        // $not with multiple conditions: NOT (value >= 10 AND value <= 20)
        QueryCriterion criterion = new QueryCriterion("test",
            Map.of("value", Map.of("$not", Map.of("$gte", 10, "$lte", 20))));

        EvaluationResult result = evaluator.evaluateQuery(doc, criterion);

        assertThat(result.state()).isEqualTo(EvaluationState.NOT_MATCHED);
    }

    @Test
    void not_withInvalidOperand_shouldNotMatch() {
        Map<String, Object> doc = Map.of("status", "ACTIVE");
        // $not with non-Map operand (invalid)
        QueryCriterion criterion = new QueryCriterion("test", Map.of("status", Map.of("$not", "BANNED")));

        EvaluationResult result = evaluator.evaluateQuery(doc, criterion);

        assertThat(result.state()).isEqualTo(EvaluationState.NOT_MATCHED);
    }

    // ========== Combined Operators Tests ==========

    @Test
    void combinedOperators_startsWithAndEndsWith_shouldMatch() {
        Map<String, Object> doc = Map.of("path", "/var/log/app.log");
        QueryCriterion criterion = new QueryCriterion("test",
            Map.of("path", Map.of("$startsWith", "/var/", "$endsWith", ".log")));

        EvaluationResult result = evaluator.evaluateQuery(doc, criterion);

        assertThat(result.state()).isEqualTo(EvaluationState.MATCHED);
    }

    @Test
    void combinedOperators_startsWithAndEndsWith_shouldNotMatch() {
        Map<String, Object> doc = Map.of("path", "/var/log/app.txt");
        QueryCriterion criterion = new QueryCriterion("test",
            Map.of("path", Map.of("$startsWith", "/var/", "$endsWith", ".log")));

        EvaluationResult result = evaluator.evaluateQuery(doc, criterion);

        assertThat(result.state()).isEqualTo(EvaluationState.NOT_MATCHED);
    }

    @Test
    void combinedOperators_containsAndNot_shouldMatch() {
        Map<String, Object> doc = Map.of("message", "Info: Operation completed");
        QueryCriterion criterion = new QueryCriterion("test",
            Map.of("message", Map.of(
                "$contains", "Info",
                "$not", Map.of("$contains", "Error")
            )));

        EvaluationResult result = evaluator.evaluateQuery(doc, criterion);

        assertThat(result.state()).isEqualTo(EvaluationState.MATCHED);
    }

    // ========== Missing Field Tests ==========

    @Test
    void contains_withMissingField_shouldBeUndetermined() {
        Map<String, Object> doc = Map.of("other", "value");
        QueryCriterion criterion = new QueryCriterion("test", Map.of("description", Map.of("$contains", "text")));

        EvaluationResult result = evaluator.evaluateQuery(doc, criterion);

        assertThat(result.state()).isEqualTo(EvaluationState.UNDETERMINED);
    }

    @Test
    void startsWith_withMissingField_shouldBeUndetermined() {
        Map<String, Object> doc = Map.of("other", "value");
        QueryCriterion criterion = new QueryCriterion("test", Map.of("email", Map.of("$startsWith", "admin")));

        EvaluationResult result = evaluator.evaluateQuery(doc, criterion);

        assertThat(result.state()).isEqualTo(EvaluationState.UNDETERMINED);
    }

    @Test
    void endsWith_withMissingField_shouldBeUndetermined() {
        Map<String, Object> doc = Map.of("other", "value");
        QueryCriterion criterion = new QueryCriterion("test", Map.of("filename", Map.of("$endsWith", ".pdf")));

        EvaluationResult result = evaluator.evaluateQuery(doc, criterion);

        assertThat(result.state()).isEqualTo(EvaluationState.UNDETERMINED);
    }

    @Test
    void not_withMissingField_shouldBeUndetermined() {
        Map<String, Object> doc = Map.of("other", "value");
        QueryCriterion criterion = new QueryCriterion("test", Map.of("status", Map.of("$not", Map.of("$eq", "BANNED"))));

        EvaluationResult result = evaluator.evaluateQuery(doc, criterion);

        assertThat(result.state()).isEqualTo(EvaluationState.UNDETERMINED);
    }

    // ========== Additional Edge Case Tests for Coverage ==========

    @Test
    void contains_withNullOperand_shouldNotMatch() {
        Map<String, Object> doc = Map.of("description", "test");
        // Create a map with null value - need to use HashMap since Map.of doesn't allow nulls
        Map<String, Object> query = new java.util.HashMap<>();
        query.put("$contains", null);
        QueryCriterion criterion = new QueryCriterion("test", Map.of("description", query));

        EvaluationResult result = evaluator.evaluateQuery(doc, criterion);

        assertThat(result.state()).isEqualTo(EvaluationState.NOT_MATCHED);
    }

    @Test
    void startsWith_withNullOperand_shouldNotMatch() {
        Map<String, Object> doc = Map.of("name", "test");
        Map<String, Object> query = new java.util.HashMap<>();
        query.put("$startsWith", null);
        QueryCriterion criterion = new QueryCriterion("test", Map.of("name", query));

        EvaluationResult result = evaluator.evaluateQuery(doc, criterion);

        assertThat(result.state()).isEqualTo(EvaluationState.NOT_MATCHED);
    }

    @Test
    void endsWith_withNullOperand_shouldNotMatch() {
        Map<String, Object> doc = Map.of("name", "test");
        Map<String, Object> query = new java.util.HashMap<>();
        query.put("$endsWith", null);
        QueryCriterion criterion = new QueryCriterion("test", Map.of("name", query));

        EvaluationResult result = evaluator.evaluateQuery(doc, criterion);

        assertThat(result.state()).isEqualTo(EvaluationState.NOT_MATCHED);
    }

    @Test
    void contains_withNonStringOperandOnString_shouldNotMatch() {
        Map<String, Object> doc = Map.of("description", "test string");
        QueryCriterion criterion = new QueryCriterion("test", Map.of("description", Map.of("$contains", 123)));

        EvaluationResult result = evaluator.evaluateQuery(doc, criterion);

        assertThat(result.state()).isEqualTo(EvaluationState.NOT_MATCHED);
    }

    @Test
    void startsWith_withNonStringOperand_shouldNotMatch() {
        Map<String, Object> doc = Map.of("name", "test");
        QueryCriterion criterion = new QueryCriterion("test", Map.of("name", Map.of("$startsWith", 123)));

        EvaluationResult result = evaluator.evaluateQuery(doc, criterion);

        assertThat(result.state()).isEqualTo(EvaluationState.NOT_MATCHED);
    }

    @Test
    void endsWith_withNonStringOperand_shouldNotMatch() {
        Map<String, Object> doc = Map.of("name", "test");
        QueryCriterion criterion = new QueryCriterion("test", Map.of("name", Map.of("$endsWith", 123)));

        EvaluationResult result = evaluator.evaluateQuery(doc, criterion);

        assertThat(result.state()).isEqualTo(EvaluationState.NOT_MATCHED);
    }

    @Test
    void not_withNullOperand_shouldNotMatch() {
        Map<String, Object> doc = Map.of("status", "ACTIVE");
        Map<String, Object> query = new java.util.HashMap<>();
        query.put("$not", null);
        QueryCriterion criterion = new QueryCriterion("test", Map.of("status", query));

        EvaluationResult result = evaluator.evaluateQuery(doc, criterion);

        assertThat(result.state()).isEqualTo(EvaluationState.NOT_MATCHED);
    }

    @Test
    void contains_collectionWithNullElement_shouldMatch() {
        // Test that collection contains works with null elements
        List<Object> listWithNull = new java.util.ArrayList<>();
        listWithNull.add("a");
        listWithNull.add(null);
        listWithNull.add("b");
        Map<String, Object> doc = Map.of("items", listWithNull);
        QueryCriterion criterion = new QueryCriterion("test", Map.of("items", Map.of("$contains", "a")));

        EvaluationResult result = evaluator.evaluateQuery(doc, criterion);

        assertThat(result.state()).isEqualTo(EvaluationState.MATCHED);
    }

    @Test
    void contains_withBooleanValue_shouldNotMatch() {
        Map<String, Object> doc = Map.of("flag", true);
        QueryCriterion criterion = new QueryCriterion("test", Map.of("flag", Map.of("$contains", "true")));

        EvaluationResult result = evaluator.evaluateQuery(doc, criterion);

        assertThat(result.state()).isEqualTo(EvaluationState.NOT_MATCHED);
    }

    @Test
    void startsWith_withEmptyValue_shouldMatch() {
        Map<String, Object> doc = Map.of("name", "");
        QueryCriterion criterion = new QueryCriterion("test", Map.of("name", Map.of("$startsWith", "")));

        EvaluationResult result = evaluator.evaluateQuery(doc, criterion);

        assertThat(result.state()).isEqualTo(EvaluationState.MATCHED);
    }

    @Test
    void endsWith_withEmptyValue_shouldMatch() {
        Map<String, Object> doc = Map.of("name", "");
        QueryCriterion criterion = new QueryCriterion("test", Map.of("name", Map.of("$endsWith", "")));

        EvaluationResult result = evaluator.evaluateQuery(doc, criterion);

        assertThat(result.state()).isEqualTo(EvaluationState.MATCHED);
    }

    @Test
    void not_withNestedUndetermined_staysUndetermined() {
        Map<String, Object> doc = Map.of("value", 42);
        QueryCriterion criterion = new QueryCriterion("test",
            Map.of("value", Map.of("$not", Map.of("$unknownOp", "test"))));

        EvaluationResult result = evaluator.evaluateQuery(doc, criterion);

        // Strong Kleene: the nested unknown operator is UNDETERMINED, and ¬UNDETERMINED
        // is UNDETERMINED — $not does not collapse "couldn't evaluate" into NOT_MATCHED.
        assertThat(result.state()).isEqualTo(EvaluationState.UNDETERMINED);
    }

    @Test
    void contains_withMapValue_shouldNotMatch() {
        Map<String, Object> doc = Map.of("nested", Map.of("key", "value"));
        QueryCriterion criterion = new QueryCriterion("test", Map.of("nested", Map.of("$contains", "key")));

        EvaluationResult result = evaluator.evaluateQuery(doc, criterion);

        assertThat(result.state()).isEqualTo(EvaluationState.NOT_MATCHED);
    }

    @Test
    void startsWith_withListValue_shouldNotMatch() {
        Map<String, Object> doc = Map.of("items", List.of("a", "b", "c"));
        QueryCriterion criterion = new QueryCriterion("test", Map.of("items", Map.of("$startsWith", "a")));

        EvaluationResult result = evaluator.evaluateQuery(doc, criterion);

        assertThat(result.state()).isEqualTo(EvaluationState.NOT_MATCHED);
    }

    @Test
    void endsWith_withListValue_shouldNotMatch() {
        Map<String, Object> doc = Map.of("items", List.of("a", "b", "c"));
        QueryCriterion criterion = new QueryCriterion("test", Map.of("items", Map.of("$endsWith", "c")));

        EvaluationResult result = evaluator.evaluateQuery(doc, criterion);

        assertThat(result.state()).isEqualTo(EvaluationState.NOT_MATCHED);
    }

    @Test
    void contains_specialCharacters_shouldMatch() {
        Map<String, Object> doc = Map.of("text", "Hello, World! @#$%^&*()");
        QueryCriterion criterion = new QueryCriterion("test", Map.of("text", Map.of("$contains", "@#$%")));

        EvaluationResult result = evaluator.evaluateQuery(doc, criterion);

        assertThat(result.state()).isEqualTo(EvaluationState.MATCHED);
    }

    @Test
    void startsWith_specialCharacters_shouldMatch() {
        Map<String, Object> doc = Map.of("text", "@user: message");
        QueryCriterion criterion = new QueryCriterion("test", Map.of("text", Map.of("$startsWith", "@user")));

        EvaluationResult result = evaluator.evaluateQuery(doc, criterion);

        assertThat(result.state()).isEqualTo(EvaluationState.MATCHED);
    }

    @Test
    void endsWith_specialCharacters_shouldMatch() {
        Map<String, Object> doc = Map.of("filename", "data.tar.gz");
        QueryCriterion criterion = new QueryCriterion("test", Map.of("filename", Map.of("$endsWith", ".tar.gz")));

        EvaluationResult result = evaluator.evaluateQuery(doc, criterion);

        assertThat(result.state()).isEqualTo(EvaluationState.MATCHED);
    }

    @Test
    void contains_unicodeCharacters_shouldMatch() {
        Map<String, Object> doc = Map.of("text", "Hello 世界 🌍");
        QueryCriterion criterion = new QueryCriterion("test", Map.of("text", Map.of("$contains", "世界")));

        EvaluationResult result = evaluator.evaluateQuery(doc, criterion);

        assertThat(result.state()).isEqualTo(EvaluationState.MATCHED);
    }
}
