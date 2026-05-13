package uk.codery.jspec.evaluator;

import org.junit.jupiter.api.Test;
import uk.codery.jspec.model.CompositeCriterion;
import uk.codery.jspec.model.CriterionReference;
import uk.codery.jspec.model.Junction;
import uk.codery.jspec.model.QueryCriterion;
import uk.codery.jspec.model.Specification;
import uk.codery.jspec.result.EvaluationOutcome;
import uk.codery.jspec.result.EvaluationState;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ContextPathCompositeTest {

    @Test
    void compositeAggregatesChildResultsThatUseContextPathRefs() {
        Specification spec = new Specification("s", List.of(
                new QueryCriterion("email-match",
                        Map.of("email", Map.of("$eq", Map.of("$contextPath", "candidate.email")))),
                new QueryCriterion("country-match",
                        Map.of("country", Map.of("$eq", Map.of("$contextPath", "candidate.country")))),
                new CompositeCriterion("eligibility", Junction.AND, List.of(
                        new CriterionReference("email-match"),
                        new CriterionReference("country-match")))));

        SpecificationEvaluator evaluator = new SpecificationEvaluator(spec);

        EvaluationOutcome outcome = evaluator.evaluate(
                Map.of("email", "a@b.com", "country", "UK"),
                Map.of("candidate", Map.of("email", "a@b.com", "country", "UK")));

        EvaluationState eligibility = outcome.results().stream()
                .filter(r -> r.id().equals("eligibility"))
                .findFirst().orElseThrow().state();

        assertThat(eligibility).isEqualTo(EvaluationState.MATCHED);
    }
}
