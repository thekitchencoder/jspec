# Evaluation Model

## Tri-State Evaluation

The engine uses a three-state evaluation model for robust and graceful error handling, avoiding exceptions for predictable evaluation failures.

### States

1.  **`MATCHED`** - The criterion evaluated successfully, and the condition is **TRUE**.
2.  **`NOT_MATCHED`** - The criterion evaluated successfully, and the condition is **FALSE**.
3.  **`UNDETERMINED`** - The criterion could **not** be evaluated due to an issue.

### When Does a Criterion Become `UNDETERMINED`?

A criterion's state becomes `UNDETERMINED` in any of the following situations:
-   **Missing Data:** A required field specified in the query does not exist in the document.
-   **Unknown Operator:** The query uses an operator that is not recognized by the engine (e.g., `$invalidOp`).
-   **Type Mismatch:** The value from the document has a different type than what the operator expects (e.g., trying to use `$gte` on a `String`).
-   **Invalid Regex:** The pattern provided to the `$regex` operator is not a valid regular expression.
-   **General Evaluation Error:** Any other unexpected error occurs during evaluation.

### Example: Handling Outcomes

Use `SpecificationEvaluator` to evaluate a document and then inspect the returned `EvaluationOutcome`.

```java
SpecificationEvaluator evaluator = new SpecificationEvaluator(spec);
EvaluationOutcome outcome = evaluator.evaluate(document);

outcome.results().forEach(result -> {
    switch (result.state()) {
        case MATCHED -> System.out.println(result.id() + " passed");
        case NOT_MATCHED -> System.out.println(result.id() + " failed");
        case UNDETERMINED -> {
            System.out.println(result.id() + " undetermined: " + result.reason());
            if (result instanceof QueryResult query && !query.missingPaths().isEmpty()) {
                System.out.println("Missing data at: " + query.missingPaths());
            }
        }
    }
});
```

For targeted lookups, use the helper methods on `EvaluationOutcome`:

```java
outcome.findQuery("minimum-order")
    .filter(result -> result.state().undetermined())
    .ifPresent(result -> log.warn("Missing {}", result.missingPaths()));
```

## Error Handling Philosophy

The engine follows a **graceful degradation** approach, designed for resilience and observability.

-   **No Exceptions for Evaluation Logic:** Criteria never throw exceptions that would halt the evaluation of a specification. A single bad criterion will not prevent others from being evaluated.
-   **Comprehensive Logging:** All evaluation failures (e.g., type mismatches, invalid operators) are logged as warnings via SLF4J, providing a clear debugging trail without interrupting the process.
-   **Missing Data is a State, Not an Error:** When data is missing from a document, the result is `UNDETERMINED`, not an exception. This allows you to distinguish between a failed check and an impossible one.
-   **Partial Evaluation is Success:** The system is designed to evaluate as much as it can and clearly report what it could not.

This design ensures that:
-   Specifications are always evaluated completely, even with data or query issues.
-   You can precisely identify which criteria failed versus which could not be evaluated.
-   The system degrades gracefully rather than failing hard on unexpected input.
