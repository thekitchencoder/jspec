package uk.codery.jspec.result;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.codery.jspec.model.CompositeCriterion;
import uk.codery.jspec.model.CriterionReference;
import uk.codery.jspec.model.Junction;
import uk.codery.jspec.model.QueryCriterion;

import java.util.*;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for EvaluationOutcome API.
 */
class EvaluationOutcomeTest {

    private EvaluationOutcome mixedOutcome;
    private QueryResult matchedQuery;
    private QueryResult notMatchedQuery;
    private QueryResult undeterminedQuery;
    private CompositeResult compositeResult;

    @BeforeEach
    void setUp() {
        // Create various result types for testing
        QueryCriterion query1 = QueryCriterion.builder()
                .id("matched-query")
                .field("age").gte(18)
                .build();
        matchedQuery = QueryResult.matched(query1);

        QueryCriterion query2 = QueryCriterion.builder()
                .id("not-matched-query")
                .field("status").eq("active")
                .build();
        notMatchedQuery = QueryResult.notMatched(query2, List.of("status"));

        QueryCriterion query3 = QueryCriterion.builder()
                .id("undetermined-query")
                .field("email").exists(true)
                .build();
        undeterminedQuery = QueryResult.undetermined(query3, "Missing field", List.of("email"));

        CompositeCriterion composite = CompositeCriterion.builder()
                .id("composite-check")
                .and()
                .criteria(query1, query2)
                .build();
        compositeResult = new CompositeResult(composite, EvaluationState.NOT_MATCHED,
                List.of(matchedQuery, notMatchedQuery));

        CriterionReference reference = new CriterionReference("ref-to-matched");
        ReferenceResult referenceResult = new ReferenceResult(reference, matchedQuery);

        List<EvaluationResult> results = List.of(
                matchedQuery, notMatchedQuery, undeterminedQuery,
                compositeResult, referenceResult
        );
        EvaluationSummary summary = EvaluationSummary.from(results);
        mixedOutcome = new EvaluationOutcome("mixed-spec", results, summary);
    }

    // ==================== Constructor Tests ====================

    @Test
    void constructor_makesResultsImmutable() {
        List<EvaluationResult> mutableList = new ArrayList<>();
        mutableList.add(matchedQuery);

        EvaluationOutcome outcome = new EvaluationOutcome("test-spec", mutableList,
                EvaluationSummary.from(mutableList));

        // Modify original list
        mutableList.add(notMatchedQuery);

        // Outcome should not be affected
        assertThat(outcome.results()).hasSize(1);
    }

    @Test
    void constructor_handlesNullResults() {
        EvaluationOutcome outcome = new EvaluationOutcome("test-spec", null,
                new EvaluationSummary(0, 0, 0, 0, true));

        assertThat(outcome.results()).isEmpty();
    }

    // ==================== Find Methods Tests ====================

    @Test
    void find_returnsResultById() {
        Optional<EvaluationResult> result = mixedOutcome.find("matched-query");

        assertThat(result).isPresent();
        assertThat(result.get().id()).isEqualTo("matched-query");
        assertThat(result.get().state()).isEqualTo(EvaluationState.MATCHED);
    }

    @Test
    void find_returnsEmptyForNonExistent() {
        Optional<EvaluationResult> result = mixedOutcome.find("non-existent");

        assertThat(result).isEmpty();
    }

    @Test
    void findQuery_returnsQueryResult() {
        Optional<QueryResult> result = mixedOutcome.findQuery("matched-query");

        assertThat(result).isPresent();
        assertThat(result.get()).isInstanceOf(QueryResult.class);
        assertThat(result.get().id()).isEqualTo("matched-query");
    }

    @Test
    void findQuery_returnsEmptyForNonQueryResult() {
        Optional<QueryResult> result = mixedOutcome.findQuery("composite-check");

        assertThat(result).isEmpty();
    }

    @Test
    void findComposite_returnsCompositeResult() {
        Optional<CompositeResult> result = mixedOutcome.findComposite("composite-check");

        assertThat(result).isPresent();
        assertThat(result.get()).isInstanceOf(CompositeResult.class);
        assertThat(result.get().junction()).isEqualTo(Junction.AND);
    }

    @Test
    void findComposite_returnsEmptyForNonCompositeResult() {
        Optional<CompositeResult> result = mixedOutcome.findComposite("matched-query");

        assertThat(result).isEmpty();
    }

    @Test
    void findReference_returnsReferenceResult() {
        Optional<ReferenceResult> result = mixedOutcome.findReference("ref-to-matched");

        assertThat(result).isPresent();
        assertThat(result.get()).isInstanceOf(ReferenceResult.class);
    }

    @Test
    void findReference_returnsEmptyForNonReferenceResult() {
        Optional<ReferenceResult> result = mixedOutcome.findReference("composite-check");

        assertThat(result).isEmpty();
    }

    // ==================== Convenience State Check Tests ====================

    @Test
    void matched_returnsTrue_whenResultMatched() {
        boolean result = mixedOutcome.matched("matched-query");

        assertThat(result).isTrue();
    }

    @Test
    void matched_returnsFalse_whenResultNotMatched() {
        boolean result = mixedOutcome.matched("not-matched-query");

        assertThat(result).isFalse();
    }

    @Test
    void matched_returnsFalse_whenResultNotFound() {
        boolean result = mixedOutcome.matched("non-existent");

        assertThat(result).isFalse();
    }

    @Test
    void notMatched_returnsTrue_whenResultNotMatched() {
        boolean result = mixedOutcome.notMatched("not-matched-query");

        assertThat(result).isTrue();
    }

    @Test
    void notMatched_returnsFalse_whenResultMatched() {
        boolean result = mixedOutcome.notMatched("matched-query");

        assertThat(result).isFalse();
    }

    @Test
    void notMatched_returnsFalse_whenResultNotFound() {
        boolean result = mixedOutcome.notMatched("non-existent");

        assertThat(result).isFalse();
    }

    @Test
    void undetermined_returnsTrue_whenResultUndetermined() {
        boolean result = mixedOutcome.undetermined("undetermined-query");

        assertThat(result).isTrue();
    }

    @Test
    void undetermined_returnsFalse_whenResultMatched() {
        boolean result = mixedOutcome.undetermined("matched-query");

        assertThat(result).isFalse();
    }

    @Test
    void undetermined_returnsTrue_whenResultNotFound() {
        // Missing = undetermined
        boolean result = mixedOutcome.undetermined("non-existent");

        assertThat(result).isTrue();
    }

    // ==================== Business Logic Helper Tests ====================

    @Test
    void hasMatches_returnsTrue_whenSomeMatched() {
        assertThat(mixedOutcome.hasMatches()).isTrue();
    }

    @Test
    void hasMatches_returnsFalse_whenNoneMatched() {
        EvaluationOutcome outcome = new EvaluationOutcome("test-spec",
                List.of(notMatchedQuery),
                EvaluationSummary.from(List.of(notMatchedQuery)));

        assertThat(outcome.hasMatches()).isFalse();
    }

    @Test
    void allMatched_returnsTrue_whenAllMatched() {
        EvaluationOutcome outcome = new EvaluationOutcome("test-spec",
                List.of(matchedQuery),
                EvaluationSummary.from(List.of(matchedQuery)));

        assertThat(outcome.allMatched()).isTrue();
    }

    @Test
    void allMatched_returnsFalse_whenSomeNotMatched() {
        assertThat(mixedOutcome.allMatched()).isFalse();
    }

    @Test
    void noneMatched_returnsTrue_whenNoneMatched() {
        EvaluationOutcome outcome = new EvaluationOutcome("test-spec",
                List.of(notMatchedQuery),
                EvaluationSummary.from(List.of(notMatchedQuery)));

        assertThat(outcome.noneMatched()).isTrue();
    }

    @Test
    void noneMatched_returnsFalse_whenSomeMatched() {
        assertThat(mixedOutcome.noneMatched()).isFalse();
    }

    @Test
    void anyFailed_returnsTrue_whenSomeFailed() {
        assertThat(mixedOutcome.anyFailed()).isTrue();
    }

    @Test
    void anyFailed_returnsFalse_whenAllMatched() {
        EvaluationOutcome outcome = new EvaluationOutcome("test-spec",
                List.of(matchedQuery),
                EvaluationSummary.from(List.of(matchedQuery)));

        assertThat(outcome.anyFailed()).isFalse();
    }

    @Test
    void isFullyDetermined_returnsTrue_whenNoUndetermined() {
        EvaluationOutcome outcome = new EvaluationOutcome("test-spec",
                List.of(matchedQuery, notMatchedQuery),
                EvaluationSummary.from(List.of(matchedQuery, notMatchedQuery)));

        assertThat(outcome.isFullyDetermined()).isTrue();
    }

    @Test
    void isFullyDetermined_returnsFalse_whenSomeUndetermined() {
        assertThat(mixedOutcome.isFullyDetermined()).isFalse();
    }

    // ==================== Stream API Tests ====================

    @Test
    void stream_returnsAllResults() {
        long count = mixedOutcome.stream().count();

        assertThat(count).isEqualTo(5);
    }

    @Test
    void stream_canFilterByState() {
        long matchedCount = mixedOutcome.stream()
                .filter(r -> r.state().matched())
                .count();

        assertThat(matchedCount).isEqualTo(2); // matched-query and reference to it
    }

    @Test
    void stream_canMapToIds() {
        List<String> ids = mixedOutcome.stream()
                .map(EvaluationResult::id)
                .toList();

        assertThat(ids).containsExactly(
                "matched-query", "not-matched-query", "undetermined-query",
                "composite-check", "ref-to-matched"
        );
    }

    // ==================== Map API Tests ====================

    @Test
    void asMap_returnsMapOfResults() {
        Map<String, EvaluationResult> map = mixedOutcome.asMap();

        assertThat(map).containsKey("matched-query");
        assertThat(map).containsKey("not-matched-query");
        assertThat(map).containsKey("undetermined-query");
        assertThat(map).containsKey("composite-check");
        assertThat(map).containsKey("ref-to-matched");
        assertThat(map).hasSize(5);
    }

    @Test
    void asMap_isImmutable() {
        Map<String, EvaluationResult> map = mixedOutcome.asMap();

        assertThatThrownBy(() -> map.put("new-key", matchedQuery))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    // ==================== First Result Tests ====================

    @Test
    void firstQuery_returnsFirstQueryResult() {
        Optional<QueryResult> result = mixedOutcome.firstQuery();

        assertThat(result).isPresent();
        assertThat(result.get().id()).isEqualTo("matched-query");
    }

    @Test
    void firstQuery_returnsEmpty_whenNoQueries() {
        EvaluationOutcome outcome = new EvaluationOutcome("test-spec",
                List.of(compositeResult),
                EvaluationSummary.from(List.of(compositeResult)));

        assertThat(outcome.firstQuery()).isEmpty();
    }

    @Test
    void firstComposite_returnsFirstCompositeResult() {
        Optional<CompositeResult> result = mixedOutcome.firstComposite();

        assertThat(result).isPresent();
        assertThat(result.get().id()).isEqualTo("composite-check");
    }

    @Test
    void firstComposite_returnsEmpty_whenNoComposites() {
        EvaluationOutcome outcome = new EvaluationOutcome("test-spec",
                List.of(matchedQuery),
                EvaluationSummary.from(List.of(matchedQuery)));

        assertThat(outcome.firstComposite()).isEmpty();
    }

    @Test
    void firstReference_returnsFirstReferenceResult() {
        Optional<ReferenceResult> result = mixedOutcome.firstReference();

        assertThat(result).isPresent();
        assertThat(result.get().id()).isEqualTo("ref-to-matched");
    }

    @Test
    void firstReference_returnsEmpty_whenNoReferences() {
        EvaluationOutcome outcome = new EvaluationOutcome("test-spec",
                List.of(matchedQuery),
                EvaluationSummary.from(List.of(matchedQuery)));

        assertThat(outcome.firstReference()).isEmpty();
    }

    // ==================== Kleene Logic Tests ====================

    @Test
    void overallState_returnsMatched_whenAllMatched() {
        EvaluationOutcome outcome = new EvaluationOutcome("test-spec",
                List.of(matchedQuery),
                EvaluationSummary.from(List.of(matchedQuery)));

        assertThat(outcome.overallState()).isEqualTo(EvaluationState.MATCHED);
    }

    @Test
    void overallState_returnsNotMatched_whenAnyNotMatched() {
        EvaluationOutcome outcome = new EvaluationOutcome("test-spec",
                List.of(matchedQuery, notMatchedQuery),
                EvaluationSummary.from(List.of(matchedQuery, notMatchedQuery)));

        assertThat(outcome.overallState()).isEqualTo(EvaluationState.NOT_MATCHED);
    }

    @Test
    void overallState_returnsUndetermined_whenNoNotMatchedButSomeUndetermined() {
        EvaluationOutcome outcome = new EvaluationOutcome("test-spec",
                List.of(matchedQuery, undeterminedQuery),
                EvaluationSummary.from(List.of(matchedQuery, undeterminedQuery)));

        assertThat(outcome.overallState()).isEqualTo(EvaluationState.UNDETERMINED);
    }

    @Test
    void overallState_returnsMatched_forEmptyResults() {
        EvaluationOutcome outcome = new EvaluationOutcome("test-spec",
                Collections.emptyList(),
                new EvaluationSummary(0, 0, 0, 0, true));

        // Empty results → identity for AND is MATCHED
        assertThat(outcome.overallState()).isEqualTo(EvaluationState.MATCHED);
    }

    @Test
    void anyMatchState_returnsMatched_whenAnyMatched() {
        assertThat(mixedOutcome.anyMatchState()).isEqualTo(EvaluationState.MATCHED);
    }

    @Test
    void anyMatchState_returnsNotMatched_whenAllNotMatched() {
        EvaluationOutcome outcome = new EvaluationOutcome("test-spec",
                List.of(notMatchedQuery),
                EvaluationSummary.from(List.of(notMatchedQuery)));

        assertThat(outcome.anyMatchState()).isEqualTo(EvaluationState.NOT_MATCHED);
    }

    @Test
    void anyMatchState_returnsUndetermined_whenNoMatchedButSomeUndetermined() {
        EvaluationOutcome outcome = new EvaluationOutcome("test-spec",
                List.of(notMatchedQuery, undeterminedQuery),
                EvaluationSummary.from(List.of(notMatchedQuery, undeterminedQuery)));

        assertThat(outcome.anyMatchState()).isEqualTo(EvaluationState.UNDETERMINED);
    }

    @Test
    void anyMatchState_returnsNotMatched_forEmptyResults() {
        EvaluationOutcome outcome = new EvaluationOutcome("test-spec",
                Collections.emptyList(),
                new EvaluationSummary(0, 0, 0, 0, true));

        // Empty results → identity for OR is NOT_MATCHED
        assertThat(outcome.anyMatchState()).isEqualTo(EvaluationState.NOT_MATCHED);
    }

    // ==================== Diagnostics Tests ====================

    @Test
    void getFailureReasons_returnsMapOfFailures() {
        Map<String, String> failures = mixedOutcome.getFailureReasons();

        assertThat(failures).containsKey("not-matched-query");
        assertThat(failures).containsKey("undetermined-query");
        assertThat(failures).containsKey("composite-check");
        assertThat(failures).doesNotContainKey("matched-query");
    }

    @Test
    void getFailureReasons_isImmutable() {
        Map<String, String> failures = mixedOutcome.getFailureReasons();

        assertThatThrownBy(() -> failures.put("new-key", "value"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void getUndeterminedIds_returnsUndeterminedCriterionIds() {
        Set<String> undetermined = mixedOutcome.getUndeterminedIds();

        assertThat(undetermined).containsExactly("undetermined-query");
    }

    @Test
    void getUndeterminedIds_isImmutable() {
        Set<String> undetermined = mixedOutcome.getUndeterminedIds();

        assertThatThrownBy(() -> undetermined.add("new-id"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    // ==================== Type Filtering Tests ====================

    @Test
    void queryResults_returnsOnlyQueryResults() {
        List<QueryResult> queries = mixedOutcome.queryResults();

        assertThat(queries).hasSize(3);
        assertThat(queries).allMatch(Objects::nonNull);
    }

    @Test
    void compositeResults_returnsOnlyCompositeResults() {
        List<CompositeResult> composites = mixedOutcome.compositeResults();

        assertThat(composites).hasSize(1);
        assertThat(composites.getFirst().id()).isEqualTo("composite-check");
    }

    @Test
    void referenceResults_returnsOnlyReferenceResults() {
        List<ReferenceResult> references = mixedOutcome.referenceResults();

        assertThat(references).hasSize(1);
        assertThat(references.getFirst().id()).isEqualTo("ref-to-matched");
    }

    @Test
    void matchedResults_returnsOnlyMatchedResults() {
        List<EvaluationResult> matched = mixedOutcome.matchedResults();

        assertThat(matched).hasSize(2);
        assertThat(matched).allMatch(r -> r.state() == EvaluationState.MATCHED);
    }

    @Test
    void notMatchedResults_returnsOnlyNotMatchedResults() {
        List<EvaluationResult> notMatched = mixedOutcome.notMatchedResults();

        assertThat(notMatched).hasSize(2); // not-matched-query and composite-check
        assertThat(notMatched).allMatch(r -> r.state() == EvaluationState.NOT_MATCHED);
    }

    @Test
    void undeterminedResults_returnsOnlyUndeterminedResults() {
        List<EvaluationResult> undetermined = mixedOutcome.undeterminedResults();

        assertThat(undetermined).hasSize(1);
        assertThat(undetermined.getFirst().id()).isEqualTo("undetermined-query");
    }

    // ==================== Edge Cases ====================

    @Test
    void emptyOutcome_handlesAllMethods() {
        EvaluationOutcome empty = new EvaluationOutcome("empty-spec",
                Collections.emptyList(),
                new EvaluationSummary(0, 0, 0, 0, true));

        assertThat(empty.results()).isEmpty();
        assertThat(empty.queryResults()).isEmpty();
        assertThat(empty.compositeResults()).isEmpty();
        assertThat(empty.referenceResults()).isEmpty();
        assertThat(empty.matchedResults()).isEmpty();
        assertThat(empty.notMatchedResults()).isEmpty();
        assertThat(empty.undeterminedResults()).isEmpty();

        assertThat(empty.find("any")).isEmpty();
        assertThat(empty.matched("any")).isFalse();
        assertThat(empty.undetermined("any")).isTrue();

        assertThat(empty.hasMatches()).isFalse();
        assertThat(empty.allMatched()).isTrue(); // 0 == 0
        assertThat(empty.noneMatched()).isTrue();
        assertThat(empty.anyFailed()).isFalse();
        assertThat(empty.isFullyDetermined()).isTrue();

        assertThat(empty.stream().count()).isEqualTo(0);
        assertThat(empty.asMap()).isEmpty();
        assertThat(empty.getFailureReasons()).isEmpty();
        assertThat(empty.getUndeterminedIds()).isEmpty();
    }

    @Test
    void specificationId_isAccessible() {
        assertThat(mixedOutcome.specificationId()).isEqualTo("mixed-spec");
    }

    @Test
    void summary_isAccessible() {
        EvaluationSummary summary = mixedOutcome.summary();

        assertThat(summary).isNotNull();
        assertThat(summary.total()).isEqualTo(5);
    }
}
