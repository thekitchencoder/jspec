package uk.codery.jspec.evaluator;

import lombok.extern.slf4j.Slf4j;
import uk.codery.jspec.model.CompositeCriterion;
import uk.codery.jspec.model.ContextPathReference;
import uk.codery.jspec.model.Criterion;
import uk.codery.jspec.model.QueryCriterion;
import uk.codery.jspec.model.Specification;
import uk.codery.jspec.model.SpecificationNormaliser;
import uk.codery.jspec.result.EvaluationOutcome;
import uk.codery.jspec.result.EvaluationResult;
import uk.codery.jspec.result.EvaluationSummary;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
 *   <li><b>Parallel Query Evaluation:</b> Query criteria are evaluated concurrently using
 *       parallel streams; composite/reference evaluation runs sequentially for cycle-safety</li>
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
public final class SpecificationEvaluator {

    private final Specification specification;
    private final CriterionEvaluator criterionEvaluator;
    private final Map<String, Criterion> criterionIndex;

    /**
     * Canonical constructor that normalises the bound specification's query
     * criteria, replacing raw {@code { "$contextPath": "..." }} maps with typed
     * {@link uk.codery.jspec.model.ContextPathReference} instances. This shifts
     * sentinel detection out of the per-evaluation hot path. The criterion index
     * (id → definition, used for on-demand reference resolution) is also built once
     * here, since the bound specification is immutable and the index never changes
     * between {@code evaluate} calls.
     *
     * @param specification the specification to bind (normalised before storing)
     * @param criterionEvaluator the criterion evaluator to use for query evaluation
     */
    public SpecificationEvaluator(Specification specification, CriterionEvaluator criterionEvaluator) {
        this.specification = normalise(specification);
        this.criterionEvaluator = criterionEvaluator;
        this.criterionIndex = buildCriterionIndex(this.specification.criteria());
    }

    /**
     * Creates a SpecificationEvaluator with default built-in operators.
     *
     * <p>This is the recommended constructor for most use cases.
     * It creates an internal {@link CriterionEvaluator} with all 23 built-in
     * query operators.
     *
     * @param specification the specification to evaluate documents against
     * @see #SpecificationEvaluator(Specification, CriterionEvaluator)
     */
    public SpecificationEvaluator(Specification specification) {
        this(specification, new CriterionEvaluator());
    }

    /**
     * Returns the bound specification in its <em>normalised</em> form — this is not
     * the identical object passed to the constructor.
     *
     * <p>Each {@code { "$contextPath": "..." }} operand literal has been replaced with a
     * typed {@link ContextPathReference}, so in memory the affected
     * {@link QueryCriterion#query()} maps hold {@code ContextPathReference} values where
     * the original held raw {@code Map<String, String>} sentinels. Callers that inspect
     * operand types directly should expect this.
     *
     * <p>Serialisation is lossless: {@code ContextPathReference} round-trips back to the
     * {@code { "$contextPath": "..." }} shape (see {@code ContextPathReferenceRoundTripTest}),
     * and re-binding via {@code new SpecificationEvaluator(evaluator.specification())} is
     * idempotent because normalisation leaves already-typed references untouched.
     *
     * @return the normalised specification bound to this evaluator
     */
    public Specification specification() {
        return specification;
    }

    /**
     * Returns the criterion evaluator bound to this specification evaluator.
     *
     * @return the evaluator used for query criterion evaluation
     */
    public CriterionEvaluator criterionEvaluator() {
        return criterionEvaluator;
    }

    private static Specification normalise(Specification spec) {
        List<Criterion> criteria = spec.criteria().stream()
                .map(SpecificationEvaluator::normaliseCriterion)
                .toList();
        return new Specification(spec.id(), criteria);
    }

    private static Criterion normaliseCriterion(Criterion c) {
        if (c instanceof QueryCriterion q) {
            @SuppressWarnings("unchecked")
            Map<String, Object> normalised =
                    (Map<String, Object>) SpecificationNormaliser.normalise(q.query());
            return new QueryCriterion(q.id(), normalised);
        }
        return c; // CompositeCriterion / CriterionReference contain no operand literals
    }

    /**
     * Builds an index of criterion id → criterion definition, walking recursively into
     * {@link CompositeCriterion#criteria()} so nested composites are indexed too. Only
     * {@link QueryCriterion} and {@link CompositeCriterion} are indexed; references are
     * skipped (their id equals their target id, so indexing them would be meaningless
     * and could shadow the real definition).
     *
     * @param criteria the (normalised) top-level criteria
     * @return a fresh map of indexable criteria by id
     */
    private static Map<String, Criterion> buildCriterionIndex(List<Criterion> criteria) {
        Map<String, Criterion> index = new HashMap<>();
        indexCriteria(criteria, index);
        return index;
    }

    private static void indexCriteria(List<Criterion> criteria, Map<String, Criterion> index) {
        for (Criterion c : criteria) {
            if (c instanceof QueryCriterion) {
                index.putIfAbsent(c.id(), c);
            } else if (c instanceof CompositeCriterion composite) {
                index.putIfAbsent(composite.id(), composite);
                indexCriteria(composite.criteria(), index);
            }
            // CriterionReference: skip (id equals target id)
        }
    }

    /**
     * Evaluates the bound specification against a document.
     *
     * <p>This method:
     * <ol>
     *   <li>Creates an {@link EvaluationContext} for result caching</li>
     *   <li>Evaluates query criteria in parallel, then composites/references sequentially
     *       (uses cache for references)</li>
     *   <li>Collects all results from the cache</li>
     *   <li>Generates summary statistics</li>
     *   <li>Returns comprehensive evaluation outcome</li>
     * </ol>
     *
     * <h3>Evaluation Process:</h3>
     * <ul>
     *   <li><b>Parallel Query Evaluation:</b> Query criteria evaluated concurrently;
     *       composites/references evaluated sequentially for reference-cycle safety</li>
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
        return evaluate(document, Map.of());
    }

    /**
     * Evaluates the bound specification against a target document, with a separate
     * context document supplied for resolving {@code $contextPath} operand references.
     *
     * <p>This is the two-arg form of {@link #evaluate(Object)}. The single-arg form
     * delegates to this method with an empty context document ({@code Map.of()}).
     *
     * <h3>Context References</h3>
     * <p>When a criterion's operand contains a {@code { "$contextPath": "a.b.c" }}
     * sentinel (normalised to a {@link uk.codery.jspec.model.ContextPathReference} at
     * construction time), the path is resolved against {@code contextDoc} — not against
     * the target {@code document}. This separates the data being evaluated from the
     * values used to parameterise the criteria.
     *
     * <h3>Example:</h3>
     * <pre>{@code
     * Specification spec = new Specification("same-email", List.of(
     *     new QueryCriterion("match",
     *         Map.of("email", Map.of("$eq", Map.of("$contextPath", "candidate.email"))))
     * ));
     * SpecificationEvaluator evaluator = new SpecificationEvaluator(spec);
     *
     * Map<String, Object> target  = Map.of("email", "a@b.com");
     * Map<String, Object> context = Map.of("candidate", Map.of("email", "a@b.com"));
     *
     * EvaluationOutcome outcome = evaluator.evaluate(target, context);
     * // outcome.summary().matched() == 1
     * }</pre>
     *
     * <h3>Thread Safety:</h3>
     * <p>This method is thread-safe and can be called concurrently from multiple threads.
     *
     * @param document the target document to evaluate (typically a Map, but can be any Object)
     * @param contextDoc the context document used to resolve {@code $contextPath} operand
     *                   references; pass {@code Map.of()} when no context references are used
     * @return evaluation outcome with results and summary
     * @see #evaluate(Object)
     * @see EvaluationContext
     * @see uk.codery.jspec.model.ContextPathReference
     */
    public EvaluationOutcome evaluate(Object document, Object contextDoc) {
        log.info("Starting evaluation of specification '{}'", specification.id());

        // Create evaluation context with result cache, context document, and the
        // pre-computed criterion index (built once at construction).
        EvaluationContext context = new EvaluationContext(criterionEvaluator, contextDoc, criterionIndex);

        List<EvaluationResult> results;
        try {
            // The context handles caching and reference resolution.

            // Phase 1 (queries) carries the parallel workload — queries never trigger
            // on-demand reference resolution, so there is no cross-thread cycle hazard here.
            specification.criteria().parallelStream().filter(QueryCriterion.class::isInstance)
                    .forEach(criterion -> context.getOrEvaluate(criterion, document));
            // Phase 2 is intentionally SEQUENTIAL: composite/reference evaluation can trigger
            // on-demand resolution of referenced criteria, and the reference-cycle guard is
            // per-thread. Running this phase in parallel allows two mutually-referencing
            // top-level composites to deadlock on ConcurrentHashMap.computeIfAbsent bin locks
            // across threads. Phase 1 (queries) carries the parallel workload; phase 2 is cheap
            // orchestration over already-cached results.
            specification.criteria().stream().filter(not(QueryCriterion.class::isInstance))
                    .forEach(criterion -> context.getOrEvaluate(criterion, document));

            log.debug("Evaluated {} criteria for specification '{}'",
                    context.cacheSize(), specification.id());

            // Collect all results from cache
            results = List.copyOf(context.getAllResults());
        } finally {
            // Remove the per-thread cycle-guard state from the calling thread so it does
            // not linger on pooled threads (phase 2 runs on the calling thread; only it
            // touches the guard).
            context.clearThreadCycleState();
        }

        // Generate summary from results
        EvaluationSummary summary = EvaluationSummary.from(results);

        log.info("Completed evaluation of specification '{}' - Total: {}, Matched: {}, Not Matched: {}, Undetermined: {}, Fully Determined: {}",
                specification.id(), summary.total(), summary.matched(),
                summary.notMatched(), summary.undetermined(), summary.fullyDetermined());

        return new EvaluationOutcome(specification.id(), results, summary);
    }
}
