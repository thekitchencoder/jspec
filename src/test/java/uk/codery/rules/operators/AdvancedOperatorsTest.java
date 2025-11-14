package uk.codery.rules.operators;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.codery.rules.*;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Comprehensive tests for advanced operators: $exists, $type, $regex, $elemMatch
 * These tests baseline the current behavior for all advanced operators.
 */
class AdvancedOperatorsTest {

    private RuleEvaluator evaluator;

    @BeforeEach
    void setUp() {
        evaluator = new RuleEvaluator();
    }

    // ========== $exists Operator Tests ==========

    @Test
    void exists_withPresentField_andTrueOperand_shouldMatch() {
        Map<String, Object> doc = Map.of("name", "John", "age", 25);
        Rule rule = new Rule("test", Map.of("name", Map.of("$exists", true)));

        EvaluationResult result = evaluator.evaluateRule(doc, rule);

        assertThat(result.state()).isEqualTo(EvaluationState.MATCHED);
    }

    @Test
    void exists_withMissingField_andTrueOperand_shouldNotMatch() {
        Map<String, Object> doc = Map.of("name", "John");
        Rule rule = new Rule("test", Map.of("age", Map.of("$exists", true)));

        EvaluationResult result = evaluator.evaluateRule(doc, rule);

        // Field doesn't exist, but we're checking if it exists=true
        // Missing field becomes null in evaluation
        assertThat(result.state()).isEqualTo(EvaluationState.NOT_MATCHED);
    }

    @Test
    void exists_withMissingField_andFalseOperand_shouldMatch() {
        Map<String, Object> doc = Map.of("name", "John");
        Rule rule = new Rule("test", Map.of("age", Map.of("$exists", false)));

        EvaluationResult result = evaluator.evaluateRule(doc, rule);

        // Field doesn't exist, checking exists=false should match
        assertThat(result.state()).isEqualTo(EvaluationState.MATCHED);
    }

    @Test
    void exists_withPresentField_andFalseOperand_shouldNotMatch() {
        Map<String, Object> doc = Map.of("name", "John", "age", 25);
        Rule rule = new Rule("test", Map.of("name", Map.of("$exists", false)));

        EvaluationResult result = evaluator.evaluateRule(doc, rule);

        assertThat(result.state()).isEqualTo(EvaluationState.NOT_MATCHED);
    }

    @Test
    void exists_withNonBooleanOperand_shouldNotMatch() {
        Map<String, Object> doc = Map.of("name", "John");
        Rule rule = new Rule("test", Map.of("name", Map.of("$exists", "yes")));

        EvaluationResult result = evaluator.evaluateRule(doc, rule);

        // Type mismatch logged as warning
        assertThat(result.state()).isEqualTo(EvaluationState.NOT_MATCHED);
    }

    @Test
    void exists_withNestedField_andTrueOperand_shouldMatch() {
        Map<String, Object> doc = Map.of("user", Map.of("profile", Map.of("email", "test@example.com")));
        Rule rule = new Rule("test", Map.of("user", Map.of("profile", Map.of("email", Map.of("$exists", true)))));

        EvaluationResult result = evaluator.evaluateRule(doc, rule);

        assertThat(result.state()).isEqualTo(EvaluationState.MATCHED);
    }

    @Test
    void exists_withNullValue_shouldWork() {
        Map<String, Object> doc = Map.of("name", "unimportant");
        Rule rule = new Rule("test", Map.of("missing", Map.of("$exists", true)));

        EvaluationResult result = evaluator.evaluateRule(doc, rule);

        // Missing field returns null from navigation
        assertThat(result.state()).isEqualTo(EvaluationState.NOT_MATCHED);
    }

    // ========== $type Operator Tests ==========

    @Test
    void type_withString_shouldMatch() {
        Map<String, Object> doc = Map.of("name", "John");
        Rule rule = new Rule("test", Map.of("name", Map.of("$type", "string")));

        EvaluationResult result = evaluator.evaluateRule(doc, rule);

        assertThat(result.state()).isEqualTo(EvaluationState.MATCHED);
    }

    @Test
    void type_withNumber_shouldMatch() {
        Map<String, Object> doc = Map.of("age", 25);
        Rule rule = new Rule("test", Map.of("age", Map.of("$type", "number")));

        EvaluationResult result = evaluator.evaluateRule(doc, rule);

        assertThat(result.state()).isEqualTo(EvaluationState.MATCHED);
    }

    @Test
    void type_withDouble_shouldMatchNumber() {
        Map<String, Object> doc = Map.of("price", 19.99);
        Rule rule = new Rule("test", Map.of("price", Map.of("$type", "number")));

        EvaluationResult result = evaluator.evaluateRule(doc, rule);

        assertThat(result.state()).isEqualTo(EvaluationState.MATCHED);
    }

    @Test
    void type_withBoolean_shouldMatch() {
        Map<String, Object> doc = Map.of("active", true);
        Rule rule = new Rule("test", Map.of("active", Map.of("$type", "boolean")));

        EvaluationResult result = evaluator.evaluateRule(doc, rule);

        assertThat(result.state()).isEqualTo(EvaluationState.MATCHED);
    }

    @Test
    void type_withArray_shouldMatch() {
        Map<String, Object> doc = Map.of("tags", List.of("admin", "user"));
        Rule rule = new Rule("test", Map.of("tags", Map.of("$type", "array")));

        EvaluationResult result = evaluator.evaluateRule(doc, rule);

        assertThat(result.state()).isEqualTo(EvaluationState.MATCHED);
    }

    @Test
    void type_withObject_shouldMatch() {
        Map<String, Object> doc = Map.of("user", Map.of("name", "John"));
        Rule rule = new Rule("test", Map.of("user", Map.of("$type", "object")));

        EvaluationResult result = evaluator.evaluateRule(doc, rule);

        assertThat(result.state()).isEqualTo(EvaluationState.MATCHED);
    }

    @Test
    void type_withWrongType_shouldNotMatch() {
        Map<String, Object> doc = Map.of("age", 25);
        Rule rule = new Rule("test", Map.of("age", Map.of("$type", "string")));

        EvaluationResult result = evaluator.evaluateRule(doc, rule);

        assertThat(result.state()).isEqualTo(EvaluationState.NOT_MATCHED);
    }

    @Test
    void type_withMissingField_shouldBeUndetermined() {
        Map<String, Object> doc = Map.of("name", "John");
        Rule rule = new Rule("test", Map.of("age", Map.of("$type", "number")));

        EvaluationResult result = evaluator.evaluateRule(doc, rule);

        assertThat(result.state()).isEqualTo(EvaluationState.UNDETERMINED);
        assertThat(result.missingPaths()).contains("age");
    }

    @Test
    void type_caseSensitivity_shouldMatter() {
        Map<String, Object> doc = Map.of("name", "John");
        Rule rule = new Rule("test", Map.of("name", Map.of("$type", "String")));

        EvaluationResult result = evaluator.evaluateRule(doc, rule);

        // Type is lowercase "string", not "String"
        assertThat(result.state()).isEqualTo(EvaluationState.NOT_MATCHED);
    }

    // ========== $regex Operator Tests ==========

    @Test
    void regex_withMatchingPattern_shouldMatch() {
        Map<String, Object> doc = Map.of("email", "test@example.com");
        Rule rule = new Rule("test", Map.of("email", Map.of("$regex", ".*@example\\.com")));

        EvaluationResult result = evaluator.evaluateRule(doc, rule);

        assertThat(result.state()).isEqualTo(EvaluationState.MATCHED);
    }

    @Test
    void regex_withNonMatchingPattern_shouldNotMatch() {
        Map<String, Object> doc = Map.of("email", "test@example.com");
        Rule rule = new Rule("test", Map.of("email", Map.of("$regex", ".*@gmail\\.com")));

        EvaluationResult result = evaluator.evaluateRule(doc, rule);

        assertThat(result.state()).isEqualTo(EvaluationState.NOT_MATCHED);
    }

    @Test
    void regex_withSimplePattern_shouldMatch() {
        Map<String, Object> doc = Map.of("name", "John Doe");
        Rule rule = new Rule("test", Map.of("name", Map.of("$regex", "John.*")));

        EvaluationResult result = evaluator.evaluateRule(doc, rule);

        assertThat(result.state()).isEqualTo(EvaluationState.MATCHED);
    }

    @Test
    void regex_withCaseSensitivePattern_shouldNotMatch() {
        Map<String, Object> doc = Map.of("name", "john doe");
        Rule rule = new Rule("test", Map.of("name", Map.of("$regex", "John.*")));

        EvaluationResult result = evaluator.evaluateRule(doc, rule);

        assertThat(result.state()).isEqualTo(EvaluationState.NOT_MATCHED);
    }

    @Test
    void regex_withDigitPattern_shouldMatch() {
        Map<String, Object> doc = Map.of("code", "ABC123");
        Rule rule = new Rule("test", Map.of("code", Map.of("$regex", ".*\\d+$")));

        EvaluationResult result = evaluator.evaluateRule(doc, rule);

        assertThat(result.state()).isEqualTo(EvaluationState.MATCHED);
    }

    @Test
    void regex_withAnchoredPattern_shouldWork() {
        Map<String, Object> doc = Map.of("name", "John");
        Rule rule = new Rule("test", Map.of("name", Map.of("$regex", "^John$")));

        EvaluationResult result = evaluator.evaluateRule(doc, rule);

        assertThat(result.state()).isEqualTo(EvaluationState.MATCHED);
    }

    @Test
    void regex_withInvalidPattern_shouldNotMatch() {
        Map<String, Object> doc = Map.of("name", "John");
        Rule rule = new Rule("test", Map.of("name", Map.of("$regex", "[invalid")));

        EvaluationResult result = evaluator.evaluateRule(doc, rule);

        // Invalid regex logged as warning, returns NOT_MATCHED
        assertThat(result.state()).isEqualTo(EvaluationState.NOT_MATCHED);
    }

    @Test
    void regex_withNonStringOperand_shouldNotMatch() {
        Map<String, Object> doc = Map.of("name", "John");
        Rule rule = new Rule("test", Map.of("name", Map.of("$regex", 123)));

        EvaluationResult result = evaluator.evaluateRule(doc, rule);

        // Type mismatch logged as warning
        assertThat(result.state()).isEqualTo(EvaluationState.NOT_MATCHED);
    }

    @Test
    void regex_withNumberValue_shouldConvertToString() {
        Map<String, Object> doc = Map.of("code", 123);
        Rule rule = new Rule("test", Map.of("code", Map.of("$regex", "\\d+")));

        EvaluationResult result = evaluator.evaluateRule(doc, rule);

        // Number converts to string via String.valueOf()
        assertThat(result.state()).isEqualTo(EvaluationState.MATCHED);
    }

    @Test
    void regex_withPartialMatch_shouldMatch() {
        Map<String, Object> doc = Map.of("description", "This is a test description");
        Rule rule = new Rule("test", Map.of("description", Map.of("$regex", "test")));

        EvaluationResult result = evaluator.evaluateRule(doc, rule);

        // find() matches substring
        assertThat(result.state()).isEqualTo(EvaluationState.MATCHED);
    }

    @Test
    void regex_withEmptyString_shouldWork() {
        Map<String, Object> doc = Map.of("name", "");
        Rule rule = new Rule("test", Map.of("name", Map.of("$regex", "^$")));

        EvaluationResult result = evaluator.evaluateRule(doc, rule);

        assertThat(result.state()).isEqualTo(EvaluationState.MATCHED);
    }

    // ========== $elemMatch Operator Tests ==========

    @Test
    void elemMatch_withMatchingElement_shouldMatch() {
        Map<String, Object> doc = Map.of("users", List.of(
            Map.of("name", "Alice", "age", 25),
            Map.of("name", "Bob", "age", 30)
        ));
        Rule rule = new Rule("test", Map.of("users", Map.of("$elemMatch",
            Map.of("age", Map.of("$gte", 30))
        )));

        EvaluationResult result = evaluator.evaluateRule(doc, rule);

        assertThat(result.state()).isEqualTo(EvaluationState.MATCHED);
    }

    @Test
    void elemMatch_withNoMatchingElement_shouldNotMatch() {
        Map<String, Object> doc = Map.of("users", List.of(
            Map.of("name", "Alice", "age", 25),
            Map.of("name", "Bob", "age", 28)
        ));
        Rule rule = new Rule("test", Map.of("users", Map.of("$elemMatch",
            Map.of("age", Map.of("$gte", 30))
        )));

        EvaluationResult result = evaluator.evaluateRule(doc, rule);

        assertThat(result.state()).isEqualTo(EvaluationState.NOT_MATCHED);
    }

    @Test
    void elemMatch_withComplexQuery_shouldMatch() {
        Map<String, Object> doc = Map.of("users", List.of(
            Map.of("name", "Alice", "age", 25, "active", true),
            Map.of("name", "Bob", "age", 30, "active", false)
        ));
        Rule rule = new Rule("test", Map.of("users", Map.of("$elemMatch",
            Map.of("age", Map.of("$gte", 30), "active", Map.of("$eq", false))
        )));

        EvaluationResult result = evaluator.evaluateRule(doc, rule);

        assertThat(result.state()).isEqualTo(EvaluationState.MATCHED);
    }

    @Test
    void elemMatch_withEmptyList_shouldNotMatch() {
        Map<String, Object> doc = Map.of("users", List.of());
        Rule rule = new Rule("test", Map.of("users", Map.of("$elemMatch",
            Map.of("age", Map.of("$gte", 30))
        )));

        EvaluationResult result = evaluator.evaluateRule(doc, rule);

        assertThat(result.state()).isEqualTo(EvaluationState.NOT_MATCHED);
    }

    @Test
    void elemMatch_withNonListValue_shouldNotMatch() {
        Map<String, Object> doc = Map.of("user", Map.of("name", "Alice"));
        Rule rule = new Rule("test", Map.of("user", Map.of("$elemMatch",
            Map.of("name", Map.of("$eq", "Alice"))
        )));

        EvaluationResult result = evaluator.evaluateRule(doc, rule);

        // Value is not a list
        assertThat(result.state()).isEqualTo(EvaluationState.NOT_MATCHED);
    }

    @Test
    void elemMatch_withNonMapOperand_shouldNotMatch() {
        Map<String, Object> doc = Map.of("users", List.of("Alice", "Bob"));
        Rule rule = new Rule("test", Map.of("users", Map.of("$elemMatch", "Alice")));

        EvaluationResult result = evaluator.evaluateRule(doc, rule);

        // Operand is not a Map
        assertThat(result.state()).isEqualTo(EvaluationState.NOT_MATCHED);
    }

    @Test
    void elemMatch_withSimpleQuery_shouldMatch() {
        Map<String, Object> doc = Map.of("numbers", List.of(
            Map.of("value", 10),
            Map.of("value", 20),
            Map.of("value", 30)
        ));
        Rule rule = new Rule("test", Map.of("numbers", Map.of("$elemMatch",
            Map.of("value", Map.of("$eq", 20))
        )));

        EvaluationResult result = evaluator.evaluateRule(doc, rule);

        assertThat(result.state()).isEqualTo(EvaluationState.MATCHED);
    }

    @Test
    void elemMatch_withNestedOperators_shouldMatch() {
        Map<String, Object> doc = Map.of("items", List.of(
            Map.of("tags", List.of("sale", "new")),
            Map.of("tags", List.of("featured"))
        ));
        Rule rule = new Rule("test", Map.of("items", Map.of("$elemMatch",
            Map.of("tags", Map.of("$in", List.of("sale", "clearance")))
        )));

        EvaluationResult result = evaluator.evaluateRule(doc, rule);

        assertThat(result.state()).isEqualTo(EvaluationState.MATCHED);
    }

    @Test
    void elemMatch_withMissingField_shouldBeUndetermined() {
        Map<String, Object> doc = Map.of("name", "John");
        Rule rule = new Rule("test", Map.of("users", Map.of("$elemMatch",
            Map.of("age", Map.of("$gte", 30))
        )));

        EvaluationResult result = evaluator.evaluateRule(doc, rule);

        assertThat(result.state()).isEqualTo(EvaluationState.UNDETERMINED);
        assertThat(result.missingPaths()).contains("users");
    }

    // ========== Combined Advanced Operators ==========

    @Test
    void combined_existsAndType_shouldWork() {
        Map<String, Object> doc = Map.of("email", "test@example.com");
        Rule rule = new Rule("test", Map.of("email", Map.of(
            "$exists", true,
            "$type", "string"
        )));

        EvaluationResult result = evaluator.evaluateRule(doc, rule);

        assertThat(result.state()).isEqualTo(EvaluationState.MATCHED);
    }

    @Test
    void combined_typeAndRegex_shouldWork() {
        Map<String, Object> doc = Map.of("email", "test@example.com");
        Rule rule = new Rule("test", Map.of("email", Map.of(
            "$type", "string",
            "$regex", ".*@example\\.com"
        )));

        EvaluationResult result = evaluator.evaluateRule(doc, rule);

        assertThat(result.state()).isEqualTo(EvaluationState.MATCHED);
    }

    @Test
    void combined_existsTypeAndRegex_allMustMatch() {
        Map<String, Object> doc = Map.of("email", "test@example.com");
        Rule rule = new Rule("test", Map.of("email", Map.of(
            "$exists", true,
            "$type", "string",
            "$regex", ".*@gmail\\.com"
        )));

        EvaluationResult result = evaluator.evaluateRule(doc, rule);

        // All operators must match, regex fails
        assertThat(result.state()).isEqualTo(EvaluationState.NOT_MATCHED);
    }

    // ========== Edge Cases ==========

    @Test
    void regex_withSpecialCharacters_shouldWork() {
        Map<String, Object> doc = Map.of("path", "/api/v1/users");
        Rule rule = new Rule("test", Map.of("path", Map.of("$regex", "^/api/.*")));

        EvaluationResult result = evaluator.evaluateRule(doc, rule);

        assertThat(result.state()).isEqualTo(EvaluationState.MATCHED);
    }

    @Test
    void elemMatch_withFirstElementMatching_shouldMatch() {
        Map<String, Object> doc = Map.of("items", List.of(
            Map.of("priority", 1),
            Map.of("priority", 2)
        ));
        Rule rule = new Rule("test", Map.of("items", Map.of("$elemMatch",
            Map.of("priority", Map.of("$eq", 1))
        )));

        EvaluationResult result = evaluator.evaluateRule(doc, rule);

        assertThat(result.state()).isEqualTo(EvaluationState.MATCHED);
    }

    @Test
    void elemMatch_withLastElementMatching_shouldMatch() {
        Map<String, Object> doc = Map.of("items", List.of(
            Map.of("priority", 1),
            Map.of("priority", 2)
        ));
        Rule rule = new Rule("test", Map.of("items", Map.of("$elemMatch",
            Map.of("priority", Map.of("$eq", 2))
        )));

        EvaluationResult result = evaluator.evaluateRule(doc, rule);

        assertThat(result.state()).isEqualTo(EvaluationState.MATCHED);
    }
}
