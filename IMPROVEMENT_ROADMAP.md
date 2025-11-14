# JSON Rules Engine - Improvement Roadmap

## Executive Summary

Your JSON Rules Engine is already a **clean, Spring-independent library** with only 2 dependencies (Jackson YAML + Lombok). The codebase consists of ~550 lines of well-architected Java 21 code using modern features (records, streams, functional programming).

**Current State:**
- ✅ Zero Spring coupling
- ✅ Clean 3-layer architecture
- ✅ Thread-safe with parallel evaluation
- ✅ 13 MongoDB-style operators
- ❌ No test coverage
- ❌ Limited extensibility (package-private classes)
- ❌ Minimal error handling
- ❌ No documentation

---

## Priority 1: Foundation (Weeks 1-2)

### 1.1 Testing Infrastructure (Critical)
**Why:** Zero test coverage is a critical risk for production use

- [ ] **Unit tests for RuleEvaluator** - Test all 13 operators individually
  - Simple operators: `$eq`, `$ne`, `$gt`, `$gte`, `$lt`, `$lte`
  - Collection operators: `$in`, `$nin`, `$all`, `$size`
  - Advanced operators: `$exists`, `$type`, `$regex`, `$elemMatch`
  - Edge cases: null values, type mismatches, nested structures

- [ ] **Unit tests for SpecificationEvaluator** - Test orchestration logic
  - Parallel rule evaluation
  - RuleSet evaluation with AND/OR operators
  - Result caching behavior
  - Thread safety verification

- [ ] **Integration tests** - End-to-end scenarios
  - Complex nested queries
  - Real-world specification examples
  - Performance under load

**Files to create:**
```
src/test/java/uk/codery/rules/
├── RuleEvaluatorTest.java
├── SpecificationEvaluatorTest.java
├── operators/
│   ├── ComparisonOperatorsTest.java
│   ├── LogicalOperatorsTest.java
│   └── AdvancedOperatorsTest.java
└── integration/
    └── EndToEndTest.java
```

### 1.2 Error Handling (High Priority)
**Why:** Current code uses `System.err.println()` and swallows errors

- [ ] **Create exception hierarchy**
  ```java
  // New exceptions to add
  public class RuleEvaluationException extends RuntimeException
  public class InvalidOperatorException extends RuleEvaluationException
  public class InvalidQueryException extends RuleEvaluationException
  public class TypeMismatchException extends RuleEvaluationException
  ```

- [ ] **Add input validation**
  - Validate null inputs at API boundaries
  - Validate query structure (detect malformed queries early)
  - Validate operator operands (e.g., `$in` requires a List)

- [ ] **Replace error printing with exceptions**
  - Fix: `RuleEvaluator.java:194` - Currently prints unknown operators to stderr
  - Throw `InvalidOperatorException` with helpful message

**Impact:** Better debugging experience, fail-fast behavior, proper error propagation

---

## Priority 2: Extensibility & API Design (Weeks 2-3)

### 2.1 Public API for Custom Operators
**Why:** Users should be able to add their own operators (currently impossible)

**Current limitation:** `RuleEvaluator` is package-private with hardcoded operators

- [ ] **Extract `OperatorHandler` to public interface**
  ```java
  // Make this public and move to separate file
  public interface OperatorHandler {
      boolean evaluate(Object value, Object operand);

      // Optional: Add metadata
      String name();
      String description();
  }
  ```

- [ ] **Create `OperatorRegistry` class**
  ```java
  public class OperatorRegistry {
      private final Map<String, OperatorHandler> operators;

      public void register(String name, OperatorHandler handler) { }
      public void unregister(String name) { }
      public OperatorHandler get(String name) { }
      public Set<String> availableOperators() { }
  }
  ```

- [ ] **Make `RuleEvaluator` extensible**
  - Change from package-private to `public`
  - Constructor accepting custom `OperatorRegistry`
  - Keep default constructor for convenience

**Example usage after improvements:**
```java
// Custom operator
OperatorHandler startsWithOp = (val, operand) ->
    String.valueOf(val).startsWith(String.valueOf(operand));

// Register and use
OperatorRegistry registry = new OperatorRegistry();
registry.register("$startsWith", startsWithOp);

RuleEvaluator evaluator = new RuleEvaluator(registry);
```

### 2.2 Builder Pattern for Configuration
**Why:** Make API more fluent and easier to configure

- [ ] **Create `RuleEvaluatorBuilder`**
  ```java
  RuleEvaluator evaluator = RuleEvaluator.builder()
      .withOperator("$custom", customHandler)
      .withRegexCache(true)
      .withStrictMode(true)  // Fail on unknown operators
      .build();
  ```

- [ ] **Create `SpecificationEvaluatorBuilder`**
  ```java
  SpecificationEvaluator evaluator = SpecificationEvaluator.builder()
      .withParallelEvaluation(false)  // Disable parallel for debugging
      .withRuleEvaluator(customEvaluator)
      .withResultCache(true)
      .build();
  ```

### 2.3 Fluent API for Programmatic Rule Building
**Why:** Current API requires manual Map construction (verbose)

- [ ] **Create fluent builders**
  ```java
  // Current (verbose)
  Rule rule = new Rule("age-check", Map.of("age", Map.of("$gte", 18)));

  // After improvement (fluent)
  Rule rule = Rule.builder()
      .id("age-check")
      .field("age").gte(18)
      .build();
  ```

**Files to create:**
```
src/main/java/uk/codery/rules/
├── OperatorHandler.java (public interface)
├── OperatorRegistry.java
├── builder/
│   ├── RuleEvaluatorBuilder.java
│   ├── SpecificationEvaluatorBuilder.java
│   ├── RuleBuilder.java
│   └── QueryBuilder.java
```

---

## Priority 3: Performance & Observability (Week 3)

### 3.1 Regex Pattern Caching
**Why:** Currently recreates Pattern on every `$regex` evaluation (expensive)

**Current code issue:** `RuleEvaluator.java:60`
```java
// BAD: Creates new Pattern every time
Pattern pattern = Pattern.compile((String) operand);
```

- [ ] **Implement LRU pattern cache**
  ```java
  private final Map<String, Pattern> patternCache =
      Collections.synchronizedMap(new LinkedHashMap<>(16, 0.75f, true) {
          protected boolean removeEldestEntry(Map.Entry eldest) {
              return size() > 100;  // Max 100 cached patterns
          }
      });
  ```

- [ ] **Add cache configuration**
  - Configurable cache size
  - Optional cache statistics (hit rate, evictions)
  - Thread-safe implementation

**Performance impact:** ~10-100x faster for repeated regex patterns

### 3.2 Logging (SLF4J)
**Why:** Production systems need observability without System.out/err

- [ ] **Add SLF4J dependency** (facade only, no implementation)
  ```xml
  <dependency>
      <groupId>org.slf4j</groupId>
      <artifactId>slf4j-api</artifactId>
      <version>2.0.9</version>
  </dependency>
  ```

- [ ] **Add logging at key points**
  - DEBUG: Rule evaluation started/completed
  - INFO: Specification evaluation results
  - WARN: Unknown operators, type mismatches
  - ERROR: Evaluation failures

- [ ] **Keep library neutral** - Let users choose logging backend
  - Logback for Spring Boot apps
  - Log4j2 for enterprise apps
  - JUL for minimal setups

**Files to modify:**
- `RuleEvaluator.java` - Add logger, replace System.err
- `SpecificationEvaluator.java` - Add evaluation logging

---

## Priority 4: Documentation & Publishing (Week 4)

### 4.1 JavaDoc (High Priority)
**Why:** Public API needs documentation for IDE autocomplete

- [ ] **Document all public classes**
  - Class-level JavaDoc with usage examples
  - Method-level JavaDoc with parameters, returns, exceptions
  - Code examples in JavaDoc

- [ ] **Add package-info.java**
  ```java
  /**
   * JSON Rules Engine - MongoDB-style query evaluation for Java.
   *
   * <p>Main entry points:
   * <ul>
   *   <li>{@link SpecificationEvaluator} - Evaluate specifications
   *   <li>{@link RuleEvaluator} - Evaluate individual rules
   * </ul>
   *
   * <h2>Example Usage</h2>
   * <pre>{@code
   * // ... example code ...
   * }</pre>
   */
  package uk.codery.rules;
  ```

### 4.2 README.md
**Why:** GitHub landing page and quick start guide

- [ ] **Create comprehensive README**
  ```markdown
  # JSON Rules Engine

  ## Features
  - 13 MongoDB-style operators
  - Zero dependencies (except Jackson for YAML parsing)
  - Thread-safe parallel evaluation
  - Works with or without Spring

  ## Quick Start
  ## Operator Reference
  ## Building Rules Programmatically
  ## Spring Integration Example
  ## Performance Benchmarks
  ```

### 4.3 Example Projects
**Why:** Show real-world usage patterns

- [ ] **Create examples directory**
  ```
  examples/
  ├── standalone/          # Plain Java example
  ├── spring-boot/         # Spring Boot integration
  └── custom-operators/    # Extending with custom operators
  ```

- [ ] **Spring Boot example** (separate module)
  - Show as `@Bean` configuration
  - Demonstrate REST API integration
  - Show YAML configuration loading

**Example Spring configuration:**
```java
@Configuration
public class RulesConfig {
    @Bean
    public SpecificationEvaluator specificationEvaluator() {
        return new SpecificationEvaluator();
    }

    @Bean
    public RuleEvaluator ruleEvaluator(OperatorRegistry registry) {
        return new RuleEvaluator(registry);
    }
}
```

### 4.4 Maven Central Publishing
**Why:** Make library easily accessible via Maven/Gradle

- [ ] **Update pom.xml for publishing**
  - Add `<scm>` section with GitHub URL
  - Add `<developers>` section
  - Add `<licenses>` section with MIT license
  - Configure maven-gpg-plugin for signing
  - Configure nexus-staging-maven-plugin

- [ ] **Create release process documentation**
  - Versioning strategy (semantic versioning)
  - Release checklist
  - Deployment instructions

**Files to create:**
```
docs/
├── RELEASING.md
└── CHANGELOG.md
```

---

## Priority 5: Advanced Features (Future)

### 5.1 Performance Benchmarks
**Why:** Users need to know performance characteristics

- [ ] **Add JMH benchmarks**
  ```xml
  <dependency>
      <groupId>org.openjdk.jmh</groupId>
      <artifactId>jmh-core</artifactId>
      <version>1.37</version>
      <scope>test</scope>
  </dependency>
  ```

- [ ] **Benchmark scenarios**
  - Simple vs complex queries
  - Parallel vs sequential evaluation
  - With/without pattern caching
  - Deep vs shallow document structures

### 5.2 Type Safety Improvements
**Why:** Currently uses raw `Object` everywhere

- [ ] **Add generic document type**
  ```java
  public class TypedRuleEvaluator<T> {
      public EvaluationResult evaluateRule(T document, Rule rule) { }
  }
  ```

- [ ] **Consider JsonPath-style type casting**
  - Support for reading typed values from documents
  - Better IDE autocomplete for known document shapes

### 5.3 Additional Operators
**Why:** Expand operator library for common use cases

- [ ] **String operators**
  - `$startsWith`, `$endsWith`, `$contains`
  - `$length` - String/array length checks

- [ ] **Date operators**
  - `$before`, `$after`, `$between`

- [ ] **Arithmetic operators**
  - `$mod`, `$abs`, `$ceil`, `$floor`

- [ ] **Logical operators**
  - `$not`, `$nor` (in addition to existing AND/OR)

---

## Implementation Strategy

### Phase 1: Stabilization (Week 1)
1. Add comprehensive test suite
2. Fix error handling
3. Add basic logging

**Goal:** Production-ready foundation

### Phase 2: Extensibility (Week 2)
1. Extract public interfaces
2. Add operator registry
3. Make RuleEvaluator public and extensible

**Goal:** Library can be extended by users

### Phase 3: Developer Experience (Week 3)
1. Add builders and fluent API
2. Implement regex caching
3. Comprehensive JavaDoc

**Goal:** Pleasant API for developers

### Phase 4: Ecosystem (Week 4)
1. Complete documentation (README, examples)
2. Create Spring Boot example
3. Prepare for Maven Central publishing

**Goal:** Ready for public release

---

## Breaking Changes to Consider

If you're planning a v1.0 release, consider these breaking changes now:

### Current Issues
1. **`SpecificationEvaluator.java:15`** - Creates new evaluator instance (ignores constructor parameter)
   ```java
   // BUG: Ignores this.evaluator
   RuleEvaluator evaluator = new RuleEvaluator();
   ```
   **Fix:** Use `this.evaluator` or remove the field

2. **Package structure** - All classes in single package
   ```
   uk.codery.rules.*
   ```
   **Better structure:**
   ```
   uk.codery.rules.api.*        (public API)
   uk.codery.rules.core.*       (core implementation)
   uk.codery.rules.operators.*  (operator implementations)
   uk.codery.rules.builder.*    (builders)
   ```

3. **Public record fields** - Direct field access
   ```java
   specification.rules()  // Exposes mutable list
   ```
   **Fix:** Return unmodifiable copies or document immutability expectations

---

## Dependencies Strategy

### Current Dependencies (Good!)
```xml
<dependencies>
    <dependency>jackson-dataformat-yaml</dependency>  <!-- YAML parsing -->
    <dependency>lombok</dependency>                   <!-- Boilerplate reduction -->
</dependencies>
```

### Recommended Additions
```xml
<!-- Logging facade (optional for users) -->
<dependency>
    <groupId>org.slf4j</groupId>
    <artifactId>slf4j-api</artifactId>
    <scope>compile</scope>  <!-- API only, no implementation -->
</dependency>

<!-- Testing -->
<dependency>
    <groupId>org.junit.jupiter</groupId>
    <artifactId>junit-jupiter</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.assertj</groupId>
    <artifactId>assertj-core</artifactId>
    <scope>test</scope>
</dependency>
```

### Keep It Simple
- ❌ **Don't add:** Spring, Guava, Apache Commons
- ✅ **Do add:** Only essential APIs (SLF4J for logging)
- ✅ **Keep:** Jackson (already using it), Lombok (reduces boilerplate)

---

## Success Metrics

After implementing these improvements, you'll have:

- ✅ **100% test coverage** - Confidence in correctness
- ✅ **Public, documented API** - Easy to use and extend
- ✅ **Proper error handling** - Debuggable failures
- ✅ **Performance optimizations** - Production-ready speed
- ✅ **Spring-compatible** - Works with or without Spring
- ✅ **Extensible** - Users can add custom operators
- ✅ **Well-documented** - README, JavaDoc, examples
- ✅ **Maven Central ready** - Easy dependency management

---

## Quick Reference: Files to Create/Modify

### New Files
```
src/main/java/uk/codery/rules/
├── OperatorHandler.java (extract from inner interface)
├── OperatorRegistry.java
├── builder/
│   ├── RuleEvaluatorBuilder.java
│   ├── SpecificationEvaluatorBuilder.java
│   └── RuleBuilder.java
├── exceptions/
│   ├── RuleEvaluationException.java
│   ├── InvalidOperatorException.java
│   └── InvalidQueryException.java

src/test/java/uk/codery/rules/
├── RuleEvaluatorTest.java
├── SpecificationEvaluatorTest.java
├── operators/ (test per operator)

docs/
├── README.md
├── CHANGELOG.md
└── RELEASING.md

examples/
├── standalone/
└── spring-boot/
```

### Files to Modify
- `pom.xml` - Add SLF4J, test dependencies, publishing config
- `RuleEvaluator.java` - Make public, add logging, fix regex caching, use OperatorRegistry
- `SpecificationEvaluator.java` - Fix evaluator usage bug, add logging, add builder

---

## Project Decisions ✅

**Confirmed:**
1. **Target Java version:** 21 (keep current)
2. **Breaking changes:** Allowed for v1.0
3. **License:** MIT
4. **Version plan:** 0.0.1-SNAPSHOT → 1.0.0

**To be decided:**
- **Publishing target:** Maven Central, private repository, or GitHub Packages?
- **Priority features:** Which improvements from this roadmap are most important?

---

## Next Steps

1. ✅ Review this roadmap
2. **Decide:** Publishing target (Maven Central recommended for open source)
3. **Prioritize:** Choose which improvements to implement first
4. **Implement:** Start with Phase 1 (testing + error handling)

**Estimated effort:** 4 weeks for full implementation (working part-time)
**Minimum viable:** Phase 1 + Phase 2 = 2 weeks for production-ready, extensible library
