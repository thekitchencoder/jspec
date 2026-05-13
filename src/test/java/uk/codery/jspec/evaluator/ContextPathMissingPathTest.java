package uk.codery.jspec.evaluator;

import org.junit.jupiter.api.Test;
import uk.codery.jspec.model.QueryCriterion;
import uk.codery.jspec.model.Specification;
import uk.codery.jspec.result.EvaluationOutcome;
import uk.codery.jspec.result.EvaluationState;
import uk.codery.jspec.result.QueryResult;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ContextPathMissingPathTest {

    @Test
    void missingContextPathSurfacesInResultsAndSummary() {
        Specification spec = new Specification("s", List.of(
                new QueryCriterion("c",
                        Map.of("email", Map.of("$eq", Map.of("$contextPath", "candidate.email"))))));
        SpecificationEvaluator evaluator = new SpecificationEvaluator(spec);

        EvaluationOutcome outcome = evaluator.evaluate(
                Map.of("email", "a@b.com"),
                Map.of("candidate", Map.of()));

        QueryResult result = (QueryResult) outcome.results().get(0);
        assertThat(result.state()).isEqualTo(EvaluationState.UNDETERMINED);
        assertThat(result.missingPaths()).containsExactly("context.candidate.email");
        assertThat(outcome.summary().undetermined()).isEqualTo(1);
    }
}
