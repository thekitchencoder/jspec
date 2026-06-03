package uk.codery.jspec.evaluator;

import org.junit.jupiter.api.Test;
import uk.codery.jspec.model.QueryCriterion;
import uk.codery.jspec.model.Specification;
import uk.codery.jspec.result.EvaluationOutcome;
import uk.codery.jspec.result.EvaluationState;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ContextPathCollectionOperatorTest {

    @Test
    void contextPathResolvingToListWorksWithDollarIn() {
        Specification spec = new Specification("s", List.of(
                new QueryCriterion("c",
                        Map.of("tag", Map.of("$in", Map.of("$contextPath", "candidate.tags"))))));
        SpecificationEvaluator evaluator = new SpecificationEvaluator(spec);

        EvaluationOutcome outcome = evaluator.evaluate(
                Map.of("tag", "gold"),
                Map.of("candidate", Map.of("tags", List.of("gold", "vip"))));

        assertThat(outcome.results().get(0).state()).isEqualTo(EvaluationState.MATCHED);
    }

    @Test
    void contextPathInsideDollarInList() {
        // Mixed: $in: [<ref>, "literal"]
        Specification spec = new Specification("s", List.of(
                new QueryCriterion("c",
                        Map.of("tag",
                                Map.of("$in", List.of(
                                        Map.of("$contextPath", "candidate.tag"),
                                        "silver"))))));
        SpecificationEvaluator evaluator = new SpecificationEvaluator(spec);

        EvaluationOutcome outcome = evaluator.evaluate(
                Map.of("tag", "gold"),
                Map.of("candidate", Map.of("tag", "gold")));

        assertThat(outcome.results().get(0).state()).isEqualTo(EvaluationState.MATCHED);
    }

    @Test
    void contextPathResolvingToListWorksWithDollarAll() {
        Specification spec = new Specification("s", List.of(
                new QueryCriterion("c",
                        Map.of("tags", Map.of("$all", Map.of("$contextPath", "candidate.required"))))));
        SpecificationEvaluator evaluator = new SpecificationEvaluator(spec);

        EvaluationOutcome outcome = evaluator.evaluate(
                Map.of("tags", List.of("gold", "vip", "newsletter")),
                Map.of("candidate", Map.of("required", List.of("gold", "vip"))));

        assertThat(outcome.results().get(0).state()).isEqualTo(EvaluationState.MATCHED);
    }
}
