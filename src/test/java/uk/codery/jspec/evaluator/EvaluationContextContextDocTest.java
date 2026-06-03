package uk.codery.jspec.evaluator;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class EvaluationContextContextDocTest {

    @Test
    void exposesContextDoc() {
        Map<String, Object> ctx = Map.of("k", "v");
        EvaluationContext context = new EvaluationContext(new CriterionEvaluator(), ctx);
        assertThat(context.contextDoc()).isSameAs(ctx);
    }

    @Test
    void singleArgConstructorDefaultsContextDocToEmptyMap() {
        EvaluationContext context = new EvaluationContext(new CriterionEvaluator());
        assertThat(context.contextDoc()).isEqualTo(Map.of());
    }
}
