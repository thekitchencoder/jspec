package uk.codery.jspec.evaluator;

import org.junit.jupiter.api.Test;
import uk.codery.jspec.model.QueryCriterion;
import uk.codery.jspec.model.Specification;
import uk.codery.jspec.operator.OperatorRegistry;
import uk.codery.jspec.result.EvaluationOutcome;
import uk.codery.jspec.result.EvaluationState;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class ContextPathTypeFidelityTest {

    @Test
    void operatorReceivesResolvedValueAsOriginalJavaType() {
        // Custom operator captures the operand it actually receives so we can
        // assert on its runtime type.
        AtomicReference<Object> captured = new AtomicReference<>();
        OperatorRegistry registry = OperatorRegistry.withDefaults();
        registry.register("$captureType", (val, operand) -> {
            captured.set(operand);
            return true;
        });

        Specification spec = new Specification("s", List.of(
                new QueryCriterion("dob-check",
                        Map.of("dob", Map.of("$captureType", Map.of("$contextPath", "candidate.dob"))))));

        SpecificationEvaluator evaluator =
                new SpecificationEvaluator(spec, new CriterionEvaluator(registry));

        LocalDate dob = LocalDate.of(1980, 1, 1);
        EvaluationOutcome outcome = evaluator.evaluate(
                Map.of("dob", dob),
                Map.of("candidate", Map.of("dob", dob)));

        assertThat(outcome.results().get(0).state()).isEqualTo(EvaluationState.MATCHED);
        assertThat(captured.get()).isInstanceOf(LocalDate.class);
        assertThat(captured.get()).isEqualTo(dob);
    }
}
