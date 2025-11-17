package uk.codery.jspec.evaluator;

import lombok.extern.slf4j.Slf4j;
import uk.codery.jspec.model.QueryCriterion;
import uk.codery.jspec.model.Specification;
import uk.codery.jspec.result.EvaluationOutcome;
import uk.codery.jspec.result.EvaluationResult;
import uk.codery.jspec.result.EvaluationSummary;

import java.util.List;

import static java.util.function.Predicate.not;

/**
 * Orchestrates the evaluation of a {@link Specification} against documents.
 *
 * <p>The {@code SpecificationEvaluator} is bound to a specific specification at construction
 * time, making it an immutable, thread-safe evaluator for that specification. Multiple
 * evaluator instances can be created for different specifications and used in parallel.
 *
 * <h2>Key Features</h2>
 * <ul>
 *   <li><b>Specification Binding:</b> Each evaluator is bound to a single specification</li>
 *   <li><b>Parallel Evaluation:</b> Criteria are evaluated concurrently using parallel streams</li>
 *   <li><b>Result Caching:</b> Individual criterion results are cached for efficient reference reuse</li>
 *   <li><b>Graceful Degradation:</b> One failed criterion never stops the overall evaluation</li>
 *   <li><b>Comprehensive Results:</b> Returns detailed outcomes with summary statistics</li>
 *   <li><b>Thread-Safe:</b> Safe to use from multiple threads concurrently</li>
 * </ul>
 *
 * <h2>How It Works</h2>
 *
 * <ol>
 *   <li><b>Bind Specification:</b> Specification is provided at construction time</li>
 *   <li><b>Create Context:</b> Initializes an {@link EvaluationContext} with result cache</li>
 *   <li><b>Evaluate Criteria:</b> All criteria are evaluated (uses cache for references)</li>
 *   <li><b>Cache Results:</b> Each criterion's result is stored by ID</li>
 *   <li><b>Generate Summary:</b> Statistics are computed from all results</li>
 *   <li><b>Return Outcome:</b> Complete outcome with results and summary</li>
 * </ol>
 *
 * <h2>Usage Examples</h2>
 *
 * <h3>Basic Evaluation</h3>
 * <pre>{@code
 * // Define specification
 * Specification spec = new Specification("user-validation", List.of(
 *     new QueryCriterion("age-check", Map.of("age", Map.of("$gte", 18))),
 *     new QueryCriterion("status-check", Map.of("status", Map.of("$eq", "active")))
 * ));
 *
 * // Create evaluator bound to specification
 * SpecificationEvaluator evaluator = new SpecificationEvaluator(spec);
 *
 * // Define document
 * Map<String, Object> document = Map.of(
 *     "age", 25,
 *     "status", "active",
 *     "email", "user@example.com"
 * );
 *
 * // Evaluate document against specification
 * EvaluationOutcome outcome = evaluator.evaluate(document);
 *
 * System.out.println("Matched: " + outcome.summary().matched());
 * System.out.println("Total: " + outcome.summary().total());
 * }</pre>
 *
 * <h3>Parallel Evaluation of Multiple Specifications</h3>
 * <pre>{@code
 * // Create evaluators for different specifications
 * List<SpecificationEvaluator> evaluators = specifications.stream()
 *     .map(SpecificationEvaluator::new)
 *     .toList();
 *
 * // Evaluate same document against all specifications in parallel
 * List<EvaluationOutcome> outcomes = evaluators.parallelStream()
 *     .map(evaluator -> evaluator.evaluate(document))
 *     .toList();
 * }</pre>
 *
 * <h3>With Composition and References</h3>
 * <pre>{@code
 * Specification spec = new Specification("complex", List.of(
 *     // Define base criteria (evaluated once)
 *     new QueryCriterion("age-check", Map.of("age", Map.of("$gte", 18))),
 *     new QueryCriterion("status-check", Map.of("status", Map.of("$eq", "active"))),
 *
 *     // Composite using references (reuses cached results)
 *     new CompositeCriterion("eligibility", Junction.AND, List.of(
 *         new CriterionReference("age-check"),     // Uses cached result
 *         new CriterionReference("status-check")   // Uses cached result
 *     ))
 * ));
 *
 * SpecificationEvaluator evaluator = new SpecificationEvaluator(spec);
 * EvaluationOutcome outcome = evaluator.evaluate(document);
 *
 * // Base criteria evaluated once, composite reused results
 * }</pre>
 *
 * <h3>Custom Operators</h3>
 * <pre>{@code
 * // Create registry with custom operators
 * OperatorRegistry registry = OperatorRegistry.withDefaults();
 * registry.register("$length", (value, operand) -> {
 *     return value instanceof String &&
 *            ((String) value).length() == ((Number) operand).intValue();
 * });
 *
 * // Define specification
 * Specification spec = new Specification("name-validation", List.of(
 *     new QueryCriterion("name-length", Map.of("name", Map.of("$length", 5)))
 * ));
 *
 * // Create evaluator with custom registry and specification
 * CriterionEvaluator criterionEvaluator = new CriterionEvaluator(registry);
 * SpecificationEvaluator evaluator = new SpecificationEvaluator(spec, criterionEvaluator);
 *
 * // Now $length operator is available in queries
 * EvaluationOutcome outcome = evaluator.evaluate(document);
 * }</pre>
 *
 * <h2>Thread Safety</h2>
 *
 * <p>This class is thread-safe and immutable:
 * <ul>
 *   <li>Record class with final fields</li>
 *   <li>Specification is immutable and bound at construction</li>
 *   <li>Uses parallel streams (thread-safe operations)</li>
 *   <li>EvaluationContext uses ConcurrentHashMap</li>
 *   <li>No mutable shared state</li>
 *   <li>Safe to share across threads and evaluate multiple documents concurrently</li>
 * </ul>
 *
 * @param specification the specification to evaluate documents against
 * @param criterionEvaluator the criterion evaluator to use for query evaluation
 * @see Specification
 * @see CriterionEvaluator
 * @see EvaluationContext
 * @see EvaluationOutcome
 * @since 0.3.0
 */
@Slf4j
public record SpecificationEvaluator(Specification specification, CriterionEvaluator criterionEvaluator) {

    /**
     * Creates a SpecificationEvaluator with default built-in operators.
     *
     * <p>This is the recommended constructor for most use cases.
     * It creates an internal {@link CriterionEvaluator} with all 13 built-in
     * MongoDB-style operators.
     *
     * @param specification the specification to evaluate documents against
     * @see #SpecificationEvaluator(Specification, CriterionEvaluator)
     */
    public SpecificationEvaluator(Specification specification) {
        this(specification, new CriterionEvaluator());
    }

    /**
     * Evaluates the bound specification against a document.
     *
     * <p>This method:
     * <ol>
     *   <li>Creates an {@link EvaluationContext} for result caching</li>
     *   <li>Evaluates all criteria in parallel (uses cache for references)</li>
     *   <li>Collects all results from the cache</li>
     *   <li>Generates summary statistics</li>
     *   <li>Returns comprehensive evaluation outcome</li>
     * </ol>
     *
     * <h3>Evaluation Process:</h3>
     * <ul>
     *   <li><b>Parallel Evaluation:</b> All criteria evaluated concurrently</li>
     *   <li><b>Result Caching:</b> Results stored in context by criterion ID</li>
     *   <li><b>Reference Reuse:</b> References use cached results (no re-evaluation)</li>
     *   <li><b>Summary Generation:</b> Statistics computed from all results</li>
     * </ul>
     *
     * <h3>Example:</h3>
     * <pre>{@code
     * Specification spec = loadSpecification();
     * SpecificationEvaluator evaluator = new SpecificationEvaluator(spec);
     *
     * Map<String, Object> document = Map.of("age", 25, "status", "active");
     * EvaluationOutcome outcome = evaluator.evaluate(document);
     *
     * System.out.println("Total: " + outcome.summary().total());
     * System.out.println("Matched: " + outcome.summary().matched());
     * System.out.println("Fully Determined: " + outcome.summary().fullyDetermined());
     *
     * // Inspect individual results
     * for (EvaluationResult result : outcome.results()) {
     *     System.out.println(result.id() + ": " + result.state());
     * }
     * }</pre>
     *
     * <h3>Thread Safety:</h3>
     * <p>This method is thread-safe and can be called concurrently from multiple threads
     * to evaluate different documents against the same specification.
     *
     * @param document the document to evaluate (typically a Map, but can be any Object)
     * @return evaluation outcome with results and summary
     * @see EvaluationOutcome
     * @see EvaluationResult
     * @see EvaluationContext
     */
    public EvaluationOutcome evaluate(Object document) {
        log.info("Starting evaluation of specification '{}'", specification.id());

        // Create evaluation context with result cache
        EvaluationContext context = new EvaluationContext(criterionEvaluator);

        // Evaluate all criteria in parallel
        // The context handles caching and reference resolution

        // Simple QueryCriterion first
        specification.criteria().parallelStream().filter(QueryCriterion.class::isInstance)
                .forEach(criterion -> context.getOrEvaluate(criterion, document));
        // Now composite and reference types
        specification.criteria().parallelStream().filter(not(QueryCriterion.class::isInstance))
                .forEach(criterion -> context.getOrEvaluate(criterion, document));

        log.debug("Evaluated {} criteria for specification '{}'",
                context.cacheSize(), specification.id());

        // Collect all results from cache
        List<EvaluationResult> results = List.copyOf(context.getAllResults());

        // Generate summary from results
        EvaluationSummary summary = EvaluationSummary.from(results);

        log.info("Completed evaluation of specification '{}' - Total: {}, Matched: {}, Not Matched: {}, Undetermined: {}, Fully Determined: {}",
                specification.id(), summary.total(), summary.matched(),
                summary.notMatched(), summary.undetermined(), summary.fullyDetermined());

        return new EvaluationOutcome(specification.id(), results, summary);
    }
}
