package uk.codery.jspec.formatter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.junit.jupiter.api.Test;
import uk.codery.jspec.model.ContextPathReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ContextPathReferenceRoundTripTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final ObjectMapper YAML = new ObjectMapper(new YAMLFactory());

    @Test
    void jsonRoundTripPreservesSentinelShape() throws Exception {
        ContextPathReference ref = new ContextPathReference("candidate.email");
        String json = JSON.writeValueAsString(ref);
        assertThat(json).isEqualTo("{\"$contextPath\":\"candidate.email\"}");

        ContextPathReference parsed = JSON.readValue(json, ContextPathReference.class);
        assertThat(parsed).isEqualTo(ref);
    }

    @Test
    void yamlRoundTripPreservesSentinelShape() throws Exception {
        ContextPathReference ref = new ContextPathReference("candidate.country");
        String yaml = YAML.writeValueAsString(ref);
        assertThat(yaml).contains("$contextPath:");
        assertThat(yaml).contains("candidate.country");

        ContextPathReference parsed = YAML.readValue(yaml, ContextPathReference.class);
        assertThat(parsed).isEqualTo(ref);
    }

    @Test
    void deserialisationRejectsMalformedShape() {
        // Wrong key
        assertThatThrownBy(() ->
            JSON.readValue("{\"path\":\"x\"}", ContextPathReference.class))
                .hasRootCauseInstanceOf(IllegalArgumentException.class);

        // Blank value
        assertThatThrownBy(() ->
            JSON.readValue("{\"$contextPath\":\"  \"}", ContextPathReference.class))
                .hasRootCauseInstanceOf(IllegalArgumentException.class);
    }
}
