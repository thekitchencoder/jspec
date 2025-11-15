# Gemini Context: JSON Specification Evaluator

This document provides an overview of the `jspec` project to be used as instructional context for Gemini.

## Project Overview

This is a lightweight, dependency-minimal Java library for evaluating business criteria against JSON/YAML documents. It uses a query syntax inspired by MongoDB.

*   **Purpose:** To provide a flexible and robust criteria engine that can be embedded in any Java application without requiring a framework like Spring.
*   **Core Technologies:**
    *   **Language:** Java 21
    *   **Build:** Apache Maven
    *   **Dependencies:**
        *   `jackson-dataformat-yaml`: For parsing JSON and YAML.
        *   `org.projectlombok`: To reduce boilerplate code.
        *   `org.slf4j`: For logging.
*   **Architecture:**
    *   The design is simple, thread-safe, and promotes immutability using Java records.
    *   `SpecificationEvaluator`: The main entry point. It orchestrates the evaluation of a `Specification` against a document. It evaluates criteria in parallel by default using `parallelStream()`.
    *   `CriterionEvaluator`: The core logic engine. It evaluates individual criteria, handles the 13 MongoDB-style operators, and implements the tri-state evaluation model.
    *   **Data Models:** `Specification`, `Criterion`, `CriteriaGroup`, and result objects (`EvaluationOutcome`, `EvaluationResult`) are all immutable Java records.

## Building and Running

The project uses standard Maven commands.

*   **Compile:**
    ```bash
    mvn compile
    ```
*   **Run Tests:**
    ```bash
    mvn test
    ```
*   **Build JAR:**
    ```bash
    mvn package
    ```
*   **Build and Install locally:**
    ```bash
    mvn clean install
    ```
*   **Run Demo Application:**
    ```bash
    mvn test-compile exec:java -Dexec.mainClass="uk.codery.jspec.demo.Main"
    ```

## Development Conventions

*   **Error Handling:** The engine uses a **tri-state evaluation model** (`MATCHED`, `NOT_MATCHED`, `UNDETERMINED`) instead of throwing exceptions for evaluation errors. This "graceful degradation" approach ensures that one bad criterion or missing piece of data doesn't halt the entire evaluation process. Failures are logged via SLF4J.
*   **Immutability:** The core data objects are immutable Java 21 records. Evaluator classes are thread-safe and have no mutable state.
*   **Testing:** The project has a comprehensive test suite.
    *   Unit tests for individual classes (`CriterionEvaluatorTest`, `SpecificationEvaluatorTest`).
    *   Operator-specific tests (`ComparisonOperatorsTest`, etc.).
    *   Integration tests (`EndToEndTest.java`) that simulate real-world use cases like employment eligibility and access control.
*   **Criterion Definition:** Criteria are defined programmatically using nested `Map` objects, mimicking the structure of a JSON query.
*   **Performance:**
    *   `SpecificationEvaluator` uses parallel streams for efficient evaluation of multiple criteria.
    *   `CriterionEvaluator` includes a thread-safe LRU cache for compiled regex patterns to optimize performance of the `$regex` operator.
*   **Extensibility:** The engine is not currently designed for easy extension. The query operators are hardcoded in the `CriterionEvaluator`. The `IMPROVEMENT_ROADMAP.md` identifies this as a key area for future improvement.
