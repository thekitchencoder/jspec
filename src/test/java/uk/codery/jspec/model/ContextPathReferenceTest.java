package uk.codery.jspec.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ContextPathReferenceTest {

    @Test
    void exposesPath() {
        ContextPathReference ref = new ContextPathReference("candidate.email");
        assertThat(ref.path()).isEqualTo("candidate.email");
    }

    @Test
    void rejectsNullPath() {
        assertThatThrownBy(() -> new ContextPathReference(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("path");
    }

    @Test
    void rejectsBlankPath() {
        assertThatThrownBy(() -> new ContextPathReference("  "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("path");
    }
}
