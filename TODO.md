# JSON Rules Engine - TODO List

## 🎯 Quick Summary

**Good News:** Your library is already Spring-independent and well-architected!

**Main gaps:** Testing, extensibility, error handling, and documentation.

---

## ✅ Project Decisions

- **Java Version:** 21 (keep current version)
- **Breaking Changes:** Allowed for v1.0
- **License:** MIT
- **Publishing Target:** TBD
- **Version:** 0.0.1-SNAPSHOT → 1.0.0

---

## 📋 Priority 1: Foundation (CRITICAL)

### Testing (Week 1)
- [ ] Unit tests for all 13 operators in RuleEvaluator
- [ ] Unit tests for SpecificationEvaluator (parallel evaluation, result caching)
- [ ] Integration tests for end-to-end scenarios
- [ ] Edge case testing (nulls, type mismatches, deep nesting)

### Error Handling (Week 1)
- [ ] Create exception hierarchy (RuleEvaluationException, InvalidOperatorException, etc.)
- [ ] Add input validation at API boundaries
- [ ] Replace `System.err.println` with proper exceptions (RuleEvaluator.java:194)
- [ ] Add descriptive error messages with context

---

## 📋 Priority 2: Extensibility (HIGH)

### Public API for Custom Operators (Week 2)
- [ ] Extract `OperatorHandler` interface to public API
- [ ] Create `OperatorRegistry` class for dynamic operator registration
- [ ] Make `RuleEvaluator` public (currently package-private)
- [ ] Add constructor accepting custom OperatorRegistry

### Builder Pattern (Week 2)
- [ ] Create `RuleEvaluatorBuilder` for configuration
- [ ] Create `SpecificationEvaluatorBuilder` for configuration
- [ ] Add fluent API for building Rules programmatically
- [ ] Add QueryBuilder for complex query construction

### Bug Fixes
- [ ] **Fix SpecificationEvaluator.java:15** - Uses new evaluator instead of constructor parameter

---

## 📋 Priority 3: Performance & Observability (MEDIUM)

### Performance Optimizations (Week 3)
- [ ] Implement regex Pattern caching (currently recreates on every evaluation)
- [ ] Add LRU cache for compiled patterns (configurable size)
- [ ] Make pattern cache thread-safe
- [ ] Add cache statistics (optional)

### Logging (Week 3)
- [ ] Add SLF4J dependency (facade only, no implementation)
- [ ] Add logging to RuleEvaluator (DEBUG: evaluations, WARN: unknown operators)
- [ ] Add logging to SpecificationEvaluator (INFO: results)
- [ ] Document logging configuration for users

---

## 📋 Priority 4: Documentation (MEDIUM-HIGH)

### JavaDoc (Week 4)
- [ ] Add comprehensive JavaDoc to all public classes
- [ ] Add method-level JavaDoc (params, returns, exceptions)
- [ ] Create package-info.java with overview
- [ ] Include code examples in JavaDoc

### User Documentation (Week 4)
- [ ] Create README.md with quick start guide
- [ ] Document all 13 operators with examples
- [ ] Add "Building Rules Programmatically" section
- [ ] Show Spring integration examples
- [ ] Create CHANGELOG.md

### Examples (Week 4)
- [ ] Create standalone Java example
- [ ] Create Spring Boot integration example (separate module)
- [ ] Create custom operators example
- [ ] Add real-world use case examples

---

## 📋 Priority 5: Publishing (OPTIONAL)

### Maven Central Preparation
- [ ] Add `<scm>`, `<developers>`, `<licenses>` to pom.xml
- [ ] Configure maven-gpg-plugin for signing
- [ ] Configure nexus-staging-maven-plugin
- [ ] Create RELEASING.md with deployment instructions
- [ ] Choose license (Apache 2.0 or MIT recommended)

---

## 📋 Priority 6: Advanced Features (FUTURE)

### Performance Benchmarks
- [ ] Add JMH dependency
- [ ] Create benchmarks for simple vs complex queries
- [ ] Benchmark parallel vs sequential evaluation
- [ ] Benchmark with/without pattern caching

### Type Safety
- [ ] Add generic document type support
- [ ] Create TypedRuleEvaluator<T>
- [ ] Improve type casting and validation

### Additional Operators
- [ ] String operators: `$startsWith`, `$endsWith`, `$contains`, `$length`
- [ ] Date operators: `$before`, `$after`, `$between`
- [ ] Arithmetic operators: `$mod`, `$abs`, `$ceil`, `$floor`
- [ ] Logical operators: `$not`, `$nor`

---

## 🏗️ Code Structure Improvements

### Package Reorganization (Breaking Change)
Consider reorganizing for v1.0:
```
uk.codery.rules.api.*        → Public API (interfaces, builders)
uk.codery.rules.core.*       → Core implementation
uk.codery.rules.operators.*  → Operator implementations
uk.codery.rules.exceptions.* → Exception hierarchy
```

### Immutability Improvements
- [ ] Return unmodifiable collections from record getters
- [ ] Document immutability contracts
- [ ] Consider defensive copying where needed

---

## 🐛 Known Issues to Fix

1. **SpecificationEvaluator.java:15** - Ignores constructor parameter
   ```java
   // BUG: Should use this.evaluator, not create new instance
   RuleEvaluator evaluator = new RuleEvaluator();
   ```

2. **RuleEvaluator.java:60** - Pattern recreation on every evaluation
   ```java
   // PERFORMANCE: Cache compiled patterns
   Pattern pattern = Pattern.compile((String) operand);
   ```

3. **RuleEvaluator.java:194** - Prints errors to stderr
   ```java
   // ERROR HANDLING: Throw exception instead
   System.err.println("Unknown operator: " + op);
   ```

---

## 📊 Success Metrics

After completing Priorities 1-4, you'll have:

- ✅ **100% test coverage** → Confidence in correctness
- ✅ **Extensible API** → Users can add custom operators
- ✅ **Proper error handling** → Debuggable failures
- ✅ **Performance optimized** → 10-100x faster regex evaluation
- ✅ **Well documented** → Easy to use and understand
- ✅ **Production ready** → Suitable for enterprise use
- ✅ **Spring compatible** → Works with or without Spring

---

## ⏱️ Estimated Timeline

- **Week 1:** Testing + Error Handling → Production-ready foundation
- **Week 2:** Extensibility + API Design → User extensibility
- **Week 3:** Performance + Logging → Enterprise-ready
- **Week 4:** Documentation + Examples → Public release ready

**Minimum viable improvement:** Weeks 1-2 (2 weeks part-time)
**Full improvement:** Weeks 1-4 (4 weeks part-time)

---

## 🚀 Getting Started

**Recommended order:**

1. Start with testing (Priority 1) - Establish baseline
2. Fix error handling (Priority 1) - Make failures debuggable
3. Add extensibility (Priority 2) - Core value proposition
4. Optimize performance (Priority 3) - Production readiness
5. Complete documentation (Priority 4) - User experience

---

## 📝 Files to Create

```
src/main/java/uk/codery/rules/
├── OperatorHandler.java
├── OperatorRegistry.java
├── builder/
│   ├── RuleEvaluatorBuilder.java
│   ├── SpecificationEvaluatorBuilder.java
│   └── RuleBuilder.java
└── exceptions/
    ├── RuleEvaluationException.java
    ├── InvalidOperatorException.java
    └── InvalidQueryException.java

src/test/java/uk/codery/rules/
├── RuleEvaluatorTest.java
├── SpecificationEvaluatorTest.java
└── operators/
    ├── ComparisonOperatorsTest.java
    └── LogicalOperatorsTest.java

examples/
├── standalone/
└── spring-boot/

docs/
├── README.md
├── CHANGELOG.md
└── RELEASING.md
```

---

## 💡 Quick Wins (Do These First)

1. **Fix the evaluator bug** (SpecificationEvaluator.java:15) - 2 minutes
2. **Add JUnit dependency** to pom.xml - 5 minutes
3. **Write first operator test** (e.g., `$eq`) - 30 minutes
4. **Create RuleEvaluationException** - 15 minutes
5. **Add basic README** with usage example - 1 hour

These 5 quick wins give you tests, error handling foundation, and documentation!

---

## ❓ Questions to Answer

Before starting implementation:

1. **Java version target?** (Keep Java 21 or support 17/11?)
2. **Breaking changes acceptable?** (For package structure and bug fixes?)
3. **License?** (Apache 2.0, MIT, or other?)
4. **Publishing target?** (Maven Central, private repo, or GitHub packages?)
5. **Most important features?** (What to prioritize?)

---

See `IMPROVEMENT_ROADMAP.md` for detailed explanations of each improvement.
