package uk.codery.jspec.evaluator;

import uk.codery.jspec.model.CompositeCriterion;
import uk.codery.jspec.model.Criterion;
import uk.codery.jspec.model.CriterionReference;
import uk.codery.jspec.result.EvaluationResult;
import uk.codery.jspec.result.ReferenceResult;

import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
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
public class EvaluationContext implements AutoCloseable {

    private final CriterionEvaluator evaluator;
    private final Object contextDoc;
    private final Map<String, EvaluationResult> cache = new ConcurrentHashMap<>();

    /**
     * Index of criterion id → criterion definition, used to resolve references to
     * targets that have not yet been evaluated (on-demand resolution). Defaults to an
     * empty map; only {@link uk.codery.jspec.evaluator.SpecificationEvaluator} supplies
     * a populated index. With an empty index, references fall back to cache-only lookup
     * (unchanged historical behaviour).
     */
    private final Map<String, Criterion> criterionIndex;

    /**
     * Per-thread guard tracking which reference ids are currently being resolved on the
     * calling thread, used to break reference cycles before they recurse unboundedly.
     */
    private final ThreadLocal<Set<String>> resolving = ThreadLocal.withInitial(HashSet::new);

    /**
     * Creates an evaluation context with the given evaluator and an empty context document.
     *
     * @param evaluator the criterion evaluator to use for query evaluation
     */
    public EvaluationContext(CriterionEvaluator evaluator) {
        this(evaluator, Map.of());
    }

    /**
     * Creates an evaluation context with the given evaluator and context document.
     *
     * <p>The context document is the source for {@code $contextPath} references
     * resolved during criterion evaluation. A {@code null} {@code contextDoc} is
     * normalised to {@link Map#of()} so downstream code never has to null-check.
     *
     * @param evaluator the criterion evaluator to use for query evaluation
     * @param contextDoc the context document used for resolving context-path references;
     *                   {@code null} is normalised to an empty map
     */
    public EvaluationContext(CriterionEvaluator evaluator, Object contextDoc) {
        this(evaluator, contextDoc, Map.of());
    }

    /**
     * Creates an evaluation context with the given evaluator, context document, and a
     * criterion index for on-demand reference resolution.
     *
     * <p>The criterion index maps criterion ids to their definitions. When a
     * {@link CriterionReference} is resolved and its target has not yet been cached, the
     * target definition is looked up in this index and evaluated on demand. This makes
     * reference resolution independent of evaluation order — in particular, references
     * to {@link uk.codery.jspec.model.CompositeCriterion} no longer depend on phase
     * ordering. A {@code null} index is normalised to {@link Map#of()}, which restores
     * the historical cache-only reference behaviour (a reference to a not-yet-evaluated
     * target degrades to UNDETERMINED/missing).
     *
     * @param evaluator the criterion evaluator to use for query evaluation
     * @param contextDoc the context document used for resolving context-path references;
     *                   {@code null} is normalised to an empty map
     * @param criterionIndex id → criterion definition index; {@code null} normalised to
     *                       an empty map
     * @since 0.7.0
     */
    public EvaluationContext(CriterionEvaluator evaluator, Object contextDoc, Map<String, Criterion> criterionIndex) {
        this.evaluator = evaluator;
        this.contextDoc = contextDoc == null ? Map.of() : contextDoc;
        this.criterionIndex = criterionIndex == null ? Map.of() : criterionIndex;
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
     * Returns the context document used for resolving {@code $contextPath} references.
     *
     * <p>Never returns {@code null}; an empty {@link Map} is the "no context" sentinel.
     *
     * @return the context document (never {@code null})
     */
    public Object contextDoc() {
        return contextDoc;
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
        // A reference's id equals its target's id, so it must never be cached under its own
        // key (that would make computeIfAbsent re-enter the same key — "Recursive update").
        if (criterion instanceof CriterionReference reference) {
            return resolveReference(reference, document);
        }
        EvaluationResult cached = cache.get(criterion.id());
        if (cached != null) {
            return cached;
        }
        // Only composites recurse into children, so only they can form a reference cycle and
        // need cycle-guard tracking. Recording the id before computeIfAbsent lets a self-
        // referencing composite trip the guard in resolveReference before a same-key re-entry.
        // Leaf queries skip the guard, keeping it single-threaded (see clearThreadCycleState).
        if (criterion instanceof CompositeCriterion) {
            Set<String> inProgress = resolving.get();
            // added==false when reached via resolveReference (which already added this id and
            // owns its removal); only the owner removes, so this finally is guarded by added.
            boolean added = inProgress.add(criterion.id());
            try {
                return cache.computeIfAbsent(criterion.id(), id -> criterion.evaluate(document, this));
            } finally {
                if (added) {
                    inProgress.remove(criterion.id());
                }
            }
        }
        return cache.computeIfAbsent(criterion.id(), id -> criterion.evaluate(document, this));
    }

    /**
     * Removes this context's per-thread reference-cycle guard state from the calling thread.
     *
     * <p>The cycle guard is a {@link ThreadLocal}; balanced add/remove leaves the set empty
     * after evaluation, but the empty set object would otherwise linger in the calling
     * thread's {@code ThreadLocalMap} until this context is garbage-collected. Pooled threads
     * (a {@code parallelStream} ForkJoinPool worker, an embedded servlet container thread, …)
     * outlive a single evaluation, so {@link SpecificationEvaluator} calls this once each
     * evaluation completes. Only the calling thread is affected — composites, the only
     * criteria that touch the guard, are evaluated sequentially on that thread.
     *
     * @since 0.7.0
     */
    public void clearThreadCycleState() {
        resolving.remove();
    }

    /**
     * Equivalent to {@link #clearThreadCycleState()}, enabling try-with-resources for callers
     * that construct an {@code EvaluationContext} directly:
     * <pre>{@code
     * try (EvaluationContext ctx = new EvaluationContext(evaluator, contextDoc, index)) {
     *     ctx.getOrEvaluate(criterion, document);
     * }
     * }</pre>
     * {@link SpecificationEvaluator} already cleans up after every evaluation, so this matters
     * only for direct, custom orchestration on pooled threads.
     *
     * @since 0.7.0
     */
    @Override
    public void close() {
        clearThreadCycleState();
    }

    /**
     * Resolves a {@link CriterionReference} to its target's result.
     *
     * <p>Resolution order:
     * <ol>
     *   <li><b>Fast path:</b> if the target is already cached, wrap and return it.</li>
     *   <li><b>On-demand:</b> otherwise look the target definition up in the criterion
     *       index and evaluate it now. The target is a query or composite (never a
     *       reference, since references are not indexed), so the subsequent
     *       {@code getOrEvaluate(def, ...)} performs {@code computeIfAbsent} on the
     *       target key — a key that is NOT currently being computed — so the
     *       "Recursive update" trap cannot fire.</li>
     *   <li><b>Unknown:</b> if no definition is indexed, return {@link ReferenceResult#missing}
     *       (UNDETERMINED) — unchanged behaviour for an empty index.</li>
     * </ol>
     *
     * <p>A per-thread {@code resolving} set guards against reference cycles: if the same
     * ref is already being resolved on this thread, the cycle is broken by returning
     * {@link ReferenceResult#cycle} (UNDETERMINED). This guard trips BEFORE any same-key
     * {@code computeIfAbsent} re-entry, so cycles never surface as exceptions.
     *
     * @param reference the reference to resolve
     * @param document the document being evaluated
     * @return the resolved reference result
     */
    private EvaluationResult resolveReference(CriterionReference reference, Object document) {
        String ref = reference.ref();

        // Fast path: target already evaluated.
        EvaluationResult cached = cache.get(ref);
        if (cached != null) {
            return new ReferenceResult(reference, cached);
        }

        // On-demand: evaluate the target now if we have its definition.
        Criterion def = criterionIndex.get(ref);
        if (def == null) {
            return ReferenceResult.missing(reference);
        }

        Set<String> inProgress = resolving.get();
        if (!inProgress.add(ref)) {
            // Already resolving this ref on this thread → cycle.
            return ReferenceResult.cycle(reference);
        }
        try {
            EvaluationResult target = getOrEvaluate(def, document);
            return new ReferenceResult(reference, target);
        } finally {
            inProgress.remove(ref);
        }
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
