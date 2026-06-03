package uk.codery.jspec.evaluator;

import org.junit.jupiter.api.Test;
import uk.codery.jspec.model.ContextPathReference;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ContextPathResolverTest {

    @Test
    void resolvesReferenceToTypedValue() {
        LocalDate dob = LocalDate.of(1980, 1, 1);
        Map<String, Object> ctx = Map.of("candidate", Map.of("dob", dob));
        Map<String, Object> query = Map.of(
                "claim.dob",
                Map.of("$eq", new ContextPathReference("candidate.dob")));

        Map<String, Object> resolved = ContextPathResolver.resolve(query, ctx);

        Map<?, ?> eq = (Map<?, ?>) resolved.get("claim.dob");
        assertThat(eq.get("$eq")).isEqualTo(dob);  // typed, not stringified
    }

    @Test
    void unresolvedReferenceBecomesSentinelWithContextPrefix() {
        Map<String, Object> ctx = Map.of("candidate", Map.of());  // dob absent
        Map<String, Object> query = Map.of(
                "claim.dob",
                Map.of("$eq", new ContextPathReference("candidate.dob")));

        Map<String, Object> resolved = ContextPathResolver.resolve(query, ctx);

        Object eqOperand = ((Map<?, ?>) resolved.get("claim.dob")).get("$eq");
        assertThat(eqOperand)
                .isEqualTo(new UnresolvedReference("context.candidate.dob"));
    }

    @Test
    void resolvesReferencesInsideLists() {
        Map<String, Object> ctx = Map.of("candidate", Map.of("tag", "gold"));
        Map<String, Object> query = Map.of(
                "claim.tag",
                Map.of("$in", List.of(
                        new ContextPathReference("candidate.tag"),
                        "silver")));

        Map<String, Object> resolved = ContextPathResolver.resolve(query, ctx);

        List<?> resolvedList = (List<?>) ((Map<?, ?>) resolved.get("claim.tag")).get("$in");
        assertThat(resolvedList).isEqualTo(List.of("gold", "silver"));
    }

    @Test
    void resolvesReferenceToList() {
        // $contextPath: "candidate.tags" → resolves to a List
        Map<String, Object> ctx = Map.of("candidate", Map.of("tags", List.of("gold", "vip")));
        Map<String, Object> query = Map.of(
                "claim.tags",
                Map.of("$all", new ContextPathReference("candidate.tags")));

        Map<String, Object> resolved = ContextPathResolver.resolve(query, ctx);

        Object resolvedAll = ((Map<?, ?>) resolved.get("claim.tags")).get("$all");
        assertThat(resolvedAll).isEqualTo(List.of("gold", "vip"));
    }

    @Test
    void plainQueriesAreReturnedUnchanged() {
        Map<String, Object> query = Map.of("age", Map.of("$gte", 18));
        Map<String, Object> resolved = ContextPathResolver.resolve(query, Map.of());
        assertThat(resolved).isEqualTo(query);
    }

    @Test
    void plainQueriesAreReturnedByReferenceWithoutReallocation() {
        // Fast path: when a query tree contains no ContextPathReference, resolve
        // must return the exact same map instance — no per-evaluation reallocation
        // of the query tree for plain specs.
        Map<String, Object> query = Map.of(
                "age", Map.of("$gte", 18),
                "tags", Map.of("$in", List.of("gold", "silver")));

        Map<String, Object> resolved = ContextPathResolver.resolve(query, Map.of());

        assertThat(resolved).isSameAs(query);
    }

    @Test
    void nullContextDocTreatsAllRefsAsUnresolved() {
        Map<String, Object> query = Map.of(
                "claim.email",
                Map.of("$eq", new ContextPathReference("candidate.email")));

        Map<String, Object> resolved = ContextPathResolver.resolve(query, null);

        Object eqOperand = ((Map<?, ?>) resolved.get("claim.email")).get("$eq");
        assertThat(eqOperand)
                .isEqualTo(new UnresolvedReference("context.candidate.email"));
    }
}
