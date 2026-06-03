package uk.codery.jspec.model;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SpecificationNormaliserTest {

    @Test
    void leavesPlainQueryUnchanged() {
        Map<String, Object> query = Map.of("age", Map.of("$gte", 18));
        Object normalised = SpecificationNormaliser.normalise(query);
        assertThat(normalised).isEqualTo(query);
    }

    @Test
    void replacesSentinelMapWithReference() {
        Map<String, Object> query = Map.of(
                "claim.email",
                Map.of("$eq", Map.of("$contextPath", "candidate.email")));

        Map<String, Object> normalised =
                (Map<String, Object>) SpecificationNormaliser.normalise(query);

        Map<String, Object> emailQ = (Map<String, Object>) normalised.get("claim.email");
        assertThat(emailQ.get("$eq")).isEqualTo(new ContextPathReference("candidate.email"));
    }

    @Test
    void replacesSentinelsInsideLists() {
        // $in: [<ref>, "literal"]
        Map<String, Object> query = Map.of(
                "claim.tag",
                Map.of("$in", List.of(
                        Map.of("$contextPath", "candidate.tag"),
                        "literal")));

        Map<String, Object> normalised =
                (Map<String, Object>) SpecificationNormaliser.normalise(query);

        List<Object> inList = (List<Object>) ((Map<?, ?>) normalised.get("claim.tag")).get("$in");
        assertThat(inList).containsExactly(
                new ContextPathReference("candidate.tag"),
                "literal");
    }

    @Test
    void replacesSentinelsInNestedOperators() {
        // $not: { $eq: <ref> }
        Map<String, Object> query = Map.of(
                "claim.status",
                Map.of("$not", Map.of("$eq", Map.of("$contextPath", "candidate.status"))));

        Map<String, Object> normalised =
                (Map<String, Object>) SpecificationNormaliser.normalise(query);

        Map<?, ?> not = (Map<?, ?>) ((Map<?, ?>) normalised.get("claim.status")).get("$not");
        assertThat(not.get("$eq")).isEqualTo(new ContextPathReference("candidate.status"));
    }

    @Test
    void resultingMapsAreImmutable() {
        Map<String, Object> query = Map.of("age", Map.of("$gte", 18));
        Map<String, Object> normalised =
                (Map<String, Object>) SpecificationNormaliser.normalise(query);
        org.junit.jupiter.api.Assertions.assertThrows(
                UnsupportedOperationException.class,
                () -> normalised.put("x", "y"));
    }

    @Test
    void nullValueInsideMapDoesNotThrow() {
        // Regression: bare YAML keys produce {key: null}; must pass through.
        java.util.Map<String, Object> query = new java.util.HashMap<>();
        query.put("$eq", null);
        Object normalised = SpecificationNormaliser.normalise(query);
        assertThat(((Map<?, ?>) normalised).get("$eq")).isNull();
    }

    @Test
    void sentinelKeyWithNullValueIsTreatedAsPlainMap() {
        // YAML "$contextPath:" with no value → {$contextPath: null}.
        // Must NOT throw; must NOT produce a ContextPathReference.
        java.util.Map<String, Object> query = new java.util.HashMap<>();
        query.put("$contextPath", null);
        Object normalised = SpecificationNormaliser.normalise(query);
        assertThat(normalised).isInstanceOf(Map.class);
        assertThat(((Map<?, ?>) normalised).containsKey("$contextPath")).isTrue();
        assertThat(((Map<?, ?>) normalised).get("$contextPath")).isNull();
    }

    @Test
    void normaliseAcceptsTopLevelNull() {
        assertThat(SpecificationNormaliser.normalise(null)).isNull();
    }
}
