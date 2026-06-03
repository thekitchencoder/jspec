package uk.codery.jspec.evaluator;

import org.junit.jupiter.api.Test;
import uk.codery.jspec.model.QueryCriterion;
import uk.codery.jspec.model.Specification;
import uk.codery.jspec.result.EvaluationOutcome;
import uk.codery.jspec.result.EvaluationState;

import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class ContextPathParallelEvaluationTest {

    @Test
    void singleEvaluatorHandlesManyPairsConcurrently() {
        Specification spec = new Specification("s", List.of(
                new QueryCriterion("c",
                        Map.of("email", Map.of("$eq", Map.of("$contextPath", "candidate.email"))))));
        SpecificationEvaluator evaluator = new SpecificationEvaluator(spec);

        int n = 200;
        List<EvaluationOutcome> outcomes = IntStream.range(0, n).parallel()
                .mapToObj(i -> evaluator.evaluate(
                        Map.of("email", "u" + i + "@x.com"),
                        Map.of("candidate", Map.of("email", "u" + i + "@x.com"))))
                .toList();

        // Every pair has matching emails, so every outcome should be MATCHED.
        assertThat(outcomes).allSatisfy(o ->
                assertThat(o.results().get(0).state()).isEqualTo(EvaluationState.MATCHED));
    }

    @Test
    void cacheDoesNotLeakBetweenConcurrentEvaluations() {
        // A spec that resolves differently per context — confirms no cross-talk
        // via the per-evaluation cache.
        Specification spec = new Specification("s", List.of(
                new QueryCriterion("c",
                        Map.of("v", Map.of("$eq", Map.of("$contextPath", "x"))))));
        SpecificationEvaluator evaluator = new SpecificationEvaluator(spec);

        // Half match, half don't — if state leaked we'd see false MATCHEDs on the
        // mismatched half.
        long matched = IntStream.range(0, 200).parallel()
                .mapToObj(i -> {
                    Object target = Map.of("v", i);
                    Object ctx = Map.of("x", i % 2 == 0 ? i : i + 1);  // 0,1,2,3 vs 0,2,2,4 → half mismatch
                    return evaluator.evaluate(target, ctx);
                })
                .filter(o -> o.results().get(0).state() == EvaluationState.MATCHED)
                .count();

        assertThat(matched).isEqualTo(100);
    }
}
