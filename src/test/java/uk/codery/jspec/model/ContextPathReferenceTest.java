package uk.codery.jspec.model;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

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

    @Test
    void rejectsLeadingDotPath() {
        // ".foo".split("\\.") yields an empty first segment that silently misses.
        assertThatThrownBy(() -> new ContextPathReference(".foo"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("path");
    }

    @Test
    void rejectsTrailingDotPath() {
        assertThatThrownBy(() -> new ContextPathReference("foo."))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("path");
    }

    @Test
    void rejectsEmptySegmentPath() {
        assertThatThrownBy(() -> new ContextPathReference("a..b"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("path");
    }

    @Test
    void fromJsonRejectsMultiKeyMap() {
        // Must agree with ContextPathReferences.fromOperand, which requires the
        // sentinel to be the sole key — otherwise the normaliser treats this as a
        // plain map but a Jackson round-trip would resurrect it as a reference.
        Map<String, Object> multiKey = new LinkedHashMap<>();
        multiKey.put(ContextPathReference.SENTINEL_KEY, "a.b");
        multiKey.put("as", "date");

        assertThatThrownBy(() -> ContextPathReference.fromJson(multiKey))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
