package uk.codery.jspec.evaluator;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.codery.jspec.model.Criterion;
import uk.codery.jspec.result.EvaluationResult;
import uk.codery.jspec.result.EvaluationState;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for CriterionEvaluator core functionality including:
 * - Document navigation (nested fields, dot notation)
 * - Error handling and graceful degradation
 * - Unknown operators
 * - Complex query structures
 * - Edge cases
 */
class CriterionEvaluatorTest {

    private CriterionEvaluator evaluator;

    @BeforeEach
    void setUp() {
        evaluator = new CriterionEvaluator();
    }

    // ========== Document Navigation Tests ==========

    @Test
    void navigation_withSimpleField_shouldWork() {
        Map<String, Object> doc = Map.of("age", 25);
        Criterion criterion = new Criterion("test", Map.of("age", Map.of("$eq", 25)));

        EvaluationResult result = evaluator.evaluateCriterion(doc, criterion);

        assertThat(result.state()).isEqualTo(EvaluationState.MATCHED);
    }

    @Test
    void navigation_withNestedFields_shouldWork() {
        Map<String, Object> doc = Map.of(
            "user", Map.of(
                "profile", Map.of(
                    "age", 25
                )
            )
        );
        Criterion criterion = new Criterion("test", Map.of("user", Map.of("profile", Map.of("age", Map.of("$eq", 25)))));

        EvaluationResult result = evaluator.evaluateCriterion(doc, criterion);

        assertThat(result.state()).isEqualTo(EvaluationState.MATCHED);
    }

    @Test
    void navigation_withDeeplyNestedFields_shouldWork() {
        Map<String, Object> doc = Map.of(
            "level1", Map.of(
                "level2", Map.of(
                    "level3", Map.of(
                        "level4", Map.of(
                            "value", "found"
                        )
                    )
                )
            )
        );
        Criterion criterion = new Criterion("test", Map.of(
            "level1", Map.of(
                "level2", Map.of(
                    "level3", Map.of(
                        "level4", Map.of(
                            "value", Map.of("$eq", "found")
                        )
                    )
                )
            )
        ));

        EvaluationResult result = evaluator.evaluateCriterion(doc, criterion);

        assertThat(result.state()).isEqualTo(EvaluationState.MATCHED);
    }

    @Test
    void navigation_withMissingNestedField_shouldBeUndetermined() {
        Map<String, Object> doc = Map.of("user", Map.of("name", "John"));
        Criterion criterion = new Criterion("test", Map.of("user", Map.of("profile", Map.of("age", Map.of("$eq", 25)))));

        EvaluationResult result = evaluator.evaluateCriterion(doc, criterion);

        assertThat(result.state()).isEqualTo(EvaluationState.UNDETERMINED);
        assertThat(result.missingPaths()).contains("user.profile");
    }

    @Test
    void navigation_withMultipleFields_shouldCheckAll() {
        Map<String, Object> doc = Map.of(
            "name", "John",
            "age", 25,
            "status", "ACTIVE"
        );
        Criterion criterion = new Criterion("test", Map.of(
            "name", Map.of("$eq", "John"),
            "age", Map.of("$gte", 18),
            "status", Map.of("$eq", "ACTIVE")
        ));

        EvaluationResult result = evaluator.evaluateCriterion(doc, criterion);

        assertThat(result.state()).isEqualTo(EvaluationState.MATCHED);
    }

    @Test
    void navigation_withOneFailingField_shouldNotMatch() {
        Map<String, Object> doc = Map.of(
            "name", "John",
            "age", 25,
            "status", "INACTIVE"
        );
        Criterion criterion = new Criterion("test", Map.of(
            "name", Map.of("$eq", "John"),
            "age", Map.of("$gte", 18),
            "status", Map.of("$eq", "ACTIVE")
        ));

        EvaluationResult result = evaluator.evaluateCriterion(doc, criterion);

        assertThat(result.state()).isEqualTo(EvaluationState.NOT_MATCHED);
    }

    // ========== Simple Value Matching Tests ==========

    @Test
    void simpleMatch_withEqualStrings_shouldMatch() {
        Map<String, Object> doc = Map.of("name", "John");
        Criterion criterion = new Criterion("test", Map.of("name", "John"));

        EvaluationResult result = evaluator.evaluateCriterion(doc, criterion);

        assertThat(result.state()).isEqualTo(EvaluationState.MATCHED);
    }

    @Test
    void simpleMatch_withDifferentStrings_shouldNotMatch() {
        Map<String, Object> doc = Map.of("name", "John");
        Criterion criterion = new Criterion("test", Map.of("name", "Jane"));

        EvaluationResult result = evaluator.evaluateCriterion(doc, criterion);

        assertThat(result.state()).isEqualTo(EvaluationState.NOT_MATCHED);
    }

    @Test
    void simpleMatch_withNumbers_shouldWork() {
        Map<String, Object> doc = Map.of("age", 25);
        Criterion criterion = new Criterion("test", Map.of("age", 25));

        EvaluationResult result = evaluator.evaluateCriterion(doc, criterion);

        assertThat(result.state()).isEqualTo(EvaluationState.MATCHED);
    }

    @Test
    void simpleMatch_withBooleans_shouldWork() {
        Map<String, Object> doc = Map.of("active", true);
        Criterion criterion = new Criterion("test", Map.of("active", true));

        EvaluationResult result = evaluator.evaluateCriterion(doc, criterion);

        assertThat(result.state()).isEqualTo(EvaluationState.MATCHED);
    }

    // ========== List Matching Tests ==========

    @Test
    void listMatch_withEqualLists_shouldMatch() {
        Map<String, Object> doc = Map.of("tags", List.of("admin", "user"));
        Criterion criterion = new Criterion("test", Map.of("tags", List.of("admin", "user")));

        EvaluationResult result = evaluator.evaluateCriterion(doc, criterion);

        assertThat(result.state()).isEqualTo(EvaluationState.MATCHED);
    }

    @Test
    void listMatch_withDifferentLists_shouldNotMatch() {
        Map<String, Object> doc = Map.of("tags", List.of("admin", "user"));
        Criterion criterion = new Criterion("test", Map.of("tags", List.of("admin", "moderator")));

        EvaluationResult result = evaluator.evaluateCriterion(doc, criterion);

        assertThat(result.state()).isEqualTo(EvaluationState.NOT_MATCHED);
    }

    @Test
    void listMatch_withDifferentSizes_shouldNotMatch() {
        Map<String, Object> doc = Map.of("tags", List.of("admin", "user"));
        Criterion criterion = new Criterion("test", Map.of("tags", List.of("admin")));

        EvaluationResult result = evaluator.evaluateCriterion(doc, criterion);

        assertThat(result.state()).isEqualTo(EvaluationState.NOT_MATCHED);
    }

    @Test
    void listMatch_withNonListValue_shouldNotMatch() {
        Map<String, Object> doc = Map.of("tags", "admin");
        Criterion criterion = new Criterion("test", Map.of("tags", List.of("admin")));

        EvaluationResult result = evaluator.evaluateCriterion(doc, criterion);

        assertThat(result.state()).isEqualTo(EvaluationState.NOT_MATCHED);
    }

    @Test
    void listMatch_withNestedObjects_shouldWork() {
        Map<String, Object> doc = Map.of("items", List.of(
            Map.of("id", 1),
            Map.of("id", 2)
        ));
        Criterion criterion = new Criterion("test", Map.of("items", List.of(
            Map.of("id", 1),
            Map.of("id", 2)
        )));

        EvaluationResult result = evaluator.evaluateCriterion(doc, criterion);

        assertThat(result.state()).isEqualTo(EvaluationState.MATCHED);
    }

    // ========== Unknown Junction Tests ==========

    @Test
    void unknownOperator_shouldReturnUndetermined() {
        Map<String, Object> doc = Map.of("age", 25);
        Criterion criterion = new Criterion("test", Map.of("age", Map.of("$unknown", 18)));

        EvaluationResult result = evaluator.evaluateCriterion(doc, criterion);

        assertThat(result.state()).isEqualTo(EvaluationState.UNDETERMINED);
        assertThat(result.failureReason()).contains("Unknown junction");
        assertThat(result.failureReason()).contains("$unknown");
    }

    @Test
    void unknownOperator_withMultipleOperators_shouldReturnUndetermined() {
        Map<String, Object> doc = Map.of("age", 25);
        Criterion criterion = new Criterion("test", Map.of("age", Map.of("$fake", 18, "$invalid", 20)));

        EvaluationResult result = evaluator.evaluateCriterion(doc, criterion);

        assertThat(result.state()).isEqualTo(EvaluationState.UNDETERMINED);
        assertThat(result.failureReason()).containsAnyOf("$fake", "$invalid");
    }

    @Test
    void unknownOperator_withValidAndInvalidOperators_shouldReturnUndetermined() {
        Map<String, Object> doc = Map.of("age", 25);
        // Has both valid ($eq) and invalid ($fake) operators
        Criterion criterion = new Criterion("test", Map.of("age", Map.of("$eq", 25, "$fake", 18)));

        EvaluationResult result = evaluator.evaluateCriterion(doc, criterion);

        // Unknown junction fails first
        assertThat(result.state()).isEqualTo(EvaluationState.UNDETERMINED);
    }

    // ========== Missing Data Tests ==========

    @Test
    void missingData_atTopLevel_shouldBeUndetermined() {
        Map<String, Object> doc = Map.of("name", "John");
        Criterion criterion = new Criterion("test", Map.of("age", Map.of("$eq", 25)));

        EvaluationResult result = evaluator.evaluateCriterion(doc, criterion);

        assertThat(result.state()).isEqualTo(EvaluationState.UNDETERMINED);
        assertThat(result.missingPaths()).contains("age");
        assertThat(result.failureReason()).contains("Missing data at: age");
    }

    @Test
    void missingData_inNestedStructure_shouldBeUndetermined() {
        Map<String, Object> doc = Map.of("user", Map.of("name", "John"));
        Criterion criterion = new Criterion("test", Map.of("user", Map.of("profile", Map.of("age", Map.of("$eq", 25)))));

        EvaluationResult result = evaluator.evaluateCriterion(doc, criterion);

        assertThat(result.state()).isEqualTo(EvaluationState.UNDETERMINED);
        assertThat(result.missingPaths()).contains("user.profile");
    }

    @Test
    void missingData_withMultipleMissingFields_shouldTrackAll() {
        Map<String, Object> doc = Map.of("name", "John");
        Criterion criterion = new Criterion("test", Map.of(
            "age", Map.of("$eq", 25),
            "status", Map.of("$eq", "ACTIVE")
        ));

        EvaluationResult result = evaluator.evaluateCriterion(doc, criterion);

        assertThat(result.state()).isEqualTo(EvaluationState.UNDETERMINED);
        // At least one missing path should be tracked
        assertThat(result.missingPaths()).isNotEmpty();
    }

    // ========== Empty Document Tests ==========

    @Test
    void emptyDocument_withAnyQuery_shouldBeUndetermined() {
        Map<String, Object> doc = Map.of();
        Criterion criterion = new Criterion("test", Map.of("age", Map.of("$eq", 25)));

        EvaluationResult result = evaluator.evaluateCriterion(doc, criterion);

        assertThat(result.state()).isEqualTo(EvaluationState.UNDETERMINED);
        assertThat(result.missingPaths()).contains("age");
    }

    @Test
    void emptyDocument_withEmptyQuery_shouldMatch() {
        Map<String, Object> doc = Map.of();
        Criterion criterion = new Criterion("test", Map.of());

        EvaluationResult result = evaluator.evaluateCriterion(doc, criterion);

        // Empty query matches empty document
        assertThat(result.state()).isEqualTo(EvaluationState.MATCHED);
    }

    // ========== Complex Query Tests ==========

    @Test
    void complexQuery_withMixedOperators_shouldWork() {
        Map<String, Object> doc = Map.of(
            "age", 25,
            "status", "ACTIVE",
            "tags", List.of("admin", "user")
        );
        Criterion criterion = new Criterion("test", Map.of(
            "age", Map.of("$gte", 18, "$lt", 65),
            "status", Map.of("$in", List.of("ACTIVE", "PENDING")),
            "tags", Map.of("$all", List.of("admin"))
        ));

        EvaluationResult result = evaluator.evaluateCriterion(doc, criterion);

        assertThat(result.state()).isEqualTo(EvaluationState.MATCHED);
    }

    @Test
    void complexQuery_withNestedOperators_shouldWork() {
        Map<String, Object> doc = Map.of(
            "user", Map.of(
                "profile", Map.of(
                    "age", 25,
                    "email", "test@example.com"
                )
            )
        );
        Criterion criterion = new Criterion("test", Map.of(
            "user", Map.of(
                "profile", Map.of(
                    "age", Map.of("$gte", 18),
                    "email", Map.of("$regex", ".*@example\\.com")
                )
            )
        ));

        EvaluationResult result = evaluator.evaluateCriterion(doc, criterion);

        assertThat(result.state()).isEqualTo(EvaluationState.MATCHED);
    }

    // ========== Edge Cases ==========

    @Test
    void edgeCase_withEmptyStringValue_shouldWork() {
        Map<String, Object> doc = Map.of("name", "");
        Criterion criterion = new Criterion("test", Map.of("name", Map.of("$eq", "")));

        EvaluationResult result = evaluator.evaluateCriterion(doc, criterion);

        assertThat(result.state()).isEqualTo(EvaluationState.MATCHED);
    }

    @Test
    void edgeCase_withZeroValue_shouldWork() {
        Map<String, Object> doc = Map.of("count", 0);
        Criterion criterion = new Criterion("test", Map.of("count", Map.of("$eq", 0)));

        EvaluationResult result = evaluator.evaluateCriterion(doc, criterion);

        assertThat(result.state()).isEqualTo(EvaluationState.MATCHED);
    }

    @Test
    void edgeCase_withNegativeNumbers_shouldWork() {
        Map<String, Object> doc = Map.of("balance", -100);
        Criterion criterion = new Criterion("test", Map.of("balance", Map.of("$lt", 0)));

        EvaluationResult result = evaluator.evaluateCriterion(doc, criterion);

        assertThat(result.state()).isEqualTo(EvaluationState.MATCHED);
    }

    @Test
    void edgeCase_withEmptyList_shouldWork() {
        Map<String, Object> doc = Map.of("tags", List.of());
        Criterion criterion = new Criterion("test", Map.of("tags", Map.of("$size", 0)));

        EvaluationResult result = evaluator.evaluateCriterion(doc, criterion);

        assertThat(result.state()).isEqualTo(EvaluationState.MATCHED);
    }

    @Test
    void edgeCase_withVeryLongString_shouldWork() {
        String longString = "a".repeat(1000);
        Map<String, Object> doc = Map.of("description", longString);
        Criterion criterion = new Criterion("test", Map.of("description", Map.of("$eq", longString)));

        EvaluationResult result = evaluator.evaluateCriterion(doc, criterion);

        assertThat(result.state()).isEqualTo(EvaluationState.MATCHED);
    }

    @Test
    void edgeCase_withSpecialCharacters_shouldWork() {
        Map<String, Object> doc = Map.of("text", "Hello!@#$%^&*()");
        Criterion criterion = new Criterion("test", Map.of("text", Map.of("$eq", "Hello!@#$%^&*()")));

        EvaluationResult result = evaluator.evaluateCriterion(doc, criterion);

        assertThat(result.state()).isEqualTo(EvaluationState.MATCHED);
    }

    @Test
    void edgeCase_withUnicodeCharacters_shouldWork() {
        Map<String, Object> doc = Map.of("name", "José");
        Criterion criterion = new Criterion("test", Map.of("name", Map.of("$eq", "José")));

        EvaluationResult result = evaluator.evaluateCriterion(doc, criterion);

        assertThat(result.state()).isEqualTo(EvaluationState.MATCHED);
    }

    // ========== Result Metadata Tests ==========

    @Test
    void result_whenMatched_shouldHaveCorrectMetadata() {
        Map<String, Object> doc = Map.of("age", 25);
        Criterion criterion = new Criterion("test-criterion", Map.of("age", Map.of("$eq", 25)));

        EvaluationResult result = evaluator.evaluateCriterion(doc, criterion);

        assertThat(result.criterion()).isEqualTo(criterion);
        assertThat(result.id()).isEqualTo("test-criterion");
        assertThat(result.state()).isEqualTo(EvaluationState.MATCHED);
        assertThat(result.matched()).isTrue();
        assertThat(result.isDetermined()).isTrue();
        assertThat(result.missingPaths()).isEmpty();
        assertThat(result.failureReason()).isNull();
    }

    @Test
    void result_whenNotMatched_shouldHaveCorrectMetadata() {
        Map<String, Object> doc = Map.of("age", 25);
        Criterion criterion = new Criterion("test-criterion", Map.of("age", Map.of("$eq", 30)));

        EvaluationResult result = evaluator.evaluateCriterion(doc, criterion);

        assertThat(result.criterion()).isEqualTo(criterion);
        assertThat(result.state()).isEqualTo(EvaluationState.NOT_MATCHED);
        assertThat(result.matched()).isFalse();
        assertThat(result.isDetermined()).isTrue();
        assertThat(result.missingPaths()).isEmpty();
    }

    @Test
    void result_whenUndetermined_shouldHaveCorrectMetadata() {
        Map<String, Object> doc = Map.of("name", "John");
        Criterion criterion = new Criterion("test-criterion", Map.of("age", Map.of("$eq", 25)));

        EvaluationResult result = evaluator.evaluateCriterion(doc, criterion);

        assertThat(result.criterion()).isEqualTo(criterion);
        assertThat(result.state()).isEqualTo(EvaluationState.UNDETERMINED);
        assertThat(result.matched()).isFalse();
        assertThat(result.isDetermined()).isFalse();
        assertThat(result.missingPaths()).isNotEmpty();
        assertThat(result.failureReason()).isNotNull();
    }

    @Test
    void result_reason_shouldBeHumanReadable() {
        Map<String, Object> doc = Map.of("name", "John");
        Criterion criterion = new Criterion("test-criterion", Map.of("age", Map.of("$eq", 25)));

        EvaluationResult result = evaluator.evaluateCriterion(doc, criterion);

        String reason = result.reason();
        assertThat(reason).isNotNull();
        assertThat(reason).contains("Missing data");
    }
}
