# JSON Rules Engine

A lightweight, Spring-independent Java library for evaluating business rules against JSON/YAML documents using MongoDB-style query operators.

## Features

- **13 MongoDB-style operators** - Familiar query syntax for developers
- **Tri-state evaluation model** - Distinguishes between MATCHED, NOT_MATCHED, and UNDETERMINED states
- **Graceful error handling** - One failed rule never stops evaluation of others
- **Zero framework dependencies** - Works with or without Spring
- **Thread-safe parallel evaluation** - Efficient processing of multiple rules
- **Deep document navigation** - Query nested structures with dot notation
- **Java 21** - Modern language features with record-based immutable design

## Quick Start

### Installation

Add to your `pom.xml`:

```xml
<dependency>
    <groupId>uk.codery</groupId>
    <artifactId>rules</artifactId>
    <version>0.0.1-SNAPSHOT</version>
</dependency>
```

### Basic Usage

```java
import uk.codery.rules.*;

// Define your rules
Rule ageCheck = new Rule("age-check",
    Map.of("age", Map.of("$gte", 18)));

Rule statusCheck = new Rule("status-check",
    Map.of("status", Map.of("$eq", "ACTIVE")));

// Create specification
Specification spec = new Specification(
    "eligibility-check",
    List.of(ageCheck, statusCheck),
    List.of()
);

// Evaluate against document
Map<String, Object> document = Map.of(
    "age", 25,
    "status", "ACTIVE"
);

SpecificationEvaluator evaluator = new SpecificationEvaluator();
EvaluationOutcome outcome = evaluator.evaluate(document, spec);

// Check results
for (EvaluationResult result : outcome.ruleResults()) {
    System.out.println(result.rule().id() + ": " +
        (result.matched() ? "MATCHED" : "NOT MATCHED"));
}
```

### Using YAML Specifications

```yaml
# specification.yaml
id: employment-eligibility
rules:
  - id: uc-active
    query:
      benefits:
        universal_credit:
          status: ACTIVE
  - id: time-on-uc
    query:
      benefits:
        universal_credit:
          duration_months:
            $gte: 6
ruleSets:
  - id: restart-program
    operator: AND
    rules: [uc-active, time-on-uc]
```

```java
// Load specification
ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
Specification spec = mapper.readValue(
    new File("specification.yaml"),
    Specification.class
);
```

## Supported Operators

### Comparison Operators

- `$eq` - Equal to
- `$ne` - Not equal to
- `$gt` - Greater than
- `$gte` - Greater than or equal to
- `$lt` - Less than
- `$lte` - Less than or equal to

```java
// Age must be 18 or older
Map.of("age", Map.of("$gte", 18))
```

### Collection Operators

- `$in` - Value in list
- `$nin` - Value not in list
- `$all` - Array contains all specified elements
- `$size` - Array size equals value

```java
// Status must be one of ACTIVE, PENDING
Map.of("status", Map.of("$in", List.of("ACTIVE", "PENDING")))

// Tags array must contain all of: urgent, verified
Map.of("tags", Map.of("$all", List.of("urgent", "verified")))
```

### Advanced Operators

- `$exists` - Field exists (boolean check)
- `$type` - Value is of specified type
- `$regex` - String matches regular expression
- `$elemMatch` - Array element matches sub-query

```java
// Email field must exist
Map.of("email", Map.of("$exists", true))

// Name must match pattern
Map.of("name", Map.of("$regex", "^[A-Z].*"))

// At least one address must be primary
Map.of("addresses", Map.of("$elemMatch",
    Map.of("isPrimary", Map.of("$eq", true))))
```

### Supported Types for `$type` operator

- `null`, `array`, `string`, `number`, `boolean`, `object`

```java
// Age must be a number
Map.of("age", Map.of("$type", "number"))
```

## Tri-State Evaluation Model

The engine uses a three-state evaluation model for robust error handling:

### States

1. **MATCHED** - Rule evaluated successfully, condition is TRUE
2. **NOT_MATCHED** - Rule evaluated successfully, condition is FALSE
3. **UNDETERMINED** - Rule could not be evaluated

### When Rules Are UNDETERMINED

Rules become UNDETERMINED when:
- Required data is missing from the document
- Unknown operator is used
- Type mismatch occurs (e.g., string provided where number expected)
- Invalid regex pattern
- Any evaluation error

```java
EvaluationResult result = evaluator.evaluateRule(document, rule);

switch (result.state()) {
    case MATCHED -> System.out.println("Rule passed");
    case NOT_MATCHED -> System.out.println("Rule failed");
    case UNDETERMINED -> {
        System.out.println("Could not evaluate: " + result.reason());
        // Check missing paths
        if (!result.missingPaths().isEmpty()) {
            System.out.println("Missing data at: " + result.missingPaths());
        }
    }
}
```

## Rule Sets

Combine multiple rules with AND/OR logic:

```java
Rule rule1 = new Rule("r1", Map.of("age", Map.of("$gte", 18)));
Rule rule2 = new Rule("r2", Map.of("status", Map.of("$eq", "ACTIVE")));

// Both rules must match (AND)
RuleSet andSet = new RuleSet(
    "adult-and-active",
    Operator.AND,
    List.of("r1", "r2")
);

// Either rule must match (OR)
RuleSet orSet = new RuleSet(
    "adult-or-active",
    Operator.OR,
    List.of("r1", "r2")
);

Specification spec = new Specification(
    "eligibility",
    List.of(rule1, rule2),
    List.of(andSet, orSet)
);
```

## Nested Document Queries

Use dot notation to query nested structures:

```java
Map<String, Object> document = Map.of(
    "user", Map.of(
        "profile", Map.of(
            "age", 25,
            "address", Map.of(
                "city", "London"
            )
        )
    )
);

// Query nested field
Rule rule = new Rule("city-check",
    Map.of("user.profile.address.city", Map.of("$eq", "London"))
);
```

## Evaluation Summary

Get statistics about evaluation outcomes:

```java
EvaluationOutcome outcome = evaluator.evaluate(document, spec);
EvaluationSummary summary = outcome.summary();

System.out.println("Total rules: " + summary.totalRules());
System.out.println("Matched: " + summary.matchedRules());
System.out.println("Not matched: " + summary.notMatchedRules());
System.out.println("Undetermined: " + summary.undeterminedRules());
System.out.println("Fully determined: " + summary.fullyDetermined());
```

## Thread Safety

The engine is thread-safe and uses parallel evaluation by default:

```java
// Rules are evaluated in parallel using parallel streams
SpecificationEvaluator evaluator = new SpecificationEvaluator();

// Safe to use from multiple threads
ExecutorService executor = Executors.newFixedThreadPool(10);
for (int i = 0; i < 100; i++) {
    executor.submit(() -> {
        EvaluationOutcome outcome = evaluator.evaluate(document, spec);
        // Process outcome
    });
}
```

## Error Handling Philosophy

The engine follows a **graceful degradation** approach:

- Rules never throw exceptions that halt evaluation
- One bad rule never prevents evaluation of other rules
- All failures are logged via SLF4J for debugging
- Missing data results in UNDETERMINED state, not errors
- Partial evaluation is clearly indicated in results

This design ensures that:
- Specifications are evaluated completely even with data issues
- You can identify which rules couldn't be evaluated
- The system degrades gracefully rather than failing hard

## Building from Source

```bash
# Clone repository
git clone https://github.com/thekitchencoder/json-rules.git
cd json-rules

# Build with Maven
mvn clean install

# Run tests
mvn test
```

## Requirements

- Java 21 or higher
- Maven 3.6+ (for building)

## Dependencies

- **Jackson DataFormat YAML** (2.20.0) - JSON/YAML parsing
- **Lombok** (1.18.42) - Boilerplate reduction
- **SLF4J API** (2.0.9) - Logging facade

## Demo

Run the demo CLI to see the engine in action:

```bash
mvn test-compile exec:java -Dexec.mainClass="uk.codery.rules.demo.Main"
```

This evaluates sample specifications against test documents using both JSON and YAML formats.

## Architecture

The engine has a clean three-layer architecture:

1. **Data Layer** - Immutable records for specifications, rules, and documents
2. **Evaluation Layer** - `RuleEvaluator` (operators) and `SpecificationEvaluator` (orchestration)
3. **Result Layer** - Tri-state results with detailed failure reasons

Key design principles:
- Immutable record-based design
- No mutable state
- Thread-safe by default
- Fail gracefully, never hard

## Use Cases

- **Benefits eligibility** - Determine program qualification based on citizen data
- **Business rule validation** - Enforce complex business rules on documents
- **Policy enforcement** - Check compliance against defined policies
- **Dynamic filtering** - Filter data based on user-defined criteria
- **Form validation** - Validate complex forms with interdependent rules

## Project Status

Current version: **0.0.1-SNAPSHOT**

Recently implemented:
- Tri-state evaluation model with UNDETERMINED state
- Graceful error handling for all edge cases
- SLF4J logging integration
- Comprehensive evaluation summary tracking

See [IMPROVEMENT_ROADMAP.md](IMPROVEMENT_ROADMAP.md) for planned enhancements.

## Documentation

- [ERROR_HANDLING_DESIGN.md](ERROR_HANDLING_DESIGN.md) - Detailed error handling approach
- [IMPROVEMENT_ROADMAP.md](IMPROVEMENT_ROADMAP.md) - Future enhancements and development priorities
- [TODO.md](TODO.md) - Current task list
- [CLAUDE.md](CLAUDE.md) - AI assistant context for development

## Contributing

Contributions are welcome! Please see [CONTRIBUTING.md](CONTRIBUTING.md) for guidelines.

## License

This project is licensed under the MIT License - see [LICENSE](LICENSE) file for details.

## Support

For issues, questions, or suggestions:
- Open an issue on GitHub
- Review existing documentation
- Check the demo examples in `src/test/java/uk/codery/rules/demo/`

## Acknowledgments

- Operator design inspired by MongoDB query language
- Tri-state evaluation model based on graceful degradation principles
- Built with modern Java 21 features for clean, maintainable code
