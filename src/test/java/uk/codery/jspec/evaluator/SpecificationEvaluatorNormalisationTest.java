package uk.codery.jspec.evaluator;

import org.junit.jupiter.api.Test;
import uk.codery.jspec.model.ContextPathReference;
import uk.codery.jspec.model.QueryCriterion;
import uk.codery.jspec.model.Specification;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SpecificationEvaluatorNormalisationTest {

    @Test
    void normalisesSentinelsInQueryCriteria() {
        Specification spec = new Specification("s", List.of(
                new QueryCriterion("c",
                        Map.of("email", Map.of("$eq", Map.of("$contextPath", "x"))))));

        SpecificationEvaluator evaluator = new SpecificationEvaluator(spec);

        QueryCriterion normalised = (QueryCriterion) evaluator.specification().criteria().get(0);
        Map<?, ?> emailQ = (Map<?, ?>) normalised.query().get("email");
        assertThat(emailQ.get("$eq")).isEqualTo(new ContextPathReference("x"));
    }

    @Test
    void leavesPlainSpecsUnchanged() {
        Specification spec = new Specification("s", List.of(
                new QueryCriterion("c", Map.of("age", Map.of("$gte", 18)))));

        SpecificationEvaluator evaluator = new SpecificationEvaluator(spec);

        QueryCriterion normalised = (QueryCriterion) evaluator.specification().criteria().get(0);
        assertThat(normalised.query()).isEqualTo(Map.of("age", Map.of("$gte", 18)));
    }
}
