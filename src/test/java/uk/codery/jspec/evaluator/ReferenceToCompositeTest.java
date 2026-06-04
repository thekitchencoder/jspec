package uk.codery.jspec.evaluator;

import org.junit.jupiter.api.Test;
import uk.codery.jspec.model.CompositeCriterion;
import uk.codery.jspec.model.CriterionReference;
import uk.codery.jspec.model.Junction;
import uk.codery.jspec.model.QueryCriterion;
import uk.codery.jspec.model.Specification;
import uk.codery.jspec.result.EvaluationOutcome;
import uk.codery.jspec.result.EvaluationResult;
import uk.codery.jspec.result.EvaluationState;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Tests that {@link CriterionReference} targets which are themselves
 * {@link CompositeCriterion} resolve to the correct determined state regardless of
 * evaluation order, and that malformed reference cycles degrade gracefully to
 * UNDETERMINED rather than throwing / overflowing the stack.
 */
class ReferenceToCompositeTest {

    private EvaluationResult resultFor(EvaluationOutcome outcome, String id) {
        return outcome.results().stream()
                .filter(r -> r.id().equals(id))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No result for id '" + id + "'"));
    }

    /**
     * Main fix: a composite {@code outer} (AND) references {@code inner}, where
     * {@code inner} is itself a composite. Before the fix, {@code inner} may not yet be
     * cached when {@code outer}'s reference resolves, yielding UNDETERMINED by thread
     * timing. After the fix it must deterministically resolve to the composite's state.
     */
    @Test
    void referenceToComposite_resolvesToCompositeState() {
        QueryCriterion base = new QueryCriterion("base", Map.of("age", Map.of("$gte", 18)));
        CompositeCriterion inner = new CompositeCriterion("inner", Junction.AND,
                List.of(new CriterionReference("base")));
        CompositeCriterion outer = new CompositeCriterion("outer", Junction.AND,
                List.of(new CriterionReference("inner")));

        Specification spec = new Specification("ref-to-composite", List.of(base, inner, outer));
        SpecificationEvaluator evaluator = new SpecificationEvaluator(spec);

        // Run repeatedly: the ordering bug is timing dependent, so a single pass may
        // pass by luck. The post-fix assertion must hold every time.
        for (int i = 0; i < 100; i++) {
            EvaluationOutcome outcome = evaluator.evaluate(Map.of("age", 25));
            assertThat(resultFor(outcome, "outer").state())
                    .as("outer composite referencing inner composite, iteration %d", i)
                    .isEqualTo(EvaluationState.MATCHED);
            assertThat(resultFor(outcome, "inner").state())
                    .as("inner composite, iteration %d", i)
                    .isEqualTo(EvaluationState.MATCHED);
        }
    }

    /**
     * Self-cycle: a composite {@code a} contains a reference to itself. Must degrade to
     * UNDETERMINED, never StackOverflow / IllegalStateException.
     */
    @Test
    void selfCycle_degradesToUndetermined() {
        CompositeCriterion a = new CompositeCriterion("a", Junction.AND,
                List.of(new CriterionReference("a")));
        Specification spec = new Specification("self-cycle", List.of(a));
        SpecificationEvaluator evaluator = new SpecificationEvaluator(spec);

        assertThatCode(() -> {
            EvaluationOutcome outcome = evaluator.evaluate(Map.of("age", 25));
            assertThat(resultFor(outcome, "a").state()).isEqualTo(EvaluationState.UNDETERMINED);
        }).doesNotThrowAnyException();
    }

    /**
     * Mutual cycle: composite {@code a} references {@code b}, composite {@code b}
     * references {@code a}. Must degrade gracefully to UNDETERMINED, no crash.
     */
    @Test
    void mutualCycle_degradesToUndetermined() {
        CompositeCriterion a = new CompositeCriterion("a", Junction.AND,
                List.of(new CriterionReference("b")));
        CompositeCriterion b = new CompositeCriterion("b", Junction.AND,
                List.of(new CriterionReference("a")));
        Specification spec = new Specification("mutual-cycle", List.of(a, b));
        SpecificationEvaluator evaluator = new SpecificationEvaluator(spec);

        assertThatCode(() -> {
            EvaluationOutcome outcome = evaluator.evaluate(Map.of("age", 25));
            assertThat(resultFor(outcome, "a").state()).isEqualTo(EvaluationState.UNDETERMINED);
            assertThat(resultFor(outcome, "b").state()).isEqualTo(EvaluationState.UNDETERMINED);
        }).doesNotThrowAnyException();
    }

    /**
     * Unknown ref: a reference to a non-existent id must remain UNDETERMINED (missing),
     * preserving the existing behaviour.
     */
    @Test
    void unknownReference_isUndetermined() {
        CompositeCriterion outer = new CompositeCriterion("outer", Junction.AND,
                List.of(new CriterionReference("does-not-exist")));
        Specification spec = new Specification("unknown-ref", List.of(outer));
        SpecificationEvaluator evaluator = new SpecificationEvaluator(spec);

        EvaluationOutcome outcome = evaluator.evaluate(Map.of("age", 25));
        assertThat(resultFor(outcome, "outer").state()).isEqualTo(EvaluationState.UNDETERMINED);
    }
}
