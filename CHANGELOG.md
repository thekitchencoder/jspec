# Changelog

All notable changes to the JSON Specification Evaluator will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [0.6.0] - 2026-06-03

### Added
- **`$contextPath` operand sentinel for context-document evaluation.** A query operand
  shaped `{ "$contextPath": "<dot.path>" }` is a late-bound reference into a separately
  supplied context document, resolved per-evaluation — letting one specification be scored
  against many context documents (e.g. matching a claim against each candidate identity).
- **Two-arg `evaluate(target, context)`** on `SpecificationEvaluator`; the single-arg
  `evaluate(target)` is sugar passing `Map.of()` as the context.
- **`ContextPathReference`** model type, normalised once at construction by
  `SpecificationNormaliser` and resolved per-evaluation by `ContextPathResolver`. Serialises
  losslessly to/from the `{ "$contextPath": "..." }` shape.
- **Present-but-null vs missing semantics:** a context path whose final segment is present
  and `null` resolves to `null` (so `$eq: null` compares against `null`); a path with a
  missing intermediate or terminal entry leaves the criterion `UNDETERMINED`.

### Fixed
- **`$or` / `$and` now follow Strong Kleene (K3) logic.** The boolean combinators
  previously collapsed an `UNDETERMINED` branch to "not matched", so a criterion
  could report `NOT_MATCHED` where the tri-state model requires `UNDETERMINED`
  (e.g. `UNDET ∨ NOT_MATCHED`, or `MATCHED ∧ UNDET`). They now fold their branch
  results with `EvaluationState.or()` / `.and()`.
- **`$contextPath` no longer produces false `UNDETERMINED` across boolean branches.**
  A missing context path inside one `$or`/`$and` branch previously short-circuited the
  *entire* criterion to `UNDETERMINED`. Now an unresolved reference is carried as a
  sentinel and evaluated in place, so a matching `$or` branch still wins despite an
  unresolved sibling, and a definitively-false `$and` short-circuits to `NOT_MATCHED`.

### Changed
- **Context-path resolution moved from a global pre-pass gate into per-operator
  evaluation.** `ContextPathResolver` now substitutes an `UnresolvedReference` sentinel
  for a missing path instead of short-circuiting; `QueryCriterion.evaluate` no longer
  gates on `ResolutionResult.hasMissingPaths()`.
- **Missing paths are reported only when they influenced an `UNDETERMINED` result.**
  A path inside a branch overridden by a `MATCHED`/`NOT_MATCHED` sibling is no longer
  listed in `missingPaths` (it did not affect the outcome). Top-level field misses are
  unchanged.

### Performance
- **Plain (sentinel-free) queries skip query-tree reallocation.** `ContextPathResolver`
  returns the original immutable query sub-tree by reference when no `$contextPath`
  operand is present, and `QueryCriterion.evaluate` reuses the existing criterion rather
  than allocating a resolved copy.

## [0.4.0] - 2025-11-17

### Added
- **Null-Safe Lookup Methods**:
  - `find(String id)` - Returns `Optional<EvaluationResult>` instead of null
  - `findQuery(String id)` - Type-safe Optional-based query lookup
  - `findComposite(String id)` - Type-safe Optional-based composite lookup
  - `findReference(String id)` - Type-safe Optional-based reference lookup

- **Convenience State Checks**:
  - `matched(String id)` - Null-safe check if criterion matched
  - `notMatched(String id)` - Null-safe check if criterion did not match
  - `undetermined(String id)` - Null-safe check if criterion was undetermined

- **Business Logic Helpers**:
  - `hasMatches()` - Check if at least one criterion matched
  - `allMatched()` - Check if all criteria matched
  - `noneMatched()` - Check if no criteria matched
  - `anyFailed()` - Check if any criteria failed or were undetermined
  - `isFullyDetermined()` - Convenience wrapper for `summary.fullyDetermined()`

- **Stream API**:
  - `stream()` - Direct stream access for functional-style processing

- **Performance Optimization**:
  - `asMap()` - Convert results to immutable Map for O(1) lookups

- **Safe Accessors**:
  - `firstQuery()` - Optional-based first query result
  - `firstComposite()` - Optional-based first composite result
  - `firstReference()` - Optional-based first reference result

- **Kleene Logic Methods**:
  - `overallState()` - AND-based overall state computation
  - `anyMatchState()` - OR-based overall state computation

- **Diagnostic Helpers**:
  - `getFailureReasons()` - Map of criterion IDs to failure reasons
  - `getUndeterminedIds()` - Set of undetermined criterion IDs

### Changed
- **BREAKING**: Removed `get(String id)` method (replaced with Optional-based `find()`)
- Updated all test code to use new Optional-based API
- Improved JavaDoc with comprehensive usage examples for all new methods

### Improved
- Developer experience with null-safe, type-safe, and functional API
- Code expressiveness with concise convenience methods
- Performance for frequent lookups via `asMap()` method

## [0.3.0] - 2025-11-17

### Added
- Standard project documentation files (README.md, CLAUDE.md, LICENSE, CONTRIBUTING.md, CHANGELOG.md)

## [0.0.1-SNAPSHOT] - 2025-11-14

### Added
- Tri-state evaluation model (MATCHED, NOT_MATCHED, UNDETERMINED)
- EvaluationState enum for explicit state tracking
- EvaluationSummary with statistics tracking
- Comprehensive error tracking with failureReason field
- Graceful error handling for all edge cases
- SLF4J logging integration
- Missing data path tracking
- Support for 13 MongoDB-style operators:
  - Comparison: `$eq`, `$ne`, `$gt`, `$gte`, `$lt`, `$lte`
  - Collection: `$in`, `$nin`, `$all`, `$size`
  - Advanced: `$exists`, `$type`, `$regex`, `$elemMatch`
- Deep document navigation with dot notation
- Parallel criterion evaluation using streams
- CriteriaGroup support with AND/OR junctions
- Thread-safe evaluation
- YAML/JSON specification loading via Jackson
- Comprehensive test suite (TriStateEvaluationTest, EvaluationSummaryTest)
- Demo CLI application
- Documentation:
  - ERROR_HANDLING_DESIGN.md - Graceful degradation approach
  - IMPROVEMENT_ROADMAP.md - Development priorities
  - TODO.md - Task tracking

### Changed
- Refactored from binary (matched/not-matched) to tri-state evaluation model
- Replaced System.err.println() with SLF4J logging
- Improved type checking before casting to prevent ClassCastException
- Enhanced error messages for better debugging

### Fixed
- Unknown operators now log warnings instead of silently continuing
- Type mismatches handled gracefully (return UNDETERMINED instead of throwing)
- Invalid regex patterns caught and handled (no PatternSyntaxException)
- Null value handling throughout evaluation logic

## [0.0.0] - Initial POC

### Added
- Basic criterion evaluation engine
- MongoDB-style operator support
- Document navigation
- Simple matched/not-matched binary evaluation

---

## Version History

### [Unreleased]
Current development work not yet released.

### [0.0.1-SNAPSHOT] - 2025-11-14
First documented snapshot with tri-state evaluation model and graceful error handling.

### [0.0.0]
Initial proof of concept with basic functionality.

---

## Types of Changes

- **Added** - New features
- **Changed** - Changes in existing functionality
- **Deprecated** - Soon-to-be removed features
- **Removed** - Removed features
- **Fixed** - Bug fixes
- **Security** - Security vulnerability fixes

---

## Upcoming Changes

See [IMPROVEMENT_ROADMAP.md](IMPROVEMENT_ROADMAP.md) for planned enhancements:

### Planned for v0.1.0
- Comprehensive test coverage (80%+)
- Enhanced error handling with exception hierarchy
- Public API for custom operators
- OperatorRegistry for extensibility
- Regex pattern caching (LRU cache)
- JavaDoc for all public classes

### Planned for v0.2.0
- Builder API for fluent criterion construction
- Configuration options (strict mode, cache size)
- Performance benchmarks
- Spring Boot integration examples

### Planned for v1.0.0
- Stable public API
- Maven Central publishing
- Complete documentation
- Production-ready release

---

## Migration Guides

### Migrating to Tri-State Model (from binary evaluation)

**Before (0.0.0)**:
```java
boolean matched = result.matched(); // Ambiguous: false could mean not-matched OR error
```

**After (0.0.1-SNAPSHOT)**:
```java
switch (result.state()) {
    case MATCHED -> System.out.println("Criterion passed");
    case NOT_MATCHED -> System.out.println("Criterion failed");
    case UNDETERMINED -> System.out.println("Could not evaluate: " + result.reason());
}

// Backward compatible: matched() still works
boolean matched = result.matched(); // Returns true only if MATCHED
```

**New capabilities**:
```java
// Check if evaluation was complete
if (result.isDetermined()) {
    // Criterion evaluated successfully (MATCHED or NOT_MATCHED)
} else {
    // Criterion could not be evaluated (UNDETERMINED)
    System.out.println("Failure reason: " + result.failureReason());
    System.out.println("Missing paths: " + result.missingPaths());
}

// Check overall evaluation completeness
EvaluationSummary summary = outcome.summary();
if (!summary.fullyDetermined()) {
    System.out.println("Warning: " + summary.undetermined() + " criteria could not be evaluated");
}
```

---

## Links

- [Repository](https://github.com/thekitchencoder/jspec)
- [Issue Tracker](https://github.com/thekitchencoder/jspec/issues)
- [Contributing Guide](CONTRIBUTING.md)
- [License](LICENSE)

---

**Note**: This project is in early development (0.0.x versions). APIs may change before 1.0.0 release.
