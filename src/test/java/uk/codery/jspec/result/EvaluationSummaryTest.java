package uk.codery.jspec.result;

import org.junit.jupiter.api.Test;
import uk.codery.jspec.model.QueryCriterion;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for EvaluationSummary functionality.
 */
class EvaluationSummaryTest {

    @Test
    void summary_allMatched() {
        List<EvaluationResult> results = List.of(
                createResult(EvaluationState.MATCHED),
                createResult(EvaluationState.MATCHED),
                createResult(EvaluationState.MATCHED)
        );

        EvaluationSummary summary = EvaluationSummary.from(results);

        assertThat(summary.total()).isEqualTo(3);
        assertThat(summary.matched()).isEqualTo(3);
        assertThat(summary.notMatched()).isEqualTo(0);
        assertThat(summary.undetermined()).isEqualTo(0);
        assertThat(summary.fullyDetermined()).isTrue();
    }

    @Test
    void summary_allNotMatched() {
        List<EvaluationResult> results = List.of(
                createResult(EvaluationState.NOT_MATCHED),
                createResult(EvaluationState.NOT_MATCHED)
        );

        EvaluationSummary summary = EvaluationSummary.from(results);

        assertThat(summary.total()).isEqualTo(2);
        assertThat(summary.matched()).isEqualTo(0);
        assertThat(summary.notMatched()).isEqualTo(2);
        assertThat(summary.undetermined()).isEqualTo(0);
        assertThat(summary.fullyDetermined()).isTrue();
    }

    @Test
    void summary_mixedStates() {
        List<EvaluationResult> results = List.of(
                createResult(EvaluationState.MATCHED),
                createResult(EvaluationState.NOT_MATCHED),
                createResult(EvaluationState.UNDETERMINED),
                createResult(EvaluationState.MATCHED)
        );

        EvaluationSummary summary = EvaluationSummary.from(results);

        assertThat(summary.total()).isEqualTo(4);
        assertThat(summary.matched()).isEqualTo(2);
        assertThat(summary.notMatched()).isEqualTo(1);
        assertThat(summary.undetermined()).isEqualTo(1);
        assertThat(summary.fullyDetermined()).isFalse();
    }

    @Test
    void summary_allUndetermined() {
        List<EvaluationResult> results = List.of(
                createResult(EvaluationState.UNDETERMINED),
                createResult(EvaluationState.UNDETERMINED)
        );

        EvaluationSummary summary = EvaluationSummary.from(results);

        assertThat(summary.total()).isEqualTo(2);
        assertThat(summary.matched()).isEqualTo(0);
        assertThat(summary.notMatched()).isEqualTo(0);
        assertThat(summary.undetermined()).isEqualTo(2);
        assertThat(summary.fullyDetermined()).isFalse();
    }

    @Test
    void summary_emptyResults() {
        List<EvaluationResult> results = List.of();

        EvaluationSummary summary = EvaluationSummary.from(results);

        assertThat(summary.total()).isEqualTo(0);
        assertThat(summary.matched()).isEqualTo(0);
        assertThat(summary.notMatched()).isEqualTo(0);
        assertThat(summary.undetermined()).isEqualTo(0);
        assertThat(summary.fullyDetermined()).isTrue();
    }

    @Test
    void summary_validation_failsWhenSumDoesNotMatchTotal() {
        assertThatThrownBy(() ->
                new EvaluationSummary(10, 3, 3, 3, true)
        ).isInstanceOf(IllegalArgumentException.class)
         .hasMessageContaining("must equal total");
    }

    @Test
    void summary_validation_passesWhenSumMatchesTotal() {
        EvaluationSummary summary = new EvaluationSummary(10, 4, 3, 3, false);

        assertThat(summary.total()).isEqualTo(10);
        assertThat(summary.matched()).isEqualTo(4);
        assertThat(summary.notMatched()).isEqualTo(3);
        assertThat(summary.undetermined()).isEqualTo(3);
    }

    @Test
    void summary_fullyDetermined_onlyTrueWhenNoUndetermined() {
        EvaluationSummary allDetermined = new EvaluationSummary(5, 3, 2, 0, true);
        assertThat(allDetermined.fullyDetermined()).isTrue();

        EvaluationSummary hasUndetermined = new EvaluationSummary(5, 3, 1, 1, false);
        assertThat(hasUndetermined.fullyDetermined()).isFalse();
    }

    private QueryResult createResult(EvaluationState state) {
        QueryCriterion dummyCriterion = new QueryCriterion("test-" + state, Map.of());
        return new QueryResult(dummyCriterion, state, List.of(), null);
    }
}
