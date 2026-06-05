package uk.codery.jspec.evaluator;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
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
import java.util.concurrent.TimeUnit;

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

        // Phase 2 is sequential, so resolution is now deterministic; a few passes suffice.
        for (int i = 0; i < 10; i++) {
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
     * Concurrency regression: two mutually-referencing TOP-LEVEL composites
     * ({@code a}→ref(b), {@code b}→ref(a)) must never deadlock. Before phase 2 was made
     * sequential, scheduling these two composites on different ForkJoinPool worker threads
     * could deadlock on {@code ConcurrentHashMap.computeIfAbsent} bin locks across threads
     * (the per-thread cycle guard cannot see across threads). The {@link Timeout} makes a
     * deadlock fail fast instead of hanging the build.
     */
    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void mutualTopLevelCycle_completesWithoutDeadlock() {
        // two TOP-LEVEL composites referencing each other
        Specification spec = new Specification("mutual", List.of(
                new CompositeCriterion("a", Junction.AND, List.of(new CriterionReference("b"))),
                new CompositeCriterion("b", Junction.AND, List.of(new CriterionReference("a")))
        ));
        SpecificationEvaluator evaluator = new SpecificationEvaluator(spec);
        // run several times to exercise scheduling; must always complete and be graceful
        for (int i = 0; i < 50; i++) {
            EvaluationOutcome outcome = evaluator.evaluate(Map.of());
            // both a and b must be UNDETERMINED (cycle), never crash/hang
            assertThat(outcome.results()).allSatisfy(r ->
                    assertThat(r.state()).isEqualTo(EvaluationState.UNDETERMINED));
        }
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

    /**
     * Deep chained references resolve transitively. References are followed through indexed
     * {@link CompositeCriterion} targets at arbitrary depth: {@code outer → ref(mid) → mid →
     * ref(inner) → inner → ref(base) → base(query)}. Each composite is indexed, so the chain
     * resolves regardless of evaluation order. (A {@link CriterionReference} is identified by
     * its target id — {@code id() == ref} — so there is no distinct "reference to a reference"
     * node; chains are always Composite-to-Composite ending at a query.)
     */
    @Test
    void deepChainedReferences_resolveTransitively() {
        QueryCriterion base = new QueryCriterion("base", Map.of("age", Map.of("$gte", 18)));
        CompositeCriterion inner = new CompositeCriterion("inner", Junction.AND,
                List.of(new CriterionReference("base")));
        CompositeCriterion mid = new CompositeCriterion("mid", Junction.AND,
                List.of(new CriterionReference("inner")));
        CompositeCriterion outer = new CompositeCriterion("outer", Junction.AND,
                List.of(new CriterionReference("mid")));

        Specification spec = new Specification("deep-chain", List.of(base, inner, mid, outer));
        SpecificationEvaluator evaluator = new SpecificationEvaluator(spec);

        for (int i = 0; i < 10; i++) {
            EvaluationOutcome outcome = evaluator.evaluate(Map.of("age", 25));
            assertThat(resultFor(outcome, "outer").state())
                    .as("4-level reference chain, iteration %d", i)
                    .isEqualTo(EvaluationState.MATCHED);
        }
    }

    /**
     * A reference whose target resolves to UNDETERMINED (here, a query over missing data)
     * propagates UNDETERMINED through the chain gracefully — documenting that an
     * unresolvable/indeterminate target degrades rather than crashing.
     */
    @Test
    void referenceToIndeterminateTarget_propagatesUndetermined() {
        QueryCriterion base = new QueryCriterion("base", Map.of("age", Map.of("$gte", 18)));
        CompositeCriterion outer = new CompositeCriterion("outer", Junction.AND,
                List.of(new CriterionReference("base")));
        Specification spec = new Specification("indeterminate", List.of(base, outer));
        SpecificationEvaluator evaluator = new SpecificationEvaluator(spec);

        // Document is missing "age", so base is UNDETERMINED and the reference propagates it.
        EvaluationOutcome outcome = evaluator.evaluate(Map.of("name", "x"));
        assertThat(resultFor(outcome, "outer").state()).isEqualTo(EvaluationState.UNDETERMINED);
    }
}
