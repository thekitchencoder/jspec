package uk.codery.jspec.evaluator;

import org.junit.jupiter.api.Test;
import uk.codery.jspec.model.QueryCriterion;
import uk.codery.jspec.model.Specification;
import uk.codery.jspec.result.EvaluationOutcome;
import uk.codery.jspec.result.EvaluationState;
import uk.codery.jspec.result.QueryResult;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Investigation harness for the "$or false-positive" (docs/IMPROVEMENT_ROADMAP.md §820).
 *
 * <p>Pins the <em>desired</em> Strong Kleene (K3) semantics for the {@code $or} and
 * {@code $and} value-combinators when a branch is UNDETERMINED — whether that
 * UNDETERMINED arises from an unknown operator or from a missing {@code $contextPath}.
 *
 * <p>Half of these pinned behaviours did not exist before the Kleene fix
 * (see CHANGELOG [Unreleased]); the other half are invariants the fix must preserve.
 *
 * <p>Desired K3 truth table:
 * <pre>
 *   OR:  MATCHED ∨ x = MATCHED            AND: NOT_MATCHED ∧ x = NOT_MATCHED
 *        NOT_MATCHED ∨ NOT_MATCHED = NM        MATCHED ∧ MATCHED = MATCHED
 *        NOT_MATCHED ∨ UNDET = UNDET           MATCHED ∧ UNDET = UNDET
 *        UNDET ∨ UNDET = UNDET                 UNDET ∧ UNDET = UNDET
 * </pre>
 */
class OrAndKleeneSemanticsTest {

    private final CriterionEvaluator evaluator = new CriterionEvaluator();

    private EvaluationState eval(Map<String, Object> query, Map<String, Object> doc) {
        return evaluator.evaluateQuery(doc, new QueryCriterion("test", query)).state();
    }

    private EvaluationState evalWithContext(Map<String, Object> query,
                                            Map<String, Object> doc,
                                            Map<String, Object> ctx) {
        return resultWithContext(query, doc, ctx).state();
    }

    private QueryResult resultWithContext(Map<String, Object> query,
                                          Map<String, Object> doc,
                                          Map<String, Object> ctx) {
        Specification spec = new Specification("s", List.of(new QueryCriterion("test", query)));
        EvaluationOutcome outcome = new SpecificationEvaluator(spec).evaluate(doc, ctx);
        return (QueryResult) outcome.results().stream()
                .filter(r -> r.id().equals("test"))
                .findFirst().orElseThrow();
    }

    // ─── $or with an unknown-operator branch (no $contextPath) ────────────────

    /** Invariant (passes today): a matching branch wins regardless of a sibling UNDETERMINED. */
    @Test
    void or_matchedBranch_winsOverUndeterminedSibling() {
        EvaluationState state = eval(
                Map.of("value", Map.of("$or", List.of(
                        Map.of("$unknownOp", 1),   // UNDETERMINED
                        Map.of("$eq", 5)))),        // MATCHED
                Map.of("value", 5));
        assertThat(state).isEqualTo(EvaluationState.MATCHED);   // MATCHED ∨ UNDET = MATCHED
    }

    /** GAP (fails today → NOT_MATCHED): UNDET ∨ NOT_MATCHED must be UNDETERMINED. */
    @Test
    void or_undeterminedBranch_withNonMatchingSibling_isUndetermined() {
        EvaluationState state = eval(
                Map.of("value", Map.of("$or", List.of(
                        Map.of("$unknownOp", 1),   // UNDETERMINED
                        Map.of("$eq", 5)))),        // NOT_MATCHED (value is 3)
                Map.of("value", 3));
        assertThat(state).isEqualTo(EvaluationState.UNDETERMINED);
    }

    // ─── $and with an unknown-operator branch (no $contextPath) ───────────────

    /** Invariant (passes today): a non-matching branch short-circuits $and to NOT_MATCHED. */
    @Test
    void and_notMatchedBranch_shortCircuitsOverUndeterminedSibling() {
        EvaluationState state = eval(
                Map.of("value", Map.of("$and", List.of(
                        Map.of("$eq", 999),        // NOT_MATCHED
                        Map.of("$unknownOp", 1)))), // UNDETERMINED
                Map.of("value", 5));
        assertThat(state).isEqualTo(EvaluationState.NOT_MATCHED);  // NM ∧ UNDET = NM
    }

    /** GAP (fails today → NOT_MATCHED): MATCHED ∧ UNDET must be UNDETERMINED. */
    @Test
    void and_matchedBranch_withUndeterminedSibling_isUndetermined() {
        EvaluationState state = eval(
                Map.of("value", Map.of("$and", List.of(
                        Map.of("$eq", 5),          // MATCHED
                        Map.of("$unknownOp", 1)))), // UNDETERMINED
                Map.of("value", 5));
        assertThat(state).isEqualTo(EvaluationState.UNDETERMINED);
    }

    // ─── $or with a missing $contextPath branch (the reported false-positive) ──

    /** GAP (fails today → UNDETERMINED): a matching branch must win even though the
     *  sibling's $contextPath is unresolved. This is the headline false-positive. */
    @Test
    void or_matchingBranch_winsOverMissingContextPathSibling() {
        EvaluationState state = evalWithContext(
                Map.of("score", Map.of("$or", List.of(
                        Map.of("$eq", 0),
                        Map.of("$gte", Map.of("$contextPath", "candidate.threshold"))))),
                Map.of("score", 0),
                Map.of());   // candidate.threshold absent
        assertThat(state).isEqualTo(EvaluationState.MATCHED);
    }

    /** Invariant (passes today, must stay): NOT_MATCHED ∨ (missing ctx) = UNDETERMINED. */
    @Test
    void or_nonMatchingBranch_withMissingContextPathSibling_isUndetermined() {
        EvaluationState state = evalWithContext(
                Map.of("score", Map.of("$or", List.of(
                        Map.of("$eq", 0),
                        Map.of("$gte", Map.of("$contextPath", "candidate.threshold"))))),
                Map.of("score", 5),   // not 0 → branch 1 NOT_MATCHED
                Map.of());            // candidate.threshold absent → branch 2 UNDETERMINED
        assertThat(state).isEqualTo(EvaluationState.UNDETERMINED);
    }

    // ─── $and with a missing $contextPath branch ──────────────────────────────

    /** GAP (fails today → UNDETERMINED): NOT_MATCHED ∧ (missing ctx) must short-circuit
     *  to NOT_MATCHED — the criterion is definitively false, not undetermined. */
    @Test
    void and_nonMatchingBranch_shortCircuitsOverMissingContextPath() {
        EvaluationState state = evalWithContext(
                Map.of("value", Map.of("$and", List.of(
                        Map.of("$eq", 999),
                        Map.of("$lte", Map.of("$contextPath", "candidate.cap"))))),
                Map.of("value", 5),   // not 999 → branch 1 NOT_MATCHED
                Map.of());            // candidate.cap absent → branch 2 UNDETERMINED
        assertThat(state).isEqualTo(EvaluationState.NOT_MATCHED);
    }

    /** Invariant (passes today, must stay): MATCHED ∧ (missing ctx) = UNDETERMINED. */
    @Test
    void and_matchingBranch_withMissingContextPath_isUndetermined() {
        EvaluationState state = evalWithContext(
                Map.of("value", Map.of("$and", List.of(
                        Map.of("$gte", 0),
                        Map.of("$lte", Map.of("$contextPath", "candidate.cap"))))),
                Map.of("value", 5),   // >= 0 → branch 1 MATCHED
                Map.of());            // candidate.cap absent → branch 2 UNDETERMINED
        assertThat(state).isEqualTo(EvaluationState.UNDETERMINED);
    }

    // ─── Missing-path reporting: only paths that influenced UNDETERMINED ──────

    /** Decision: a path inside a branch overridden by a MATCHED sibling did not
     *  influence the outcome, so it is NOT reported. */
    @Test
    void matchedOr_doesNotReportTheUnresolvedSiblingsPath() {
        QueryResult result = resultWithContext(
                Map.of("score", Map.of("$or", List.of(
                        Map.of("$eq", 0),
                        Map.of("$gte", Map.of("$contextPath", "candidate.threshold"))))),
                Map.of("score", 0),
                Map.of());
        assertThat(result.state()).isEqualTo(EvaluationState.MATCHED);
        assertThat(result.missingPaths()).isEmpty();
    }

    /** Decision: when the unresolved path is what leaves the criterion UNDETERMINED,
     *  it IS reported. */
    @Test
    void undeterminedOr_reportsTheInfluentialPath() {
        QueryResult result = resultWithContext(
                Map.of("score", Map.of("$or", List.of(
                        Map.of("$eq", 0),
                        Map.of("$gte", Map.of("$contextPath", "candidate.threshold"))))),
                Map.of("score", 5),
                Map.of());
        assertThat(result.state()).isEqualTo(EvaluationState.UNDETERMINED);
        assertThat(result.missingPaths()).containsExactly("context.candidate.threshold");
    }

    /** Decision: a definitively-false $and short-circuits to NOT_MATCHED and reports
     *  no paths, even though a later branch had an unresolved reference. */
    @Test
    void notMatchedAnd_reportsNoPaths() {
        QueryResult result = resultWithContext(
                Map.of("value", Map.of("$and", List.of(
                        Map.of("$eq", 999),
                        Map.of("$lte", Map.of("$contextPath", "candidate.cap"))))),
                Map.of("value", 5),
                Map.of());
        assertThat(result.state()).isEqualTo(EvaluationState.NOT_MATCHED);
        assertThat(result.missingPaths()).isEmpty();
    }
}
