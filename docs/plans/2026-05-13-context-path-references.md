# `$contextPath` operand references + context-document evaluation — Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Allow a `Specification` to contain operand values that are late-bound references into a separately-supplied context document, resolved at evaluation time, so the same spec template can score `(target, context)` pairs without spec regeneration or string interpolation.

**Architecture:** Three layers. (1) A `ContextPathReference` record represents the typed reference. (2) `SpecificationEvaluator` runs a one-off **normalisation walk** at construction time that converts raw `{"$contextPath": "..."}` maps inside the spec into `ContextPathReference` instances. (3) At evaluation time, each `QueryCriterion` runs a **resolution walk** over its query against the context doc, producing either a fully-resolved query (which is then evaluated by the unchanged `CriterionEvaluator`) or a list of missing paths (which short-circuits to `UNDETERMINED`). The `OperatorHandler` signature is unchanged — handlers never see references.

**Tech Stack:** Java 21, Maven, JUnit 5, AssertJ, Jackson YAML, SLF4J.

**Source requirements doc:** Obsidian vault note `Efforts/entity-reconciliation-library/jspec-template-context-feature.md`.

**Key design decisions (already settled):**
- Sentinel: **`$contextPath`** (not `$path` — would crowd `CriterionReference`'s spec-internal-path namespace).
- Syntax: operand-as-map (Option A in the requirements doc): `{ "$eq": { "$contextPath": "candidate.email" } }`.
- Normalisation runs **once at `SpecificationEvaluator` construction**, not per-evaluation (satisfies the "spec parsing is a one-time cost" non-functional requirement).
- Resolution runs **at `QueryCriterion.evaluate` time**, before the operator handler is invoked, so handlers receive only resolved typed values. No `OperatorHandler` signature change.
- Public API: additive two-arg overload `evaluate(targetDoc, contextDoc)`. The existing single-arg form delegates with `contextDoc = Map.of()`.
- Missing context path → `UNDETERMINED` for the containing criterion, with the unresolved path in `missingPaths` (prefixed `context.` to distinguish from target-doc missing paths).
- Out of scope: typed coercion hints (`"as": "date"`), default values, computed expressions, multi-context JOINs, exposing the resolved spec on `EvaluationOutcome`.

---

## Task 1 — `ContextPathReference` record (data type)

**Files:**
- Create: `src/main/java/uk/codery/jspec/model/ContextPathReference.java`
- Test: `src/test/java/uk/codery/jspec/model/ContextPathReferenceTest.java`

**Step 1: Write the failing test**

```java
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
```

**Step 2: Run test to verify it fails**

Run: `mvn test -Dtest=ContextPathReferenceTest`
Expected: COMPILATION FAILURE — `ContextPathReference` does not exist.

**Step 3: Implement the record**

```java
package uk.codery.jspec.model;

/**
 * A late-bound reference into a context document.
 *
 * <p>When a {@code Specification} is evaluated with the two-arg
 * {@code evaluate(targetDoc, contextDoc)} form, any operand whose runtime
 * value is a {@code ContextPathReference} is replaced — before the operator
 * handler is invoked — with the value found by walking {@code path} into the
 * context document. If the path does not resolve, the containing criterion
 * is marked {@code UNDETERMINED}.
 *
 * <p>Created by the spec normalisation walk from raw map literals shaped
 * {@code { "$contextPath": "<path>" }} (see {@link Specification}).
 *
 * @param path dot-notation path into the context document; must be non-blank
 */
public record ContextPathReference(String path) {

    public static final String SENTINEL_KEY = "$contextPath";

    public ContextPathReference {
        if (path == null || path.isBlank()) {
            throw new IllegalArgumentException("ContextPathReference path must be non-blank");
        }
    }
}
```

**Step 4: Run test to verify it passes**

Run: `mvn test -Dtest=ContextPathReferenceTest`
Expected: PASS (3 tests).

**Step 5: Commit**

```bash
git add src/main/java/uk/codery/jspec/model/ContextPathReference.java \
        src/test/java/uk/codery/jspec/model/ContextPathReferenceTest.java
git commit -m "feat: add ContextPathReference record for late-bound operands"
```

---

## Task 2 — Detect the `{ "$contextPath": "..." }` sentinel shape

**Files:**
- Create: `src/main/java/uk/codery/jspec/model/ContextPathReferences.java` (utility class — note plural)
- Test: `src/test/java/uk/codery/jspec/model/ContextPathReferencesTest.java`

**Step 1: Write the failing test**

```java
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
```

**Step 2: Run test to verify it fails**

Run: `mvn test -Dtest=ContextPathReferencesTest`
Expected: COMPILATION FAILURE.

**Step 3: Implement the detector**

```java
package uk.codery.jspec.model;

import java.util.Map;
import java.util.Optional;

/**
 * Utility for recognising and constructing {@link ContextPathReference} instances
 * from raw operand shapes (typically produced by Jackson YAML/JSON parsing).
 */
public final class ContextPathReferences {

    private ContextPathReferences() {}

    /**
     * Returns a {@link ContextPathReference} if {@code operand} is exactly the
     * shape {@code { "$contextPath": "<non-blank-string>" }}, otherwise empty.
     *
     * <p>The sentinel must be the sole key — extra keys cause the operand to be
     * treated as a plain map, leaving room to add typed coercion hints
     * (e.g. {@code "as"}) in a future PR without silently changing behaviour.
     */
    public static Optional<ContextPathReference> fromOperand(Object operand) {
        if (!(operand instanceof Map<?, ?> map)) return Optional.empty();
        if (map.size() != 1) return Optional.empty();
        Object value = map.get(ContextPathReference.SENTINEL_KEY);
        if (!(value instanceof String path) || path.isBlank()) return Optional.empty();
        return Optional.of(new ContextPathReference(path));
    }
}
```

**Step 4: Run test to verify it passes**

Run: `mvn test -Dtest=ContextPathReferencesTest`
Expected: PASS (5 tests).

**Step 5: Commit**

```bash
git add src/main/java/uk/codery/jspec/model/ContextPathReferences.java \
        src/test/java/uk/codery/jspec/model/ContextPathReferencesTest.java
git commit -m "feat: add ContextPathReferences detector for $contextPath sentinel"
```

---

## Task 3 — Recursive normalisation walk over a query tree

A spec's `query` is a `Map<String, Object>` of arbitrary nesting (maps inside maps, lists inside maps). The walk traverses it once, replacing every sentinel-shape map with a `ContextPathReference` and leaving everything else alone.

**Files:**
- Create: `src/main/java/uk/codery/jspec/model/SpecificationNormaliser.java`
- Test: `src/test/java/uk/codery/jspec/model/SpecificationNormaliserTest.java`

**Step 1: Write the failing test**

```java
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

        List<?> inList = (List<?>) ((Map<?, ?>) normalised.get("claim.tag")).get("$in");
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
}
```

**Step 2: Run test to verify it fails**

Run: `mvn test -Dtest=SpecificationNormaliserTest`
Expected: COMPILATION FAILURE.

**Step 3: Implement the walk**

```java
package uk.codery.jspec.model;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * One-pass walk that converts raw {@code { "$contextPath": "..." }} maps inside
 * a query tree into {@link ContextPathReference} instances.
 *
 * <p>Runs once at {@link uk.codery.jspec.evaluator.SpecificationEvaluator}
 * construction; per-evaluation resolution then operates on typed references
 * instead of repeatedly pattern-matching raw map shapes.
 */
public final class SpecificationNormaliser {

    private SpecificationNormaliser() {}

    /**
     * Recursively normalises any value found in a query tree.
     * Returns immutable structures so that normalised specs cannot be mutated
     * by evaluation code.
     */
    @SuppressWarnings("unchecked")
    public static Object normalise(Object value) {
        if (value instanceof Map<?, ?> map) {
            // Whole-map check first — sentinel shape collapses to a reference.
            var ref = ContextPathReferences.fromOperand(map);
            if (ref.isPresent()) return ref.get();

            return ((Map<String, Object>) map).entrySet().stream()
                    .collect(Collectors.collectingAndThen(
                            Collectors.toUnmodifiableMap(
                                    Map.Entry::getKey,
                                    e -> normalise(e.getValue())),
                            Map::copyOf));
        }
        if (value instanceof List<?> list) {
            return list.stream().map(SpecificationNormaliser::normalise).toList();
        }
        return value;
    }
}
```

**Step 4: Run test to verify it passes**

Run: `mvn test -Dtest=SpecificationNormaliserTest`
Expected: PASS (5 tests).

**Step 5: Commit**

```bash
git add src/main/java/uk/codery/jspec/model/SpecificationNormaliser.java \
        src/test/java/uk/codery/jspec/model/SpecificationNormaliserTest.java
git commit -m "feat: add SpecificationNormaliser to replace sentinel maps with refs"
```

---

## Task 4 — Wire normalisation into `SpecificationEvaluator` construction

The bound spec should hold a normalised copy of every `QueryCriterion`'s query, so the per-evaluation hot path never has to recognise raw sentinel shapes.

**Files:**
- Modify: `src/main/java/uk/codery/jspec/evaluator/SpecificationEvaluator.java:144-159`
- Test: `src/test/java/uk/codery/jspec/evaluator/SpecificationEvaluatorNormalisationTest.java`

**Step 1: Write the failing test**

```java
package uk.codery.jspec.evaluator;

import org.junit.jupiter.api.Test;
import uk.codery.jspec.model.ContextPathReference;
import uk.codery.jspec.model.QueryCriterion;
import uk.codery.jspec.model.Specification;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SpecificationEvaluatorNormalisationTest {

    @Test
    void normalisesSentinelsInQueryCriteria() {
        Specification spec = new Specification("s", List.of(
                new QueryCriterion("c",
                        Map.of("email", Map.of("$eq", Map.of("$contextPath", "x"))))));

        SpecificationEvaluator evaluator = new SpecificationEvaluator(spec);

        QueryCriterion normalised = (QueryCriterion) evaluator.specification().criteria().get(0);
        Map<?, ?> emailQ = (Map<?, ?>) normalised.query().get("email");
        assertThat(emailQ.get("$eq")).isEqualTo(new ContextPathReference("x"));
    }

    @Test
    void leavesPlainSpecsUnchanged() {
        Specification spec = new Specification("s", List.of(
                new QueryCriterion("c", Map.of("age", Map.of("$gte", 18)))));

        SpecificationEvaluator evaluator = new SpecificationEvaluator(spec);

        QueryCriterion normalised = (QueryCriterion) evaluator.specification().criteria().get(0);
        assertThat(normalised.query()).isEqualTo(Map.of("age", Map.of("$gte", 18)));
    }
}
```

**Step 2: Run test to verify it fails**

Run: `mvn test -Dtest=SpecificationEvaluatorNormalisationTest`
Expected: FAIL — the spec's QueryCriterion still contains the raw map.

**Step 3: Implement normalisation in the constructor**

Edit `SpecificationEvaluator.java`. Replace the single-arg constructor (and the canonical constructor body if needed) so that the spec it stores has had its `QueryCriterion` queries normalised.

```java
public SpecificationEvaluator(Specification specification) {
    this(normalise(specification), new CriterionEvaluator());
}

public SpecificationEvaluator(Specification specification, CriterionEvaluator criterionEvaluator) {
    // Canonical constructor of the record. Keep null-safety minimal; existing callers
    // already construct a non-null spec.
    this.specification = normalise(specification);
    this.criterionEvaluator = criterionEvaluator;
}

private static Specification normalise(Specification spec) {
    List<Criterion> criteria = spec.criteria().stream()
            .map(SpecificationEvaluator::normaliseCriterion)
            .toList();
    return new Specification(spec.id(), criteria);
}

@SuppressWarnings("unchecked")
private static Criterion normaliseCriterion(Criterion c) {
    if (c instanceof QueryCriterion q) {
        Map<String, Object> normalised =
                (Map<String, Object>) SpecificationNormaliser.normalise(q.query());
        return new QueryCriterion(q.id(), normalised);
    }
    return c; // CompositeCriterion / CriterionReference contain no operand literals
}
```

Note: this requires switching from a Java `record` to a class IF the record's canonical constructor cannot be customised cleanly. Records *do* allow custom canonical constructors, so keep it as a record. Double-check the existing record's structure when implementing — if there's any signature collision, prefer adding a static factory `SpecificationEvaluator.forSpec(spec)` rather than fighting the record.

Add imports for `Criterion`, `QueryCriterion`, `SpecificationNormaliser`, `Map`.

**Step 4: Run test to verify it passes**

Run: `mvn test -Dtest=SpecificationEvaluatorNormalisationTest`
Expected: PASS (2 tests).

**Step 5: Run full suite — must remain green**

Run: `mvn test`
Expected: PASS (all existing tests + 2 new = whatever the count is). Investigate any failure before moving on; the normalisation is structurally identical to the input for plain specs so failures here would indicate an unexpected reliance on map identity or mutability.

**Step 6: Commit**

```bash
git add src/main/java/uk/codery/jspec/evaluator/SpecificationEvaluator.java \
        src/test/java/uk/codery/jspec/evaluator/SpecificationEvaluatorNormalisationTest.java
git commit -m "feat: normalise sentinel maps when constructing SpecificationEvaluator"
```

---

## Task 5 — Resolution utility: resolved query OR missing-paths

Given a normalised query tree and a context document, produce either a fully-resolved query (no `ContextPathReference` instances anywhere) or a list of unresolved paths.

**Files:**
- Create: `src/main/java/uk/codery/jspec/evaluator/ContextPathResolver.java`
- Create: `src/main/java/uk/codery/jspec/evaluator/ResolutionResult.java`
- Test: `src/test/java/uk/codery/jspec/evaluator/ContextPathResolverTest.java`

**Step 1: Write the failing test**

```java
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

        ResolutionResult result = ContextPathResolver.resolve(query, ctx);

        assertThat(result.missingPaths()).isEmpty();
        Map<?, ?> eq = (Map<?, ?>) ((Map<?, ?>) result.resolved().get("claim.dob"));
        assertThat(eq.get("$eq")).isEqualTo(dob);  // typed, not stringified
    }

    @Test
    void reportsMissingPathsWithContextPrefix() {
        Map<String, Object> ctx = Map.of("candidate", Map.of());  // dob absent
        Map<String, Object> query = Map.of(
                "claim.dob",
                Map.of("$eq", new ContextPathReference("candidate.dob")));

        ResolutionResult result = ContextPathResolver.resolve(query, ctx);

        assertThat(result.missingPaths()).containsExactly("context.candidate.dob");
    }

    @Test
    void resolvesReferencesInsideLists() {
        Map<String, Object> ctx = Map.of("candidate", Map.of("tag", "gold"));
        Map<String, Object> query = Map.of(
                "claim.tag",
                Map.of("$in", List.of(
                        new ContextPathReference("candidate.tag"),
                        "silver")));

        ResolutionResult result = ContextPathResolver.resolve(query, ctx);

        List<?> resolvedList = (List<?>) ((Map<?, ?>) result.resolved().get("claim.tag")).get("$in");
        assertThat(resolvedList).containsExactly("gold", "silver");
    }

    @Test
    void resolvesReferenceToList() {
        // $contextPath: "candidate.tags" → resolves to a List
        Map<String, Object> ctx = Map.of("candidate", Map.of("tags", List.of("gold", "vip")));
        Map<String, Object> query = Map.of(
                "claim.tags",
                Map.of("$all", new ContextPathReference("candidate.tags")));

        ResolutionResult result = ContextPathResolver.resolve(query, ctx);

        Object resolvedAll = ((Map<?, ?>) result.resolved().get("claim.tags")).get("$all");
        assertThat(resolvedAll).isEqualTo(List.of("gold", "vip"));
    }

    @Test
    void plainQueriesAreReturnedUnchanged() {
        Map<String, Object> query = Map.of("age", Map.of("$gte", 18));
        ResolutionResult result = ContextPathResolver.resolve(query, Map.of());
        assertThat(result.missingPaths()).isEmpty();
        assertThat(result.resolved()).isEqualTo(query);
    }

    @Test
    void nullContextDocTreatsAllRefsAsMissing() {
        Map<String, Object> query = Map.of(
                "claim.email",
                Map.of("$eq", new ContextPathReference("candidate.email")));

        ResolutionResult result = ContextPathResolver.resolve(query, null);

        assertThat(result.missingPaths()).containsExactly("context.candidate.email");
    }
}
```

**Step 2: Run test to verify it fails**

Run: `mvn test -Dtest=ContextPathResolverTest`
Expected: COMPILATION FAILURE.

**Step 3: Implement the result type**

```java
package uk.codery.jspec.evaluator;

import java.util.List;
import java.util.Map;

/**
 * Outcome of resolving a normalised query against a context document.
 * Either {@link #missingPaths()} is empty (in which case {@link #resolved()}
 * is a fully-resolved query ready for the evaluator) or it lists the
 * unresolved {@code context.<path>} references that prevented resolution.
 */
public record ResolutionResult(Map<String, Object> resolved, List<String> missingPaths) {

    public boolean hasMissingPaths() {
        return !missingPaths.isEmpty();
    }
}
```

**Step 4: Implement the resolver**

```java
package uk.codery.jspec.evaluator;

import uk.codery.jspec.model.ContextPathReference;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Walks a normalised query tree, replacing every {@link ContextPathReference}
 * with the value found at its path in the supplied context document.
 * Collects any unresolved references rather than failing fast — that way the
 * caller can report all missing paths in one go.
 */
public final class ContextPathResolver {

    private static final String CONTEXT_PREFIX = "context.";

    private ContextPathResolver() {}

    @SuppressWarnings("unchecked")
    public static ResolutionResult resolve(Map<String, Object> query, Object contextDoc) {
        List<String> missing = new ArrayList<>();
        Object resolved = walk(query, contextDoc, missing);
        return new ResolutionResult((Map<String, Object>) resolved, List.copyOf(missing));
    }

    @SuppressWarnings("unchecked")
    private static Object walk(Object value, Object contextDoc, List<String> missing) {
        if (value instanceof ContextPathReference ref) {
            return lookup(ref, contextDoc, missing);
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> out = new LinkedHashMap<>(map.size());
            for (Map.Entry<?, ?> e : map.entrySet()) {
                out.put((String) e.getKey(), walk(e.getValue(), contextDoc, missing));
            }
            return Map.copyOf(out);
        }
        if (value instanceof List<?> list) {
            return list.stream().map(v -> walk(v, contextDoc, missing)).toList();
        }
        return value;
    }

    private static Object lookup(ContextPathReference ref, Object contextDoc, List<String> missing) {
        Object current = contextDoc;
        for (String segment : ref.path().split("\\.")) {
            if (!(current instanceof Map<?, ?> map) || !map.containsKey(segment)) {
                missing.add(CONTEXT_PREFIX + ref.path());
                return null;  // sentinel; caller short-circuits via hasMissingPaths()
            }
            current = map.get(segment);
        }
        // A null value at the path is itself a present entry — treat as resolved-to-null
        // so operators like $exists / $eq:null work. If the user wants "missing", they
        // shouldn't put the key in the context doc at all.
        return current;
    }
}
```

**Step 5: Run test to verify it passes**

Run: `mvn test -Dtest=ContextPathResolverTest`
Expected: PASS (6 tests).

**Step 6: Commit**

```bash
git add src/main/java/uk/codery/jspec/evaluator/ContextPathResolver.java \
        src/main/java/uk/codery/jspec/evaluator/ResolutionResult.java \
        src/test/java/uk/codery/jspec/evaluator/ContextPathResolverTest.java
git commit -m "feat: add ContextPathResolver to bind references to context values"
```

---

## Task 6 — `EvaluationContext` carries the context document

The context doc needs to flow from `SpecificationEvaluator.evaluate(target, context)` down to `QueryCriterion.evaluate(document, context)`. The least intrusive plumbing is an extra field on `EvaluationContext`.

**Files:**
- Modify: `src/main/java/uk/codery/jspec/evaluator/EvaluationContext.java`
- Test: `src/test/java/uk/codery/jspec/evaluator/EvaluationContextContextDocTest.java`

**Step 1: Write the failing test**

```java
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
```

**Step 2: Run test to verify it fails**

Run: `mvn test -Dtest=EvaluationContextContextDocTest`
Expected: COMPILATION FAILURE.

**Step 3: Implement**

Edit `EvaluationContext.java`. Add a field, a new constructor, and an accessor. Keep the existing constructor as a delegating overload.

```java
private final Object contextDoc;

public EvaluationContext(CriterionEvaluator evaluator) {
    this(evaluator, Map.of());
}

public EvaluationContext(CriterionEvaluator evaluator, Object contextDoc) {
    this.evaluator = evaluator;
    this.contextDoc = contextDoc == null ? Map.of() : contextDoc;
}

public Object contextDoc() {
    return contextDoc;
}
```

**Step 4: Run test to verify it passes**

Run: `mvn test -Dtest=EvaluationContextContextDocTest`
Expected: PASS (2 tests).

**Step 5: Full suite stays green**

Run: `mvn test`
Expected: PASS.

**Step 6: Commit**

```bash
git add src/main/java/uk/codery/jspec/evaluator/EvaluationContext.java \
        src/test/java/uk/codery/jspec/evaluator/EvaluationContextContextDocTest.java
git commit -m "feat: thread contextDoc through EvaluationContext"
```

---

## Task 7 — `QueryCriterion.evaluate` resolves refs before invoking the evaluator

This is where the resolver actually fires. If the resolver reports missing paths, the criterion short-circuits to `UNDETERMINED`; otherwise a resolved copy is passed to `CriterionEvaluator.evaluateQuery`.

**Files:**
- Modify: `src/main/java/uk/codery/jspec/model/QueryCriterion.java` (the `evaluate` method)
- Test: `src/test/java/uk/codery/jspec/evaluator/QueryCriterionContextResolutionTest.java`

**Step 1: Read `QueryCriterion.evaluate` first** (it's small but you need its current signature). Then write the failing test:

```java
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
```

**Step 2: Run test to verify it fails**

Run: `mvn test -Dtest=QueryCriterionContextResolutionTest`
Expected: FAIL — `ContextPathReference` operand is passed straight to the operator handler, which doesn't recognise it.

**Step 3: Implement resolution in `QueryCriterion.evaluate`**

Open `QueryCriterion.java`. Find the `evaluate(Object document, EvaluationContext context)` method (look for the pattern shown in `EvaluationContext.java:48-53`). Replace its body with:

```java
@Override
public EvaluationResult evaluate(Object document, EvaluationContext context) {
    ResolutionResult resolution =
            ContextPathResolver.resolve(query, context.contextDoc());

    if (resolution.hasMissingPaths()) {
        return QueryResult.undetermined(
                this,
                "Unresolved context paths: " + String.join(", ", resolution.missingPaths()),
                resolution.missingPaths());
    }

    QueryCriterion resolved = new QueryCriterion(id, resolution.resolved());
    return context.evaluator().evaluateQuery(document, resolved);
}
```

Add imports:
- `uk.codery.jspec.evaluator.ContextPathResolver`
- `uk.codery.jspec.evaluator.ResolutionResult`
- `uk.codery.jspec.result.QueryResult`

(If `QueryResult.undetermined(criterion, reason, paths)` does not yet accept a `List<String>`, check `QueryResult.java`. The CLAUDE.md docs say the factory exists with that signature — verify on the file. If it doesn't, add the overload; do not change existing call sites.)

**Step 4: Run test to verify it passes**

Run: `mvn test -Dtest=QueryCriterionContextResolutionTest`
Expected: PASS (3 tests).

**Step 5: Full suite stays green**

Run: `mvn test`
Expected: PASS. Plain criteria are unaffected because the resolver returns the query unchanged when there are no references.

**Step 6: Commit**

```bash
git add src/main/java/uk/codery/jspec/model/QueryCriterion.java \
        src/test/java/uk/codery/jspec/evaluator/QueryCriterionContextResolutionTest.java
git commit -m "feat: resolve $contextPath refs before query evaluation"
```

---

## Task 8 — Two-arg `SpecificationEvaluator.evaluate(target, context)` public API

**Files:**
- Modify: `src/main/java/uk/codery/jspec/evaluator/SpecificationEvaluator.java:209-239`
- Test: `src/test/java/uk/codery/jspec/evaluator/SpecificationEvaluatorTwoArgTest.java`

**Step 1: Write the failing test**

```java
package uk.codery.jspec.evaluator;

import org.junit.jupiter.api.Test;
import uk.codery.jspec.model.QueryCriterion;
import uk.codery.jspec.model.Specification;
import uk.codery.jspec.result.EvaluationOutcome;
import uk.codery.jspec.result.EvaluationState;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SpecificationEvaluatorTwoArgTest {

    @Test
    void twoArgEvaluateMatchesContextPathOperand() {
        Specification spec = new Specification("s", List.of(
                new QueryCriterion("same-email",
                        Map.of("email", Map.of("$eq", Map.of("$contextPath", "candidate.email"))))));
        SpecificationEvaluator evaluator = new SpecificationEvaluator(spec);

        EvaluationOutcome outcome = evaluator.evaluate(
                Map.of("email", "a@b.com"),
                Map.of("candidate", Map.of("email", "a@b.com")));

        assertThat(outcome.summary().matched()).isEqualTo(1);
        assertThat(outcome.results().get(0).state()).isEqualTo(EvaluationState.MATCHED);
    }

    @Test
    void singleArgEvaluateOnPlainSpecMatchesTwoArgWithEmptyContext() {
        // TC1 from requirements doc — regression / parity check.
        Specification spec = new Specification("s", List.of(
                new QueryCriterion("c", Map.of("age", Map.of("$gte", 18)))));
        SpecificationEvaluator evaluator = new SpecificationEvaluator(spec);

        EvaluationOutcome single = evaluator.evaluate(Map.of("age", 25));
        EvaluationOutcome dual = evaluator.evaluate(Map.of("age", 25), Map.of());

        assertThat(single.summary().matched()).isEqualTo(dual.summary().matched());
        assertThat(single.summary().total()).isEqualTo(dual.summary().total());
    }
}
```

**Step 2: Run test to verify it fails**

Run: `mvn test -Dtest=SpecificationEvaluatorTwoArgTest`
Expected: COMPILATION FAILURE — `evaluate(Object, Object)` does not exist.

**Step 3: Implement**

Edit `SpecificationEvaluator.evaluate` (line 209). Add an overload, and refactor the existing single-arg form to delegate.

```java
public EvaluationOutcome evaluate(Object document) {
    return evaluate(document, Map.of());
}

public EvaluationOutcome evaluate(Object document, Object contextDoc) {
    log.info("Starting evaluation of specification '{}'", specification.id());

    EvaluationContext context = new EvaluationContext(criterionEvaluator, contextDoc);

    specification.criteria().parallelStream().filter(QueryCriterion.class::isInstance)
            .forEach(criterion -> context.getOrEvaluate(criterion, document));
    specification.criteria().parallelStream().filter(not(QueryCriterion.class::isInstance))
            .forEach(criterion -> context.getOrEvaluate(criterion, document));

    log.debug("Evaluated {} criteria for specification '{}'",
            context.cacheSize(), specification.id());

    List<EvaluationResult> results = List.copyOf(context.getAllResults());
    EvaluationSummary summary = EvaluationSummary.from(results);

    log.info("Completed evaluation of specification '{}' - Total: {}, Matched: {}, " +
            "Not Matched: {}, Undetermined: {}, Fully Determined: {}",
            specification.id(), summary.total(), summary.matched(),
            summary.notMatched(), summary.undetermined(), summary.fullyDetermined());

    return new EvaluationOutcome(specification.id(), results, summary);
}
```

**Step 4: Run test to verify it passes**

Run: `mvn test -Dtest=SpecificationEvaluatorTwoArgTest`
Expected: PASS (2 tests).

**Step 5: Full suite stays green**

Run: `mvn test`
Expected: PASS.

**Step 6: Commit**

```bash
git add src/main/java/uk/codery/jspec/evaluator/SpecificationEvaluator.java \
        src/test/java/uk/codery/jspec/evaluator/SpecificationEvaluatorTwoArgTest.java
git commit -m "feat: add SpecificationEvaluator.evaluate(target, context) overload"
```

---

## Task 9 — TC2: `$contextPath` carries typed values (LocalDate) through to handlers

This proves the type-fidelity non-functional requirement.

**Files:**
- Test: `src/test/java/uk/codery/jspec/evaluator/ContextPathTypeFidelityTest.java`

**Step 1: Write the test**

```java
package uk.codery.jspec.evaluator;

import org.junit.jupiter.api.Test;
import uk.codery.jspec.model.QueryCriterion;
import uk.codery.jspec.model.Specification;
import uk.codery.jspec.operator.OperatorRegistry;
import uk.codery.jspec.result.EvaluationOutcome;
import uk.codery.jspec.result.EvaluationState;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class ContextPathTypeFidelityTest {

    @Test
    void operatorReceivesResolvedValueAsOriginalJavaType() {
        // Custom operator captures the operand it actually receives so we can
        // assert on its runtime type.
        AtomicReference<Object> captured = new AtomicReference<>();
        OperatorRegistry registry = OperatorRegistry.withDefaults();
        registry.register("$captureType", (val, operand) -> {
            captured.set(operand);
            return true;
        });

        Specification spec = new Specification("s", List.of(
                new QueryCriterion("dob-check",
                        Map.of("dob", Map.of("$captureType", Map.of("$contextPath", "candidate.dob"))))));

        SpecificationEvaluator evaluator =
                new SpecificationEvaluator(spec, new CriterionEvaluator(registry));

        LocalDate dob = LocalDate.of(1980, 1, 1);
        EvaluationOutcome outcome = evaluator.evaluate(
                Map.of("dob", dob),
                Map.of("candidate", Map.of("dob", dob)));

        assertThat(outcome.results().get(0).state()).isEqualTo(EvaluationState.MATCHED);
        assertThat(captured.get()).isInstanceOf(LocalDate.class);
        assertThat(captured.get()).isEqualTo(dob);
    }
}
```

**Step 2: Run** — should pass with no production changes (the resolver preserves types). If it fails, that's a real bug in the resolver to investigate before moving on.

Run: `mvn test -Dtest=ContextPathTypeFidelityTest`
Expected: PASS.

**Step 3: Commit**

```bash
git add src/test/java/uk/codery/jspec/evaluator/ContextPathTypeFidelityTest.java
git commit -m "test: confirm typed values flow through $contextPath resolution"
```

---

## Task 10 — TC3: missing context path yields UNDETERMINED + missing-path metadata

Most of this is already covered by Task 7, but TC3 in the requirements doc specifies that `EvaluationOutcome` exposes the missing paths through the summary as well. Add an end-to-end assertion.

**Files:**
- Test: `src/test/java/uk/codery/jspec/evaluator/ContextPathMissingPathTest.java`

**Step 1: Write the test**

```java
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
```

**Step 2: Run**

Run: `mvn test -Dtest=ContextPathMissingPathTest`
Expected: PASS (already implemented via Task 7).

**Step 3: Commit**

```bash
git add src/test/java/uk/codery/jspec/evaluator/ContextPathMissingPathTest.java
git commit -m "test: cover missing context path → UNDETERMINED with summary"
```

---

## Task 11 — TC4: custom operator + `$contextPath` operand

Already exercised by the type-fidelity test (Task 9 uses `OperatorRegistry.register`), but worth an explicit dedicated test asserting the `MATCHED` outcome too.

**Files:**
- Test: `src/test/java/uk/codery/jspec/evaluator/ContextPathCustomOperatorTest.java`

**Step 1: Write the test**

```java
package uk.codery.jspec.evaluator;

import org.junit.jupiter.api.Test;
import uk.codery.jspec.model.QueryCriterion;
import uk.codery.jspec.model.Specification;
import uk.codery.jspec.operator.OperatorRegistry;
import uk.codery.jspec.result.EvaluationOutcome;
import uk.codery.jspec.result.EvaluationState;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

class ContextPathCustomOperatorTest {

    @Test
    void customOperatorReceivesResolvedOperand() {
        OperatorRegistry registry = OperatorRegistry.withDefaults();
        registry.register("$equalsIgnoreCase", (val, operand) ->
                val instanceof String s && operand instanceof String o
                        && s.equalsIgnoreCase(o));

        Specification spec = new Specification("s", List.of(
                new QueryCriterion("c",
                        Map.of("email",
                                Map.of("$equalsIgnoreCase",
                                        Map.of("$contextPath", "candidate.email"))))));

        SpecificationEvaluator evaluator =
                new SpecificationEvaluator(spec, new CriterionEvaluator(registry));

        EvaluationOutcome outcome = evaluator.evaluate(
                Map.of("email", "A@B.COM"),
                Map.of("candidate", Map.of("email", "a@b.com")));

        assertThat(outcome.results().get(0).state()).isEqualTo(EvaluationState.MATCHED);
    }
}
```

**Step 2: Run**

Run: `mvn test -Dtest=ContextPathCustomOperatorTest`
Expected: PASS.

**Step 3: Commit**

```bash
git add src/test/java/uk/codery/jspec/evaluator/ContextPathCustomOperatorTest.java
git commit -m "test: cover $contextPath operand passed to custom operator"
```

---

## Task 12 — TC5: parallel evaluation of many `(target, context)` pairs

One evaluator instance, many threads — proves the per-evaluation `EvaluationContext` doesn't leak state between threads.

**Files:**
- Test: `src/test/java/uk/codery/jspec/evaluator/ContextPathParallelEvaluationTest.java`

**Step 1: Write the test**

```java
package uk.codery.jspec.evaluator;

import org.junit.jupiter.api.Test;
import uk.codery.jspec.model.QueryCriterion;
import uk.codery.jspec.model.Specification;
import uk.codery.jspec.result.EvaluationOutcome;
import uk.codery.jspec.result.EvaluationState;

import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class ContextPathParallelEvaluationTest {

    @Test
    void singleEvaluatorHandlesManyPairsConcurrently() {
        Specification spec = new Specification("s", List.of(
                new QueryCriterion("c",
                        Map.of("email", Map.of("$eq", Map.of("$contextPath", "candidate.email"))))));
        SpecificationEvaluator evaluator = new SpecificationEvaluator(spec);

        int n = 200;
        List<EvaluationOutcome> outcomes = IntStream.range(0, n).parallel()
                .mapToObj(i -> evaluator.evaluate(
                        Map.of("email", "u" + i + "@x.com"),
                        Map.of("candidate", Map.of("email", "u" + i + "@x.com"))))
                .toList();

        // Every pair has matching emails, so every outcome should be MATCHED.
        assertThat(outcomes).allSatisfy(o ->
                assertThat(o.results().get(0).state()).isEqualTo(EvaluationState.MATCHED));
    }

    @Test
    void cacheDoesNotLeakBetweenConcurrentEvaluations() {
        // A spec that resolves differently per context — confirms no cross-talk
        // via the per-evaluation cache.
        Specification spec = new Specification("s", List.of(
                new QueryCriterion("c",
                        Map.of("v", Map.of("$eq", Map.of("$contextPath", "x"))))));
        SpecificationEvaluator evaluator = new SpecificationEvaluator(spec);

        // Half match, half don't — if state leaked we'd see false MATCHEDs on the
        // mismatched half.
        long matched = IntStream.range(0, 200).parallel()
                .mapToObj(i -> {
                    Object target = Map.of("v", i);
                    Object ctx = Map.of("x", i % 2 == 0 ? i : i + 1);  // 0,1,2,3 vs 0,2,2,4 → half mismatch
                    return evaluator.evaluate(target, ctx);
                })
                .filter(o -> o.results().get(0).state() == EvaluationState.MATCHED)
                .count();

        assertThat(matched).isEqualTo(100);
    }
}
```

**Step 2: Run**

Run: `mvn test -Dtest=ContextPathParallelEvaluationTest`
Expected: PASS. If the second test is flaky, that's a real concurrency bug — investigate before moving on (most likely culprit would be a stray static field or a shared `EvaluationContext`).

**Step 3: Commit**

```bash
git add src/test/java/uk/codery/jspec/evaluator/ContextPathParallelEvaluationTest.java
git commit -m "test: cover parallel evaluation of many (target, context) pairs"
```

---

## Task 13 — TC6: `$contextPath` inside a `CompositeCriterion` child

`CompositeCriterion` evaluates by composing child results. As long as each child `QueryCriterion` resolves its own refs, composites should just work. Confirm that explicitly.

**Files:**
- Test: `src/test/java/uk/codery/jspec/evaluator/ContextPathCompositeTest.java`

**Step 1: Write the test**

```java
package uk.codery.jspec.evaluator;

import org.junit.jupiter.api.Test;
import uk.codery.jspec.model.CompositeCriterion;
import uk.codery.jspec.model.CriterionReference;
import uk.codery.jspec.model.Junction;
import uk.codery.jspec.model.QueryCriterion;
import uk.codery.jspec.model.Specification;
import uk.codery.jspec.result.EvaluationOutcome;
import uk.codery.jspec.result.EvaluationState;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ContextPathCompositeTest {

    @Test
    void compositeAggregatesChildResultsThatUseContextPathRefs() {
        Specification spec = new Specification("s", List.of(
                new QueryCriterion("email-match",
                        Map.of("email", Map.of("$eq", Map.of("$contextPath", "candidate.email")))),
                new QueryCriterion("country-match",
                        Map.of("country", Map.of("$eq", Map.of("$contextPath", "candidate.country")))),
                new CompositeCriterion("eligibility", Junction.AND, List.of(
                        new CriterionReference("email-match"),
                        new CriterionReference("country-match")))));

        SpecificationEvaluator evaluator = new SpecificationEvaluator(spec);

        EvaluationOutcome outcome = evaluator.evaluate(
                Map.of("email", "a@b.com", "country", "UK"),
                Map.of("candidate", Map.of("email", "a@b.com", "country", "UK")));

        EvaluationState eligibility = outcome.results().stream()
                .filter(r -> r.id().equals("eligibility"))
                .findFirst().orElseThrow().state();

        assertThat(eligibility).isEqualTo(EvaluationState.MATCHED);
    }
}
```

**Step 2: Run**

Run: `mvn test -Dtest=ContextPathCompositeTest`
Expected: PASS.

**Step 3: Commit**

```bash
git add src/test/java/uk/codery/jspec/evaluator/ContextPathCompositeTest.java
git commit -m "test: cover $contextPath inside composite criteria"
```

---

## Task 14 — TC7: `$contextPath` resolves to a typed list inside `$in` / `$all`

**Files:**
- Test: `src/test/java/uk/codery/jspec/evaluator/ContextPathCollectionOperatorTest.java`

**Step 1: Write the test**

```java
package uk.codery.jspec.evaluator;

import org.junit.jupiter.api.Test;
import uk.codery.jspec.model.QueryCriterion;
import uk.codery.jspec.model.Specification;
import uk.codery.jspec.result.EvaluationOutcome;
import uk.codery.jspec.result.EvaluationState;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ContextPathCollectionOperatorTest {

    @Test
    void contextPathResolvingToListWorksWithDollarIn() {
        Specification spec = new Specification("s", List.of(
                new QueryCriterion("c",
                        Map.of("tag", Map.of("$in", Map.of("$contextPath", "candidate.tags"))))));
        SpecificationEvaluator evaluator = new SpecificationEvaluator(spec);

        EvaluationOutcome outcome = evaluator.evaluate(
                Map.of("tag", "gold"),
                Map.of("candidate", Map.of("tags", List.of("gold", "vip"))));

        assertThat(outcome.results().get(0).state()).isEqualTo(EvaluationState.MATCHED);
    }

    @Test
    void contextPathInsideDollarInList() {
        // Mixed: $in: [<ref>, "literal"]
        Specification spec = new Specification("s", List.of(
                new QueryCriterion("c",
                        Map.of("tag",
                                Map.of("$in", List.of(
                                        Map.of("$contextPath", "candidate.tag"),
                                        "silver"))))));
        SpecificationEvaluator evaluator = new SpecificationEvaluator(spec);

        EvaluationOutcome outcome = evaluator.evaluate(
                Map.of("tag", "gold"),
                Map.of("candidate", Map.of("tag", "gold")));

        assertThat(outcome.results().get(0).state()).isEqualTo(EvaluationState.MATCHED);
    }

    @Test
    void contextPathResolvingToListWorksWithDollarAll() {
        Specification spec = new Specification("s", List.of(
                new QueryCriterion("c",
                        Map.of("tags", Map.of("$all", Map.of("$contextPath", "candidate.required"))))));
        SpecificationEvaluator evaluator = new SpecificationEvaluator(spec);

        EvaluationOutcome outcome = evaluator.evaluate(
                Map.of("tags", List.of("gold", "vip", "newsletter")),
                Map.of("candidate", Map.of("required", List.of("gold", "vip"))));

        assertThat(outcome.results().get(0).state()).isEqualTo(EvaluationState.MATCHED);
    }
}
```

**Step 2: Run**

Run: `mvn test -Dtest=ContextPathCollectionOperatorTest`
Expected: PASS.

**Step 3: Commit**

```bash
git add src/test/java/uk/codery/jspec/evaluator/ContextPathCollectionOperatorTest.java
git commit -m "test: cover $contextPath resolving inside collection operators"
```

---

## Task 15 — YAML round-trip with a fixture file

End-to-end test: parse a YAML spec containing `$contextPath`, evaluate it against `(target, context)` pairs.

**Files:**
- Create: `src/test/resources/spec-with-context-path.yaml`
- Test: `src/test/java/uk/codery/jspec/e2e/ContextPathYamlTest.java`

**Step 1: Write the fixture**

`src/test/resources/spec-with-context-path.yaml`:
```yaml
id: claim-vs-candidate
criteria:
  - id: same-email
    query:
      email:
        $eq:
          $contextPath: candidate.email
  - id: same-country
    query:
      country:
        $eq:
          $contextPath: candidate.country
```

**Step 2: Write the failing test**

```java
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
```

**Step 3: Run**

Run: `mvn test -Dtest=ContextPathYamlTest`
Expected: PASS. If Jackson struggles with the `Specification` polymorphism (`QueryCriterion` vs `CompositeCriterion`), it would already have failed on the existing YAML tests — debug only if this is the first new test to fail there.

**Step 4: Commit**

```bash
git add src/test/resources/spec-with-context-path.yaml \
        src/test/java/uk/codery/jspec/e2e/ContextPathYamlTest.java
git commit -m "test: cover YAML spec with \$contextPath operands end-to-end"
```

---

## Task 16 — Documentation updates

**Files:**
- Modify: `CLAUDE.md`
- Modify: `README.md`

**Step 1: Update CLAUDE.md**

Add a new short section under "Core Concepts" (or wherever sentinels logically fit — likely near the Operator System section, but make it clear it's not itself an operator). One paragraph plus a small example:

```markdown
### 5. Context-Document References

A `Specification` operand can be a late-bound reference into a separately-supplied context document, written as the map literal `{ "$contextPath": "<dot.notation.path>" }`. At construction time `SpecificationEvaluator` normalises these into `ContextPathReference` instances; at evaluation time `evaluator.evaluate(target, context)` resolves each reference to the value at that path in the context document before any operator handler runs. Missing context paths yield `UNDETERMINED` for the containing criterion with the unresolved path recorded in `missingPaths` (prefixed `context.`).

`$contextPath` is **not** an operator — it's an operand sentinel. It cannot appear at the position where an operator would (it must be the value side of an operator like `$eq`, `$in`, `$gte`, etc.). The single-arg `evaluate(target)` continues to work for plain specs.
```

Also update the "Last Updated" / "Version" footer in CLAUDE.md to bump the date (use 2026-05-13) and note the new feature.

**Step 2: Update README.md**

Add a user-facing section (likely under "Advanced features" or similar — find where existing operators are introduced). Include:
- One paragraph explaining the feature and when to use it (template specs scored against many context docs).
- A small worked example showing the YAML/Map shape and the two-arg `evaluate` call.
- A note about the `UNDETERMINED` behaviour for missing paths.

Keep examples short — full code lives in tests; the README's job is to show shape, not exhaustively cover edge cases.

**Step 3: No new tests** — docs only.

**Step 4: Commit**

```bash
git add CLAUDE.md README.md
git commit -m "docs: document \$contextPath operand sentinel"
```

---

## Final verification

Run the full suite once more end-to-end:

```bash
mvn clean test
```

Expected: All tests pass. New tests added in tasks 1–15 should number in the high teens or low twenties.

Sanity-check coverage by skimming `git log --oneline` since the start of this plan — the commit list should read as a coherent feature delivery, each commit small and reverting any one of them should leave the project in a buildable state.

## Out-of-plan follow-ups (deliberately deferred)

These are *not* in scope for this PR but should be noted in the commit message of Task 16 or as TODO comments where relevant:

- Typed coercion hints (`{ "$contextPath": "x", "as": "date" }`) — adding `"as"` to the sentinel detector would require sibling-key handling; the current detector deliberately rejects multi-key sentinel maps to preserve room for this.
- Default values for unresolved references.
- Exposing the resolved spec on `EvaluationOutcome` for audit/debug — jclaim will want this; defer until the consuming use case is concrete.
- Multi-context-document JOIN semantics.
