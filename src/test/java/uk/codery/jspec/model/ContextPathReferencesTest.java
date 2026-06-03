package uk.codery.jspec.model;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ContextPathReferencesTest {

    @Test
    void recognisesSentinelMap() {
        ContextPathReference ref = ContextPathReferences.fromOperand(
                Map.of("$contextPath", "candidate.email")).orElseThrow();
        assertThat(ref.path()).isEqualTo("candidate.email");
    }

    @Test
    void ignoresMapsWithoutSentinel() {
        assertThat(ContextPathReferences.fromOperand(Map.of("$eq", "x"))).isEmpty();
    }

    @Test
    void ignoresMapsWithSentinelPlusOtherKeys() {
        // Sentinel must be the SOLE key — protects future syntactic siblings (e.g. "as")
        // from being silently misinterpreted today.
        assertThat(ContextPathReferences.fromOperand(
                Map.of("$contextPath", "x", "as", "date"))).isEmpty();
    }

    @Test
    void ignoresNonMapValues() {
        assertThat(ContextPathReferences.fromOperand("a string")).isEmpty();
        assertThat(ContextPathReferences.fromOperand(42)).isEmpty();
        assertThat(ContextPathReferences.fromOperand(null)).isEmpty();
    }

    @Test
    void rejectsSentinelWithNonStringValue() {
        // Defensive: yaml/json could parse the value as something other than String.
        assertThat(ContextPathReferences.fromOperand(Map.of("$contextPath", 42))).isEmpty();
    }
}
