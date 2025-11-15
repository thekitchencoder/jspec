# Repository Guidelines

## Project Structure & Module Organization
Core engine code lives under `src/main/java/uk/codery/jspec`, with `Specification`, `Criterion`, operator evaluators, and the tri-state `EvaluationOutcome` pipeline. Tests and demos sit in `src/test/java`, while reusable fixtures (JSON/YAML specs, sample documents) live in `src/test/resources`. Skim `README.md`, `ERROR_HANDLING_DESIGN.md`, and `IMPROVEMENT_ROADMAP.md` before altering behavior.

## Build, Test, and Development Commands
- `mvn clean verify` — full build plus unit suite; run before pushing.
- `mvn test` — quickest way to re-run JUnit/AssertJ tests while iterating.
- `mvn test-compile exec:java -Dexec.mainClass="uk.codery.jspec.demo.Main"` — exercises the demo evaluator against bundled specs.
- `mvn -DskipTests package` — produces a local JAR when you only touch docs or metadata (avoid for functional work).
Requires Java 21, Maven 3.6+, and Lombok annotation processing enabled in your IDE.

## Coding Style & Naming Conventions
Follow the Java conventions from `CONTRIBUTING.md`: 4-space indentation, 120-character lines, and K&R braces. Keep records, collections, and operators immutable; prefer factory helpers (`List.of`, `Map.of`) and descriptive ids such as `status-active-criterion`. Package names stay under `uk.codery.jspec.<feature>`, classes end with `*Evaluator`, `*Operator`, or `*Result` to signal their layer. Avoid wildcard imports and favor expressive variable names (`candidate`, `spec`, `documentNode`).

## Testing Guidelines
Write focused JUnit Jupiter tests in the matching `src/test/java/uk/codery/jspec/...` package and assert outcomes with AssertJ’s fluent API. Cover all three evaluation states (MATCHED, NOT_MATCHED, UNDETERMINED) for any new operator or criterion combination, and add resource-backed tests when parsing YAML/JSON fixtures. Run `mvn test` locally; add `-Dtest=ClassNameTest` for targeted runs, and keep deterministic data to ensure parallel evaluation remains thread-safe.

## Commit & Pull Request Guidelines
Use conventional commit prefixes (`feat`, `fix`, `perf`, `chore`, etc.) with an optional scope (`feat(operators): add $between`). Each PR should describe the motivation, link the relevant issue or TODO item, enumerate testing performed, and attach snippets or screenshots when adjusting docs or demo output. Keep commits small, include tests alongside code, and call out any follow-ups needed so maintainers can merge confidently.

## Security & Configuration Tips
Never commit live credentials; sample data belongs in `src/test/resources` or masked YAML files. Keep SLF4J calls structured and avoid logging full documents—redact personally identifiable fields before adding diagnostics. The engine runs safely in parallel threads, but prefer deterministic iterables and immutable maps so agent runs remain reproducible.
