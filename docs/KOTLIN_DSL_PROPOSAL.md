# Kotlin DSL Proposal for JSpec

## Executive Summary

This proposal evaluates the introduction of a Kotlin DSL as an alternative configuration method for JSpec specifications. The DSL would complement the existing YAML format, providing type-safe, IDE-assisted specification authoring with compile-time validation.

---

## 1. Current State Analysis

### 1.1 YAML Configuration Format

The current YAML-based configuration is declarative and readable:

```yaml
id: loan-eligibility
criteria:
  - id: age-minimum
    query:
      applicant.age: { $gte: 18 }

  - id: credit-good
    query:
      financial.credit_score: { $gt: 650 }

  - id: basic-eligibility
    junction: AND
    criteria:
      - ref: age-minimum
      - ref: credit-good
```

### 1.2 Existing Java Builder API

JSpec already has fluent builders for programmatic construction:

```java
QueryCriterion ageMin = QueryCriterion.builder()
    .id("age-minimum")
    .field("applicant.age").gte(18)
    .build();

CompositeCriterion eligibility = CompositeCriterion.builder()
    .id("basic-eligibility")
    .and()
    .addReference(ageMin)
    .addReference(creditGood)
    .build();
```

---

## 2. Proposed Kotlin DSL Design

### 2.1 Basic Syntax

```kotlin
val loanEligibility = specification("loan-eligibility") {

    // Query criteria with type-safe operators
    val ageMinimum by criterion("age-minimum") {
        "applicant.age" gte 18
    }

    val creditGood by criterion("credit-good") {
        "financial.credit_score" gt 650
    }

    val employmentStatus by criterion("employment-status") {
        "employment.status" eq "EMPLOYED"
    }

    // Composite with AND junction
    val basicEligibility by composite("basic-eligibility") {
        and {
            +ageMinimum
            +employmentStatus
            criterion("not-bankrupt") {
                "financial.bankruptcy_flag" ne true
            }
        }
    }

    // Composite with OR junction
    val financialStrength by composite("financial-strength") {
        or {
            +creditGood
            criterion("income-diverse") {
                "financial.income_sources" all listOf("salary", "investments")
            }
        }
    }

    // Nested composite
    composite("overall-eligibility") {
        and {
            +basicEligibility
            +financialStrength
        }
    }
}
```

### 2.2 Advanced Operators

```kotlin
val advancedChecks = specification("validation") {

    // Multiple conditions on same field
    criterion("income-range") {
        "financial.annual_income" gte 50000 lte 500000
    }

    // Collection operators
    criterion("approved-states") {
        "applicant.address.state" inList listOf("CA", "NY", "TX", "FL")
    }

    criterion("excluded-industries") {
        "employment.industry" notIn listOf("gambling", "cannabis")
    }

    // Existence and type checks
    criterion("contact-valid") {
        "applicant.contact.email" exists true
        "applicant.contact.phone" type "string"
    }

    // Regex pattern matching
    criterion("email-format") {
        "applicant.contact.email" regex "^[\\w.]+@[\\w.]+\\.[a-z]+$"
    }

    // Element match for arrays
    criterion("has-paid-loan") {
        "financial.loan_history" elemMatch {
            "status" eq "paid"
            "amount" gte 10000
        }
    }
}
```

### 2.3 Reusable Templates

```kotlin
// Define reusable criterion patterns
fun CriterionContext.ageCheck(minAge: Int) {
    "applicant.age" gte minAge
}

fun CriterionContext.stateCheck(states: List<String>) {
    "applicant.address.state" inList states
}

// Use in specifications
val seniorLoan = specification("senior-loan") {
    criterion("age-check") {
        ageCheck(65)
    }

    criterion("state-check") {
        stateCheck(listOf("FL", "AZ", "NV"))
    }
}
```

### 2.4 Conditional Building

```kotlin
fun buildSpec(config: LoanConfig) = specification("dynamic-loan") {

    criterion("base-age") {
        "applicant.age" gte config.minimumAge
    }

    // Conditional criteria
    if (config.requiresEmployment) {
        criterion("employment-check") {
            "employment.status" eq "EMPLOYED"
        }
    }

    // Dynamic operator values
    criterion("credit-threshold") {
        "financial.credit_score" gt config.minCreditScore
    }

    // Build composites based on config
    composite("eligibility") {
        if (config.strictMode) {
            and {
                ref("base-age")
                if (config.requiresEmployment) ref("employment-check")
                ref("credit-threshold")
            }
        } else {
            or {
                ref("base-age")
                ref("credit-threshold")
            }
        }
    }
}
```

---

## 3. Comparison: Kotlin DSL vs YAML

### 3.1 Advantages of Kotlin DSL

| Aspect | Benefit | Example |
|--------|---------|---------|
| **Type Safety** | Compile-time operator validation | `"age" gte "invalid"` fails to compile |
| **IDE Support** | Autocomplete, refactoring, navigation | Ctrl+Click on criterion reference |
| **Reusability** | Extract common patterns as functions | `ageCheck(18)` template |
| **Dynamic Building** | Conditional logic, loops, variables | `if (strict) and { ... }` |
| **Refactoring** | Safe rename across entire spec | Rename `ageMinimum` everywhere |
| **Testing** | Unit test individual criteria | Test templates in isolation |
| **Documentation** | KDoc on criteria and templates | Inline documentation |
| **Validation** | Custom compile-time checks | Verify operator compatibility |

### 3.2 Advantages of YAML Configuration

| Aspect | Benefit | Example |
|--------|---------|---------|
| **Simplicity** | No programming knowledge required | Business analysts can edit |
| **Runtime Loading** | Hot-reload without recompilation | Change rules in production |
| **Portability** | Language-agnostic format | Share with non-Kotlin systems |
| **Versioning** | Easy diff in version control | Clear YAML diffs |
| **Tooling** | Standard YAML editors/validators | VS Code YAML extension |
| **Separation** | Configuration separate from code | Different deployment artifacts |

### 3.3 Disadvantages of Kotlin DSL

| Aspect | Issue | Mitigation |
|--------|-------|------------|
| **Learning Curve** | Requires Kotlin knowledge | Provide extensive examples |
| **Compilation Required** | Changes need rebuild | Provide hot-reload dev mode |
| **Language Lock-in** | JVM/Kotlin only | Keep YAML as alternative |
| **Complexity** | DSL implementation overhead | Hide complexity in library |
| **Debugging** | Stack traces in DSL code | Good error messages |

### 3.4 Disadvantages of YAML Configuration

| Aspect | Issue | Mitigation |
|--------|-------|------------|
| **No Type Safety** | Typos discovered at runtime | Schema validation |
| **Limited IDE Support** | Basic YAML editing only | Custom YAML schema |
| **No Reusability** | Copy-paste for similar criteria | YAML anchors (limited) |
| **No Logic** | Cannot use conditionals | Separate specs per scenario |
| **String References** | `ref: typo` fails at runtime | Validation tooling |

---

## 4. Implementation Architecture

### 4.1 DSL Core Components

```kotlin
// Core DSL marker for scope control
@DslMarker
annotation class JSpecDsl

// Specification builder
@JSpecDsl
class SpecificationBuilder(private val id: String) {
    private val criteria = mutableListOf<Criterion>()

    fun criterion(id: String, block: CriterionContext.() -> Unit): CriterionDelegate {
        val context = CriterionContext(id)
        context.block()
        val criterion = context.build()
        criteria.add(criterion)
        return CriterionDelegate(criterion)
    }

    fun composite(id: String, block: CompositeContext.() -> Unit): CriterionDelegate {
        val context = CompositeContext(id)
        context.block()
        val composite = context.build()
        criteria.add(composite)
        return CriterionDelegate(composite)
    }

    fun build(): Specification = Specification(id, criteria)
}

// Property delegate for named criteria
class CriterionDelegate(private val criterion: Criterion) {
    operator fun getValue(thisRef: Any?, property: KProperty<*>): Criterion = criterion
}
```

### 4.2 Criterion Context

```kotlin
@JSpecDsl
class CriterionContext(private val id: String) {
    private val query = mutableMapOf<String, MutableMap<String, Any>>()

    // Infix operators for field conditions
    infix fun String.eq(value: Any) = addOperator(this, "\$eq", value)
    infix fun String.ne(value: Any) = addOperator(this, "\$ne", value)
    infix fun String.gt(value: Number) = addOperator(this, "\$gt", value)
    infix fun String.gte(value: Number) = addOperator(this, "\$gte", value)
    infix fun String.lt(value: Number) = addOperator(this, "\$lt", value)
    infix fun String.lte(value: Number) = addOperator(this, "\$lte", value)

    infix fun String.inList(values: List<Any>) = addOperator(this, "\$in", values)
    infix fun String.notIn(values: List<Any>) = addOperator(this, "\$nin", values)
    infix fun String.all(values: List<Any>) = addOperator(this, "\$all", values)
    infix fun String.size(count: Int) = addOperator(this, "\$size", count)

    infix fun String.exists(exists: Boolean) = addOperator(this, "\$exists", exists)
    infix fun String.type(typeName: String) = addOperator(this, "\$type", typeName)
    infix fun String.regex(pattern: String) = addOperator(this, "\$regex", pattern)

    infix fun String.elemMatch(block: CriterionContext.() -> Unit) {
        val context = CriterionContext("")
        context.block()
        addOperator(this, "\$elemMatch", context.buildQueryMap())
    }

    // Chaining support for multiple operators on same field
    infix fun FieldCondition.lte(value: Number): FieldCondition {
        addOperator(field, "\$lte", value)
        return this
    }

    private fun addOperator(field: String, op: String, value: Any): FieldCondition {
        query.getOrPut(field) { mutableMapOf() }[op] = value
        return FieldCondition(field)
    }

    fun build(): QueryCriterion = QueryCriterion(id, buildQueryMap())

    private fun buildQueryMap(): Map<String, Any> = query.mapValues { it.value.toMap() }
}

data class FieldCondition(val field: String)
```

### 4.3 Composite Context

```kotlin
@JSpecDsl
class CompositeContext(private val id: String) {
    private var junction: Junction = Junction.AND
    private val criteria = mutableListOf<Criterion>()

    fun and(block: JunctionContext.() -> Unit) {
        junction = Junction.AND
        JunctionContext(criteria).block()
    }

    fun or(block: JunctionContext.() -> Unit) {
        junction = Junction.OR
        JunctionContext(criteria).block()
    }

    fun build(): CompositeCriterion = CompositeCriterion(id, junction, criteria)
}

@JSpecDsl
class JunctionContext(private val criteria: MutableList<Criterion>) {

    // Add criterion by reference
    operator fun Criterion.unaryPlus() {
        criteria.add(CriterionReference(this.id()))
    }

    // Add by string ID
    fun ref(id: String) {
        criteria.add(CriterionReference(id))
    }

    // Inline criterion definition
    fun criterion(id: String, block: CriterionContext.() -> Unit) {
        val context = CriterionContext(id)
        context.block()
        criteria.add(context.build())
    }
}
```

### 4.4 Entry Point Function

```kotlin
fun specification(id: String, block: SpecificationBuilder.() -> Unit): Specification {
    return SpecificationBuilder(id).apply(block).build()
}

// Extension for immediate evaluation
fun Specification.evaluate(document: Map<String, Any>): EvaluationOutcome {
    return SpecificationEvaluator(this).evaluate(document)
}
```

---

## 5. Type Safety Features

### 5.1 Operator Type Constraints

```kotlin
// Type-safe operators prevent invalid combinations
infix fun String.gt(value: Number) = addOperator(this, "\$gt", value)
infix fun String.gt(value: String) = error("Cannot use ${"$"}gt with String")

// Compile-time error:
"age" gt "invalid"  // Error: None of the following functions can be called...
```

### 5.2 Pattern Validation

```kotlin
infix fun String.regex(pattern: String): FieldCondition {
    // Validate at build time
    try {
        Pattern.compile(pattern)
    } catch (e: PatternSyntaxException) {
        throw IllegalArgumentException("Invalid regex pattern: $pattern", e)
    }
    return addOperator(this, "\$regex", pattern)
}
```

### 5.3 Reference Validation

```kotlin
// Compile-time safety via property delegates
val ageCheck by criterion("age-check") { "age" gte 18 }
val statusCheck by criterion("status-check") { "status" eq "active" }

composite("eligibility") {
    and {
        +ageCheck      // Type-safe reference
        +statusCheck   // IDE autocomplete available
        +typoCheck     // Compile error: unresolved reference
    }
}
```

---

## 6. Migration Strategy

### 6.1 Phase 1: Core DSL Implementation

1. Implement DSL builders with all 13 operators
2. Generate Java-compatible `Specification` objects
3. Full interoperability with existing evaluation engine
4. Unit tests for all DSL constructs

### 6.2 Phase 2: Advanced Features

1. Criterion templates/functions
2. Conditional building support
3. Custom validation hooks
4. Error message improvements

### 6.3 Phase 3: Tooling & Documentation

1. YAML to Kotlin DSL converter
2. IDE plugin for enhanced support
3. Migration guide and examples
4. Performance benchmarks

### 6.4 Coexistence Strategy

Both formats should coexist:

```kotlin
// Load YAML spec
val yamlSpec = YamlLoader.load("specs/loan-eligibility.yaml")

// Define DSL spec
val dslSpec = specification("loan-eligibility") {
    // ...
}

// Both produce Specification objects
val evaluator1 = SpecificationEvaluator(yamlSpec)
val evaluator2 = SpecificationEvaluator(dslSpec)
```

---

## 7. Real-World Example Comparison

### 7.1 YAML Version (Current)

```yaml
id: e-commerce-validation
criteria:
  - id: minimum-order
    query:
      order.total: { $gte: 25.00 }

  - id: customer-verified
    query:
      customer.verified: { $eq: true }

  - id: shipping-eligible
    query:
      order.shipping.country: { $in: [US, CA, UK, AU] }

  - id: prime-member
    query:
      customer.membership: { $eq: PRIME }

  - id: items-in-stock
    query:
      order.items:
        $elemMatch:
          in_stock: { $eq: true }
          quantity: { $gte: 1 }

  - id: express-eligibility
    junction: AND
    criteria:
      - ref: minimum-order
      - ref: customer-verified
      - ref: shipping-eligible
      - ref: prime-member

  - id: standard-eligibility
    junction: OR
    criteria:
      - ref: express-eligibility
      - ref: items-in-stock
```

### 7.2 Kotlin DSL Version (Proposed)

```kotlin
val ecommerceValidation = specification("e-commerce-validation") {

    val minimumOrder by criterion("minimum-order") {
        "order.total" gte 25.00
    }

    val customerVerified by criterion("customer-verified") {
        "customer.verified" eq true
    }

    val shippingEligible by criterion("shipping-eligible") {
        "order.shipping.country" inList listOf("US", "CA", "UK", "AU")
    }

    val primeMember by criterion("prime-member") {
        "customer.membership" eq "PRIME"
    }

    val itemsInStock by criterion("items-in-stock") {
        "order.items" elemMatch {
            "in_stock" eq true
            "quantity" gte 1
        }
    }

    val expressEligibility by composite("express-eligibility") {
        and {
            +minimumOrder
            +customerVerified
            +shippingEligible
            +primeMember
        }
    }

    composite("standard-eligibility") {
        or {
            +expressEligibility
            +itemsInStock
        }
    }
}

// Usage
fun main() {
    val document = loadDocument("order.json")
    val outcome = ecommerceValidation.evaluate(document)

    if (outcome.summary().matched() > 0) {
        println("Order is eligible!")
    }
}
```

---

## 8. Testing Support

### 8.1 Unit Testing Criteria

```kotlin
class CriterionTests {

    @Test
    fun `age check should match adult`() {
        val criterion = criterion("age-check") {
            "age" gte 18
        }

        val document = mapOf("age" to 25)
        val result = criterion.testEvaluate(document)

        assertThat(result.state()).isEqualTo(EvaluationState.MATCHED)
    }

    @Test
    fun `age check should not match minor`() {
        val criterion = criterion("age-check") {
            "age" gte 18
        }

        val document = mapOf("age" to 16)
        val result = criterion.testEvaluate(document)

        assertThat(result.state()).isEqualTo(EvaluationState.NOT_MATCHED)
    }
}
```

### 8.2 Testing Templates

```kotlin
class TemplateTests {

    @Test
    fun `age template should apply correct threshold`() {
        fun CriterionContext.ageCheck(min: Int) {
            "applicant.age" gte min
        }

        val seniorCriterion = criterion("senior") {
            ageCheck(65)
        }

        val document = mapOf("applicant" to mapOf("age" to 70))
        val result = seniorCriterion.testEvaluate(document)

        assertThat(result.state()).isEqualTo(EvaluationState.MATCHED)
    }
}
```

---

## 9. Performance Considerations

### 9.1 Build-Time Performance

- DSL construction is one-time at startup
- Property delegates evaluated lazily
- No runtime overhead after specification built

### 9.2 Evaluation Performance

- Produces same `Specification` objects as YAML
- Identical evaluation performance
- Same caching, parallel evaluation benefits

### 9.3 Memory Footprint

- Slightly higher at build time (Kotlin closures)
- Identical runtime footprint (same Java objects)
- Property delegates add minimal overhead

---

## 10. Recommendation

### When to Use Kotlin DSL

- Complex specifications with many criteria
- Specifications requiring conditional logic
- Teams with Kotlin expertise
- Specifications that need extensive testing
- Reusable patterns across multiple specs

### When to Use YAML

- Simple, static specifications
- Non-developer stakeholders need to edit
- Runtime configuration changes required
- Specifications shared with non-JVM systems
- Quick prototyping without compilation

### Hybrid Approach

Consider a hybrid approach where:
1. Core business rules use Kotlin DSL (type-safe, testable)
2. Runtime overrides use YAML (hot-reloadable)
3. DSL defines base spec, YAML extends/modifies

```kotlin
// Base specification in DSL
val baseSpec = specification("loan-base") {
    val ageCheck by criterion("age") { "age" gte 18 }
    val creditCheck by criterion("credit") { "score" gt 650 }
    composite("eligibility") {
        and { +ageCheck; +creditCheck }
    }
}

// Load runtime overrides from YAML
val overrides = YamlLoader.loadOverrides("runtime-config.yaml")

// Merge
val finalSpec = baseSpec.merge(overrides)
```

---

## 11. Conclusion

A Kotlin DSL for JSpec would provide significant benefits for teams requiring:
- Compile-time safety and validation
- IDE-assisted specification authoring
- Testable, reusable criterion patterns
- Dynamic specification building

The DSL should complement rather than replace YAML, allowing teams to choose the right tool for each use case. The implementation leverages Kotlin's excellent DSL support while maintaining full compatibility with the existing evaluation engine.

### Next Steps

1. **Approval**: Review and approve this proposal
2. **Prototype**: Implement core DSL with basic operators
3. **Validation**: Test with real-world specifications
4. **Iteration**: Refine syntax based on feedback
5. **Documentation**: Create user guide and examples
6. **Release**: Publish as optional Kotlin module

---

## Appendix A: Full Operator Reference

| Operator | DSL Syntax | YAML Equivalent |
|----------|------------|-----------------|
| `$eq` | `"field" eq value` | `field: {$eq: value}` |
| `$ne` | `"field" ne value` | `field: {$ne: value}` |
| `$gt` | `"field" gt 10` | `field: {$gt: 10}` |
| `$gte` | `"field" gte 10` | `field: {$gte: 10}` |
| `$lt` | `"field" lt 10` | `field: {$lt: 10}` |
| `$lte` | `"field" lte 10` | `field: {$lte: 10}` |
| `$in` | `"field" inList listOf(...)` | `field: {$in: [...]}` |
| `$nin` | `"field" notIn listOf(...)` | `field: {$nin: [...]}` |
| `$all` | `"field" all listOf(...)` | `field: {$all: [...]}` |
| `$size` | `"field" size 3` | `field: {$size: 3}` |
| `$exists` | `"field" exists true` | `field: {$exists: true}` |
| `$type` | `"field" type "string"` | `field: {$type: string}` |
| `$regex` | `"field" regex "^..."` | `field: {$regex: "^..."}` |
| `$elemMatch` | `"field" elemMatch { ... }` | `field: {$elemMatch: {...}}` |

---

## Appendix B: DSL Grammar Summary

```
specification      := "specification" "(" id ")" "{" criteria* "}"
criteria           := criterion | composite
criterion          := "val"? id "by"? "criterion" "(" id ")" "{" conditions "}"
composite          := "val"? id "by"? "composite" "(" id ")" "{" junction "}"
conditions         := condition+
condition          := field operator value
junction           := ("and" | "or") "{" references "}"
references         := ("+criterion" | "ref(id)" | inline-criterion)+
inline-criterion   := "criterion" "(" id ")" "{" conditions "}"
field              := string (dot-notation path)
operator           := "eq" | "ne" | "gt" | "gte" | "lt" | "lte" |
                      "inList" | "notIn" | "all" | "size" |
                      "exists" | "type" | "regex" | "elemMatch"
```

---

*Document Version: 1.0*
*Created: 2025-11-19*
*Status: Proposal*
