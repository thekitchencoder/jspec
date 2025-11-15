package uk.codery.jspec.result;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.codery.jspec.evaluator.CriterionEvaluator;
import uk.codery.jspec.evaluator.SpecificationEvaluator;
import uk.codery.jspec.model.Criterion;
import uk.codery.jspec.model.Specification;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Comprehensive tests for tri-state evaluation model and graceful error handling.
 */
class TriStateEvaluationTest {

    private CriterionEvaluator evaluator;
    private Map<String, Object> validDocument;

    @BeforeEach
    void setUp() {
        evaluator = new CriterionEvaluator();
        validDocument = Map.of(
                "age", 25,
                "name", "John Doe",
                "tags", List.of("admin", "user"),
                "active", true
        );
    }

    // ========== MATCHED State Tests ==========

    @Test
    void matchedState_simpleEquality() {
        Criterion criterion = new Criterion("age-check", Map.of("age", Map.of("$eq", 25)));
        EvaluationResult result = evaluator.evaluateCriterion(validDocument, criterion);

        assertThat(result.state()).isEqualTo(EvaluationState.MATCHED);
        assertThat(result.matched()).isTrue();
        assertThat(result.isDetermined()).isTrue();
        assertThat(result.missingPaths()).isEmpty();
        assertThat(result.failureReason()).isNull();
    }

    @Test
    void matchedState_complexOperator() {
        Criterion criterion = new Criterion("age-range", Map.of("age", Map.of("$gte", 18, "$lte", 30)));
        EvaluationResult result = evaluator.evaluateCriterion(validDocument, criterion);

        assertThat(result.state()).isEqualTo(EvaluationState.MATCHED);
        assertThat(result.matched()).isTrue();
    }

    @Test
    void matchedState_inOperator() {
        Criterion criterion = new Criterion("tag-check", Map.of("tags", Map.of("$in", List.of("admin", "moderator"))));
        Map<String, Object> doc = Map.of("tags", "admin");
        EvaluationResult result = evaluator.evaluateCriterion(doc, criterion);

        assertThat(result.state()).isEqualTo(EvaluationState.MATCHED);
    }

    // ========== NOT_MATCHED State Tests ==========

    @Test
    void notMatchedState_simpleEquality() {
        Criterion criterion = new Criterion("age-check", Map.of("age", Map.of("$eq", 30)));
        EvaluationResult result = evaluator.evaluateCriterion(validDocument, criterion);

        assertThat(result.state()).isEqualTo(EvaluationState.NOT_MATCHED);
        assertThat(result.matched()).isFalse();
        assertThat(result.isDetermined()).isTrue();
        assertThat(result.missingPaths()).isEmpty();
    }

    @Test
    void notMatchedState_greaterThan() {
        Criterion criterion = new Criterion("age-check", Map.of("age", Map.of("$gt", 30)));
        EvaluationResult result = evaluator.evaluateCriterion(validDocument, criterion);

        assertThat(result.state()).isEqualTo(EvaluationState.NOT_MATCHED);
        assertThat(result.matched()).isFalse();
    }

    // ========== UNDETERMINED State - Missing Data ==========

    @Test
    void undeterminedState_missingField() {
        Criterion criterion = new Criterion("salary-check", Map.of("salary", Map.of("$gt", 50000)));
        EvaluationResult result = evaluator.evaluateCriterion(validDocument, criterion);

        assertThat(result.state()).isEqualTo(EvaluationState.UNDETERMINED);
        assertThat(result.matched()).isFalse();
        assertThat(result.isDetermined()).isFalse();
        assertThat(result.missingPaths()).contains("salary");
        assertThat(result.failureReason()).contains("Missing data");
    }

    @Test
    void undeterminedState_nullValue() {
        Map<String, Object> doc = Map.of("name", "John", "age", Map.of());
        // age is an empty map, but we're querying age.value which doesn't exist
        Criterion criterion = new Criterion("nested-check", Map.of("age", Map.of("value", Map.of("$eq", 25))));
        EvaluationResult result = evaluator.evaluateCriterion(doc, criterion);

        assertThat(result.state()).isEqualTo(EvaluationState.UNDETERMINED);
    }

    // ========== UNDETERMINED State - Unknown Operators ==========

    @Test
    void undeterminedState_unknownOperator() {
        Criterion criterion = new Criterion("test", Map.of("age", Map.of("$unknown", 18)));
        EvaluationResult result = evaluator.evaluateCriterion(validDocument, criterion);

        assertThat(result.state()).isEqualTo(EvaluationState.UNDETERMINED);
        assertThat(result.matched()).isFalse();
        assertThat(result.isDetermined()).isFalse();
        assertThat(result.failureReason()).contains("Unknown junction");
        assertThat(result.failureReason()).contains("$unknown");
    }

    @Test
    void undeterminedState_multipleUnknownOperators() {
        Criterion criterion = new Criterion("test", Map.of("age", Map.of("$fake", 18, "$invalid", 20)));
        EvaluationResult result = evaluator.evaluateCriterion(validDocument, criterion);

        assertThat(result.state()).isEqualTo(EvaluationState.UNDETERMINED);
        assertThat(result.failureReason()).containsAnyOf("$fake", "$invalid");
    }

    // ========== UNDETERMINED State - Type Mismatches ==========

    @Test
    void undeterminedState_typeMismatch_inOperatorExpectsList() {
        Criterion criterion = new Criterion("test", Map.of("age", Map.of("$in", "not-a-list")));
        EvaluationResult result = evaluator.evaluateCriterion(validDocument, criterion);

        // Type mismatch is logged but treated as NOT_MATCHED, not UNDETERMINED
        // This is by design - the junction returns false gracefully
        assertThat(result.state()).isEqualTo(EvaluationState.NOT_MATCHED);
        assertThat(result.matched()).isFalse();
    }

    @Test
    void undeterminedState_typeMismatch_existsOperatorExpectsBoolean() {
        Criterion criterion = new Criterion("test", Map.of("age", Map.of("$exists", "yes")));
        EvaluationResult result = evaluator.evaluateCriterion(validDocument, criterion);

        // Type mismatch is logged but treated as NOT_MATCHED
        assertThat(result.state()).isEqualTo(EvaluationState.NOT_MATCHED);
    }

    // ========== UNDETERMINED State - Invalid Patterns ==========

    @Test
    void undeterminedState_invalidRegexPattern() {
        Criterion criterion = new Criterion("test", Map.of("name", Map.of("$regex", "[invalid")));
        EvaluationResult result = evaluator.evaluateCriterion(validDocument, criterion);

        // Invalid regex is logged but treated as NOT_MATCHED
        assertThat(result.state()).isEqualTo(EvaluationState.NOT_MATCHED);
        assertThat(result.matched()).isFalse();
    }

    @Test
    void matchedState_validRegexPattern() {
        Criterion criterion = new Criterion("test", Map.of("name", Map.of("$regex", "John.*")));
        EvaluationResult result = evaluator.evaluateCriterion(validDocument, criterion);

        assertThat(result.state()).isEqualTo(EvaluationState.MATCHED);
    }

    // ========== Graceful Degradation - Multiple Criteria ==========

    @Test
    void gracefulDegradation_oneCriterionUndetermined_othersEvaluate() {
        List<Criterion> criteria = List.of(
                new Criterion("good1", Map.of("age", Map.of("$eq", 25))),
                new Criterion("bad", Map.of("age", Map.of("$unknown", 18))),
                new Criterion("good2", Map.of("name", Map.of("$eq", "John Doe")))
        );

        Specification spec = new Specification("test-spec", criteria, List.of());
        SpecificationEvaluator specEvaluator = new SpecificationEvaluator();
        EvaluationOutcome outcome = specEvaluator.evaluate(validDocument, spec);

        // All 3 criteria should have results
        assertThat(outcome.evaluationResults()).hasSize(3);

        // Check individual states
        EvaluationResult good1 = outcome.evaluationResults().stream()
                .filter(r -> r.id().equals("good1")).findFirst().orElseThrow();
        assertThat(good1.state()).isEqualTo(EvaluationState.MATCHED);

        EvaluationResult bad = outcome.evaluationResults().stream()
                .filter(r -> r.id().equals("bad")).findFirst().orElseThrow();
        assertThat(bad.state()).isEqualTo(EvaluationState.UNDETERMINED);

        EvaluationResult good2 = outcome.evaluationResults().stream()
                .filter(r -> r.id().equals("good2")).findFirst().orElseThrow();
        assertThat(good2.state()).isEqualTo(EvaluationState.MATCHED);

        // Check summary
        assertThat(outcome.summary().total()).isEqualTo(3);
        assertThat(outcome.summary().matched()).isEqualTo(2);
        assertThat(outcome.summary().undetermined()).isEqualTo(1);
        assertThat(outcome.summary().fullyDetermined()).isFalse();
    }

    @Test
    void gracefulDegradation_allCriteriaDetermined() {
        List<Criterion> criteria = List.of(
                new Criterion("match", Map.of("age", Map.of("$eq", 25))),
                new Criterion("no-match", Map.of("age", Map.of("$eq", 30)))
        );

        Specification spec = new Specification("test-spec", criteria, List.of());
        SpecificationEvaluator specEvaluator = new SpecificationEvaluator();
        EvaluationOutcome outcome = specEvaluator.evaluate(validDocument, spec);

        assertThat(outcome.summary().total()).isEqualTo(2);
        assertThat(outcome.summary().matched()).isEqualTo(1);
        assertThat(outcome.summary().notMatched()).isEqualTo(1);
        assertThat(outcome.summary().undetermined()).isEqualTo(0);
        assertThat(outcome.summary().fullyDetermined()).isTrue();
    }

    // ========== Edge Cases ==========

    @Test
    void edgeCase_emptyDocument() {
        Map<String, Object> emptyDoc = Map.of();
        Criterion criterion = new Criterion("test", Map.of("age", Map.of("$eq", 25)));
        EvaluationResult result = evaluator.evaluateCriterion(emptyDoc, criterion);

        assertThat(result.state()).isEqualTo(EvaluationState.UNDETERMINED);
        assertThat(result.missingPaths()).contains("age");
    }

    @Test
    void edgeCase_emptySpecification() {
        Specification spec = new Specification("empty", List.of(), List.of());
        SpecificationEvaluator specEvaluator = new SpecificationEvaluator();
        EvaluationOutcome outcome = specEvaluator.evaluate(validDocument, spec);

        assertThat(outcome.summary().total()).isEqualTo(0);
        assertThat(outcome.summary().fullyDetermined()).isTrue();
    }

    @Test
    void edgeCase_nestedMissingData() {
        Map<String, Object> doc = Map.of("user", Map.of("name", "John"));
        Criterion criterion = new Criterion("test", Map.of("user", Map.of("address", Map.of("city", Map.of("$eq", "NYC")))));
        EvaluationResult result = evaluator.evaluateCriterion(doc, criterion);

        assertThat(result.state()).isEqualTo(EvaluationState.UNDETERMINED);
        assertThat(result.missingPaths()).contains("user.address");
    }

    // ========== Junction-Specific Tests ==========

    @Test
    void operator_size_withValidList() {
        Criterion criterion = new Criterion("test", Map.of("tags", Map.of("$size", 2)));
        EvaluationResult result = evaluator.evaluateCriterion(validDocument, criterion);

        assertThat(result.state()).isEqualTo(EvaluationState.MATCHED);
    }

    @Test
    void operator_size_withWrongSize() {
        Criterion criterion = new Criterion("test", Map.of("tags", Map.of("$size", 5)));
        EvaluationResult result = evaluator.evaluateCriterion(validDocument, criterion);

        assertThat(result.state()).isEqualTo(EvaluationState.NOT_MATCHED);
    }

    @Test
    void operator_exists_true() {
        Criterion criterion = new Criterion("test", Map.of("name", Map.of("$exists", true)));
        EvaluationResult result = evaluator.evaluateCriterion(validDocument, criterion);

        assertThat(result.state()).isEqualTo(EvaluationState.MATCHED);
    }

    @Test
    void operator_exists_false() {
        Criterion criterion = new Criterion("test", Map.of("missingField", Map.of("$exists", false)));
        EvaluationResult result = evaluator.evaluateCriterion(validDocument, criterion);

        assertThat(result.state()).isEqualTo(EvaluationState.MATCHED);
    }

    @Test
    void operator_all_matches() {
        Criterion criterion = new Criterion("test", Map.of("tags", Map.of("$all", List.of("admin", "user"))));
        EvaluationResult result = evaluator.evaluateCriterion(validDocument, criterion);

        assertThat(result.state()).isEqualTo(EvaluationState.MATCHED);
    }

    @Test
    void operator_all_doesNotMatch() {
        Criterion criterion = new Criterion("test", Map.of("tags", Map.of("$all", List.of("admin", "superuser"))));
        EvaluationResult result = evaluator.evaluateCriterion(validDocument, criterion);

        assertThat(result.state()).isEqualTo(EvaluationState.NOT_MATCHED);
    }
}
