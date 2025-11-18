# JSPEC

[![Maven Central](https://img.shields.io/maven-central/v/uk.codery/jspec.svg?label=Maven%20Central)](https://search.maven.org/search?q=g:%22uk.codery%22%20AND%20a:%22jspec%22)
[![CI](https://github.com/thekitchencoder/jspec/workflows/CI/badge.svg)](https://github.com/thekitchencoder/jspec/actions)
[![codecov](https://codecov.io/gh/thekitchencoder/jspec/branch/main/graph/badge.svg)](https://codecov.io/gh/thekitchencoder/jspec)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![FOSSA Status](https://app.fossa.com/api/projects/git%2Bgithub.com%2Fthekitchencoder%2Fjspec.svg?type=shield&issueType=license)](https://app.fossa.com/projects/git%2Bgithub.com%2Fthekitchencoder%2Fjspec?ref=badge_shield&issueType=license)
[![FOSSA Status](https://app.fossa.com/api/projects/git%2Bgithub.com%2Fthekitchencoder%2Fjspec.svg?type=shield&issueType=security)](https://app.fossa.com/projects/git%2Bgithub.com%2Fthekitchencoder%2Fjspec?ref=badge_shield&issueType=security)
[![Java](https://img.shields.io/badge/Java-21+-blue.svg)](https://openjdk.org/projects/jdk/21/)
[![Javadoc](https://javadoc.io/badge2/uk.codery/jspec/javadoc.svg)](https://javadoc.io/doc/uk.codery/jspec)

JSPEC (JSON Specification Evaluator) is a lightweight, Spring-independent Java library for evaluating business criteria against JSON/YAML documents with MongoDB-style operators.

## Features

- **14 MongoDB-style operators** – Reuse familiar query syntax for comparison, collection, and advanced checks.
- **Tri-state evaluation** – Every criterion reports `MATCHED`, `NOT_MATCHED`, or `UNDETERMINED` for precise diagnostics.
- **Graceful error handling** – Bad data, unknown operators, or type mismatches never halt the evaluation pipeline.
- **Thread-safe by design** – Immutable records and parallel execution make large specifications fast and safe.
- **Deep document navigation** – Dot notation walks arbitrarily nested JSON/YAML payloads.
- **Zero framework dependencies** – Works in plain Java applications or alongside Spring.
- **Java 21 foundation** – Modern records, switch expressions, and immutable collections throughout.

## Quick Start

### Installation

Add the dependency to your `pom.xml`:

```xml
<dependency>
    <groupId>uk.codery</groupId>
    <artifactId>jspec</artifactId>
    <version>0.4.1</version>
</dependency>
```

### Basic Usage

1. **Define a `Specification`** containing your criteria. You can write it as YAML/JSON or construct it programmatically.

    ```yaml
    # specification.yaml
    id: order-validation
    criteria:
      - id: minimum-order
        query:
          order.total:
            $gte: 25.00
      - id: customer-verified
        query:
          customer.verified: true
    criteriaGroups:
      - id: express-shipping-eligible
        junction: AND
        criteria: [minimum-order, customer-verified]
    ```

2. **Instantiate the evaluator** and inspect the tri-state results.

    ```java
    import com.fasterxml.jackson.databind.ObjectMapper;
    import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
    import uk.codery.jspec.evaluator.SpecificationEvaluator;
    import uk.codery.jspec.model.Specification;
    import uk.codery.jspec.result.EvaluationOutcome;

    ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
    Specification spec = mapper.readValue(new File("specification.yaml"), Specification.class);

    SpecificationEvaluator evaluator = new SpecificationEvaluator(spec);

    Map<String, Object> document = Map.of(
        "order", Map.of("total", 50.00),
        "customer", Map.of("verified", true)
    );

    EvaluationOutcome outcome = evaluator.evaluate(document);

    System.out.println("Summary: " + outcome.summary());
    outcome.results().forEach(result ->
        System.out.printf(" - %s -> %s%n", result.id(), result.state())
    );
    ```

## Documentation

For deeper dives, read the docs in `docs/`:

- **[Supported Operators](docs/OPERATORS.md)** – Reference for all 14 MongoDB-style operators.
- **[Evaluation Model](docs/EVALUATION_MODEL.md)** – How the tri-state pipeline surfaces errors without throwing.
- **[Architecture](docs/ARCHITECTURE.md)** – Core records, evaluators, and thread-safety guidance.
- **[General Use Cases](docs/usecases.md)** – Practical scenarios for embedding JSPEC.
- **[AI Use Cases](docs/AI_USECASES.md)** – Synthesized guidance for guardrails, hybrid-symbolic patterns, and agent routing.
- **[JavaDoc](https://javadoc.io/doc/uk.codery/jspec)** – Full API reference.

## Building from Source

To build the project locally:

```bash
# Clone the repository
git clone https://github.com/thekitchencoder/jspec.git
cd jspec

# Compile and run the full suite
mvn clean verify

# Fast feedback while iterating
mvn test
```

### Requirements
- Java 21 or higher
- Maven 3.6+

### Dependencies
- **Jackson DataFormat YAML** – JSON/YAML parsing for specifications and documents.
- **Lombok** – Annotation-driven boilerplate reduction during compilation.
- **SLF4J API** – Logging abstraction; bring your own binding.

## Demo

To run the demo application and see the engine in action:

```bash
mvn test-compile exec:java -Dexec.mainClass="uk.codery.jspec.demo.Main"
```

## Contributing

Contributions are welcome! Please see [CONTRIBUTING.md](CONTRIBUTING.md) for guidelines.

## License

This project is licensed under the MIT License. See the [LICENSE](LICENSE) file for details.
