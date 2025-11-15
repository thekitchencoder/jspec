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
  - **Type mismatch** (operator expects number, got string)
  - **Operator error** (regex pattern invalid, etc.)

---

## Current Implementation Analysis

### What's Already Working ✅

The current `EvaluationResult` has the foundation:

```java
public record EvaluationResult(
    Criterion criterion,
    boolean matched,           // true/false
    List<String> missingPaths  // tracks missing data
) implements Result
```

**Good patterns:**
- `missingPaths` tracks where data is missing
- `reason()` method explains failures
- Static factory `missing(criterion)` for undefined criteria
- Graceful handling of null values

**Current behavior:**
- Missing data → `matched=false`, `missingPaths=["path.to.field"]`
- Invalid criterion → `matched=false`, `missingPaths=["criterion definition"]`
- Not matched → `matched=false`, `missingPaths=[]`

### What Needs Improvement ⚠️

**Problem:** `matched=false` is ambiguous
- Does it mean "evaluated to false" (NOT_MATCHED)?
- Or "couldn't evaluate" (UNDETERMINED)?

**Current issues:**
1. **Unknown operators** → Prints to stderr, continues silently (CriterionEvaluator.java:194)
2. **Type mismatches** → May throw ClassCastException
3. **No distinction** between false and undetermined
4. **Partial results** → No way to know if evaluation was complete

---

## Proposed Enhancement: Tri-State Model

### Option 1: Add Explicit State Enum (Recommended)

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

**Benefits:**
- Explicit tri-state model
- Easy to detect partial evaluation
- Backward compatible (`matched()` still works)
- Clear semantics

### Option 2: Use Optional + Reason (Alternative)

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

## Graceful Failure Handling Strategy

### Unknown Operators

**Current behavior:**
```java
// CriterionEvaluator.java:194
System.err.println("Unknown operator: " + op);
continue;  // Silently ignores
```

**Proposed behavior:**
```java
if (handler == null) {
    logger.warn("Unknown operator '{}' in criterion '{}'", op, context);
    return InnerResult.undetermined("Unknown operator: " + op);
}
```

**Result:**
- No exception thrown
- Evaluation continues
- Criterion marked as UNDETERMINED
- Logged for debugging

### Type Mismatches

**Current behavior:**
```java
// May throw ClassCastException
List<?> list = (List<?>) operand;
```

**Proposed behavior:**
```java
if (!(operand instanceof List)) {
    logger.warn("Operator $in expects List, got {} in criterion '{}'",
                operand.getClass().getSimpleName(), context);
    return InnerResult.undetermined("Type mismatch: $in expects List");
}
List<?> list = (List<?>) operand;
```

**Result:**
- No exception thrown
- Evaluation continues
- Criterion marked as UNDETERMINED
- Logged for debugging

### Invalid Regex Patterns

**Current behavior:**
```java
// May throw PatternSyntaxException
Pattern pattern = Pattern.compile((String) operand);
```

**Proposed behavior:**
```java
try {
    Pattern pattern = Pattern.compile((String) operand);
    return pattern.matcher(String.valueOf(val)).find();
} catch (PatternSyntaxException e) {
    logger.warn("Invalid regex pattern '{}' in criterion '{}': {}",
                operand, context, e.getMessage());
    return false;  // Treat as UNDETERMINED
}
```

**Result:**
- No exception thrown
- Evaluation continues
- Criterion marked as UNDETERMINED
- Logged for debugging

### Missing Data

**Current behavior:** ✅ Already handled gracefully
```java
if (val == null) {
    return createMissingResult(path);
}
```

**Keep this pattern!** It's already correct.

---

## Strict vs Lenient Modes (Future Enhancement)

### Lenient Mode (Default - Recommended for v1.0)
- Unknown operators → UNDETERMINED + warn
- Type mismatches → UNDETERMINED + warn
- Invalid patterns → UNDETERMINED + warn
- Missing data → UNDETERMINED
- **Never throws exceptions**

### Strict Mode (Future Feature)
- Unknown operators → Throw `InvalidOperatorException`
- Type mismatches → Throw `TypeMismatchException`
- Invalid patterns → Throw `InvalidQueryException`
- Missing data → Still UNDETERMINED (not an error)
- **Fail fast for development/testing**

**Configuration (future):**
```java
CriterionEvaluator evaluator = CriterionEvaluator.builder()
    .strictMode(true)  // Throw exceptions instead of UNDETERMINED
    .build();
```

**For v1.0:** Implement lenient mode only. Add strict mode later if needed.

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
    List<EvaluationResult> criterionResults,
    List<CriteriaGroupResult> criteriaGroupResults,
    EvaluationSummary summary  // NEW
)

public record EvaluationSummary(
    int total,
    int matched,
    int notMatched,
    int undetermined,     // NEW - count of criteria that couldn't evaluate
    boolean fullyDetermined    // true if all criteria evaluated successfully
)
```

**Benefits:**
- See at a glance if any criteria failed to evaluate
- Detect partial evaluations
- Make informed decisions about result confidence

---

## Updated Priority 1: Error Handling

### Week 1 Tasks (Revised)

**Instead of throwing exceptions, implement graceful degradation:**

1. ✅ **Add tri-state evaluation model**
   - Add `EvaluationState` enum (MATCHED / NOT_MATCHED / UNDETERMINED)
   - Update `EvaluationResult` with state and failureReason
   - Keep backward compatibility with `matched()` method

2. ✅ **Handle unknown operators gracefully**
   - Return UNDETERMINED instead of printing to stderr
   - Add warning log with operator name and criterion ID
   - Track failure reason for debugging

3. ✅ **Handle type mismatches gracefully**
   - Check types before casting
   - Return UNDETERMINED on type errors
   - Add warning log with expected vs actual types

4. ✅ **Handle invalid patterns gracefully**
   - Wrap regex compilation in try-catch
   - Return UNDETERMINED on PatternSyntaxException
   - Add warning log with pattern and error

5. ✅ **Add SLF4J logging**
   - Replace all `System.err.println()` with logger.warn()
   - Add INFO logging for specification evaluation
   - Add DEBUG logging for criterion evaluation
   - Keep library neutral (SLF4J API only)

6. ✅ **Add evaluation summary**
   - Track determined vs undetermined criteria
   - Add `fullyDetermined` flag to outcome
   - Help users detect partial evaluations

---

## Testing Strategy

### Test Cases for Graceful Degradation

```java
@Test
void unknownOperator_shouldReturnUndetermined() {
    Criterion criterion = new Criterion("test", Map.of("age", Map.of("$unknown", 18)));
    EvaluationResult result = evaluator.evaluateCriterion(document, criterion);

    assertEquals(EvaluationState.UNDETERMINED, result.state());
    assertThat(result.failureReason()).contains("Unknown operator: $unknown");
}

@Test
void typeMismatch_shouldReturnUndetermined() {
    Criterion criterion = new Criterion("test", Map.of("age", Map.of("$in", "not-a-list")));
    EvaluationResult result = evaluator.evaluateCriterion(document, criterion);

    assertEquals(EvaluationState.UNDETERMINED, result.state());
    assertThat(result.failureReason()).contains("Type mismatch");
}

@Test
void invalidRegex_shouldReturnUndetermined() {
    Criterion criterion = new Criterion("test", Map.of("name", Map.of("$regex", "[invalid")));
    EvaluationResult result = evaluator.evaluateCriterion(document, criterion);

    assertEquals(EvaluationState.UNDETERMINED, result.state());
    assertThat(result.failureReason()).contains("Invalid regex");
}

@Test
void missingData_shouldReturnUndetermined() {
    Map<String, Object> doc = Map.of(); // empty
    Criterion criterion = new Criterion("test", Map.of("age", Map.of("$gt", 18)));
    EvaluationResult result = evaluator.evaluateCriterion(doc, criterion);

    assertEquals(EvaluationState.UNDETERMINED, result.state());
    assertThat(result.missingPaths()).contains("age");
}

@Test
void multipleCriteria_oneUndetermined_shouldContinue() {
    List<Criterion> criteria = List.of(
        new Criterion("good", Map.of("age", Map.of("$eq", 25))),
        new Criterion("bad", Map.of("age", Map.of("$unknown", 18))),  // Unknown operator
        new Criterion("good2", Map.of("name", Map.of("$eq", "John")))
    );

    EvaluationOutcome outcome = evaluator.evaluate(document,
        new Specification("spec", criteria, List.of()));

    // All 3 criteria should have results
    assertEquals(3, outcome.criterionResults().size());

    // One should be UNDETERMINED
    assertEquals(1, outcome.summary().undeterminedCriteria());
    assertFalse(outcome.summary().fullyDetermined());

    // Other two should have valid states
    assertEquals(2, outcome.summary().matchedCriteria() + outcome.summary().notMatchedCriteria());
}
```

---

## Migration Path

### Phase 1: v0.x → v1.0 (Lenient Mode Only)
1. Add `EvaluationState` enum
2. Update `EvaluationResult` (breaking change OK)
3. Implement graceful degradation everywhere
4. Replace stderr with SLF4J logging
5. Add comprehensive tests

### Phase 2: v1.1+ (Add Strict Mode - Future)
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

**Key Changes:**
1. Add `EvaluationState` enum
2. Update `EvaluationResult` with state + failureReason
3. Return UNDETERMINED instead of throwing
4. Add SLF4J logging (warn on failures)
5. Track evaluation completeness

This design maintains backward compatibility while adding the graceful degradation contract you need.
