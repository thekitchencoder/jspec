# Operator Registry Unification & Doc Reconciliation Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Make `new CriterionEvaluator()` and `new CriterionEvaluator(OperatorRegistry.withDefaults())` expose the identical operator set with identical behaviour, expose that set programmatically, reconcile all operator-count claims in docs/Javadoc to the true number, and (optionally) fix the reference-to-composite evaluation-ordering gap.

**Architecture:** `OperatorRegistry` becomes the single canonical declaration of the *default operator name set*. Because ~half the operators are evaluator-bound (they need `patternCache`, recursive `matchValue`/`evaluateOperatorQuery`, or the shared `compare()`/`parseToInstant()` helpers), the real implementations stay in `CriterionEvaluator`. Both constructors funnel through one code path so the operator key set and behaviour are guaranteed identical; the evaluator-bound implementations always override the registry's self-contained defaults. A new `supportedOperators()` accessor makes the set inspectable and becomes the source of truth for documentation.

**Tech Stack:** Java 21, Maven, JUnit 5, AssertJ. Build: `mvn test`.

---

## Background: verified findings (2026-06-04)

Three issues were raised in review and verified against the code:

1. **Doc/count drift (real).** README:15/123, `docs/OPERATORS.md:3`, `docs/ARCHITECTURE.md:16`, and `OperatorRegistry.size()` Javadoc (line 332) say "14"; `OperatorRegistry` class/`withDefaults()` Javadoc (lines 16, 152, 250) say "13" *while listing 14*; `docs/TODO.md:103`, `docs/IMPROVEMENT_ROADMAP.md:11/56/334` say "13". The no-arg evaluator actually registers **21 operator handlers** plus `$and`/`$or` (special-cased). No doc states the real number.

2. **Constructor divergence (real bug — primary).** `CriterionEvaluator.registerOperators()` (line 297) registers 12 ops (`$eq $ne $gt $gte $lt $lte $contains $startsWith $endsWith $between $dateBefore $dateAfter`) then `registerInternalOperators()` (line 318) adds 9 (`$in $nin $exists $type $regex $size $elemMatch $all $not`). The `OperatorRegistry`-arg constructor (line 282) copies `registry.getAll()` then calls only `registerInternalOperators()`. Result for `new CriterionEvaluator(OperatorRegistry.withDefaults())`:
   - **Missing entirely:** `$contains`, `$startsWith`, `$endsWith`, `$between`, `$dateBefore`, `$dateAfter`.
   - **Different implementation:** `$gt/$gte/$lt/$lte` use `OperatorRegistry`'s `greaterThan()` etc. (line 421+) instead of the evaluator's `compare()`-based versions, because `registerInternalOperators()` does not override them.

3. **Reference-to-composite ordering (real, narrow).** `SpecificationEvaluator.evaluate()` (lines 322–326) evaluates all `QueryCriterion` first, then composites/references in one parallel batch. `CriterionReference` resolves via `EvaluationContext.getCached()` (no compute-if-absent). A reference pointing at a *composite* can therefore observe a not-yet-populated cache entry and fall to `missing → UNDETERMINED`. References to query criteria are safe (evaluated in the prior batch).

**Operator dependency map (drives the design):**
- *Self-contained* (val/operand only): `$eq`, `$ne`, `$contains`, `$startsWith`, `$endsWith`, `$exists`, `$type`, `$size`.
- *Evaluator-bound* (need instance state/helpers): `$gt/$gte/$lt/$lte`, `$between` (→ `compare()`), `$dateBefore/$dateAfter` (→ `parseToInstant()`), `$regex` (→ `patternCache`), `$elemMatch`, `$not`, `$in`, `$nin`, `$all` (→ recursive `matchValue`/`evaluateOperatorQuery` or richer impls).
- *Special-cased, not handlers:* `$and`, `$or` (intercepted in `evaluateOperator()`, lines 889–890).

**Existing tests to protect:** `CriterionEvaluatorCustomOperatorTest` (uses the registry-arg constructor heavily — lines 51, 79, 104, 129, 158, 197, 224, 244, 267, 317), `ComparisonOperatorsTest`, `CollectionOperatorsTest`, `AdvancedOperatorsTest`, `OperatorRegistryTest`, `DotNotationTest` (307, 346), `ContextPathCustomOperatorTest`, `ContextPathTypeFidelityTest`. All currently pass; the refactor must keep them green.

---

## Task Group A — Fix constructor divergence (primary)

### Task A1: Characterisation test proving the two constructors diverge

**Files:**
- Test: `src/test/java/uk/codery/jspec/evaluator/OperatorParityTest.java` (create)

**Step 1: Write the failing test**

This test asserts the invariant we want and will FAIL today (proving the bug). It uses behavioural probes because `CriterionEvaluator.operators` is private (Task A2 adds an accessor; until then, probe via evaluation).

```java
package uk.codery.jspec.evaluator;

import org.junit.jupiter.api.Test;
import uk.codery.jspec.model.QueryCriterion;
import uk.codery.jspec.operator.OperatorRegistry;
import uk.codery.jspec.result.EvaluationState;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The no-arg constructor and the OperatorRegistry.withDefaults() constructor
 * must expose the SAME operator set with the SAME behaviour. See
 * docs/plans/2026-06-04-operator-registry-unification.md.
 */
class OperatorParityTest {

    private final CriterionEvaluator noArg = new CriterionEvaluator();
    private final CriterionEvaluator viaRegistry =
            new CriterionEvaluator(OperatorRegistry.withDefaults());

    private static EvaluationState eval(CriterionEvaluator e, Map<String, Object> query) {
        return e.evaluateQuery(Map.of("v", "hello", "n", 5),
                new QueryCriterion("probe", query)).state();
    }

    @Test
    void stringOperatorsPresentInBothConstructors() {
        Map<String, Object> q = Map.of("v", Map.of("$startsWith", "he"));
        assertThat(eval(noArg, q)).isEqualTo(EvaluationState.MATCHED);
        // FAILS today: $startsWith missing from registry path -> UNDETERMINED
        assertThat(eval(viaRegistry, q)).isEqualTo(EvaluationState.MATCHED);
    }

    @Test
    void comparisonOperatorsBehaveIdenticallyInBothConstructors() {
        Map<String, Object> q = Map.of("n", Map.of("$gte", 5));
        assertThat(eval(noArg, q)).isEqualTo(eval(viaRegistry, q));
    }
}
```

**Step 2: Run to verify it fails**

Run: `mvn test -Dtest=OperatorParityTest`
Expected: FAIL — `stringOperatorsPresentInBothConstructors` shows `viaRegistry` returns `UNDETERMINED`, not `MATCHED`.

**Step 3: Commit the failing test**

```bash
git add src/test/java/uk/codery/jspec/evaluator/OperatorParityTest.java
git commit -m "test: prove CriterionEvaluator constructors expose divergent operator sets"
```

---

### Task A2: Expose the operator set programmatically

**Files:**
- Modify: `src/main/java/uk/codery/jspec/evaluator/CriterionEvaluator.java` (add accessor near the operator field, ~line 176)
- Test: `src/test/java/uk/codery/jspec/evaluator/OperatorParityTest.java` (add a key-set parity test)

**Step 1: Write the failing test** (append to `OperatorParityTest`)

```java
    @Test
    void bothConstructorsExposeIdenticalOperatorKeySet() {
        assertThat(viaRegistry.supportedOperators())
                .isEqualTo(noArg.supportedOperators());
    }

    @Test
    void supportedOperatorsIncludesLogicalCombinators() {
        // $and/$or are special-cased but are part of the supported surface
        assertThat(noArg.supportedOperators()).contains("$and", "$or");
    }
```

**Step 2: Run to verify it fails**

Run: `mvn test -Dtest=OperatorParityTest#bothConstructorsExposeIdenticalOperatorKeySet`
Expected: FAIL — `supportedOperators()` does not exist (compile error), then once added but before A3, key sets differ.

**Step 3: Implement the accessor**

In `CriterionEvaluator.java`, add (after the `operators` field / near `evaluateQuery`):

```java
    /**
     * Returns the full set of query operators this evaluator supports, including
     * the {@code $and}/{@code $or} logical combinators (which are evaluated tri-state
     * rather than via {@link uk.codery.jspec.operator.OperatorHandler}). This is the
     * canonical source of truth for documentation and tooling.
     *
     * @return an unmodifiable, sorted set of operator names (each beginning with {@code $})
     */
    public java.util.SortedSet<String> supportedOperators() {
        java.util.TreeSet<String> all = new java.util.TreeSet<>(operators.keySet());
        all.add("$and");
        all.add("$or");
        return java.util.Collections.unmodifiableSortedSet(all);
    }
```

**Step 4: Run** `mvn test -Dtest=OperatorParityTest#supportedOperatorsIncludesLogicalCombinators` — Expected: PASS. The key-set parity test still FAILS (fixed in A3).

**Step 5: Commit**

```bash
git add src/main/java/uk/codery/jspec/evaluator/CriterionEvaluator.java \
        src/test/java/uk/codery/jspec/evaluator/OperatorParityTest.java
git commit -m "feat: add CriterionEvaluator.supportedOperators() accessor"
```

---

### Task A3: Unify the two constructor paths (the fix)

**Design (registry as source of truth):**
1. `OperatorRegistry.registerDefaultOperators()` becomes the canonical *name* declaration — extend it to register the 6 missing operators (`$contains`, `$startsWith`, `$endsWith`, `$between`, `$dateBefore`, `$dateAfter`) with simple self-contained default impls (the simple ones for the string operators; for `$between`/`$dateBefore`/`$dateAfter` a best-effort default is acceptable because the evaluator always overrides them — see step 2).
2. The no-arg `CriterionEvaluator()` constructor delegates: `this(OperatorRegistry.withDefaults())`.
3. Rename `registerInternalOperators()` → `registerEvaluatorBoundOperators()` and extend it to override **every** evaluator-bound operator so the evaluator's implementation always wins regardless of constructor: add `$eq`, `$ne`, `$gt`, `$gte`, `$lt`, `$lte`, `$contains`, `$startsWith`, `$endsWith`, `$between`, `$dateBefore`, `$dateAfter` to the existing 9. Delete the now-dead `registerOperators()`.

This guarantees: both constructors copy the registry's canonical name set, then overlay the identical evaluator-bound implementations → identical key set AND identical behaviour.

**Files:**
- Modify: `src/main/java/uk/codery/jspec/operator/OperatorRegistry.java` (`registerDefaultOperators()`, line 392; class/`withDefaults()` Javadoc lines 16, 150–161, 250)
- Modify: `src/main/java/uk/codery/jspec/evaluator/CriterionEvaluator.java` (constructors lines 255–291; `registerOperators()`/`registerInternalOperators()` lines 293–330)

**Step 1: Read the protected tests first**

Run: `sed -n '1,330p' src/test/java/uk/codery/jspec/evaluator/CriterionEvaluatorCustomOperatorTest.java`
Confirm none assert the *absence* of string/date operators or pin `OperatorRegistry`'s `greaterThan()` semantics specifically. (If any does, note it and adjust — flag to the human partner before proceeding.)

**Step 2: Extend `OperatorRegistry.registerDefaultOperators()`**

Add after the advanced-operator block (after line 413, before the log line):

```java
        // String operators
        register("$contains", this::evaluateContains);
        register("$startsWith", this::evaluateStartsWith);
        register("$endsWith", this::evaluateEndsWith);

        // Range / date operators (best-effort defaults; CriterionEvaluator overrides
        // these with implementations that share its compare()/parseToInstant() helpers)
        register("$between", this::evaluateBetween);
        register("$dateBefore", this::evaluateDateBefore);
        register("$dateAfter", this::evaluateDateAfter);
```

Add the corresponding private self-contained implementations to `OperatorRegistry` (mirror the simple bodies from `CriterionEvaluator`; for `$between`/date ops a minimal numeric/`Instant.parse` version is fine since the evaluator overrides them). Keep them `private boolean (...)`.

**Step 3: Update `CriterionEvaluator` constructors**

Replace the no-arg constructor body (lines 255–258):

```java
    public CriterionEvaluator() {
        this(OperatorRegistry.withDefaults());
    }
```

In the registry-arg constructor (line 282), rename the call `registerInternalOperators()` → `registerEvaluatorBoundOperators()`.

Delete `registerOperators()` (lines 293–311). Rename `registerInternalOperators()` (line 318) to `registerEvaluatorBoundOperators()` and extend it:

```java
    private void registerEvaluatorBoundOperators() {
        // Comparison — evaluator's compare() coercion is authoritative
        operators.put("$eq", Objects::equals);
        operators.put("$ne", (val, operand) -> !Objects.equals(val, operand));
        operators.put("$gt", (val, operand) -> compare(val, operand) > 0);
        operators.put("$gte", (val, operand) -> compare(val, operand) >= 0);
        operators.put("$lt", (val, operand) -> compare(val, operand) < 0);
        operators.put("$lte", (val, operand) -> compare(val, operand) <= 0);
        // String
        operators.put("$contains", this::evaluateContainsOperator);
        operators.put("$startsWith", this::evaluateStartsWithOperator);
        operators.put("$endsWith", this::evaluateEndsWithOperator);
        // Range / date
        operators.put("$between", this::evaluateBetweenOperator);
        operators.put("$dateBefore", this::evaluateDateBeforeOperator);
        operators.put("$dateAfter", this::evaluateDateAfterOperator);
        // Collection / advanced (recursive or cache-backed)
        operators.put("$in", this::evaluateInOperator);
        operators.put("$nin", this::evaluateNotInOperator);
        operators.put("$exists", this::evaluateExistsOperator);
        operators.put("$type", (val, operand) -> getType(val).equals(operand));
        operators.put("$regex", this::evaluateRegexOperator);
        operators.put("$size", this::evaluateSizeOperator);
        operators.put("$elemMatch", this::evaluateElemMatchOperator);
        operators.put("$all", this::evaluateAllOperator);
        operators.put("$not", this::evaluateNotOperator);
        // $and / $or remain intercepted in evaluateOperator() (Strong Kleene).
    }
```

**Step 4: Run the parity test and the full suite**

Run: `mvn test -Dtest=OperatorParityTest`
Expected: PASS (all parity tests, including `bothConstructorsExposeIdenticalOperatorKeySet`).

Run: `mvn test`
Expected: PASS — entire suite green (especially `CriterionEvaluatorCustomOperatorTest`, `ComparisonOperatorsTest`, `OperatorRegistryTest`).

**Step 5: Commit**

```bash
git add src/main/java/uk/codery/jspec/operator/OperatorRegistry.java \
        src/main/java/uk/codery/jspec/evaluator/CriterionEvaluator.java
git commit -m "fix: unify operator set across CriterionEvaluator constructors

Both constructors now funnel through OperatorRegistry.withDefaults() for the
canonical name set and overlay identical evaluator-bound implementations, so
new CriterionEvaluator(OperatorRegistry.withDefaults()) no longer silently
drops the string/range/date operators or diverge on comparison semantics."
```

---

### Task A4: Reconcile `OperatorRegistry`'s own count Javadoc

**Files:**
- Modify: `src/main/java/uk/codery/jspec/operator/OperatorRegistry.java` (Javadoc lines 16, 86, 150–161, 313, 332)
- Test: `src/test/java/uk/codery/jspec/operator/OperatorRegistryTest.java` (add/adjust a `size()` assertion)

**Step 1: Write/adjust the failing test**

Add a test asserting the true default count (it is now 20 default *handlers* registered by `withDefaults()` after adding the 6 string/date ops to the prior 14):

```java
    @Test
    void withDefaultsRegistersAllStandardOperators() {
        OperatorRegistry registry = OperatorRegistry.withDefaults();
        assertThat(registry.availableOperators()).contains(
                "$eq","$ne","$gt","$gte","$lt","$lte",
                "$in","$nin","$all","$size",
                "$exists","$type","$regex","$elemMatch",
                "$contains","$startsWith","$endsWith",
                "$between","$dateBefore","$dateAfter");
        assertThat(registry.size()).isEqualTo(20);
    }
```

**Step 2: Run** `mvn test -Dtest=OperatorRegistryTest#withDefaultsRegistersAllStandardOperators` — Expected: PASS after Task A3 (verify the actual number; correct the literal `20` if the count differs and update this plan note).

**Step 3: Fix the Javadoc** — replace every "13 built-in operators" / list-of-14 in `OperatorRegistry` with the accurate count and full list (comparison 6, collection 4, advanced 4, string 3, range/date 3). Update the `size()` example output comment (line 332) to the real number.

**Step 4: Commit**

```bash
git add src/main/java/uk/codery/jspec/operator/OperatorRegistry.java \
        src/test/java/uk/codery/jspec/operator/OperatorRegistryTest.java
git commit -m "docs: correct OperatorRegistry operator-count Javadoc to true set"
```

---

## Task Group B — Reconcile documentation counts (#1)

> Depends on Task Group A (the true count is only stable once the sets are unified). The canonical number is whatever `new CriterionEvaluator().supportedOperators().size()` returns — verify by running it, do not hand-count.

### Task B1: Establish the canonical number

**Step 1:** Add a temporary throwaway probe or a small test that prints/asserts `new CriterionEvaluator().supportedOperators()` and its size. Expected surface: the 20 handler operators + `$and` + `$or` = **22** named operators. Confirm the exact value before editing prose.

### Task B2: Update user-facing docs

**Files:**
- Modify: `README.md:15`, `README.md:123`
- Modify: `docs/OPERATORS.md:3` (and ensure the operator reference body lists the string/range/date operators — add sections if `$contains`/`$between`/`$dateBefore`/etc. are undocumented)
- Modify: `docs/ARCHITECTURE.md:16`

Replace "14 MongoDB-style operators" with the verified count and phrasing, e.g. "22 query operators (20 value operators plus the `$and`/`$or` logical combinators)". Ensure `docs/OPERATORS.md` documents every operator in `supportedOperators()`; add reference entries for any missing (`$contains`, `$startsWith`, `$endsWith`, `$between`, `$dateBefore`, `$dateAfter`, `$not`, `$and`, `$or`).

**Step:** Commit `docs: reconcile operator counts and document all operators (README, OPERATORS, ARCHITECTURE)`.

### Task B3: Update internal/roadmap docs and CLAUDE.md

**Files:**
- Modify: `docs/TODO.md:103`, `docs/IMPROVEMENT_ROADMAP.md:11/56/334`
- Modify: `CLAUDE.md` (the "13 MongoDB-style operators" section and the stale line counts — `CriterionEvaluator.java` is ~1091 lines, not 488; `SpecificationEvaluator` ~343, not 49)

**Step:** Commit `docs: update roadmap/TODO/CLAUDE.md operator counts and stale line references`.

---

## Task Group C — Reference-to-composite ordering (#3, optional)

> Narrow, currently masked by graceful `UNDETERMINED`. Decide with the human partner whether to **fix** or **document the constraint**. Both options below; pick one.

### Option C-fix: On-demand reference resolution with cycle guard

**Files:**
- Modify: `src/main/java/uk/codery/jspec/evaluator/EvaluationContext.java` (add an `id → Criterion` index supplied at construction; on a `CriterionReference` cache-miss, evaluate the referenced criterion via `getOrEvaluate` instead of returning `missing`)
- Modify: `src/main/java/uk/codery/jspec/model/CriterionReference` (resolution path)
- Modify: `src/main/java/uk/codery/jspec/evaluator/SpecificationEvaluator.java` (pass the criterion index into the context)
- Test: `src/test/java/uk/codery/jspec/evaluator/ReferenceToCompositeTest.java` (create)

**Step 1: Failing test** — a spec where a `CompositeCriterion` contains a `CriterionReference` to *another composite*; assert it resolves to the determined state (e.g. `MATCHED`) rather than `UNDETERMINED`, and is order-independent across repeated runs.

**Step 2:** Run — expect FAIL (`UNDETERMINED` today).

**Step 3:** Implement: build `Map<String, Criterion>` from `specification.criteria()` in `SpecificationEvaluator`, hand it to `EvaluationContext`. Change reference resolution so a cache-miss triggers `getOrEvaluate(referencedCriterion, document)`. Add cycle detection (a per-thread "in-progress" set; a back-edge resolves to `UNDETERMINED` with a clear `failureReason`, preserving graceful degradation). Keep `getCached` for the fast path.

**Step 4:** Run `mvn test` — Expected: PASS, no regressions.

**Step 5:** Commit `fix: resolve references to composites on demand with cycle detection`.

### Option C-doc: Document the constraint

**Files:**
- Modify: `docs/OPERATORS.md` or `docs/ARCHITECTURE.md`, and `README.md` references section
- Modify: `CLAUDE.md`

State explicitly: "A `CriterionReference` may only point at a `QueryCriterion` (or a criterion evaluated in an earlier phase); references to composites are not guaranteed to resolve and may yield `UNDETERMINED`." Commit `docs: document CriterionReference target constraint`.

---

## Final verification (all groups)

**Step 1:** Run full suite: `mvn clean test` — Expected: BUILD SUCCESS, all tests pass.

**Step 2:** Confirm the invariant holds end-to-end:

```bash
mvn test -Dtest=OperatorParityTest
```

Expected: PASS — both constructors expose identical operator sets and behaviour.

**Step 3:** Grep for any remaining stale counts:

```bash
grep -rniE "1[0-9] (mongodb|operator|built-in)" README.md docs/ src/ CLAUDE.md
```

Expected: only matches that state the verified canonical number; no "13"/"14" stragglers.

---

## Notes for the executor

- **DRY:** `OperatorRegistry` keeps simple self-contained default impls; `CriterionEvaluator` keeps the evaluator-bound ones. Do not duplicate the recursive/cache-backed logic into the registry — it cannot access `patternCache`/`matchValue`.
- **YAGNI:** Do not add configurability (custom default sets, etc.) — only unify what exists.
- **Behaviour parity is the contract.** If any protected test (`CriterionEvaluatorCustomOperatorTest`, `ComparisonOperatorsTest`, `OperatorRegistryTest`) goes red, stop and reconcile — a red there means the unification changed observable behaviour and the human partner must confirm the new behaviour is intended.
- The exact operator counts (`20`, `22`) in Tasks A4/B1 are predictions — **verify by running `supportedOperators()` / `availableOperators().size()`** and correct the literals before finalising prose.
