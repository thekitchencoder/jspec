package uk.codery.jspec.evaluator;

import org.junit.jupiter.api.Test;
import uk.codery.jspec.model.QueryCriterion;
import uk.codery.jspec.model.Specification;
import uk.codery.jspec.result.EvaluationOutcome;
import uk.codery.jspec.result.EvaluationState;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SpecificationEvaluatorTwoArgTest {

    @Test
    void twoArgEvaluateMatchesContextPathOperand() {
        Specification spec = new Specification("s", List.of(
                new QueryCriterion("same-email",
                        Map.of("email", Map.of("$eq", Map.of("$contextPath", "candidate.email"))))));
        SpecificationEvaluator evaluator = new SpecificationEvaluator(spec);

        EvaluationOutcome outcome = evaluator.evaluate(
                Map.of("email", "a@b.com"),
                Map.of("candidate", Map.of("email", "a@b.com")));

        assertThat(outcome.summary().matched()).isEqualTo(1);
        assertThat(outcome.results().get(0).state()).isEqualTo(EvaluationState.MATCHED);
    }

    @Test
    void singleArgEvaluateOnPlainSpecMatchesTwoArgWithEmptyContext() {
        // TC1 from requirements doc — regression / parity check.
        Specification spec = new Specification("s", List.of(
                new QueryCriterion("c", Map.of("age", Map.of("$gte", 18)))));
        SpecificationEvaluator evaluator = new SpecificationEvaluator(spec);

        EvaluationOutcome single = evaluator.evaluate(Map.of("age", 25));
        EvaluationOutcome dual = evaluator.evaluate(Map.of("age", 25), Map.of());

        assertThat(single.summary().matched()).isEqualTo(dual.summary().matched());
        assertThat(single.summary().total()).isEqualTo(dual.summary().total());
    }
}
