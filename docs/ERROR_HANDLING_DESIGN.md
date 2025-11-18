# Error Handling Design - Graceful Degradation

## Core Principle

**Criteria must never fail hard.** A failure in one criterion should never prevent evaluation of other criteria or stop the overall specification evaluation.

---

## Evaluation States

Every criterion evaluation produces one of three states:

### 1. ✅ MATCHED
- Criterion evaluated successfully
- Condition is TRUE
- All required data present and valid

### 2. ❌ NOT_MATCHED
- Criterion evaluated successfully
- Condition is FALSE
- All required data present and valid

### 3. ⚠️ UNDETERMINED
- Criterion could not be evaluated definitively
- Reasons include:
  - **Missing data** in input document
  - **Invalid criterion** (unknown operator, malformed query)
  - **Severe type mismatch** where the evaluator cannot safely continue (e.g., unknown query type)
  - **Operator error** (custom handler threw, runtime regex failure, etc.)

---

## Implementation Status ✅

### Tri-State Model (Implemented in v0.2.0)

The tri-state model has been fully implemented with **Strong Kleene Logic (K3)**:

```java
// Enum with helper methods and logic operators
public enum EvaluationState {
    MATCHED,
    NOT_MATCHED,
    UNDETERMINED;

    // Helper methods
    public boolean matched() { return this == MATCHED; }
    public boolean notMatched() { return this == NOT_MATCHED; }
    public boolean undetermined() { return this == UNDETERMINED; }
    public boolean determined() { return this != UNDETERMINED; }

    // Kleene logic operators
    public EvaluationState and(EvaluationState other) { ... }
    public EvaluationState or(EvaluationState other) { ... }
}

// Sealed interface for polymorphic results
public sealed interface EvaluationResult
        permits QueryResult, CompositeResult, ReferenceResult {
    String id();
    EvaluationState state();
    String reason();
}

// Query result implementation
public record QueryResult(
    QueryCriterion criterion,
    EvaluationState state,
    List<String> missingPaths,
    String failureReason
) implements EvaluationResult { ... }
```

**Implemented features:**
- ✅ Explicit tri-state model with `EvaluationState` enum
- ✅ Helper methods on state (`matched()`, `notMatched()`, `undetermined()`, `determined()`)
- ✅ Kleene three-valued logic for combining states (`.and()`, `.or()`)
- ✅ Sealed interface hierarchy (QueryResult, CompositeResult, ReferenceResult)
- ✅ `reason()` method explains failures
- ✅ Static factories for common cases
- ✅ Graceful handling of all error cases
- ✅ SLF4J logging throughout

---

## Kleene Three-Valued Logic

### What is Kleene Logic?

The implementation uses **Strong Kleene Logic (K3)** for combining evaluation states. This is more powerful than conservative logic because it can still make definitive conclusions even when some values are UNDETERMINED.

### Truth Tables

**AND Logic (Conjunction):**
```
            | MATCHED | NOT_MATCHED | UNDETERMINED
------------|---------|-------------|-------------
MATCHED     | MATCHED | NOT_MATCHED | UNDETERMINED
NOT_MATCHED | NOT_MATCHED | NOT_MATCHED | NOT_MATCHED
UNDETERMINED| UNDETERMINED | NOT_MATCHED | UNDETERMINED
```

**Key Insight:** `NOT_MATCHED AND anything = NOT_MATCHED` because one false value makes the entire AND expression false.

**OR Logic (Disjunction):**
```
            | MATCHED | NOT_MATCHED | UNDETERMINED
------------|---------|-------------|-------------
MATCHED     | MATCHED | MATCHED     | MATCHED
NOT_MATCHED | MATCHED | NOT_MATCHED | UNDETERMINED
UNDETERMINED| MATCHED | UNDETERMINED | UNDETERMINED
```

**Key Insight:** `MATCHED OR anything = MATCHED` because one true value makes the entire OR expression true.

### Usage in Composite Criteria

```java
// CompositeCriterion.calculateCompositeState()
private EvaluationState calculateCompositeState(List<EvaluationResult> childResults) {
    if (childResults.isEmpty()) {
        return EvaluationState.UNDETERMINED;
    }

    return switch (junction) {
        case AND -> childResults.stream()
                .map(EvaluationResult::state)
                .reduce(EvaluationState.MATCHED, EvaluationState::and);

        case OR -> childResults.stream()
                .map(EvaluationResult::state)
                .reduce(EvaluationState.NOT_MATCHED, EvaluationState::or);
    };
}
```

**Example scenarios:**

```java
// AND composite with UNDETERMINED child
// [MATCHED, MATCHED, UNDETERMINED] → UNDETERMINED
// We can't say the AND succeeded because one child is unknown

// AND composite with NOT_MATCHED child
// [MATCHED, NOT_MATCHED, UNDETERMINED] → NOT_MATCHED
// We KNOW the AND failed because one child is false (short-circuit!)

// OR composite with MATCHED child
// [NOT_MATCHED, MATCHED, UNDETERMINED] → MATCHED
// We KNOW the OR succeeded because one child is true (short-circuit!)

// OR composite with all UNDETERMINED
// [UNDETERMINED, UNDETERMINED] → UNDETERMINED
// We can't determine the result
```

### Benefits of Kleene Logic

1. **Short-circuiting with unknown values:**
   - `false AND unknown = false` (we know it's false!)
   - `true OR unknown = true` (we know it's true!)

2. **More useful than conservative logic:**
   - Conservative logic: any UNDETERMINED taints everything → UNDETERMINED
   - Kleene logic: makes conclusions when possible, even with UNDETERMINED values

3. **Mathematically sound:**
   - Well-established in formal logic and database theory
   - Widely used in SQL NULL handling (SQL uses Kleene K3 logic)

---

## Original Design Proposal (Archived)

### Option 1: Add Explicit State Enum ✅ IMPLEMENTED

```java
public enum EvaluationState {
    MATCHED,       // Criterion evaluated, condition is true
    NOT_MATCHED,   // Criterion evaluated, condition is false
    UNDETERMINED   // Could not evaluate (missing data or invalid criterion)
}

public record EvaluationResult(
    Criterion criterion,
    EvaluationState state,
    List<String> missingPaths,
    String failureReason  // For UNDETERMINED: "Unknown operator: $foo", "Missing data at: x.y.z"
) implements Result {

    @Override
    public boolean matched() {
        return state == EvaluationState.MATCHED;
    }

    public boolean isDetermined() {
        return state != EvaluationState.UNDETERMINED;
    }
}
```

**Status:** ✅ Fully implemented with enhancements (Kleene logic operators)

**Benefits realized:**
- ✅ Explicit tri-state model
- ✅ Easy to detect partial evaluation
- ✅ Clear semantics
- ✅ Helper methods on EvaluationState for cleaner code
- ✅ Kleene logic operators for combining states
- ✅ API simplified - clients use `state().matched()` instead of `result.matched()`

**Note:** The API evolved to remove the `matched()` method from `EvaluationResult` interface in favor of using `state().matched()` for clarity.

### Option 2: Use Optional + Reason (Not Chosen)

```java
public record EvaluationResult(
    Criterion criterion,
    Optional<Boolean> result,  // empty = UNDETERMINED, true = MATCHED, false = NOT_MATCHED
    List<String> missingPaths,
    String failureReason
) implements Result {

    @Override
    public boolean matched() {
        return result.orElse(false);
    }

    public boolean isDetermined() {
        return result.isPresent();
    }
}
```

**Benefits:**
- Uses standard Java Optional
- Clear semantics (empty = couldn't evaluate)

**Drawbacks:**
- Slightly less intuitive than explicit enum

---

## Failure Handling Paths

### Unknown Operators
- `CriterionEvaluator` checks every `$operator` lookup. If no handler exists it logs a warning and returns an `InnerResult` with `EvaluationState.UNDETERMINED`.
- The corresponding `QueryResult` carries `reason = "Unknown operator: $foo"` so orchestrators can surface the misconfiguration.
- Other criteria (and criteria groups) continue evaluating normally.

### Type Mismatches
- Each operator validates operand types before casting. When a mismatch occurs, JSPEC logs a warning (e.g., “Operator $in expects List, got String”) and the operator returns `false`.
- Because the handler reported `false`, the enclosing criterion ends up `NOT_MATCHED`. This mirrors MongoDB’s semantics (invalid comparison evaluates to false) while still surfacing the warning in logs.
- Severe errors inside an operator (e.g., unexpected runtime exception) are caught and converted to `UNDETERMINED`.

### Invalid Regex Patterns
- `$regex` compiles the pattern inside a try/catch. Invalid patterns log a warning and the operator returns `false`, producing a `NOT_MATCHED` result. If the failure is due to runtime issues (e.g., the JDK regex engine throws), the exception path bubbles up to `evaluateOperatorQuery`, which marks the criterion `UNDETERMINED`.

### Missing Data
- Dot-notation navigation and array traversal record the exact missing path (e.g., `applicant.address.city`). The evaluator emits `UNDETERMINED` with `missingPaths` populated so orchestrators can decide whether to retry, fetch more data, or route to a human.

### Summary
| Scenario             | State Returned   | Logging Level | Notes |
|----------------------|------------------|---------------|-------|
| Unknown operator     | `UNDETERMINED`   | `WARN`        | Stops evaluating the offending operator but keeps processing the rest of the specification. |
| Type mismatch        | `NOT_MATCHED`    | `WARN`/`DEBUG`| Operator returns `false`; severe exceptions escalate to `UNDETERMINED`. |
| Invalid regex        | `NOT_MATCHED` or `UNDETERMINED` | `WARN` | Compile errors yield `NOT_MATCHED`; runtime regex errors escalate to `UNDETERMINED`. |
| Missing data         | `UNDETERMINED`   | `DEBUG`/`WARN`| `missingPaths` enumerates absent fields. |

---

## Strict vs Lenient Modes (Future Enhancement)

### Lenient Mode (Current Default)
- Unknown operators → UNDETERMINED + warn
- Type mismatches → NOT_MATCHED + warn (mirrors MongoDB behavior)
- Invalid patterns → NOT_MATCHED + warn (compile errors) / UNDETERMINED (runtime errors)
- Missing data → UNDETERMINED
- **Never throws exceptions**

### Strict Mode (Future Feature)
- Unknown operators → Throw `InvalidOperatorException`
- Type mismatches → Throw `TypeMismatchException`
- Invalid patterns → Throw `InvalidQueryException`
- Missing data → Still UNDETERMINED (not an error)
- **Fail fast for development/testing**

**Configuration (future idea):**
```java
CriterionEvaluator evaluator = CriterionEvaluatorFactory.strict(); // hypothetical
```

**Status:** Only lenient mode ships today; strict mode remains on the roadmap.

---

## Logging Strategy

### Use SLF4J (Recommended)

**Levels:**
- `ERROR` - Never used (criteria never error)
- `WARN` - Unknown operators, type mismatches, invalid patterns
- `INFO` - Specification evaluation started/completed
- `DEBUG` - Individual criterion evaluations
- `TRACE` - Detailed matching logic

**Example:**
```java
private static final Logger logger = LoggerFactory.getLogger(CriterionEvaluator.class);

// In operator evaluation
if (handler == null) {
    logger.warn("Unknown operator '{}' in criterion '{}' - marking as UNDETERMINED",
                op, getCurrentCriterionId());
}

// In type checking
if (!(operand instanceof List)) {
    logger.warn("Type mismatch: operator '{}' expects List but got {} in criterion '{}' - marking as UNDETERMINED",
                op, operand.getClass().getSimpleName(), getCurrentCriterionId());
}
```

**Why SLF4J?**
- Industry standard facade
- Zero runtime dependencies (users choose backend)
- Works with Logback, Log4j2, JUL
- Better than `System.err.println()`

---

## Specification-Level Results

### Partial Evaluation Tracking

```java
public record EvaluationOutcome(
        String specificationId,
        List<EvaluationResult> results,
        EvaluationSummary summary) {

    public EvaluationOutcome {
        results = results != null ? List.copyOf(results) : List.of();
    }
}

public record EvaluationSummary(
        int total,
        int matched,
        int notMatched,
        int undetermined,
        boolean fullyDetermined) {

    public static EvaluationSummary from(Iterable<EvaluationResult> results) { ... }
}
```

**Benefits:**
- Summaries tell you instantly whether the evaluation was fully determined.
- Consumers iterate `outcome.results()` to inspect both query and composite criteria without juggling separate collections.
- Helper methods (`EvaluationOutcome.find`, `findQuery`, `findComposite`, etc.) simplify targeted lookups while maintaining immutability.

---

## Future Enhancments

### Phase 2: v1.1+ (Add Strict Mode)
1. Add `StrictMode` configuration option
2. Create exception hierarchy for strict mode
3. Make strict mode opt-in
4. Document when to use each mode

---

## Summary

**Design Contract:**
- ✅ Criteria never throw exceptions (lenient mode)
- ✅ Three explicit states: MATCHED / NOT_MATCHED / UNDETERMINED
- ✅ One bad criterion never stops specification evaluation
- ✅ All failures logged for debugging
- ✅ Partial evaluation clearly indicated
- ✅ Future: opt-in strict mode for development
