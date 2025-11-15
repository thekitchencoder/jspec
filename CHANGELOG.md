# Changelog

All notable changes to the JSON Specification Evaluator will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

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
- CriteriaGroup support with AND/OR operators
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
