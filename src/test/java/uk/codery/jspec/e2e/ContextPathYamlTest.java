package uk.codery.jspec.e2e;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.junit.jupiter.api.Test;
import uk.codery.jspec.evaluator.SpecificationEvaluator;
import uk.codery.jspec.model.Specification;
import uk.codery.jspec.result.EvaluationOutcome;

import java.io.InputStream;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ContextPathYamlTest {

    private static final ObjectMapper YAML = new ObjectMapper(new YAMLFactory());

    @Test
    void yamlSpecWithContextPathOperandsEvaluatesEndToEnd() throws Exception {
        Specification spec;
        try (InputStream in = getClass().getResourceAsStream("/spec-with-context-path.yaml")) {
            spec = YAML.readValue(in, Specification.class);
        }

        SpecificationEvaluator evaluator = new SpecificationEvaluator(spec);

        EvaluationOutcome match = evaluator.evaluate(
                Map.of("email", "a@b.com", "country", "UK"),
                Map.of("candidate", Map.of("email", "a@b.com", "country", "UK")));
        assertThat(match.summary().matched()).isEqualTo(2);

        EvaluationOutcome miss = evaluator.evaluate(
                Map.of("email", "a@b.com", "country", "UK"),
                Map.of("candidate", Map.of("email", "different@b.com", "country", "UK")));
        assertThat(miss.summary().matched()).isEqualTo(1);
        assertThat(miss.summary().notMatched()).isEqualTo(1);
    }
}
