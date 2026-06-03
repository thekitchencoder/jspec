package uk.codery.jspec.evaluator;

import org.junit.jupiter.api.Test;
import uk.codery.jspec.model.QueryCriterion;
import uk.codery.jspec.model.Specification;
import uk.codery.jspec.operator.OperatorRegistry;
import uk.codery.jspec.result.EvaluationOutcome;
import uk.codery.jspec.result.EvaluationState;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ContextPathCustomOperatorTest {

    @Test
    void customOperatorReceivesResolvedOperand() {
        OperatorRegistry registry = OperatorRegistry.withDefaults();
        registry.register("$equalsIgnoreCase", (val, operand) ->
                val instanceof String s && operand instanceof String o
                        && s.equalsIgnoreCase(o));

        Specification spec = new Specification("s", List.of(
                new QueryCriterion("c",
                        Map.of("email",
                                Map.of("$equalsIgnoreCase",
                                        Map.of("$contextPath", "candidate.email"))))));

        SpecificationEvaluator evaluator =
                new SpecificationEvaluator(spec, new CriterionEvaluator(registry));

        EvaluationOutcome outcome = evaluator.evaluate(
                Map.of("email", "A@B.COM"),
                Map.of("candidate", Map.of("email", "a@b.com")));

        assertThat(outcome.results().get(0).state()).isEqualTo(EvaluationState.MATCHED);
    }
}
