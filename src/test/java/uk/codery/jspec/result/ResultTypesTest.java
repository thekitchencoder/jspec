package uk.codery.jspec.result;

import org.junit.jupiter.api.Test;
import uk.codery.jspec.model.CompositeCriterion;
import uk.codery.jspec.model.CriterionReference;
import uk.codery.jspec.model.Junction;
import uk.codery.jspec.model.QueryCriterion;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for QueryResult, ReferenceResult, and CompositeResult classes.
 */
class ResultTypesTest {

    // ==================== QueryResult Factory Method Tests ====================

    @Test
    void queryResult_matched_createsMatchedResult() {
        QueryCriterion criterion = new QueryCriterion("test-id", Map.of("field", "value"));

        QueryResult result = QueryResult.matched(criterion);

        assertThat(result.state()).isEqualTo(EvaluationState.MATCHED);
        assertThat(result.criterion()).isEqualTo(criterion);
        assertThat(result.missingPaths()).isEmpty();
        assertThat(result.failureReason()).isNull();
        assertThat(result.id()).isEqualTo("test-id");
        assertThat(result.isDetermined()).isTrue();
    }

    @Test
    void queryResult_notMatched_createsNotMatchedResult() {
        QueryCriterion criterion = new QueryCriterion("test-id", Map.of("field", "value"));
        List<String> missingPaths = List.of("field");

        QueryResult result = QueryResult.notMatched(criterion, missingPaths);

        assertThat(result.state()).isEqualTo(EvaluationState.NOT_MATCHED);
        assertThat(result.missingPaths()).containsExactly("field");
        assertThat(result.failureReason()).isNull();
        assertThat(result.isDetermined()).isTrue();
    }

    @Test
    void queryResult_undetermined_createsUndeterminedResult() {
        QueryCriterion criterion = new QueryCriterion("test-id", Map.of("field", "value"));
        List<String> missingPaths = List.of("field", "nested.path");

        QueryResult result = QueryResult.undetermined(criterion, "Custom reason", missingPaths);

        assertThat(result.state()).isEqualTo(EvaluationState.UNDETERMINED);
        assertThat(result.failureReason()).isEqualTo("Custom reason");
        assertThat(result.missingPaths()).containsExactly("field", "nested.path");
        assertThat(result.isDetermined()).isFalse();
    }

    @Test
    void queryResult_missing_withCriterion_createsUndeterminedResult() {
        QueryCriterion criterion = new QueryCriterion("missing-id");

        QueryResult result = QueryResult.missing(criterion);

        assertThat(result.state()).isEqualTo(EvaluationState.UNDETERMINED);
        assertThat(result.id()).isEqualTo("missing-id");
        assertThat(result.failureReason()).isEqualTo("Criterion definition not found");
        assertThat(result.missingPaths()).containsExactly("criterion definition");
    }

    @Test
    void queryResult_missing_withId_createsUndeterminedResult() {
        QueryResult result = QueryResult.missing("missing-id");

        assertThat(result.state()).isEqualTo(EvaluationState.UNDETERMINED);
        assertThat(result.id()).isEqualTo("missing-id");
        assertThat(result.failureReason()).isEqualTo("Criterion definition not found");
    }

    // ==================== QueryResult Reason Tests ====================

    @Test
    void queryResult_reason_returnsNull_forMatched() {
        QueryCriterion criterion = new QueryCriterion("test", Map.of("field", "value"));
        QueryResult result = QueryResult.matched(criterion);

        assertThat(result.reason()).isNull();
    }

    @Test
    void queryResult_reason_returnsFailureReason_forUndetermined() {
        QueryCriterion criterion = new QueryCriterion("test", Map.of("field", "value"));
        QueryResult result = QueryResult.undetermined(criterion, "Custom failure", Collections.emptyList());

        assertThat(result.reason()).isEqualTo("Custom failure");
    }

    @Test
    void queryResult_reason_returnsMissingDataMessage_forUndeterminedWithPaths() {
        QueryCriterion criterion = new QueryCriterion("test", Map.of("field", "value"));
        QueryResult result = QueryResult.undetermined(criterion, null, List.of("field"));

        assertThat(result.reason()).contains("Missing data at");
        assertThat(result.reason()).contains("field");
    }

    @Test
    void queryResult_reason_returnsEvaluationFailed_forUndeterminedWithoutReasonOrPaths() {
        QueryCriterion criterion = new QueryCriterion("test", Map.of("field", "value"));
        QueryResult result = QueryResult.undetermined(criterion, null, Collections.emptyList());

        assertThat(result.reason()).isEqualTo("Evaluation failed");
    }

    @Test
    void queryResult_reason_returnsNonMatchingValues_forNotMatched() {
        QueryCriterion criterion = new QueryCriterion("test", Map.of("field", "value"));
        QueryResult result = QueryResult.notMatched(criterion, Collections.emptyList());

        assertThat(result.reason()).contains("Non-matching values at");
    }

    @Test
    void queryResult_reason_returnsMissingData_forNotMatchedWithPaths() {
        QueryCriterion criterion = new QueryCriterion("test", Map.of("field", "value"));
        QueryResult result = QueryResult.notMatched(criterion, List.of("field"));

        assertThat(result.reason()).contains("Missing data at");
        assertThat(result.reason()).contains("field");
    }

    // ==================== QueryResult Constructor Tests ====================

    @Test
    void queryResult_constructor_makesPathsImmutable() {
        QueryCriterion criterion = new QueryCriterion("test");

        QueryResult result = new QueryResult(criterion, EvaluationState.MATCHED,
                null, null);

        assertThat(result.missingPaths()).isEmpty();
    }

    @Test
    void queryResult_toString_containsId() {
        QueryCriterion criterion = new QueryCriterion("test-id", Map.of("field", "value"));
        QueryResult result = QueryResult.matched(criterion);

        String str = result.toString();

        assertThat(str).contains("test-id");
        assertThat(str).contains("match: true");
        assertThat(str).contains("state: MATCHED");
    }

    @Test
    void queryResult_toString_containsMissingPaths() {
        QueryCriterion criterion = new QueryCriterion("test-id", Map.of("field", "value"));
        QueryResult result = QueryResult.undetermined(criterion, "reason", List.of("path1", "path2"));

        String str = result.toString();

        assertThat(str).contains("missing: [path1, path2]");
        assertThat(str).contains("reason");
    }

    // ==================== ReferenceResult Tests ====================

    @Test
    void referenceResult_delegatesState() {
        QueryCriterion criterion = new QueryCriterion("original");
        QueryResult original = QueryResult.matched(criterion);
        CriterionReference reference = new CriterionReference("original");

        ReferenceResult result = new ReferenceResult(reference, original);

        assertThat(result.state()).isEqualTo(EvaluationState.MATCHED);
        assertThat(result.id()).isEqualTo("original");
    }

    @Test
    void referenceResult_delegatesReason() {
        QueryCriterion criterion = new QueryCriterion("original", Map.of("field", "value"));
        QueryResult original = QueryResult.undetermined(criterion, "Test reason", Collections.emptyList());
        CriterionReference reference = new CriterionReference("original");

        ReferenceResult result = new ReferenceResult(reference, original);

        assertThat(result.reason()).isEqualTo("Test reason");
    }

    @Test
    void referenceResult_missing_createsUndeterminedResult() {
        CriterionReference reference = new CriterionReference("missing-ref");

        ReferenceResult result = ReferenceResult.missing(reference);

        assertThat(result.state()).isEqualTo(EvaluationState.UNDETERMINED);
        assertThat(result.reason()).contains("not found");
    }

    @Test
    void referenceResult_isDetermined_delegatesToState() {
        QueryCriterion criterion = new QueryCriterion("original");
        QueryResult original = QueryResult.matched(criterion);
        CriterionReference reference = new CriterionReference("original");

        ReferenceResult result = new ReferenceResult(reference, original);

        assertThat(result.isDetermined()).isTrue();
    }

    @Test
    void referenceResult_unwrap_returnsOriginalResult() {
        QueryCriterion criterion = new QueryCriterion("original");
        QueryResult original = QueryResult.matched(criterion);
        CriterionReference reference = new CriterionReference("original");

        ReferenceResult result = new ReferenceResult(reference, original);

        assertThat(result.unwrap()).isEqualTo(original);
    }

    @Test
    void referenceResult_unwrap_recursivelyUnwrapsNestedReferences() {
        QueryCriterion criterion = new QueryCriterion("original");
        QueryResult original = QueryResult.matched(criterion);

        CriterionReference ref1 = new CriterionReference("ref1");
        ReferenceResult refResult1 = new ReferenceResult(ref1, original);

        CriterionReference ref2 = new CriterionReference("ref2");
        ReferenceResult refResult2 = new ReferenceResult(ref2, refResult1);

        CriterionReference ref3 = new CriterionReference("ref3");
        ReferenceResult refResult3 = new ReferenceResult(ref3, refResult2);

        EvaluationResult unwrapped = refResult3.unwrap();

        assertThat(unwrapped).isEqualTo(original);
        assertThat(unwrapped).isInstanceOf(QueryResult.class);
    }

    @Test
    void referenceResult_toString_containsId() {
        QueryCriterion criterion = new QueryCriterion("original");
        QueryResult original = QueryResult.matched(criterion);
        CriterionReference reference = new CriterionReference("original");

        ReferenceResult result = new ReferenceResult(reference, original);
        String str = result.toString();

        assertThat(str).contains("original (reference)");
        assertThat(str).contains("match: true");
        assertThat(str).contains("state: MATCHED");
    }

    // ==================== CompositeResult Tests ====================

    @Test
    void compositeResult_calculatesStatistics() {
        QueryCriterion q1 = new QueryCriterion("q1");
        QueryCriterion q2 = new QueryCriterion("q2");
        QueryCriterion q3 = new QueryCriterion("q3");

        QueryResult r1 = QueryResult.matched(q1);
        QueryResult r2 = QueryResult.notMatched(q2, Collections.emptyList());
        QueryResult r3 = QueryResult.undetermined(q3, "reason", Collections.emptyList());

        CompositeCriterion composite = CompositeCriterion.builder()
                .id("test")
                .and()
                .criteria(q1, q2, q3)
                .build();

        CompositeResult result = new CompositeResult(composite, EvaluationState.NOT_MATCHED,
                List.of(r1, r2, r3));

        CompositeResult.Statistics stats = result.statistics();

        assertThat(stats.matched()).isEqualTo(1);
        assertThat(stats.notMatched()).isEqualTo(1);
        assertThat(stats.undetermined()).isEqualTo(1);
    }

    @Test
    void compositeResult_id_returnsCriterionId() {
        QueryCriterion q = new QueryCriterion("q1");
        CompositeCriterion composite = CompositeCriterion.builder()
                .id("my-composite")
                .and()
                .addCriterion(q)
                .build();

        CompositeResult result = new CompositeResult(composite, EvaluationState.MATCHED,
                Collections.emptyList());

        assertThat(result.id()).isEqualTo("my-composite");
    }

    @Test
    void compositeResult_junction_returnsJunction() {
        QueryCriterion q = new QueryCriterion("q1");
        CompositeCriterion composite = CompositeCriterion.builder()
                .id("test")
                .or()
                .addCriterion(q)
                .build();

        CompositeResult result = new CompositeResult(composite, EvaluationState.MATCHED,
                Collections.emptyList());

        assertThat(result.junction()).isEqualTo(Junction.OR);
    }

    @Test
    void compositeResult_reason_forMatched() {
        QueryCriterion q1 = new QueryCriterion("q1");
        QueryResult r1 = QueryResult.matched(q1);

        CompositeCriterion composite = CompositeCriterion.builder()
                .id("test")
                .and()
                .criteria(q1)
                .build();

        CompositeResult result = new CompositeResult(composite, EvaluationState.MATCHED,
                List.of(r1));

        assertThat(result.reason()).isNull();
    }

    @Test
    void compositeResult_reason_forNotMatchedAnd() {
        QueryCriterion q1 = new QueryCriterion("q1");
        QueryResult r1 = QueryResult.notMatched(q1, Collections.emptyList());

        CompositeCriterion composite = CompositeCriterion.builder()
                .id("test")
                .and()
                .criteria(q1)
                .build();

        CompositeResult result = new CompositeResult(composite, EvaluationState.NOT_MATCHED,
                List.of(r1));

        assertThat(result.reason()).contains("AND composite failed");
    }

    @Test
    void compositeResult_reason_forNotMatchedOr() {
        QueryCriterion q1 = new QueryCriterion("q1");
        QueryResult r1 = QueryResult.notMatched(q1, Collections.emptyList());

        CompositeCriterion composite = CompositeCriterion.builder()
                .id("test")
                .or()
                .criteria(q1)
                .build();

        CompositeResult result = new CompositeResult(composite, EvaluationState.NOT_MATCHED,
                List.of(r1));

        assertThat(result.reason()).contains("OR composite failed");
    }

    @Test
    void compositeResult_reason_forUndetermined() {
        QueryCriterion q1 = new QueryCriterion("q1");
        QueryResult r1 = QueryResult.undetermined(q1, "reason", Collections.emptyList());

        CompositeCriterion composite = CompositeCriterion.builder()
                .id("test")
                .and()
                .criteria(q1)
                .build();

        CompositeResult result = new CompositeResult(composite, EvaluationState.UNDETERMINED,
                List.of(r1));

        assertThat(result.reason()).contains("undetermined");
    }

    @Test
    void compositeResult_isDetermined_returnsTrue_whenDetermined() {
        QueryCriterion q = new QueryCriterion("q1");
        CompositeCriterion composite = CompositeCriterion.builder()
                .id("test")
                .and()
                .addCriterion(q)
                .build();

        CompositeResult result = new CompositeResult(composite, EvaluationState.MATCHED,
                Collections.emptyList());

        assertThat(result.isDetermined()).isTrue();
    }

    @Test
    void compositeResult_isDetermined_returnsFalse_whenUndetermined() {
        QueryCriterion q = new QueryCriterion("q1");
        CompositeCriterion composite = CompositeCriterion.builder()
                .id("test")
                .and()
                .addCriterion(q)
                .build();

        CompositeResult result = new CompositeResult(composite, EvaluationState.UNDETERMINED,
                Collections.emptyList());

        assertThat(result.isDetermined()).isFalse();
    }

    @Test
    void compositeResult_toString_containsIdAndJunction() {
        QueryCriterion q = new QueryCriterion("q1");
        CompositeCriterion composite = CompositeCriterion.builder()
                .id("test-composite")
                .and()
                .addCriterion(q)
                .build();

        CompositeResult result = new CompositeResult(composite, EvaluationState.MATCHED,
                Collections.emptyList());

        String str = result.toString();

        assertThat(str).contains("test-composite");
        assertThat(str).contains("AND");
        assertThat(str).contains("MATCHED");
    }
}
