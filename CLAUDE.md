# CLAUDE.md - AI Assistant Context

This document provides context for AI assistants (like Claude) working with the JSON Specification Evaluator codebase.

## Project Overview

**JSON Specification Evaluator** is a lightweight Java 21 library for evaluating business criteria against JSON/YAML documents using MongoDB-style operators. The codebase is intentionally minimal (~757 lines) with a focus on clean architecture and zero framework dependencies.

### Key Characteristics

- **Language**: Java 21 (uses records, sealed classes, streams, text blocks)
- **Build Tool**: Maven
- **Architecture**: Clean 3-layer design (Data → Evaluation → Results)
- **Design Philosophy**: Immutable, thread-safe, graceful degradation
- **Dependencies**: Jackson YAML (parsing), Lombok (boilerplate), SLF4J (logging)

## Codebase Structure

```
jspec/
├── pom.xml                                          # Maven configuration
├── src/main/java/uk/codery/jspec/                  # Core library (12 classes, 757 lines)
│   ├── model/                                      # Domain models (what users create)
│   │   ├── Criterion.java                          # [10 lines] Criterion definition record
│   │   ├── CriteriaGroup.java                      # [9 lines] Grouped criteria with AND/OR
│   │   ├── Specification.java                      # [6 lines] Collection of criteria
│   │   └── Junction.java                           # [6 lines] AND/OR enum
│   ├── evaluator/                                  # Evaluation engine (what users call)
│   │   ├── CriterionEvaluator.java                 # [418 lines] Query matching engine
│   │   └── SpecificationEvaluator.java             # [49 lines] Orchestrates evaluation
│   ├── result/                                     # Result types (what users receive)
│   │   ├── EvaluationState.java                    # [40 lines] MATCHED/NOT_MATCHED/UNDETERMINED
│   │   ├── EvaluationResult.java                   # [106 lines] Individual criterion result
│   │   ├── EvaluationOutcome.java                  # [16 lines] Overall specification result
│   │   ├── EvaluationSummary.java                  # [60 lines] Evaluation statistics
│   │   ├── CriteriaGroupResult.java                # [30 lines] CriteriaGroup evaluation result
│   │   └── Result.java                             # [7 lines] Interface for results
│   └── operator/                                   # Future: custom operator support
├── src/test/java/uk/codery/jspec/                  # Tests and demo
│   ├── TriStateEvaluationTest.java                 # Comprehensive test suite
│   ├── EvaluationSummaryTest.java                  # Summary calculation tests
│   └── demo/Main.java                              # Demo CLI application
└── src/test/resources/                             # Test data
    ├── specification.{json,yaml}                   # Sample specifications
    ├── document.yaml                               # Sample document
    └── seed/citizens.json                          # Employment/benefits data

Documentation:
├── README.md                              # User-facing documentation
├── CLAUDE.md                              # This file - AI assistant context
├── ERROR_HANDLING_DESIGN.md               # Tri-state model design doc
├── IMPROVEMENT_ROADMAP.md                 # Development priorities
├── TODO.md                                # Task tracking
├── CONTRIBUTING.md                        # Contribution guidelines
├── CHANGELOG.md                           # Version history
└── LICENSE                                # MIT License
```

## Core Concepts

### 1. Tri-State Evaluation Model

Every criterion evaluation produces one of three states:

- **MATCHED** - Criterion evaluated successfully, condition is TRUE
- **NOT_MATCHED** - Criterion evaluated successfully, condition is FALSE
- **UNDETERMINED** - Could not evaluate (missing data, invalid criterion, type mismatch)

This is the core innovation that enables graceful degradation.

### 2. Graceful Degradation

**Design Contract**: Criteria never fail hard. One bad criterion never stops specification evaluation.

Implementation:
- Unknown operators → UNDETERMINED + log warning
- Type mismatches → UNDETERMINED + log warning
- Invalid patterns → UNDETERMINED + log warning
- Missing data → UNDETERMINED (not an error)
- Never throw exceptions during evaluation

### 3. Operator System

13 MongoDB-style operators implemented in `CriterionEvaluator`:

**Comparison**: `$eq`, `$ne`, `$gt`, `$gte`, `$lt`, `$lte`
**Collection**: `$in`, `$nin`, `$all`, `$size`
**Advanced**: `$exists`, `$type`, `$regex`, `$elemMatch`

Operators are implemented as lambda-based handlers in a map (CriterionEvaluator.java:50-100).

### 4. Deep Document Navigation

Uses dot notation to traverse nested maps:
- `benefits.universal_credit.status` → `document.get("benefits").get("universal_credit").get("status")`
- Implemented in `CriterionEvaluator.navigate()` method

## Key Files Deep Dive

### CriterionEvaluator.java (418 lines)

**Purpose**: Core query evaluation engine

**Key Methods**:
- `evaluateCriterion(document, criterion)` - Main entry point for single criterion
- `evaluate(document, query)` - Recursive query evaluation
- `navigate(document, path)` - Deep document navigation with dot notation
- Operator handlers (lines 50-100) - Lambda-based operator implementations

**Important Patterns**:
- Uses `InnerResult` record for tracking missing paths during evaluation
- Operator handlers: `BiFunction<Object, Object, Boolean>`
- Type checking before casting to prevent ClassCastException
- Regex pattern compilation (potential caching opportunity - see IMPROVEMENT_ROADMAP.md)

**Known Issues**:
- Line 194: Used to print to stderr for unknown operators (now logs via SLF4J)
- No regex pattern caching yet (creates new Pattern on each evaluation)

### SpecificationEvaluator.java (49 lines)

**Purpose**: Orchestrates parallel evaluation of specifications

**Key Methods**:
- `evaluate(document, specification)` - Evaluates all criteria and criteriaGroups
- Uses parallel streams for concurrent evaluation
- Caches criterion results for efficient criteriaGroup evaluation

**Architecture**:
- Line 15: Creates internal CriterionEvaluator instance
- Lines 16-22: Parallel evaluation of all criteria
- Lines 24-35: Sequential evaluation of criteriaGroups (uses cached results)
- Lines 37-46: Build EvaluationOutcome with summary

**Thread Safety**: Fully thread-safe (uses parallel streams, no mutable state)

### EvaluationResult.java (106 lines)

**Purpose**: Represents individual criterion evaluation outcome

**Key Fields**:
```java
record EvaluationResult(
    Criterion criterion,
    EvaluationState state,      // MATCHED/NOT_MATCHED/UNDETERMINED
    List<String> missingPaths,  // Tracks missing document fields
    String failureReason        // Explains UNDETERMINED state
)
```

**Factory Methods**:
- `matched(criterion)` - Successful match
- `notMatched(criterion)` - Evaluated but didn't match
- `undetermined(criterion, reason, paths)` - Couldn't evaluate
- `missing(criterion)` - Criterion not found in specification

**Important Methods**:
- `matched()` - Returns true only if state == MATCHED
- `isDetermined()` - Returns false if state == UNDETERMINED
- `reason()` - Human-readable explanation (for logging/debugging)

### Data Model Records

All domain models use Java records (immutable by default):

```java
record Criterion(String id, Map<String, Object> query)
record CriteriaGroup(String id, Junction junction, List<String> criteria)
record Specification(String id, List<Criterion> criteria, List<CriteriaGroup> criteriaGroups)
```

**Design Choice**: Records provide immutability, structural equality, and clean toString() for free.

## Development Guidelines

### Making Code Changes

1. **Preserve Immutability**: All records must remain immutable. Don't add setters or mutable fields.

2. **Maintain Thread Safety**: No mutable shared state. Use parallel streams for concurrency.

3. **Follow Graceful Degradation**: Never throw exceptions in evaluation logic. Return UNDETERMINED state instead.

4. **Add Tests**: Every new operator or feature needs comprehensive tests.

5. **Log, Don't Print**: Use SLF4J logger, not System.out/err.

### Adding New Operators

To add a new operator to `CriterionEvaluator`:

```java
// In CriterionEvaluator constructor (around line 50)
operators.put("$myOperator", (val, operand) -> {
    // Type checking (prevent ClassCastException)
    if (!(operand instanceof ExpectedType)) {
        logger.warn("Operator $myOperator expects {} but got {}",
                    ExpectedType.class, operand.getClass());
        return false; // Will become UNDETERMINED
    }

    // Implementation
    ExpectedType typedOperand = (ExpectedType) operand;
    return /* evaluation logic */;
});
```

**Important**: Always check types before casting. Return `false` for type mismatches (becomes UNDETERMINED).

### Testing Patterns

See `TriStateEvaluationTest.java` for examples:

```java
@Test
void testOperator_shouldMatch() {
    Criterion criterion = new Criterion("test", Map.of("field", Map.of("$operator", value)));
    EvaluationResult result = evaluator.evaluateCriterion(document, criterion);

    assertEquals(EvaluationState.MATCHED, result.state());
}

@Test
void testOperator_shouldBeUndetermined() {
    // Test with missing data, invalid types, etc.
    assertEquals(EvaluationState.UNDETERMINED, result.state());
    assertThat(result.failureReason()).contains("expected reason");
}
```

### Logging Levels

- **WARN**: Unknown operators, type mismatches, invalid patterns
- **INFO**: Specification evaluation started/completed
- **DEBUG**: Individual criterion evaluations
- **TRACE**: Detailed matching logic
- **ERROR**: Never used (criteria don't error, they become UNDETERMINED)

## Common Tasks

### Running Tests

```bash
mvn test                                    # Run all tests
mvn test -Dtest=TriStateEvaluationTest     # Run specific test
```

### Running Demo

```bash
mvn test-compile exec:java -Dexec.mainClass="uk.codery.jspec.demo.Main"
```

### Building

```bash
mvn clean install                           # Full build with tests
mvn clean package -DskipTests              # Build without tests
```

### Checking Coverage (if configured)

```bash
mvn test jacoco:report                      # Generate coverage report
```

## Extension Points

Areas designed for future extension:

### 1. Custom Operators (Future)

Currently operators are hardcoded. Planned improvement:

```java
// Future API
OperatorRegistry registry = new OperatorRegistry();
registry.register("$custom", customHandler);
CriterionEvaluator evaluator = new CriterionEvaluator(registry);
```

See IMPROVEMENT_ROADMAP.md § 2.1 for details.

### 2. Builder API (Future)

Planned fluent API for easier criterion construction:

```java
// Future API
Criterion criterion = Criterion.builder()
    .id("age-check")
    .field("age").gte(18)
    .build();
```

See IMPROVEMENT_ROADMAP.md § 2.3 for details.

### 3. Configuration Options (Future)

Potential configuration points:
- Strict mode (throw exceptions instead of UNDETERMINED)
- Regex pattern cache size
- Parallel vs sequential evaluation
- Custom type converters

## Performance Considerations

### Current Performance Characteristics

- **Operator evaluation**: O(1) map lookup
- **Document navigation**: O(d) where d = dot-notation depth
- **Parallel evaluation**: Criteria evaluated concurrently using parallel streams
- **Regex**: Pattern compiled on every evaluation (no caching)

### Known Bottlenecks

1. **Regex Pattern Compilation** (CriterionEvaluator.java:60)
   - Currently: `Pattern.compile()` called on every `$regex` evaluation
   - Impact: 10-100x slower for repeated patterns
   - Solution: Add LRU pattern cache (see IMPROVEMENT_ROADMAP.md § 3.1)

2. **Deep Document Navigation**
   - Each dot notation lookup traverses the document
   - Impact: O(d) lookups for depth d
   - Solution: Consider path caching for repeated queries

### Optimization Opportunities

- Regex pattern caching (high impact)
- Document path caching (medium impact)
- Operator handler inlining (low impact - JIT already does this)

## Recent Changes

### Latest Commits

```
b393adc - Merge PR: implement tri-state evaluation
f06b836 - refactor: remove backward compatibility, simplify implementation
2b4828e - feat: implement tri-state evaluation model with graceful error handling
85002b3 - Merge PR: analyze codebase improvements
71c8161 - docs: add graceful error handling design
```

### What Changed in Tri-State Implementation

**Before**: Binary matched/not-matched (ambiguous)
**After**: Three states (MATCHED/NOT_MATCHED/UNDETERMINED)

**Added**:
- `EvaluationState` enum
- `failureReason` field in `EvaluationResult`
- `EvaluationSummary` with `undeterminedCriteria` count
- Comprehensive error tracking

**Changed**:
- All operators now check types before casting
- Unknown operators log warnings instead of printing to stderr
- Invalid regex patterns handled gracefully

## Known Limitations

1. **No custom operator support** - Operators are hardcoded (planned fix)
2. **No regex caching** - Patterns recompiled every time (planned fix)
3. **Package-private classes** - Limited extensibility (planned fix)
4. **No builder API** - Verbose Map construction (planned fix)
5. **No Spring integration examples** - Works with Spring but no documented patterns

See IMPROVEMENT_ROADMAP.md for planned solutions.

## Troubleshooting

### Common Issues

**Q: Criterion always returns UNDETERMINED**
A: Check `result.failureReason()` and `result.missingPaths()` for details.

**Q: Unknown operator warnings in logs**
A: Verify operator spelling (must start with `$`). See README.md for supported operators.

**Q: Type mismatch warnings**
A: Check that operand type matches operator expectations (e.g., `$in` needs List, not String).

**Q: Parallel evaluation issues**
A: Ensure documents are thread-safe (use immutable collections).

### Debugging Tips

1. **Enable DEBUG logging**: Set SLF4J level to DEBUG to see individual criterion evaluations
2. **Check evaluation summary**: `outcome.summary()` shows counts of UNDETERMINED criteria
3. **Inspect failure reasons**: `result.failureReason()` explains why evaluation failed
4. **Review missing paths**: `result.missingPaths()` lists absent document fields

## Dependencies

### Runtime Dependencies

```xml
<!-- JSON/YAML parsing -->
<dependency>
    <groupId>com.fasterxml.jackson.dataformat</groupId>
    <artifactId>jackson-dataformat-yaml</artifactId>
    <version>2.20.0</version>
</dependency>

<!-- Boilerplate reduction -->
<dependency>
    <groupId>org.projectlombok</groupId>
    <artifactId>lombok</artifactId>
    <version>1.18.42</version>
    <scope>provided</scope>
</dependency>

<!-- Logging facade -->
<dependency>
    <groupId>org.slf4j</groupId>
    <artifactId>slf4j-api</artifactId>
    <version>2.0.9</version>
</dependency>
```

### Test Dependencies

```xml
<dependency>
    <groupId>org.junit.jupiter</groupId>
    <artifactId>junit-jupiter</artifactId>
    <version>5.10.1</version>
    <scope>test</scope>
</dependency>

<dependency>
    <groupId>org.assertj</groupId>
    <artifactId>assertj-core</artifactId>
    <version>3.24.2</version>
    <scope>test</scope>
</dependency>

<dependency>
    <groupId>org.slf4j</groupId>
    <artifactId>slf4j-simple</artifactId>
    <version>2.0.9</version>
    <scope>test</scope>
</dependency>
```

## Spring Integration (Unofficial)

While Spring-independent, the library works well with Spring:

```java
@Configuration
public class CriteriaConfig {

    @Bean
    public SpecificationEvaluator specificationEvaluator() {
        return new SpecificationEvaluator();
    }

    @Bean
    public ObjectMapper yamlMapper() {
        return new ObjectMapper(new YAMLFactory());
    }
}

@Service
public class EligibilityService {

    @Autowired
    private SpecificationEvaluator evaluator;

    public boolean checkEligibility(Map<String, Object> citizen,
                                   Specification spec) {
        EvaluationOutcome outcome = evaluator.evaluate(citizen, spec);
        return outcome.summary().matchedCriteria() > 0;
    }
}
```

## Project Roadmap

See [IMPROVEMENT_ROADMAP.md](IMPROVEMENT_ROADMAP.md) for detailed plans:

**Phase 1**: Foundation (testing, error handling)
**Phase 2**: Extensibility (custom operators, public API)
**Phase 3**: Developer experience (builders, caching, docs)
**Phase 4**: Ecosystem (publishing, examples)

## Questions for AI Assistants

When working with this codebase, consider:

1. **Is this change preserving immutability?**
2. **Does this maintain thread safety?**
3. **Will this throw exceptions or degrade gracefully?**
4. **Are there tests for this change?**
5. **Is logging using SLF4J, not System.out/err?**
6. **Does this follow the tri-state evaluation model?**

## Additional Context

### Why MongoDB-Style Operators?

- Familiar to developers already using MongoDB
- Expressive query language without custom DSL
- Well-understood semantics
- Easy to document and learn

### Why Java Records?

- Immutability by default (thread-safe)
- Structural equality (easier testing)
- Concise syntax (reduces boilerplate)
- Pattern matching support (future Java versions)

### Why Graceful Degradation?

- Real-world data is messy and incomplete
- Business criteria evaluation shouldn't halt on missing fields
- Users need to know which criteria couldn't evaluate
- Partial results are better than no results

### Why Spring-Independent?

- Broader applicability (not just Spring projects)
- Lighter weight (fewer dependencies)
- Easier testing (no container needed)
- Can still be used with Spring (as shown above)

## Contact

For questions about this codebase:
- Review this document first
- Check ERROR_HANDLING_DESIGN.md for error handling details
- Check IMPROVEMENT_ROADMAP.md for planned changes
- Check README.md for user-facing documentation

---

**Last Updated**: 2025-11-14
**Version**: 0.1.0-SNAPSHOT
**Java Version**: 21
