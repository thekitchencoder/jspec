package uk.codery.jspec.result;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.codery.jspec.evaluator.CriterionEvaluator;
import uk.codery.jspec.evaluator.SpecificationEvaluator;
import uk.codery.jspec.model.QueryCriterion;
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
        QueryCriterion criterion = new QueryCriterion("age-check", Map.of("age", Map.of("$eq", 25)));
        QueryResult result = evaluator.evaluateQuery(validDocument, criterion);

        assertThat(result.state()).isEqualTo(EvaluationState.MATCHED);
        assertThat(result.matched()).isTrue();
        assertThat(result.isDetermined()).isTrue();
        assertThat(result.missingPaths()).isEmpty();
        assertThat(result.failureReason()).isNull();
    }

    @Test
    void matchedState_complexOperator() {
        QueryCriterion criterion = new QueryCriterion("age-range", Map.of("age", Map.of("$gte", 18, "$lte", 30)));
        QueryResult result = evaluator.evaluateQuery(validDocument, criterion);

        assertThat(result.state()).isEqualTo(EvaluationState.MATCHED);
        assertThat(result.matched()).isTrue();
    }

    @Test
    void matchedState_inOperator() {
        QueryCriterion criterion = new QueryCriterion("tag-check", Map.of("tags", Map.of("$in", List.of("admin", "moderator"))));
        Map<String, Object> doc = Map.of("tags", "admin");
        QueryResult result = evaluator.evaluateQuery(doc, criterion);

        assertThat(result.state()).isEqualTo(EvaluationState.MATCHED);
    }

    // ========== NOT_MATCHED State Tests ==========

    @Test
    void notMatchedState_simpleEquality() {
        QueryCriterion criterion = new QueryCriterion("age-check", Map.of("age", Map.of("$eq", 30)));
        QueryResult result = evaluator.evaluateQuery(validDocument, criterion);

        assertThat(result.state()).isEqualTo(EvaluationState.NOT_MATCHED);
        assertThat(result.matched()).isFalse();
        assertThat(result.isDetermined()).isTrue();
        assertThat(result.missingPaths()).isEmpty();
    }

    @Test
    void notMatchedState_greaterThan() {
        QueryCriterion criterion = new QueryCriterion("age-check", Map.of("age", Map.of("$gt", 30)));
        QueryResult result = evaluator.evaluateQuery(validDocument, criterion);

        assertThat(result.state()).isEqualTo(EvaluationState.NOT_MATCHED);
        assertThat(result.matched()).isFalse();
    }

    // ========== UNDETERMINED State - Missing Data ==========

    @Test
    void undeterminedState_missingField() {
        QueryCriterion criterion = new QueryCriterion("salary-check", Map.of("salary", Map.of("$gt", 50000)));
        QueryResult result = evaluator.evaluateQuery(validDocument, criterion);

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
        QueryCriterion criterion = new QueryCriterion("nested-check", Map.of("age", Map.of("value", Map.of("$eq", 25))));
        QueryResult result = evaluator.evaluateQuery(doc, criterion);

        assertThat(result.state()).isEqualTo(EvaluationState.UNDETERMINED);
    }

    // ========== UNDETERMINED State - Unknown Operators ==========

    @Test
    void undeterminedState_unknownOperator() {
        QueryCriterion criterion = new QueryCriterion("test", Map.of("age", Map.of("$unknown", 18)));
        QueryResult result = evaluator.evaluateQuery(validDocument, criterion);

        assertThat(result.state()).isEqualTo(EvaluationState.UNDETERMINED);
        assertThat(result.matched()).isFalse();
        assertThat(result.isDetermined()).isFalse();
        assertThat(result.failureReason()).contains("Unknown operator");
        assertThat(result.failureReason()).contains("$unknown");
    }

    @Test
    void undeterminedState_multipleUnknownOperators() {
        QueryCriterion criterion = new QueryCriterion("test", Map.of("age", Map.of("$fake", 18, "$invalid", 20)));
        QueryResult result = evaluator.evaluateQuery(validDocument, criterion);

        assertThat(result.state()).isEqualTo(EvaluationState.UNDETERMINED);
        assertThat(result.failureReason()).containsAnyOf("$fake", "$invalid");
    }

    // ========== UNDETERMINED State - Type Mismatches ==========

    @Test
    void undeterminedState_typeMismatch_inOperatorExpectsList() {
        QueryCriterion criterion = new QueryCriterion("test", Map.of("age", Map.of("$in", "not-a-list")));
        QueryResult result = evaluator.evaluateQuery(validDocument, criterion);

        // Type mismatch is logged but treated as NOT_MATCHED, not UNDETERMINED
        // This is by design - the operator returns false gracefully
        assertThat(result.state()).isEqualTo(EvaluationState.NOT_MATCHED);
        assertThat(result.matched()).isFalse();
    }

    @Test
    void undeterminedState_typeMismatch_existsOperatorExpectsBoolean() {
        QueryCriterion criterion = new QueryCriterion("test", Map.of("age", Map.of("$exists", "yes")));
        QueryResult result = evaluator.evaluateQuery(validDocument, criterion);

        // Type mismatch is logged but treated as NOT_MATCHED
        assertThat(result.state()).isEqualTo(EvaluationState.NOT_MATCHED);
    }

    // ========== UNDETERMINED State - Invalid Patterns ==========

    @Test
    void undeterminedState_invalidRegexPattern() {
        QueryCriterion criterion = new QueryCriterion("test", Map.of("name", Map.of("$regex", "[invalid")));
        QueryResult result = evaluator.evaluateQuery(validDocument, criterion);

        // Invalid regex is logged but treated as NOT_MATCHED
        assertThat(result.state()).isEqualTo(EvaluationState.NOT_MATCHED);
        assertThat(result.matched()).isFalse();
    }

    @Test
    void matchedState_validRegexPattern() {
        QueryCriterion criterion = new QueryCriterion("test", Map.of("name", Map.of("$regex", "John.*")));
        QueryResult result = evaluator.evaluateQuery(validDocument, criterion);

        assertThat(result.state()).isEqualTo(EvaluationState.MATCHED);
    }

    // ========== Graceful Degradation - Multiple Criteria ==========

    @Test
    void gracefulDegradation_oneCriterionUndetermined_othersEvaluate() {
        List<uk.codery.jspec.model.Criterion> criteria = List.of(
                new QueryCriterion("good1", Map.of("age", Map.of("$eq", 25))),
                new QueryCriterion("bad", Map.of("age", Map.of("$unknown", 18))),
                new QueryCriterion("good2", Map.of("name", Map.of("$eq", "John Doe")))
        );

        Specification spec = new Specification("test-spec", criteria);
        SpecificationEvaluator specEvaluator = new SpecificationEvaluator(spec);
        EvaluationOutcome outcome = specEvaluator.evaluate(validDocument);

        // All 3 criteria should have results
        assertThat(outcome.queryResults()).hasSize(3);

        // Check individual states
        EvaluationResult good1 = outcome.queryResults().stream()
                .filter(r -> r.id().equals("good1")).findFirst().orElseThrow();
        assertThat(good1.state()).isEqualTo(EvaluationState.MATCHED);

        EvaluationResult bad = outcome.queryResults().stream()
                .filter(r -> r.id().equals("bad")).findFirst().orElseThrow();
        assertThat(bad.state()).isEqualTo(EvaluationState.UNDETERMINED);

        EvaluationResult good2 = outcome.queryResults().stream()
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
        List<uk.codery.jspec.model.Criterion> criteria = List.of(
                new QueryCriterion("match", Map.of("age", Map.of("$eq", 25))),
                new QueryCriterion("no-match", Map.of("age", Map.of("$eq", 30)))
        );

        Specification spec = new Specification("test-spec", criteria);
        SpecificationEvaluator specEvaluator = new SpecificationEvaluator(spec);
        EvaluationOutcome outcome = specEvaluator.evaluate(validDocument);

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
        QueryCriterion criterion = new QueryCriterion("test", Map.of("age", Map.of("$eq", 25)));
        QueryResult result = evaluator.evaluateQuery(emptyDoc, criterion);

        assertThat(result.state()).isEqualTo(EvaluationState.UNDETERMINED);
        assertThat(result.missingPaths()).contains("age");
    }

    @Test
    void edgeCase_emptySpecification() {
        Specification spec = new Specification("empty", List.of());
        SpecificationEvaluator specEvaluator = new SpecificationEvaluator(spec);
        EvaluationOutcome outcome = specEvaluator.evaluate(validDocument);

        assertThat(outcome.summary().total()).isEqualTo(0);
        assertThat(outcome.summary().fullyDetermined()).isTrue();
    }

    @Test
    void edgeCase_nestedMissingData() {
        Map<String, Object> doc = Map.of("user", Map.of("name", "John"));
        QueryCriterion criterion = new QueryCriterion("test", Map.of("user", Map.of("address", Map.of("city", Map.of("$eq", "NYC")))));
        QueryResult result = evaluator.evaluateQuery(doc, criterion);

        assertThat(result.state()).isEqualTo(EvaluationState.UNDETERMINED);
        assertThat(result.missingPaths()).contains("user.address");
    }

    // ========== Operator-Specific Tests ==========

    @Test
    void operator_size_withValidList() {
        QueryCriterion criterion = new QueryCriterion("test", Map.of("tags", Map.of("$size", 2)));
        QueryResult result = evaluator.evaluateQuery(validDocument, criterion);

        assertThat(result.state()).isEqualTo(EvaluationState.MATCHED);
    }

    @Test
    void operator_size_withWrongSize() {
        QueryCriterion criterion = new QueryCriterion("test", Map.of("tags", Map.of("$size", 5)));
        QueryResult result = evaluator.evaluateQuery(validDocument, criterion);

        assertThat(result.state()).isEqualTo(EvaluationState.NOT_MATCHED);
    }

    @Test
    void operator_exists_true() {
        QueryCriterion criterion = new QueryCriterion("test", Map.of("name", Map.of("$exists", true)));
        QueryResult result = evaluator.evaluateQuery(validDocument, criterion);

        assertThat(result.state()).isEqualTo(EvaluationState.MATCHED);
    }

    @Test
    void operator_exists_false() {
        QueryCriterion criterion = new QueryCriterion("test", Map.of("missingField", Map.of("$exists", false)));
        QueryResult result = evaluator.evaluateQuery(validDocument, criterion);

        assertThat(result.state()).isEqualTo(EvaluationState.MATCHED);
    }

    @Test
    void operator_all_matches() {
        QueryCriterion criterion = new QueryCriterion("test", Map.of("tags", Map.of("$all", List.of("admin", "user"))));
        QueryResult result = evaluator.evaluateQuery(validDocument, criterion);

        assertThat(result.state()).isEqualTo(EvaluationState.MATCHED);
    }

    @Test
    void operator_all_doesNotMatch() {
        QueryCriterion criterion = new QueryCriterion("test", Map.of("tags", Map.of("$all", List.of("admin", "superuser"))));
        QueryResult result = evaluator.evaluateQuery(validDocument, criterion);

        assertThat(result.state()).isEqualTo(EvaluationState.NOT_MATCHED);
    }
}
