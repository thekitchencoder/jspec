# Architecture

## Core Design

The engine has a clean, layered architecture designed for simplicity, immutability, and thread safety.

1.  **Data Layer:** This layer consists of immutable Java records that model the core concepts:
    *   `Specification`: The top-level container for all criteria (queries, composites, references).
    *   `QueryCriterion`: A single, named MongoDB-style query.
    *   `CompositeCriterion`: A set of child criteria combined with a `Junction` (`AND`/`OR`).
    *   `CriterionReference`: A pointer to another criterion by ID so results can be reused.
    *   `Junction`: An enum representing `AND` or `OR` logic.

2.  **Evaluation Layer:** This is the logic engine of the library.
    *   `SpecificationEvaluator`: The main entry point. It is bound to a specific `Specification` and orchestrates the evaluation of all criteria (queries, composites, references) against a document in parallel.
    *   `CriterionEvaluator`: The workhorse that evaluates a single `Criterion`. It contains the logic for all 14 built-in operators and implements the tri-state evaluation model.

3.  **Result Layer:** This layer consists of immutable records that capture the outcome of an evaluation.
    *   `EvaluationOutcome`: The final result of a `SpecificationEvaluator` run (with helper finders and summaries).
    *   `QueryResult`, `CompositeResult`, `ReferenceResult`: Concrete `EvaluationResult` implementations for each criterion type.
    *   `EvaluationSummary`: A statistical summary of the evaluation (counts of matched, not matched, undetermined, and a `fullyDetermined` flag).

### Key Design Principles
- **Immutability:** All data and result objects are immutable Java records, which is key to ensuring thread safety.
- **No Mutable State:** Evaluator classes have no mutable state, allowing a single instance to be safely shared across threads.
- **Thread-Safety by Default:** See the section below.
- **Fail Gracefully:** The engine is designed to never throw exceptions during evaluation, instead reporting failures through the tri-state result model (see [Error Handling Design](ERROR_HANDLING_DESIGN.md)).

## Thread Safety

The engine is thread-safe by design, and evaluation is parallelized by default for performance.

### `SpecificationEvaluator`
A `SpecificationEvaluator` instance is bound to a single, immutable `Specification`. Because the evaluator holds no state related to any single evaluation, **a single `SpecificationEvaluator` instance is thread-safe and can be shared across multiple threads**.

Criteria within a specification are evaluated in parallel using `parallelStream()`.

```java
// Create one evaluator bound to a specification
SpecificationEvaluator evaluator = new SpecificationEvaluator(spec);

// This single instance is safe to use from multiple threads
ExecutorService executor = Executors.newFixedThreadPool(10);
for (int i = 0; i < 100; i++) {
    executor.submit(() -> {
        // Each thread evaluates the document using the same evaluator instance
        EvaluationOutcome outcome = evaluator.evaluate(document);
        // Process the outcome...
    });
}
```

### Parallel Evaluation of Multiple Specifications
You can also evaluate a document against multiple different specifications in parallel.

```java
// Create a list of evaluators, one for each specification
List<SpecificationEvaluator> evaluators = specifications.stream()
    .map(SpecificationEvaluator::new)
    .toList();

// Evaluate the same document against all specifications in parallel
List<EvaluationOutcome> outcomes = evaluators.parallelStream()
    .map(evaluator -> evaluator.evaluate(document))
    .toList();
```
