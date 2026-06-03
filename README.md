# JSPEC

[![Maven Central](https://img.shields.io/maven-central/v/uk.codery/jspec.svg?label=Maven%20Central)](https://search.maven.org/search?q=g:%22uk.codery%22%20AND%20a:%22jspec%22)
[![Build and Test](https://github.com/thekitchencoder/jspec/actions/workflows/build.yml/badge.svg)](https://github.com/thekitchencoder/jspec/actions/workflows/build.yml)
[![codecov](https://codecov.io/gh/thekitchencoder/jspec/branch/main/graph/badge.svg)](https://codecov.io/gh/thekitchencoder/jspec)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![FOSSA Status](https://app.fossa.com/api/projects/git%2Bgithub.com%2Fthekitchencoder%2Fjspec.svg?type=shield)](https://app.fossa.com/projects/git%2Bgithub.com%2Fthekitchencoder%2Fjspec?ref=badge_shield)
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
- **Context-document references** – `$contextPath` operand sentinel lets one specification be scored against many context documents.

## Quick Start

### Installation

Add the dependency to your `pom.xml`:

```xml
<dependency>
    <groupId>uk.codery</groupId>
    <artifactId>jspec</artifactId>
    <version>0.5.2</version>
</dependency>
```

### Basic Usage

1. **Define a `Specification`** containing your criteria. You can write it as YAML/JSON or construct it programmatically. Each entry is auto-typed: a `query` map becomes a `QueryCriterion`, a `junction` block becomes a `CompositeCriterion`, and `ref` entries reference previously defined criteria (so they only evaluate once).

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
      - id: express-shipping-eligible
        junction: AND
        criteria:
          - ref: minimum-order
          - ref: customer-verified
    ```

2. **Instantiate the evaluator** and inspect the tri-state results.

    ```java
    import com.fasterxml.jackson.databind.ObjectMapper;
    import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
    import java.io.File;
    import java.util.Map;
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

## Context-Document References

`$contextPath` is an operand sentinel that defers a value lookup to a separately-supplied context document. Reach for it when the same specification needs to be evaluated against many context documents — for example, scoring a single claim against each of several candidate identities — so the criteria stay templated and only the context changes per call.

```java
import java.util.Map;
import uk.codery.jspec.evaluator.SpecificationEvaluator;
import uk.codery.jspec.model.QueryCriterion;
import uk.codery.jspec.model.Specification;
import uk.codery.jspec.result.EvaluationOutcome;

Specification spec = new Specification("same-email", java.util.List.of(
    new QueryCriterion("match",
        Map.of("email", Map.of("$eq", Map.of("$contextPath", "candidate.email"))))));

SpecificationEvaluator evaluator = new SpecificationEvaluator(spec);

Map<String, Object> target  = Map.of("email", "a@b.com");
Map<String, Object> context = Map.of("candidate", Map.of("email", "a@b.com"));

EvaluationOutcome outcome = evaluator.evaluate(target, context);
// outcome.summary().matched() == 1
```

If a `$contextPath` operand fails to resolve, the containing criterion becomes `UNDETERMINED` and the unresolved path is recorded as `context.<path>` in the result's `missingPaths` — consistent with how target-document misses are surfaced.

A path whose final segment is present-but-`null` resolves successfully and yields `null` to the operator (so `$eq: null` works); a path with a missing entry is unresolved and short-circuits to `UNDETERMINED`. Omit a key entirely if you want the criterion to be `UNDETERMINED` rather than compared against `null`.

The sentinel works identically in YAML specifications — just write `$contextPath: candidate.email` wherever an operand value would appear.

## Documentation

For deeper dives, read the docs in `docs/`:

- **[Supported Operators](docs/OPERATORS.md)** – Reference for all 14 MongoDB-style operators.
- **[Evaluation Model](docs/EVALUATION_MODEL.md)** – How the tri-state pipeline surfaces errors without throwing.
- **[Architecture](docs/ARCHITECTURE.md)** – Core records, evaluators, and thread-safety guidance.
- **[General Use Cases](docs/USECASES.md)** – Practical scenarios for embedding JSPEC.
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

This project is licensed under the MIT License - see [LICENSE](LICENSE) file for details.

[![FOSSA Status](https://app.fossa.com/api/projects/git%2Bgithub.com%2Fthekitchencoder%2Fjspec.svg?type=large)](https://app.fossa.com/projects/git%2Bgithub.com%2Fthekitchencoder%2Fjspec?ref=badge_large)

## Support

For issues, questions, or suggestions:
- Open an issue on GitHub
- Review existing documentation
- Check the demo examples in `src/test/java/uk/codery/jspec/demo/`

## Acknowledgments

- Operator design inspired by MongoDB query language
- Tri-state evaluation model based on graceful degradation principles
- Built with modern Java 21 features for clean, maintainable code
