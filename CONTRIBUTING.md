# Contributing to JSON Specification Evalutor

Thank you for considering contributing to the JSON Specification Evalutor! This document provides guidelines and information for contributors.

## Table of Contents

- [Code of Conduct](#code-of-conduct)
- [Getting Started](#getting-started)
- [Development Setup](#development-setup)
- [How to Contribute](#how-to-contribute)
- [Coding Standards](#coding-standards)
- [Testing Guidelines](#testing-guidelines)
- [Submitting Changes](#submitting-changes)
- [Reporting Bugs](#reporting-bugs)
- [Suggesting Enhancements](#suggesting-enhancements)

## Code of Conduct

### Our Standards

- Be respectful and inclusive
- Focus on constructive feedback
- Accept criticism gracefully
- Prioritize the project and community over personal agendas

### Unacceptable Behavior

- Harassment or discriminatory language
- Trolling or inflammatory comments
- Personal attacks
- Publishing others' private information

## Getting Started

1. Fork the repository on GitHub
2. Clone your fork locally
3. Set up the development environment
4. Create a feature branch
5. Make your changes
6. Submit a pull request

## Development Setup

### Requirements

- **Java 21** or higher
- **Maven 3.6+**
- Git
- IDE with Java 21 support (IntelliJ IDEA, Eclipse, VS Code)

### Initial Setup

```bash
# Clone your fork
git clone https://github.com/YOUR_USERNAME/jspec.git
cd jspec

# Add upstream remote
git remote add upstream https://github.com/thekitchencoder/jspec.git

# Install dependencies and build
mvn clean install

# Run tests to verify setup
mvn test
```

### IDE Configuration

#### IntelliJ IDEA

1. Import as Maven project
2. Enable annotation processing for Lombok:
   - Settings → Build, Execution, Deployment → Compiler → Annotation Processors
   - Check "Enable annotation processing"
3. Install Lombok plugin if not already installed
4. Set project SDK to Java 21

#### Eclipse

1. Import as Maven project
2. Install Lombok:
   - Download lombok.jar
   - Run `java -jar lombok.jar`
   - Select Eclipse installation
3. Set Java compliance to 21

#### VS Code

1. Install Java Extension Pack
2. Install Lombok Annotations Support extension
3. Ensure Java 21 is configured in settings

## How to Contribute

### Types of Contributions

We welcome various types of contributions:

- **Bug fixes** - Fix existing issues
- **New features** - Add operators, enhance functionality
- **Documentation** - Improve docs, add examples
- **Tests** - Increase test coverage
- **Performance** - Optimize existing code
- **Examples** - Add usage examples

### Contribution Workflow

1. **Check existing issues** - See if someone else is working on it
2. **Create an issue** - Discuss significant changes before implementing
3. **Fork and branch** - Create a feature branch from `main`
4. **Implement changes** - Follow coding standards
5. **Write tests** - Add tests for new functionality
6. **Update documentation** - Update relevant docs
7. **Submit PR** - Create pull request with clear description

## Coding Standards

### Java Style Guide

Follow standard Java conventions with these specifics:

#### Formatting

- **Indentation**: 4 spaces (no tabs)
- **Line length**: 120 characters maximum
- **Braces**: K&R style (opening brace on same line)
- **Imports**: No wildcards, organize logically

```java
// Good
public class Example {
    private final String field;

    public Example(String field) {
        this.field = field;
    }
}

// Bad
public class Example
{
  private String field;
  public Example(String field){this.field=field;}
}
```

#### Naming Conventions

- **Classes**: PascalCase (`CriterionEvaluator`, `EvaluationResult`)
- **Methods**: camelCase (`evaluateCriterion`, `navigate`)
- **Constants**: UPPER_SNAKE_CASE (`MAX_DEPTH`, `DEFAULT_SIZE`)
- **Variables**: camelCase (`evaluator`, `criterionResult`)
- **Packages**: lowercase (`uk.codery.jspec`)

#### Code Organization

- **One class per file** (except inner classes)
- **Logical grouping** - Related methods together
- **Private first** - Order: fields → constructors → public methods → private methods
- **Immutability** - Prefer immutable data structures

### Design Principles

#### 1. Graceful Degradation

Never throw exceptions during evaluation. Return UNDETERMINED state instead.

```java
// Good
if (!(operand instanceof List)) {
    logger.warn("Type mismatch: expected List, got {}", operand.getClass());
    return false; // Becomes UNDETERMINED
}

// Bad
List<?> list = (List<?>) operand; // May throw ClassCastException
```

#### 2. Immutability

All domain models must be immutable.

```java
// Good
public record Criterion(String id, Map<String, Object> query) {
    public Criterion {
        query = Map.copyOf(query); // Defensive copy
    }
}

// Bad
public class Criterion {
    private String id;
    public void setId(String id) { this.id = id; } // Mutable!
}
```

#### 3. Thread Safety

Code must be thread-safe by default. Avoid mutable shared state.

```java
// Good
private static final Logger logger = LoggerFactory.getLogger(CriterionEvaluator.class);
private final Map<String, OperatorHandler> operators; // Final reference

// Bad
private static int counter = 0; // Mutable shared state
```

#### 4. Logging over Printing

Use SLF4J logger, never System.out/err.

```java
// Good
logger.warn("Unknown operator: {}", operatorName);

// Bad
System.err.println("Unknown operator: " + operatorName);
```

### Java 21 Features

Leverage modern Java features:

- **Records** for immutable data
- **Sealed classes** for restricted hierarchies
- **Pattern matching** where appropriate
- **Text blocks** for multi-line strings
- **Streams** for collection processing

## Testing Guidelines

### Test Coverage Requirements

- **New features**: Must have tests
- **Bug fixes**: Add regression test
- **Target coverage**: Aim for 80%+ line coverage
- **Critical paths**: 100% coverage for evaluation logic

### Test Structure

Use AAA pattern (Arrange, Act, Assert):

```java
@Test
void evaluateCriterion_withMatchingQuery_shouldReturnMatched() {
    // Arrange
    Map<String, Object> document = Map.of("age", 25);
    Criterion criterion = new Criterion("age-check", Map.of("age", Map.of("$gte", 18)));
    CriterionEvaluator evaluator = new CriterionEvaluator();

    // Act
    EvaluationResult result = evaluator.evaluateCriterion(document, criterion);

    // Assert
    assertEquals(EvaluationState.MATCHED, result.state());
    assertTrue(result.matched());
}
```

### Test Categories

#### Unit Tests

Test individual components in isolation:

```java
@Test
void navigate_withDotNotation_shouldTraverseNestedMap() {
    Map<String, Object> doc = Map.of(
        "user", Map.of(
            "profile", Map.of("age", 25)
        )
    );

    Object result = evaluator.navigate(doc, "user.profile.age");

    assertEquals(25, result);
}
```

#### Integration Tests

Test components working together:

```java
@Test
void evaluateSpecification_withMultipleCriteria_shouldEvaluateAll() {
    Specification spec = new Specification(
        "test",
        List.of(criterion1, criterion2, criterion3),
        List.of()
    );

    EvaluationOutcome outcome = evaluator.evaluate(document, spec);

    assertEquals(3, outcome.criterionResults().size());
}
```

#### Edge Case Tests

Test boundary conditions:

```java
@Test
void evaluateCriterion_withNullDocument_shouldReturnUndetermined() {
    // Test null handling
}

@Test
void evaluateCriterion_withEmptyQuery_shouldHandleGracefully() {
    // Test empty input
}

@Test
void evaluateCriterion_withMissingField_shouldReturnUndetermined() {
    // Test missing data
}
```

### Running Tests

```bash
# Run all tests
mvn test

# Run specific test class
mvn test -Dtest=CriterionEvaluatorTest

# Run specific test method
mvn test -Dtest=CriterionEvaluatorTest#testMethodName

# Run with coverage
mvn test jacoco:report

# Run in parallel (faster)
mvn test -T 4
```

## Submitting Changes

### Before Submitting

- [ ] All tests pass (`mvn test`)
- [ ] Code follows style guidelines
- [ ] New tests added for new functionality
- [ ] Documentation updated (README, JavaDoc)
- [ ] Commit messages are clear and descriptive
- [ ] No unnecessary dependencies added
- [ ] Code reviewed locally

### Pull Request Process

1. **Update your fork**:
   ```bash
   git fetch upstream
   git rebase upstream/main
   ```

2. **Create feature branch**:
   ```bash
   git checkout -b feature/your-feature-name
   ```

3. **Make commits**:
   ```bash
   git add .
   git commit -m "feat: add new operator $contains"
   ```

4. **Push to your fork**:
   ```bash
   git push origin feature/your-feature-name
   ```

5. **Open Pull Request** on GitHub with:
   - Clear title describing the change
   - Description of what changed and why
   - Link to related issue (if applicable)
   - Screenshots/examples (if relevant)

### Commit Message Format

Follow conventional commits:

```
<type>(<scope>): <subject>

<body>

<footer>
```

**Types**:
- `feat`: New feature
- `fix`: Bug fix
- `docs`: Documentation changes
- `test`: Adding tests
- `refactor`: Code refactoring
- `perf`: Performance improvement
- `chore`: Build/tooling changes

**Examples**:
```
feat(operators): add $contains operator for string matching

Implements new $contains operator that checks if a string
contains a substring. Useful for partial text matching.

Closes #42
```

```
fix(evaluation): handle null values in $in operator

Previously threw NullPointerException when operand was null.
Now returns UNDETERMINED state with appropriate warning.

Fixes #38
```

### Pull Request Review

PRs will be reviewed for:

- **Correctness** - Does it work as intended?
- **Tests** - Are there adequate tests?
- **Style** - Follows coding standards?
- **Documentation** - Is it documented?
- **Performance** - Any performance concerns?
- **Breaking changes** - Are they necessary and documented?

Reviewers may request changes. Address feedback and push updates.

## Reporting Bugs

### Before Reporting

1. **Check existing issues** - Bug may already be reported
2. **Verify it's a bug** - Not a configuration issue
3. **Test with latest version** - Bug may be fixed

### Bug Report Template

```markdown
## Bug Description
Clear description of the bug

## Steps to Reproduce
1. Step one
2. Step two
3. Step three

## Expected Behavior
What you expected to happen

## Actual Behavior
What actually happened

## Environment
- Java version: 21
- Library version: 0.0.1-SNAPSHOT
- OS: Linux/Mac/Windows

## Code Example
```java
// Minimal code to reproduce
```

## Additional Context
Stack traces, logs, etc.
```

## Suggesting Enhancements

### Enhancement Request Template

```markdown
## Feature Description
Clear description of the proposed feature

## Use Case
Why is this feature needed? What problem does it solve?

## Proposed Solution
How should this feature work?

## Alternatives Considered
What other approaches did you consider?

## Additional Context
Examples, mockups, references, etc.
```

### Enhancement Discussion

- Open an issue to discuss before implementing
- Explain the use case and benefits
- Consider backward compatibility
- Be open to alternative solutions

## Development Priorities

See [IMPROVEMENT_ROADMAP.md](IMPROVEMENT_ROADMAP.md) for current priorities:

1. **Foundation** - Testing, error handling
2. **Extensibility** - Custom operators, public API
3. **Developer Experience** - Builders, caching
4. **Ecosystem** - Documentation, examples

Focus contributions on current priority areas for faster merge.

## Questions?

- **Documentation**: Check [README.md](README.md) and [CLAUDE.md](CLAUDE.md)
- **Architecture**: See [ERROR_HANDLING_DESIGN.md](ERROR_HANDLING_DESIGN.md)
- **Roadmap**: Review [IMPROVEMENT_ROADMAP.md](IMPROVEMENT_ROADMAP.md)
- **Issues**: Open a GitHub issue for discussion

## Recognition

Contributors will be recognized in:
- GitHub contributors list
- CHANGELOG.md (for significant contributions)
- Release notes

Thank you for contributing to JSON Specification Evalutor!
