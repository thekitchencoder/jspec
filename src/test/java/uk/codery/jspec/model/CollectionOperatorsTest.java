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
 * Comprehensive tests for collection junctions: $in, $nin, $all, $size
 * These tests baseline the current behavior for all collection junctions.
 */
class CollectionJunctionsTest {

    private CriterionEvaluator evaluator;

    @BeforeEach
    void setUp() {
        evaluator = new CriterionEvaluator();
    }

    // ========== $in Junction Tests ==========

    @Test
    void in_withMatchingStringValue_shouldMatch() {
        Map<String, Object> doc = Map.of("status", "ACTIVE");
        Criterion criterion = new Criterion("test", Map.of("status", Map.of("$in", List.of("ACTIVE", "PENDING"))));

        EvaluationResult result = evaluator.evaluateCriterion(doc, criterion);

        assertThat(result.state()).isEqualTo(EvaluationState.MATCHED);
    }

    @Test
    void in_withNonMatchingValue_shouldNotMatch() {
        Map<String, Object> doc = Map.of("status", "INACTIVE");
        Criterion criterion = new Criterion("test", Map.of("status", Map.of("$in", List.of("ACTIVE", "PENDING"))));

        EvaluationResult result = evaluator.evaluateCriterion(doc, criterion);

        assertThat(result.state()).isEqualTo(EvaluationState.NOT_MATCHED);
    }

    @Test
    void in_withMatchingIntegerValue_shouldMatch() {
        Map<String, Object> doc = Map.of("age", 25);
        Criterion criterion = new Criterion("test", Map.of("age", Map.of("$in", List.of(18, 25, 30, 40))));

        EvaluationResult result = evaluator.evaluateCriterion(doc, criterion);

        assertThat(result.state()).isEqualTo(EvaluationState.MATCHED);
    }

    @Test
    void in_withSingleElementList_shouldWork() {
        Map<String, Object> doc = Map.of("status", "ACTIVE");
        Criterion criterion = new Criterion("test", Map.of("status", Map.of("$in", List.of("ACTIVE"))));

        EvaluationResult result = evaluator.evaluateCriterion(doc, criterion);

        assertThat(result.state()).isEqualTo(EvaluationState.MATCHED);
    }

    @Test
    void in_withEmptyList_shouldNotMatch() {
        Map<String, Object> doc = Map.of("status", "ACTIVE");
        Criterion criterion = new Criterion("test", Map.of("status", Map.of("$in", List.of())));

        EvaluationResult result = evaluator.evaluateCriterion(doc, criterion);

        assertThat(result.state()).isEqualTo(EvaluationState.NOT_MATCHED);
    }

    @Test
    void in_withNonListOperand_shouldNotMatch() {
        Map<String, Object> doc = Map.of("status", "ACTIVE");
        Criterion criterion = new Criterion("test", Map.of("status", Map.of("$in", "ACTIVE")));

        EvaluationResult result = evaluator.evaluateCriterion(doc, criterion);

        // Type mismatch logged as warning, returns NOT_MATCHED
        assertThat(result.state()).isEqualTo(EvaluationState.NOT_MATCHED);
    }

    @Test
    void in_withMixedTypes_shouldWork() {
        Map<String, Object> doc = Map.of("value", 42);
        Criterion criterion = new Criterion("test", Map.of("value", Map.of("$in", List.of("42", 42, true))));

        EvaluationResult result = evaluator.evaluateCriterion(doc, criterion);

        assertThat(result.state()).isEqualTo(EvaluationState.MATCHED);
    }

    @Test
    void in_withBlankValue_shouldWork() {
        Map<String, Object> doc = Map.of("name", "test");
        // Querying missing field
        Criterion criterion = new Criterion("test", Map.of("missing", Map.of("$in", List.of("", "value"))));

        EvaluationResult result = evaluator.evaluateCriterion(doc, criterion);

        // Missing field becomes UNDETERMINED
        assertThat(result.state()).isEqualTo(EvaluationState.UNDETERMINED);
    }

    // ========== $nin Junction Tests ==========

    @Test
    void nin_withNonMatchingValue_shouldMatch() {
        Map<String, Object> doc = Map.of("status", "INACTIVE");
        Criterion criterion = new Criterion("test", Map.of("status", Map.of("$nin", List.of("ACTIVE", "PENDING"))));

        EvaluationResult result = evaluator.evaluateCriterion(doc, criterion);

        assertThat(result.state()).isEqualTo(EvaluationState.MATCHED);
    }

    @Test
    void nin_withMatchingValue_shouldNotMatch() {
        Map<String, Object> doc = Map.of("status", "ACTIVE");
        Criterion criterion = new Criterion("test", Map.of("status", Map.of("$nin", List.of("ACTIVE", "PENDING"))));

        EvaluationResult result = evaluator.evaluateCriterion(doc, criterion);

        assertThat(result.state()).isEqualTo(EvaluationState.NOT_MATCHED);
    }

    @Test
    void nin_withEmptyList_shouldMatch() {
        Map<String, Object> doc = Map.of("status", "ACTIVE");
        Criterion criterion = new Criterion("test", Map.of("status", Map.of("$nin", List.of())));

        EvaluationResult result = evaluator.evaluateCriterion(doc, criterion);

        // Not in empty list means matches (everything is not in empty list)
        assertThat(result.state()).isEqualTo(EvaluationState.MATCHED);
    }

    @Test
    void nin_withIntegers_shouldWork() {
        Map<String, Object> doc = Map.of("age", 35);
        Criterion criterion = new Criterion("test", Map.of("age", Map.of("$nin", List.of(18, 25, 30))));

        EvaluationResult result = evaluator.evaluateCriterion(doc, criterion);

        assertThat(result.state()).isEqualTo(EvaluationState.MATCHED);
    }

    @Test
    void nin_withNonListOperand_shouldNotMatch() {
        Map<String, Object> doc = Map.of("status", "INACTIVE");
        Criterion criterion = new Criterion("test", Map.of("status", Map.of("$nin", "ACTIVE")));

        EvaluationResult result = evaluator.evaluateCriterion(doc, criterion);

        // Type mismatch logged as warning, returns NOT_MATCHED
        assertThat(result.state()).isEqualTo(EvaluationState.NOT_MATCHED);
    }

    // ========== $all Junction Tests ==========

    @Test
    void all_withAllElementsPresent_shouldMatch() {
        Map<String, Object> doc = Map.of("tags", List.of("admin", "user", "verified"));
        Criterion criterion = new Criterion("test", Map.of("tags", Map.of("$all", List.of("admin", "user"))));

        EvaluationResult result = evaluator.evaluateCriterion(doc, criterion);

        assertThat(result.state()).isEqualTo(EvaluationState.MATCHED);
    }

    @Test
    void all_withMissingElement_shouldNotMatch() {
        Map<String, Object> doc = Map.of("tags", List.of("admin", "user"));
        Criterion criterion = new Criterion("test", Map.of("tags", Map.of("$all", List.of("admin", "verified"))));

        EvaluationResult result = evaluator.evaluateCriterion(doc, criterion);

        assertThat(result.state()).isEqualTo(EvaluationState.NOT_MATCHED);
    }

    @Test
    void all_withExactMatch_shouldMatch() {
        Map<String, Object> doc = Map.of("tags", List.of("admin", "user"));
        Criterion criterion = new Criterion("test", Map.of("tags", Map.of("$all", List.of("admin", "user"))));

        EvaluationResult result = evaluator.evaluateCriterion(doc, criterion);

        assertThat(result.state()).isEqualTo(EvaluationState.MATCHED);
    }

    @Test
    void all_withEmptyQueryList_shouldMatch() {
        Map<String, Object> doc = Map.of("tags", List.of("admin", "user"));
        Criterion criterion = new Criterion("test", Map.of("tags", Map.of("$all", List.of())));

        EvaluationResult result = evaluator.evaluateCriterion(doc, criterion);

        // Empty list means all elements are present (vacuous truth)
        assertThat(result.state()).isEqualTo(EvaluationState.MATCHED);
    }

    @Test
    void all_withEmptyDocumentList_shouldNotMatch() {
        Map<String, Object> doc = Map.of("tags", List.of());
        Criterion criterion = new Criterion("test", Map.of("tags", Map.of("$all", List.of("admin"))));

        EvaluationResult result = evaluator.evaluateCriterion(doc, criterion);

        assertThat(result.state()).isEqualTo(EvaluationState.NOT_MATCHED);
    }

    @Test
    void all_withSingleElement_shouldWork() {
        Map<String, Object> doc = Map.of("tags", List.of("admin", "user"));
        Criterion criterion = new Criterion("test", Map.of("tags", Map.of("$all", List.of("admin"))));

        EvaluationResult result = evaluator.evaluateCriterion(doc, criterion);

        assertThat(result.state()).isEqualTo(EvaluationState.MATCHED);
    }

    @Test
    void all_withNonListValue_shouldNotMatch() {
        Map<String, Object> doc = Map.of("tags", "admin");
        Criterion criterion = new Criterion("test", Map.of("tags", Map.of("$all", List.of("admin"))));

        EvaluationResult result = evaluator.evaluateCriterion(doc, criterion);

        // Value is not a list
        assertThat(result.state()).isEqualTo(EvaluationState.NOT_MATCHED);
    }

    @Test
    void all_withNonListOperand_shouldNotMatch() {
        Map<String, Object> doc = Map.of("tags", List.of("admin", "user"));
        Criterion criterion = new Criterion("test", Map.of("tags", Map.of("$all", "admin")));

        EvaluationResult result = evaluator.evaluateCriterion(doc, criterion);

        // Operand is not a list
        assertThat(result.state()).isEqualTo(EvaluationState.NOT_MATCHED);
    }

    @Test
    void all_withDuplicatesInQueryList_shouldWork() {
        Map<String, Object> doc = Map.of("tags", List.of("admin", "user"));
        Criterion criterion = new Criterion("test", Map.of("tags", Map.of("$all", List.of("admin", "admin"))));

        EvaluationResult result = evaluator.evaluateCriterion(doc, criterion);

        // containsAll should handle duplicates correctly
        assertThat(result.state()).isEqualTo(EvaluationState.MATCHED);
    }

    @Test
    void all_withIntegers_shouldWork() {
        Map<String, Object> doc = Map.of("numbers", List.of(1, 2, 3, 4, 5));
        Criterion criterion = new Criterion("test", Map.of("numbers", Map.of("$all", List.of(2, 4))));

        EvaluationResult result = evaluator.evaluateCriterion(doc, criterion);

        assertThat(result.state()).isEqualTo(EvaluationState.MATCHED);
    }

    // ========== $size Junction Tests ==========

    @Test
    void size_withMatchingSize_shouldMatch() {
        Map<String, Object> doc = Map.of("tags", List.of("admin", "user"));
        Criterion criterion = new Criterion("test", Map.of("tags", Map.of("$size", 2)));

        EvaluationResult result = evaluator.evaluateCriterion(doc, criterion);

        assertThat(result.state()).isEqualTo(EvaluationState.MATCHED);
    }

    @Test
    void size_withNonMatchingSize_shouldNotMatch() {
        Map<String, Object> doc = Map.of("tags", List.of("admin", "user"));
        Criterion criterion = new Criterion("test", Map.of("tags", Map.of("$size", 3)));

        EvaluationResult result = evaluator.evaluateCriterion(doc, criterion);

        assertThat(result.state()).isEqualTo(EvaluationState.NOT_MATCHED);
    }

    @Test
    void size_withEmptyList_shouldMatch() {
        Map<String, Object> doc = Map.of("tags", List.of());
        Criterion criterion = new Criterion("test", Map.of("tags", Map.of("$size", 0)));

        EvaluationResult result = evaluator.evaluateCriterion(doc, criterion);

        assertThat(result.state()).isEqualTo(EvaluationState.MATCHED);
    }

    @Test
    void size_withNonListValue_shouldNotMatch() {
        Map<String, Object> doc = Map.of("tags", "single-value");
        Criterion criterion = new Criterion("test", Map.of("tags", Map.of("$size", 1)));

        EvaluationResult result = evaluator.evaluateCriterion(doc, criterion);

        // Value is not a list
        assertThat(result.state()).isEqualTo(EvaluationState.NOT_MATCHED);
    }

    @Test
    void size_withNonNumberOperand_shouldNotMatch() {
        Map<String, Object> doc = Map.of("tags", List.of("admin", "user"));
        Criterion criterion = new Criterion("test", Map.of("tags", Map.of("$size", "2")));

        EvaluationResult result = evaluator.evaluateCriterion(doc, criterion);

        // Operand is not a number
        assertThat(result.state()).isEqualTo(EvaluationState.NOT_MATCHED);
    }

    @Test
    void size_withLargeList_shouldWork() {
        List<Integer> largeList = List.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10);
        Map<String, Object> doc = Map.of("numbers", largeList);
        Criterion criterion = new Criterion("test", Map.of("numbers", Map.of("$size", 10)));

        EvaluationResult result = evaluator.evaluateCriterion(doc, criterion);

        assertThat(result.state()).isEqualTo(EvaluationState.MATCHED);
    }

    @Test
    void size_withDoubleOperand_shouldWork() {
        Map<String, Object> doc = Map.of("tags", List.of("admin", "user"));
        Criterion criterion = new Criterion("test", Map.of("tags", Map.of("$size", 2.0)));

        EvaluationResult result = evaluator.evaluateCriterion(doc, criterion);

        // Double 2.0 should convert to int 2
        assertThat(result.state()).isEqualTo(EvaluationState.MATCHED);
    }

    @Test
    void size_withNegativeSize_shouldNotMatch() {
        Map<String, Object> doc = Map.of("tags", List.of("admin", "user"));
        Criterion criterion = new Criterion("test", Map.of("tags", Map.of("$size", -1)));

        EvaluationResult result = evaluator.evaluateCriterion(doc, criterion);

        // List can never have negative size
        assertThat(result.state()).isEqualTo(EvaluationState.NOT_MATCHED);
    }

    // ========== Combined Collection Junctions ==========

    @Test
    void combined_inAndSize_shouldWork() {
        Map<String, Object> doc = Map.of(
            "status", "ACTIVE",
            "tags", List.of("admin", "user")
        );
        Criterion criterion = new Criterion("test", Map.of(
            "status", Map.of("$in", List.of("ACTIVE", "PENDING")),
            "tags", Map.of("$size", 2)
        ));

        EvaluationResult result = evaluator.evaluateCriterion(doc, criterion);

        assertThat(result.state()).isEqualTo(EvaluationState.MATCHED);
    }

    @Test
    void combined_allAndSize_shouldWork() {
        Map<String, Object> doc = Map.of("tags", List.of("admin", "user", "verified"));
        // All elements present AND size is 3
        Criterion criterion = new Criterion("test", Map.of("tags", Map.of(
            "$all", List.of("admin", "user"),
            "$size", 3
        )));

        EvaluationResult result = evaluator.evaluateCriterion(doc, criterion);

        assertThat(result.state()).isEqualTo(EvaluationState.MATCHED);
    }

    // ========== Edge Cases ==========

    @Test
    void collection_withMissingField_shouldBeUndetermined() {
        Map<String, Object> doc = Map.of("name", "John");
        Criterion criterion = new Criterion("test", Map.of("tags", Map.of("$size", 2)));

        EvaluationResult result = evaluator.evaluateCriterion(doc, criterion);

        assertThat(result.state()).isEqualTo(EvaluationState.UNDETERMINED);
        assertThat(result.missingPaths()).contains("tags");
    }

    @Test
    void in_withNestedValues_shouldWork() {
        Map<String, Object> doc = Map.of("nested", Map.of("status", "ACTIVE"));
        Criterion criterion = new Criterion("test", Map.of("nested", Map.of("status", Map.of("$in", List.of("ACTIVE", "PENDING")))));

        EvaluationResult result = evaluator.evaluateCriterion(doc, criterion);

        assertThat(result.state()).isEqualTo(EvaluationState.MATCHED);
    }

    @Test
    void all_orderDoesNotMatter_shouldMatch() {
        Map<String, Object> doc = Map.of("tags", List.of("user", "admin", "verified"));
        Criterion criterion = new Criterion("test", Map.of("tags", Map.of("$all", List.of("verified", "admin"))));

        EvaluationResult result = evaluator.evaluateCriterion(doc, criterion);

        // Order shouldn't matter for $all
        assertThat(result.state()).isEqualTo(EvaluationState.MATCHED);
    }
}
