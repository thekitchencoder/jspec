package uk.codery.jspec.evaluator;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import uk.codery.jspec.model.ContextPathReference;
import uk.codery.jspec.model.QueryCriterion;
import uk.codery.jspec.model.Specification;
import uk.codery.jspec.result.EvaluationOutcome;
import uk.codery.jspec.result.EvaluationState;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the documented behaviour of {@link SpecificationEvaluator#specification()}:
 * it returns the <em>normalised</em> spec (operand literals replaced with typed
 * {@link ContextPathReference}s), and that form serialises losslessly and re-binds
 * idempotently. Guards the "potential bug" raised in the PR #24 review.
 */
class SpecificationEvaluatorNormalisedSpecTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    private static Specification specWithContextPath() {
        return new Specification("s", List.of(
                new QueryCriterion("email-match",
                        Map.of("email", Map.of("$eq", Map.of("$contextPath", "candidate.email"))))));
    }

    @Test
    void specificationAccessorReturnsNormalisedForm() {
        SpecificationEvaluator evaluator = new SpecificationEvaluator(specWithContextPath());

        QueryCriterion criterion = (QueryCriterion) evaluator.specification().criteria().get(0);
        Object eqOperand = ((Map<?, ?>) criterion.query().get("email")).get("$eq");

        // In memory the raw {$contextPath: ...} map has become a typed reference.
        assertThat(eqOperand).isEqualTo(new ContextPathReference("candidate.email"));
    }

    @Test
    void normalisedSpecSerialisesBackToSentinelShape() throws Exception {
        SpecificationEvaluator evaluator = new SpecificationEvaluator(specWithContextPath());

        String json = JSON.writeValueAsString(evaluator.specification());

        // Lossless: the typed reference serialises back to the {$contextPath: ...} literal,
        // and no internal class name leaks into the output.
        assertThat(json).contains("\"$contextPath\":\"candidate.email\"");
        assertThat(json).doesNotContain("ContextPathReference");
    }

    @Test
    void reBindingNormalisedSpecIsIdempotentAndStillEvaluates() {
        SpecificationEvaluator first = new SpecificationEvaluator(specWithContextPath());

        // Round-trip the already-normalised spec back through the constructor.
        SpecificationEvaluator second = new SpecificationEvaluator(first.specification());

        EvaluationOutcome outcome = second.evaluate(
                Map.of("email", "a@b.com"),
                Map.of("candidate", Map.of("email", "a@b.com")));

        assertThat(outcome.results().get(0).state()).isEqualTo(EvaluationState.MATCHED);
    }
}
