# JSON Specification Evaluator - Examples

This directory contains simple, standalone examples demonstrating how to use the JSON Specification Evaluator library.

## Available Examples

### 1. Standalone Hello World

**Location**: `standalone/HelloWorld.java`

A simple "Hello World" example that demonstrates the core features:
- Creating a document (as a Map)
- Defining criteria with MongoDB-style operators
- Evaluating the document against criteria
- Reading and interpreting results

**What it does**:
- Creates a simple user document with name, age, city, and status
- Defines three criteria to check:
  - Age must be >= 18 (`$gte` operator)
  - Status must be "ACTIVE" (`$eq` operator)
  - City must be in a list of allowed cities (`$in` operator)
- Evaluates and displays results

**How to run**:

```bash
# Compile the library first
mvn clean install

# Compile and run the example
cd examples/standalone
javac -cp ../../target/jspec-0.1.0-SNAPSHOT.jar HelloWorld.java
java -cp ../../target/jspec-0.1.0-SNAPSHOT.jar:. HelloWorld
```

**Expected output**:
```
=== JSON Specification Evaluator - Hello World ===

Document:
  name: John Doe
  age: 25
  city: London
  status: ACTIVE

Criteria:
  ✓ age-check: age >= 18
  ✓ status-check: status == 'ACTIVE'
  ✓ city-check: city in ['London', 'Paris', 'Berlin']

Results:
  ✓ MATCHED - age-check
  ✓ MATCHED - status-check
  ✓ MATCHED - city-check

Summary:
  Total criteria: 3
  Matched: 3
  Not matched: 0
  Undetermined: 0

🎉 All criteria matched! Document is valid.
```

## More Examples

For more advanced examples, see:

- **Test Suite**: `src/test/java/uk/codery/jspec/` - Comprehensive test coverage showing all operators
- **Demo CLI**: `src/test/java/uk/codery/jspec/demo/Main.java` - Full CLI application with YAML support
- **Custom Operators**: `src/test/java/uk/codery/jspec/OperatorRegistryTest.java` - How to add custom operators
- **Builder API**: Tests showing fluent API for building criteria programmatically

## Supported Operators

The library supports 13 MongoDB-style operators:

**Comparison**: `$eq`, `$ne`, `$gt`, `$gte`, `$lt`, `$lte`
**Collection**: `$in`, `$nin`, `$all`, `$size`
**Advanced**: `$exists`, `$type`, `$regex`, `$elemMatch`

See the main [README.md](../README.md) for detailed documentation on each operator.

## Need Help?

- Read the [main README](../README.md) for comprehensive documentation
- Check [CLAUDE.md](../CLAUDE.md) for codebase architecture
- Review the [test suite](../src/test/java/uk/codery/jspec/) for more examples
- See [ERROR_HANDLING_DESIGN.md](../docs/ERROR_HANDLING_DESIGN.md) for tri-state evaluation model

## Contributing

Want to add more examples? See [CONTRIBUTING.md](../CONTRIBUTING.md) for guidelines.

Ideas for future examples:
- Spring Boot integration example
- Complex nested document evaluation
- Real-world business rule scenarios
- Performance benchmarking examples
