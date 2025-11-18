package uk.codery.jspec.evaluator;

import uk.codery.jspec.model.Criterion;
import uk.codery.jspec.result.EvaluationResult;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Context for criterion evaluation that maintains a result cache.
 *
 * <p>The {@code EvaluationContext} is the core of the "evaluate once, reference many times" pattern.
 * It provides:
 * <ul>
 *   <li><b>Result Caching:</b> Stores evaluation results by criterion ID</li>
 *   <li><b>Cache-Aware Evaluation:</b> Automatically uses cached results when available</li>
 *   <li><b>Thread-Safe:</b> Uses ConcurrentHashMap for parallel evaluation</li>
 *   <li><b>Evaluator Access:</b> Provides access to the CriterionEvaluator</li>
 * </ul>
 *
 * <h2>How It Works</h2>
 *
 * <pre>{@code
 * // 1. Create context with evaluator
 * EvaluationContext context = new EvaluationContext(evaluator);
 *
 * // 2. Evaluate criteria (results automatically cached)
 * EvaluationResult result1 = context.getOrEvaluate(criterion1, document);  // Evaluates & caches
 * EvaluationResult result2 = context.getOrEvaluate(criterion2, document);  // Evaluates & caches
 *
 * // 3. References use cached results
 * EvaluationResult ref1 = context.getOrEvaluate(reference1, document);  // Uses cached result
 * EvaluationResult ref2 = context.getOrEvaluate(reference2, document);  // Uses cached result
 * }</pre>
 *
 * <h2>Cache Behavior</h2>
 *
 * <ul>
 *   <li><b>Cache Miss:</b> Criterion not in cache → evaluate and store result</li>
 *   <li><b>Cache Hit:</b> Criterion in cache → return cached result (no re-evaluation)</li>
 *   <li><b>Thread-Safe:</b> ConcurrentHashMap.computeIfAbsent ensures single evaluation per criterion</li>
 *   <li><b>Immutable Results:</b> Cached results are immutable records, safe to share</li>
 * </ul>
 *
 * <h2>Usage in Criterion Implementations</h2>
 *
 * <h3>QueryCriterion (Leaf)</h3>
 * <pre>{@code
 * public EvaluationResult evaluate(Object document, EvaluationContext context) {
 *     return context.evaluator().evaluateQuery(document, this);
 * }
 * }</pre>
 *
 * <h3>CompositeCriterion (Composite)</h3>
 * <pre>{@code
 * public EvaluationResult evaluate(Object document, EvaluationContext context) {
 *     List<EvaluationResult> childResults = criteria.stream()
 *         .map(child -> context.getOrEvaluate(child, document))  // Uses cache!
 *         .toList();
 *     return new CompositeResult(this, calculateState(childResults), childResults);
 * }
 * }</pre>
 *
 * <h3>CriterionReference (Reference)</h3>
 * <pre>{@code
 * public EvaluationResult evaluate(Object document, EvaluationContext context) {
 *     EvaluationResult cached = context.getCached(ref);  // Direct cache lookup
 *     return cached != null ? new ReferenceResult(this, cached)
 *                           : ReferenceResult.missing(this);
 * }
 * }</pre>
 *
 * <h2>Thread Safety</h2>
 *
 * <p>This class is thread-safe:
 * <ul>
 *   <li>Uses {@link ConcurrentHashMap} for cache</li>
 *   <li>{@code computeIfAbsent} ensures atomic cache updates</li>
 *   <li>Safe for parallel stream evaluation</li>
 *   <li>Immutable {@link EvaluationResult} records prevent shared mutable state</li>
 * </ul>
 *
 * @see Criterion
 * @see CriterionEvaluator
 * @see EvaluationResult
 * @since 0.2.0
 */
public class EvaluationContext {

    private final CriterionEvaluator evaluator;
    private final Map<String, EvaluationResult> cache = new ConcurrentHashMap<>();

    /**
     * Creates an evaluation context with the given evaluator.
     *
     * @param evaluator the criterion evaluator to use for query evaluation
     */
    public EvaluationContext(CriterionEvaluator evaluator) {
        this.evaluator = evaluator;
    }

    /**
     * Returns the criterion evaluator.
     *
     * @return the evaluator used for query criterion evaluation
     */
    public CriterionEvaluator evaluator() {
        return evaluator;
    }

    /**
     * Gets or evaluates a criterion, using cached results when available.
     *
     * <p>This is the primary method for criterion evaluation. It:
     * <ol>
     *   <li>Checks the cache for an existing result</li>
     *   <li>If found, returns the cached result</li>
     *   <li>If not found, evaluates the criterion and caches the result</li>
     * </ol>
     *
     * <p><b>Thread Safety:</b> Uses {@code computeIfAbsent} to ensure each criterion
     * is evaluated exactly once, even in parallel evaluation scenarios.
     *
     * <h3>Example:</h3>
     * <pre>{@code
     * // First call - evaluates and caches
     * EvaluationResult result1 = context.getOrEvaluate(criterion, document);
     *
     * // Second call - uses cached result
     * EvaluationResult result2 = context.getOrEvaluate(criterion, document);
     *
     * assert result1 == result2;  // Same object from cache
     * }</pre>
     *
     * @param criterion the criterion to evaluate
     * @param document the document to evaluate against
     * @return the evaluation result (from cache or freshly evaluated)
     */
    public EvaluationResult getOrEvaluate(Criterion criterion, Object document) {
        return cache.computeIfAbsent(
                criterion.id(),
                id -> criterion.evaluate(document, this)
        );
    }

    /**
     * Gets a cached result by criterion ID.
     *
     * <p>This is primarily used by {@link uk.codery.jspec.model.CriterionReference}
     * to look up referenced criteria results.
     *
     * <h3>Example:</h3>
     * <pre>{@code
     * // Evaluate a criterion
     * context.getOrEvaluate(ageCheck, document);
     *
     * // Later, look up by ID
     * EvaluationResult cached = context.getCached("age-check");
     * }</pre>
     *
     * @param criterionId the ID of the criterion whose result to retrieve
     * @return the cached result, or null if not found
     */
    public EvaluationResult getCached(String criterionId) {
        return cache.get(criterionId);
    }

    /**
     * Returns all cached results.
     *
     * <p>Useful for creating summaries or collecting all results after evaluation.
     *
     * <h3>Example:</h3>
     * <pre>{@code
     * // After evaluating all criteria
     * Collection<EvaluationResult> allResults = context.getAllResults();
     * EvaluationSummary summary = EvaluationSummary.from(allResults);
     * }</pre>
     *
     * @return all cached evaluation results
     */
    public Collection<EvaluationResult> getAllResults() {
        return cache.values();
    }

    /**
     * Returns the number of cached results.
     *
     * @return the cache size
     */
    public int cacheSize() {
        return cache.size();
    }

    /**
     * Checks if a criterion result is cached.
     *
     * @param criterionId the criterion ID to check
     * @return true if the result is cached, false otherwise
     */
    public boolean isCached(String criterionId) {
        return cache.containsKey(criterionId);
    }

    /**
     * Clears the cache.
     *
     * <p>Useful for testing or reusing the same context with a new document.
     *
     * <p><b>Warning:</b> Calling this during evaluation will invalidate references.
     * Only use between separate evaluations.
     */
    public void clearCache() {
        cache.clear();
    }
}
