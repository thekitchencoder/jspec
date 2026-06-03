package uk.codery.jspec.evaluator;

import org.junit.jupiter.api.Test;
import uk.codery.jspec.model.ContextPathReference;
import uk.codery.jspec.model.QueryCriterion;
import uk.codery.jspec.result.EvaluationResult;
import uk.codery.jspec.result.EvaluationState;
import uk.codery.jspec.result.QueryResult;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class QueryCriterionContextResolutionTest {

    @Test
    void resolvesContextRefBeforeEvaluation() {
        QueryCriterion criterion = new QueryCriterion("c",
                Map.of("email", Map.of("$eq", new ContextPathReference("candidate.email"))));
        EvaluationContext context = new EvaluationContext(
                new CriterionEvaluator(),
                Map.of("candidate", Map.of("email", "a@b.com")));

        EvaluationResult result = criterion.evaluate(
                Map.of("email", "a@b.com"), context);

        assertThat(result.state()).isEqualTo(EvaluationState.MATCHED);
    }

    @Test
    void missingContextPathYieldsUndeterminedWithContextPrefix() {
        QueryCriterion criterion = new QueryCriterion("c",
                Map.of("email", Map.of("$eq", new ContextPathReference("candidate.email"))));
        EvaluationContext context = new EvaluationContext(
                new CriterionEvaluator(),
                Map.of("candidate", Map.of()));  // no email

        QueryResult result = (QueryResult) criterion.evaluate(
                Map.of("email", "a@b.com"), context);

        assertThat(result.state()).isEqualTo(EvaluationState.UNDETERMINED);
        assertThat(result.missingPaths()).containsExactly("context.candidate.email");
        assertThat(result.failureReason()).contains("context.candidate.email");
    }

    @Test
    void plainCriterionUnaffectedByContext() {
        QueryCriterion criterion = new QueryCriterion("c",
                Map.of("age", Map.of("$gte", 18)));
        EvaluationContext context = new EvaluationContext(
                new CriterionEvaluator(), Map.of());

        EvaluationResult result = criterion.evaluate(Map.of("age", 25), context);

        assertThat(result.state()).isEqualTo(EvaluationState.MATCHED);
    }
}
