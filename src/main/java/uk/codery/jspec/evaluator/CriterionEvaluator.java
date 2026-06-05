package uk.codery.jspec.evaluator;

import lombok.extern.slf4j.Slf4j;
import uk.codery.jspec.model.QueryCriterion;
import uk.codery.jspec.operator.OperatorHandler;
import uk.codery.jspec.operator.OperatorRegistry;
import uk.codery.jspec.result.QueryResult;
import uk.codery.jspec.result.EvaluationState;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.TemporalAccessor;
import java.util.*;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Core evaluation engine for individual {@link QueryCriterion} instances using MongoDB-style query operators.
 *
 * <p>The {@code CriterionEvaluator} is responsible for evaluating a single query criterion against
 * a document. It supports 23 built-in query operators spanning comparison, collection, advanced,
 * string, date/range, and logical ({@code $and}/{@code $or}/{@code $not}) categories, and can be
 * extended with custom operators via {@link uk.codery.jspec.operator.OperatorRegistry}.
 *
 * <h2>Supported Operators</h2>
 *
 * <h3>Comparison Operators (6)</h3>
 * <ul>
 *   <li><b>$eq</b> - Equality: {@code {field: {$eq: value}}}</li>
 *   <li><b>$ne</b> - Not equal: {@code {field: {$ne: value}}}</li>
 *   <li><b>$gt</b> - Greater than: {@code {field: {$gt: value}}}</li>
 *   <li><b>$gte</b> - Greater than or equal: {@code {field: {$gte: value}}}</li>
 *   <li><b>$lt</b> - Less than: {@code {field: {$lt: value}}}</li>
 *   <li><b>$lte</b> - Less than or equal: {@code {field: {$lte: value}}}</li>
 * </ul>
 *
 * <h3>Collection Operators (4)</h3>
 * <ul>
 *   <li><b>$in</b> - Value in array: {@code {field: {$in: [val1, val2]}}}</li>
 *   <li><b>$nin</b> - Value not in array: {@code {field: {$nin: [val1, val2]}}}</li>
 *   <li><b>$all</b> - Array contains all values: {@code {field: {$all: [val1, val2]}}}</li>
 *   <li><b>$size</b> - Array size: {@code {field: {$size: 5}}}</li>
 * </ul>
 *
 * <h3>String Operators (3)</h3>
 * <ul>
 *   <li><b>$contains</b> - Substring/element check: {@code {field: {$contains: "value"}}}</li>
 *   <li><b>$startsWith</b> - String prefix match: {@code {field: {$startsWith: "prefix"}}}</li>
 *   <li><b>$endsWith</b> - String suffix match: {@code {field: {$endsWith: ".pdf"}}}</li>
 * </ul>
 *
 * <h3>Logical Operators (3)</h3>
 * <ul>
 *   <li><b>$not</b> - Invert condition: {@code {field: {$not: {$eq: "value"}}}}</li>
 *   <li><b>$and</b> - All conditions must match: {@code {field: {$and: [{$gte: 18}, {$lt: 65}]}}}</li>
 *   <li><b>$or</b> - Any condition must match: {@code {field: {$or: [{$eq: 0}, {$gte: 80}]}}}</li>
 * </ul>
 *
 * <h3>Range Operators (1)</h3>
 * <ul>
 *   <li><b>$between</b> - Inclusive range: {@code {field: {$between: [100, 500]}}}</li>
 * </ul>
 *
 * <h3>Date Operators (2)</h3>
 * <ul>
 *   <li><b>$dateBefore</b> - Date less than: {@code {field: {$dateBefore: "2025-01-01"}}}</li>
 *   <li><b>$dateAfter</b> - Date greater than: {@code {field: {$dateAfter: "2024-01-01"}}}</li>
 * </ul>
 *
 * <h3>Advanced Operators (4)</h3>
 * <ul>
 *   <li><b>$exists</b> - Field existence: {@code {field: {$exists: true}}}</li>
 *   <li><b>$type</b> - Type check: {@code {field: {$type: "string"}}}</li>
 *   <li><b>$regex</b> - Pattern match: {@code {field: {$regex: "^[a-z]+"}}}</li>
 *   <li><b>$elemMatch</b> - Array element match: {@code {field: {$elemMatch: {subfield: value}}}}</li>
 * </ul>
 *
 * <h2>Key Features</h2>
 * <ul>
 *   <li><b>Tri-State Evaluation:</b> MATCHED / NOT_MATCHED / UNDETERMINED</li>
 *   <li><b>Graceful Degradation:</b> never throws — unknown operators, missing data and
 *       unresolved {@code $contextPath} references become UNDETERMINED; operand type
 *       mismatches become NOT_MATCHED</li>
 *   <li><b>Regex Caching:</b> Thread-safe LRU cache (100 patterns) for ~10-100x speedup</li>
 *   <li><b>Dot Notation:</b> Navigate nested fields with "address.city" syntax</li>
 *   <li><b>Custom Operators:</b> Extensible via OperatorRegistry</li>
 *   <li><b>Performance Optimized:</b> HashSet-based $all operator, cached patterns</li>
 * </ul>
 *
 * <h2>Usage Examples</h2>
 *
 * <h3>Basic Query Criterion Evaluation</h3>
 * <pre>{@code
 * CriterionEvaluator evaluator = new CriterionEvaluator();
 *
 * Map<String, Object> document = Map.of("age", 25, "status", "active");
 *
 * QueryCriterion criterion = QueryCriterion.builder()
 *     .id("age-check")
 *     .field("age").gte(18)
 *     .build();
 *
 * QueryResult result = evaluator.evaluateQuery(document, criterion);
 *
 * if (result.state() == EvaluationState.MATCHED) {
 *     System.out.println("Criterion matched!");
 * } else if (result.state() == EvaluationState.UNDETERMINED) {
 *     System.out.println("Could not evaluate: " + result.reason());
 * }
 * }</pre>
 *
 * <h3>Nested Field Navigation (Dot Notation)</h3>
 * <pre>{@code
 * Map<String, Object> document = Map.of(
 *     "user", Map.of(
 *         "address", Map.of(
 *             "city", "London"
 *         )
 *     )
 * );
 *
 * QueryCriterion criterion = QueryCriterion.builder()
 *     .id("city-check")
 *     .field("user.address.city").eq("London")
 *     .build();
 *
 * QueryResult result = evaluator.evaluateQuery(document, criterion);
 * }</pre>
 *
 * <h3>Custom Operators</h3>
 * <pre>{@code
 * // Create registry with custom operator
 * OperatorRegistry registry = OperatorRegistry.withDefaults();
 * registry.register("$length", (value, operand) -> {
 *     if (!(value instanceof String) || !(operand instanceof Number)) {
 *         return false;
 *     }
 *     return ((String) value).length() == ((Number) operand).intValue();
 * });
 *
 * // Use custom registry
 * CriterionEvaluator evaluator = new CriterionEvaluator(registry);
 * }</pre>
 *
 * <h2>Tri-State Evaluation Model</h2>
 *
 * <p>Every criterion evaluation produces one of three states:
 * <ul>
 *   <li><b>MATCHED</b> - Criterion evaluated successfully, condition is TRUE</li>
 *   <li><b>NOT_MATCHED</b> - Criterion evaluated successfully, condition is FALSE</li>
 *   <li><b>UNDETERMINED</b> - Could not evaluate (missing data, unknown operator, unresolved context path)</li>
 * </ul>
 *
 * <p>This model enables graceful degradation - one bad criterion never stops evaluation.
 *
 * <h2>Thread Safety</h2>
 * <p>This class is thread-safe:
 * <ul>
 *   <li>Uses synchronized LRU cache for regex patterns</li>
 *   <li>No mutable shared state</li>
 *   <li>Safe for concurrent evaluations</li>
 * </ul>
 *
 * @see QueryCriterion
 * @see QueryResult
 * @see EvaluationState
 * @see uk.codery.jspec.operator.OperatorRegistry
 * @see uk.codery.jspec.operator.OperatorHandler
 * @since 0.2.0
 */
@Slf4j
public class CriterionEvaluator {
    private final Map<String, OperatorHandler> operators = new HashMap<>();

    /** Cached, unmodifiable view of {@link #supportedOperators()} — stable after construction. */
    private final SortedSet<String> supportedOperators;

    /**
     * Returns the full set of query operators this evaluator supports. This is the canonical
     * source of truth for documentation and tooling.
     *
     * <p>Most operators are backed by a boolean {@link uk.codery.jspec.operator.OperatorHandler}
     * in the internal map. The three logical operators {@code $not}, {@code $and} and
     * {@code $or} are NOT handlers — they are dispatched tri-state in {@code evaluateOperator}
     * so they can preserve UNDETERMINED under Strong Kleene logic — so they are added to the
     * returned set explicitly.
     *
     * @return an unmodifiable, sorted set of operator names (each beginning with {@code $})
     * @since 0.7.0
     */
    public SortedSet<String> supportedOperators() {
        return supportedOperators;
    }

    private SortedSet<String> computeSupportedOperators() {
        TreeSet<String> all = new TreeSet<>(operators.keySet());
        all.add("$not");
        all.add("$and");
        all.add("$or");
        return Collections.unmodifiableSortedSet(all);
    }

    /**
     * Thread-safe LRU cache for compiled regex patterns.
     * Caches up to 100 patterns to avoid recompiling frequently used patterns.
     * Performance impact: ~10-100x faster for repeated patterns.
     */
    private final Map<String, Pattern> patternCache = Collections.synchronizedMap(
            new LinkedHashMap<>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Pattern> eldest) {
                    //TODO - make the Regex pattern cache size configurable
                    return size() > 100;
                }
            }
    );

    /**
     * Internal result that includes tri-state model.
     */
    private record InnerResult(EvaluationState state, List<String> missingPaths, String failureReason){
        /**
         * Creates a MATCHED result.
         */
        static InnerResult matched() {
            return new InnerResult(EvaluationState.MATCHED, List.of(), null);
        }

        /**
         * Creates a NOT_MATCHED result.
         */
        static InnerResult notMatched() {
            return new InnerResult(EvaluationState.NOT_MATCHED, List.of(), null);
        }

        /**
         * Creates a NOT_MATCHED result with missing paths.
         */
        static InnerResult notMatched(List<String> missingPaths) {
            return new InnerResult(EvaluationState.NOT_MATCHED, missingPaths, null);
        }

        /**
         * Creates an UNDETERMINED result with a failure reason.
         */
        static InnerResult undetermined(String reason) {
            return new InnerResult(EvaluationState.UNDETERMINED, List.of(), reason);
        }

        /**
         * Creates an UNDETERMINED result for missing data.
         */
        static InnerResult undeterminedMissingData(String path) {
            String missingPath = path.isEmpty() ? "root" : path;
            return new InnerResult(EvaluationState.UNDETERMINED, List.of(missingPath), "Missing data at: " + missingPath);
        }
    }

    public QueryResult evaluateQuery(Object doc, QueryCriterion criterion) {
        log.debug("Evaluating query criterion '{}' against document", criterion.id());

        return Optional.of(criterion)
                .map(QueryCriterion::query)
                .map(query -> matchValue(doc, query, ""))
                .map(result -> new QueryResult(criterion, result.state, result.missingPaths, result.failureReason))
                .orElseGet(() -> {
                    log.warn("Query criterion '{}' has no query defined", criterion.id());
                    return QueryResult.missing(criterion);
                });
    }

    /**
     * Creates a CriterionEvaluator with built-in operators.
     *
     * <p>This constructor initializes the evaluator with the default set of 23 query operators:
     * the 6 comparison operators seeded by {@link uk.codery.jspec.operator.OperatorRegistry#withDefaults()},
     * the 14 boolean-handler operators registered in {@code registerEvaluatorBoundOperators()},
     * and the 3 tri-state logical operators ({@code $not}, {@code $and}, {@code $or}). Use this
     * constructor for standard evaluation needs.
     *
     * <p>For custom operators, use {@link #CriterionEvaluator(OperatorRegistry)} instead.
     */
    public CriterionEvaluator() {
        this(OperatorRegistry.withDefaults());
    }

    /**
     * Creates a CriterionEvaluator with a custom operator registry.
     *
     * <p>This constructor allows you to provide custom operators or override built-in
     * operators. The registry should contain all operators needed for evaluation.
     *
     * <h3>Example:</h3>
     * <pre>{@code
     * // Create registry with defaults and add custom operators
     * OperatorRegistry registry = OperatorRegistry.withDefaults();
     * registry.register("$length", (val, operand) -> {
     *     return val instanceof String &&
     *            ((String) val).length() == ((Number) operand).intValue();
     * });
     *
     * // Create evaluator with custom registry
     * CriterionEvaluator evaluator = new CriterionEvaluator(registry);
     * }</pre>
     *
     * @param registry the operator registry to use for evaluation
     * @throws IllegalArgumentException if registry is null
     */
    public CriterionEvaluator(OperatorRegistry registry) {
        if (registry == null) {
            throw new IllegalArgumentException("OperatorRegistry cannot be null");
        }
        this.operators.putAll(registry.getAll());
        // Register the evaluator-owned operators on top of the registry's defaults (and any
        // custom operators the registry carries). The registry seeds only the six overridable
        // comparison operators; everything else is owned here.
        registerEvaluatorBoundOperators();
        this.supportedOperators = computeSupportedOperators();
        log.debug("Created CriterionEvaluator with custom registry ({} operators)", operators.size());
    }

    /**
     * Registers the boolean-handler operators the evaluator owns — everything except the six
     * comparison operators and the three logical operators. These implementations live here
     * (not in {@link OperatorRegistry}) because they need evaluator instance state (the regex
     * cache) or recursion through {@code matchValue()}/{@code evaluateOperatorQuery()}
     * ({@code $elemMatch}), or are jspec extensions tied to the evaluator's helpers
     * ({@code $between} and the date operators via {@code compare()}/{@code parseToInstant()}).
     *
     * <p>{@code $not}, {@code $and} and {@code $or} are NOT registered here — they are
     * dispatched tri-state in {@link #evaluateOperator} so they preserve UNDETERMINED under
     * Strong Kleene logic, which a boolean handler cannot express.
     *
     * <p>The plain comparison operators ({@code $eq}, {@code $ne}, {@code $gt}, {@code $gte},
     * {@code $lt}, {@code $lte}) are intentionally NOT registered here: they are supplied by
     * {@link OperatorRegistry#withDefaults()} so they remain registry-overridable (see
     * {@code CriterionEvaluatorCustomOperatorTest}). Their type-mismatch handling follows the
     * project contract — incomparable operands (e.g. a String value against a numeric operand)
     * yield NOT_MATCHED (the handler returns {@code false}), not UNDETERMINED.
     */
    private void registerEvaluatorBoundOperators() {
        operators.put("$contains", this::evaluateContainsOperator);
        operators.put("$startsWith", this::evaluateStartsWithOperator);
        operators.put("$endsWith", this::evaluateEndsWithOperator);
        operators.put("$between", this::evaluateBetweenOperator);
        operators.put("$dateBefore", this::evaluateDateBeforeOperator);
        operators.put("$dateAfter", this::evaluateDateAfterOperator);
        operators.put("$in", this::evaluateInOperator);
        operators.put("$nin", this::evaluateNotInOperator);
        operators.put("$exists", this::evaluateExistsOperator);
        operators.put("$type", (val, operand) -> getType(val).equals(operand));
        operators.put("$regex", this::evaluateRegexOperator);
        operators.put("$size", this::evaluateSizeOperator);
        operators.put("$elemMatch", this::evaluateElemMatchOperator);
        operators.put("$all", this::evaluateAllOperator);
        // $not, $and and $or are NOT boolean OperatorHandlers — a boolean handler can only
        // express MATCHED/NOT_MATCHED, but these must preserve UNDETERMINED under Strong
        // Kleene logic. They are intercepted in evaluateOperator() and evaluated tri-state.
    }

    private boolean evaluateInOperator(Object val, Object operand) {
        try {
            if (!(operand instanceof List<?> list)) {
                log.warn("Operator $in expects List, got {} - treating as not matched",
                           operand == null ? "null" : operand.getClass().getSimpleName());
                return false;
            }

            // MongoDB behavior: if val is an array, check if ANY element in val is in the operand list
            if (val instanceof List<?> valList) {
                for (Object item : valList) {
                    if (list.contains(item)) {
                        return true;
                    }
                }
                return false;
            }

            // Otherwise, check if val is in the operand list
            return list.contains(val);
        } catch (Exception e) {
            log.warn("Error evaluating $in operator: {}", e.getMessage(), e);
            return false;
        }
    }

    private boolean evaluateNotInOperator(Object val, Object operand) {
        try {
            if (!(operand instanceof List<?> list)) {
                log.warn("Operator $nin expects List, got {} - treating as not matched",
                           operand == null ? "null" : operand.getClass().getSimpleName());
                return false;
            }

            // MongoDB behavior: if val is an array, check that NO element in val is in the operand list
            if (val instanceof List<?> valList) {
                for (Object item : valList) {
                    if (list.contains(item)) {
                        return false;
                    }
                }
                return true;
            }

            // Otherwise, check if val is not in the operand list
            return !list.contains(val);
        } catch (Exception e) {
            log.warn("Error evaluating $nin operator: {}", e.getMessage(), e);
            return false;
        }
    }

    private boolean evaluateExistsOperator(Object val, Object operand) {
        try {
            if (!(operand instanceof Boolean)) {
                log.warn("Operator $exists expects Boolean, got {} - treating as not matched",
                           operand == null ? "null" : operand.getClass().getSimpleName());
                return false;
            }
            boolean query = (Boolean) operand;
            return query == (val != null);
        } catch (Exception e) {
            log.warn("Error evaluating $exists operator: {}", e.getMessage(), e);
            return false;
        }
    }

    private boolean evaluateRegexOperator(Object val, Object operand) {
        try {
            if (!(operand instanceof String patternString)) {
                log.warn("Operator $regex expects String pattern, got {} - treating as not matched",
                           operand == null ? "null" : operand.getClass().getSimpleName());
                return false;
            }
            Pattern pattern = getOrCompilePattern(patternString);
            return pattern.matcher(String.valueOf(val)).find();
        } catch (PatternSyntaxException e) {
            log.warn("Invalid regex pattern '{}': {} - treating as not matched", operand, e.getMessage());
            return false;
        } catch (Exception e) {
            log.warn("Error evaluating $regex operator: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Gets a compiled Pattern from the cache, or compiles and caches it if not present.
     * Thread-safe and uses LRU eviction when cache size exceeds 100 patterns.
     *
     * @param patternString the regex pattern string to compile
     * @return the compiled Pattern
     * @throws PatternSyntaxException if the pattern string is invalid
     */
    private Pattern getOrCompilePattern(String patternString) throws PatternSyntaxException {
        // Check cache first
        Pattern cached = patternCache.get(patternString);
        if (cached != null) {
            log.trace("Regex pattern '{}' found in cache", patternString);
            return cached;
        }

        // Not in cache, compile and cache it
        log.debug("Compiling and caching regex pattern '{}'", patternString);
        Pattern pattern = Pattern.compile(patternString);
        patternCache.put(patternString, pattern);
        return pattern;
    }

    private boolean evaluateSizeOperator(Object val, Object operand) {
        try {
            if (!(val instanceof List)) {
                log.debug("Operator $size expects List value, got {} - treating as not matched",
                            val == null ? "null" : val.getClass().getSimpleName());
                return false;
            }
            if (!(operand instanceof Number)) {
                log.warn("Operator $size expects Number operand, got {} - treating as not matched",
                           operand == null ? "null" : operand.getClass().getSimpleName());
                return false;
            }
            return ((List<?>) val).size() == ((Number) operand).intValue();
        } catch (Exception e) {
            log.warn("Error evaluating $size operator: {}", e.getMessage(), e);
            return false;
        }
    }

    private boolean evaluateElemMatchOperator(Object val, Object operand) {
        try {
            if (!(val instanceof List)) {
                log.debug("Operator $elemMatch expects List value, got {} - treating as not matched",
                            val == null ? "null" : val.getClass().getSimpleName());
                return false;
            }
            if (!(operand instanceof Map)) {
                log.warn("Operator $elemMatch expects Map operand, got {} - treating as not matched",
                           operand == null ? "null" : operand.getClass().getSimpleName());
                return false;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> queryMap = (Map<String, Object>) operand;
            for (Object item : (List<?>) val) {
                if (matchValue(item, queryMap, "").state == EvaluationState.MATCHED) {
                    return true;
                }
            }
            return false;
        } catch (Exception e) {
            log.warn("Error evaluating $elemMatch operator: {}", e.getMessage(), e);
            return false;
        }
    }

    private boolean evaluateAllOperator(Object val, Object operand) {
        try {
            if (!(val instanceof List<?> valList)) {
                log.debug("Operator $all expects List value, got {} - treating as not matched",
                            val == null ? "null" : val.getClass().getSimpleName());
                return false;
            }
            if (!(operand instanceof List<?> queryList)) {
                log.warn("Operator $all expects List operand, got {} - treating as not matched",
                           operand == null ? "null" : operand.getClass().getSimpleName());
                return false;
            }
            //noinspection SuspiciousMethodCalls
            return new HashSet<>(valList).containsAll(queryList);
        } catch (Exception e) {
            log.warn("Error evaluating $all operator: {}", e.getMessage(), e);
            return false;
        }
    }

    private boolean evaluateContainsOperator(Object val, Object operand) {
        try {
            if (val == null || operand == null) {
                return false;
            }
            // String contains substring
            if (val instanceof String str && operand instanceof String substring) {
                return str.contains(substring);
            }
            // Collection contains element
            if (val instanceof Collection<?> collection) {
                return collection.contains(operand);
            }
            log.debug("Operator $contains expects String or Collection value, got {} - treating as not matched",
                        val.getClass().getSimpleName());
            return false;
        } catch (Exception e) {
            log.warn("Error evaluating $contains operator: {}", e.getMessage(), e);
            return false;
        }
    }

    private boolean evaluateStartsWithOperator(Object val, Object operand) {
        try {
            if (!(val instanceof String str)) {
                log.debug("Operator $startsWith expects String value, got {} - treating as not matched",
                            val == null ? "null" : val.getClass().getSimpleName());
                return false;
            }
            if (!(operand instanceof String prefix)) {
                log.warn("Operator $startsWith expects String operand, got {} - treating as not matched",
                           operand == null ? "null" : operand.getClass().getSimpleName());
                return false;
            }
            return str.startsWith(prefix);
        } catch (Exception e) {
            log.warn("Error evaluating $startsWith operator: {}", e.getMessage(), e);
            return false;
        }
    }

    private boolean evaluateEndsWithOperator(Object val, Object operand) {
        try {
            if (!(val instanceof String str)) {
                log.debug("Operator $endsWith expects String value, got {} - treating as not matched",
                            val == null ? "null" : val.getClass().getSimpleName());
                return false;
            }
            if (!(operand instanceof String suffix)) {
                log.warn("Operator $endsWith expects String operand, got {} - treating as not matched",
                           operand == null ? "null" : operand.getClass().getSimpleName());
                return false;
            }
            return str.endsWith(suffix);
        } catch (Exception e) {
            log.warn("Error evaluating $endsWith operator: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * {@code $not}: Strong Kleene negation of a nested sub-query. Unlike a boolean
     * {@link OperatorHandler} (which can only yield MATCHED/NOT_MATCHED), this is
     * dispatched tri-state from {@link #evaluateOperator} so it can preserve UNDETERMINED:
     * MATCHED becomes NOT_MATCHED, NOT_MATCHED becomes MATCHED, and UNDETERMINED stays
     * UNDETERMINED (¬unknown = unknown) — carrying its missing paths and reason through.
     */
    private InnerResult evaluateNot(Object val, Object operand) {
        if (!(operand instanceof Map)) {
            log.warn("Operator $not expects Map operand (nested query), got {} - treating as not matched",
                       operand == null ? "null" : operand.getClass().getSimpleName());
            return InnerResult.notMatched();
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> nestedQuery = (Map<String, Object>) operand;

        InnerResult inner = evaluateOperatorQuery(val, nestedQuery);
        return switch (inner.state()) {
            case MATCHED -> InnerResult.notMatched();
            case NOT_MATCHED -> InnerResult.matched();
            case UNDETERMINED -> inner; // ¬UNDETERMINED = UNDETERMINED (preserve paths/reason)
        };
    }


    private boolean evaluateBetweenOperator(Object val, Object operand) {
        try {
            if (!(operand instanceof List<?> range)) {
                log.warn("Operator $between expects List [min, max], got {} - treating as not matched",
                           operand == null ? "null" : operand.getClass().getSimpleName());
                return false;
            }
            if (range.size() != 2) {
                log.warn("Operator $between expects List of exactly 2 elements [min, max], got {} elements - treating as not matched",
                           range.size());
                return false;
            }

            Object min = range.get(0);
            Object max = range.get(1);

            // Value must be >= min AND <= max
            return compare(val, min) >= 0 && compare(val, max) <= 0;
        } catch (Exception e) {
            log.warn("Error evaluating $between operator: {}", e.getMessage(), e);
            return false;
        }
    }

    private boolean evaluateDateBeforeOperator(Object val, Object operand) {
        try {
            Instant valInstant = parseToInstant(val);
            Instant operandInstant = parseToInstant(operand);

            if (valInstant == null || operandInstant == null) {
                log.debug("Could not parse dates for $dateBefore comparison - treating as not matched");
                return false;
            }

            return valInstant.isBefore(operandInstant);
        } catch (Exception e) {
            log.warn("Error evaluating $dateBefore operator: {}", e.getMessage(), e);
            return false;
        }
    }

    private boolean evaluateDateAfterOperator(Object val, Object operand) {
        try {
            Instant valInstant = parseToInstant(val);
            Instant operandInstant = parseToInstant(operand);

            if (valInstant == null || operandInstant == null) {
                log.debug("Could not parse dates for $dateAfter comparison - treating as not matched");
                return false;
            }

            return valInstant.isAfter(operandInstant);
        } catch (Exception e) {
            log.warn("Error evaluating $dateAfter operator: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Parses various date representations to an Instant.
     * Supports:
     * - "now" keyword for current time
     * - ISO 8601 date-time strings (e.g., "2025-01-01T00:00:00Z")
     * - ISO 8601 date strings (e.g., "2025-01-01")
     * - Long epoch milliseconds
     * - Instant objects
     *
     * @param value the value to parse
     * @return the parsed Instant, or null if parsing fails
     */
    private Instant parseToInstant(Object value) {
        if (value == null) {
            return null;
        }

        // Already an Instant
        if (value instanceof Instant instant) {
            return instant;
        }

        // Long epoch milliseconds
        if (value instanceof Long epochMillis) {
            return Instant.ofEpochMilli(epochMillis);
        }

        // Number (could be epoch millis or seconds)
        if (value instanceof Number number) {
            long longValue = number.longValue();
            // Heuristic: if < 1e10, treat as seconds; otherwise as milliseconds
            if (longValue < 10_000_000_000L) {
                return Instant.ofEpochSecond(longValue);
            } else {
                return Instant.ofEpochMilli(longValue);
            }
        }

        // String parsing
        if (value instanceof String str) {
            // Special keyword "now"
            if ("now".equalsIgnoreCase(str)) {
                return Instant.now();
            }

            // Try ISO 8601 date-time first
            try {
                return Instant.parse(str);
            } catch (DateTimeParseException ignored) {
                // Try other formats
            }

            // Try ISO 8601 date-time with offset
            try {
                return DateTimeFormatter.ISO_DATE_TIME
                        .parse(str, Instant::from);
            } catch (DateTimeParseException ignored) {
                // Try other formats
            }

            // Try ISO 8601 date only (assume start of day in UTC)
            try {
                LocalDate date = LocalDate.parse(str, DateTimeFormatter.ISO_DATE);
                return date.atStartOfDay(ZoneId.of("UTC")).toInstant();
            } catch (DateTimeParseException ignored) {
                // Failed to parse
            }

            log.debug("Could not parse date string '{}' - unsupported format", str);
            return null;
        }

        log.debug("Unsupported date type: {} - cannot parse to Instant", value.getClass().getSimpleName());
        return null;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private int compare(Object a, Object b) {
        if (a instanceof Number && b instanceof Number) {
            double aNum = ((Number) a).doubleValue();
            double bNum = ((Number) b).doubleValue();
            return Double.compare(aNum, bNum);
        }
        if (a instanceof Comparable && b instanceof Comparable) {
            return ((Comparable) a).compareTo(b);
        }
        return 0;
    }

    private String getType(Object val) {
        return switch (val) {
            case null -> "null";
            case List<?> ignored -> "array";
            case String ignored -> "string";
            case Number ignored -> "number";
            case Boolean ignored -> "boolean";
            case Map<?,?> ignored -> "object";
            default -> val.getClass().getSimpleName().toLowerCase();
        };
    }

    private InnerResult matchValue(Object val, Object query, String path) {
        // Special handling for $exists operator - it needs to evaluate even when val is null
        // $exists checks if a field exists (null means it doesn't exist)
        if (val == null) {
            if (query instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> queryMap = (Map<String, Object>) query;
                if (queryMap.containsKey("$exists")) {
                    // Allow $exists to evaluate against null values
                    return matchMapValue(null, queryMap, path);
                }
            }
            return createMissingResult(path);
        }

        if (isSimpleQuery(query)) {
            return matchSimpleValue(val, query);
        }

        if (query instanceof List<?> queryList) {
            return matchListValue(val, queryList, path);
        }

        if (query instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> queryMap = (Map<String, Object>) query;
            return matchMapValue(val, queryMap, path);
        }

        log.warn("Unknown query type: {} - treating as UNDETERMINED",
                query.getClass().getSimpleName());
        return InnerResult.undetermined("Unknown query type: " + query.getClass().getSimpleName());
    }

    private boolean isSimpleQuery(Object query) {
        return !(query instanceof Map) && !(query instanceof List);
    }

    private InnerResult createMissingResult(String path) {
        return InnerResult.undeterminedMissingData(path);
    }

    private InnerResult matchSimpleValue(Object val, Object query) {
        boolean match = Objects.equals(val, query);
        return match ? InnerResult.matched() : InnerResult.notMatched();
    }

    private InnerResult matchListValue(Object val, List<?> queryList, String path) {
        if (!(val instanceof List<?> valList)) {
            return InnerResult.notMatched();
        }

        if (valList.size() != queryList.size()) {
            return InnerResult.notMatched();
        }

        return matchListElements(valList, queryList, path);
    }

    private InnerResult matchListElements(List<?> valList, List<?> queryList, String path) {
        List<String> missingPaths = new ArrayList<>();
        EvaluationState overallState = EvaluationState.MATCHED;
        String firstFailureReason = null;

        for (int i = 0; i < queryList.size(); i++) {
            String newPath = buildArrayPath(path, i);
            InnerResult subResult = matchValue(valList.get(i), queryList.get(i), newPath);

            if (subResult.state != EvaluationState.MATCHED) {
                // Priority: UNDETERMINED > NOT_MATCHED
                if (subResult.state == EvaluationState.UNDETERMINED) {
                    overallState = EvaluationState.UNDETERMINED;
                    if (firstFailureReason == null) {
                        firstFailureReason = subResult.failureReason;
                    }
                } else if (overallState == EvaluationState.MATCHED) {
                    overallState = EvaluationState.NOT_MATCHED;
                }
                missingPaths.addAll(subResult.missingPaths);
            }
        }

        if (overallState == EvaluationState.UNDETERMINED) {
            return new InnerResult(EvaluationState.UNDETERMINED, missingPaths, firstFailureReason);
        } else if (overallState == EvaluationState.NOT_MATCHED) {
            return InnerResult.notMatched(missingPaths);
        } else {
            return InnerResult.matched();
        }
    }

    private String buildArrayPath(String path, int index) {
        return path.isEmpty() ? "[" + index + "]" : path + "[" + index + "]";
    }

    private InnerResult matchMapValue(Object val, Map<String, Object> queryMap, String path) {
        boolean isOperatorQuery = isOperatorQuery(queryMap);

        if (isOperatorQuery) {
            return evaluateOperatorQuery(val, queryMap);
        }

        return evaluateFieldQuery(val, queryMap, path);
    }

    private boolean isOperatorQuery(Map<String, Object> queryMap) {
        return queryMap.keySet().stream().anyMatch(k -> k.startsWith("$"));
    }

    /**
     * Evaluates the operators in a single query map and combines them with Strong
     * Kleene AND (multiple operators on one field are a conjunction). The combinators
     * {@code $or}/{@code $and} are handled tri-state so an UNDETERMINED branch is
     * combined via Kleene logic rather than collapsed to "not matched".
     */
    private InnerResult evaluateOperatorQuery(Object val, Map<String, Object> queryMap) {
        EvaluationState combined = EvaluationState.MATCHED;
        List<String> missingPaths = new ArrayList<>();
        String failureReason = null;

        for (Map.Entry<String, Object> entry : queryMap.entrySet()) {
            String op = entry.getKey();
            if (!op.startsWith("$")) continue;

            InnerResult opResult = evaluateOperator(val, op, entry.getValue());

            combined = combined.and(opResult.state);
            missingPaths.addAll(opResult.missingPaths);
            if (failureReason == null) failureReason = opResult.failureReason;

            if (combined == EvaluationState.NOT_MATCHED) break;  // AND short-circuit
        }

        return finalise(combined, missingPaths, failureReason);
    }

    /**
     * Evaluates one operator entry to a tri-state result. An operand carrying an
     * unresolved {@code $contextPath} sentinel yields UNDETERMINED before the handler
     * runs; {@code $or}/{@code $and} are dispatched to their tri-state combinators.
     */
    private InnerResult evaluateOperator(Object val, String op, Object operand) {
        if (op.equals("$or")) return evaluateOr(val, operand);
        if (op.equals("$and")) return evaluateAnd(val, operand);
        if (op.equals("$not")) return evaluateNot(val, operand);

        List<String> unresolved = collectUnresolved(operand);
        if (!unresolved.isEmpty()) {
            return new InnerResult(EvaluationState.UNDETERMINED, List.copyOf(unresolved),
                    "Unresolved context path" + (unresolved.size() == 1 ? "" : "s") + ": "
                            + String.join(", ", unresolved));
        }

        OperatorHandler handler = operators.get(op);
        if (handler == null) {
            log.warn("Unknown operator '{}' - marking criterion as UNDETERMINED", op);
            return InnerResult.undetermined("Unknown operator: " + op);
        }

        try {
            return handler.evaluate(val, operand) ? InnerResult.matched() : InnerResult.notMatched();
        } catch (Exception e) {
            log.warn("Error evaluating operator '{}': {} - marking as UNDETERMINED", op, e.getMessage(), e);
            return InnerResult.undetermined("Error evaluating operator " + op + ": " + e.getMessage());
        }
    }

    /** {@code $or}: Kleene disjunction over the branch results (identity NOT_MATCHED). */
    private InnerResult evaluateOr(Object val, Object operand) {
        if (!(operand instanceof List<?> conditions)) {
            log.warn("Operator $or expects List of conditions, got {} - treating as not matched",
                    operand == null ? "null" : operand.getClass().getSimpleName());
            return InnerResult.notMatched();
        }
        return combineBranches(val, conditions, EvaluationState.NOT_MATCHED, EvaluationState.MATCHED, "$or");
    }

    /** {@code $and}: Kleene conjunction over the branch results (identity MATCHED). */
    private InnerResult evaluateAnd(Object val, Object operand) {
        if (!(operand instanceof List<?> conditions)) {
            log.warn("Operator $and expects List of conditions, got {} - treating as not matched",
                    operand == null ? "null" : operand.getClass().getSimpleName());
            return InnerResult.notMatched();
        }
        return combineBranches(val, conditions, EvaluationState.MATCHED, EvaluationState.NOT_MATCHED, "$and");
    }

    /**
     * Folds the branches of a boolean combinator with Kleene logic. {@code identity}
     * is the fold seed (NOT_MATCHED for OR, MATCHED for AND) and {@code shortCircuit}
     * is the absorbing state that lets the fold stop early (MATCHED for OR,
     * NOT_MATCHED for AND).
     */
    private InnerResult combineBranches(Object val, List<?> conditions,
                                        EvaluationState identity, EvaluationState shortCircuit,
                                        String operatorName) {
        EvaluationState combined = identity;
        List<String> missingPaths = new ArrayList<>();
        String failureReason = null;

        for (Object condition : conditions) {
            if (!(condition instanceof Map)) {
                log.warn("Each condition in {} must be a Map, got {} - treating as not matched",
                        operatorName, condition == null ? "null" : condition.getClass().getSimpleName());
                return InnerResult.notMatched();
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> conditionMap = (Map<String, Object>) condition;

            InnerResult branch = evaluateOperatorQuery(val, conditionMap);
            combined = (identity == EvaluationState.MATCHED)
                    ? combined.and(branch.state)
                    : combined.or(branch.state);
            missingPaths.addAll(branch.missingPaths);
            if (failureReason == null) failureReason = branch.failureReason;

            if (combined == shortCircuit) break;
        }

        return finalise(combined, missingPaths, failureReason);
    }

    /**
     * Builds the result for a combined evaluation. Per the design decision, missing
     * paths are surfaced only when they left the result UNDETERMINED — a path inside a
     * branch that was overridden by a MATCHED/NOT_MATCHED sibling did not influence the
     * outcome and is therefore not reported.
     */
    private static InnerResult finalise(EvaluationState state, List<String> missingPaths, String failureReason) {
        return switch (state) {
            case MATCHED -> InnerResult.matched();
            case NOT_MATCHED -> InnerResult.notMatched();
            case UNDETERMINED -> new InnerResult(EvaluationState.UNDETERMINED, List.copyOf(missingPaths), failureReason);
        };
    }

    /**
     * Recursively collects the {@code context.<path>} of every unresolved
     * {@code $contextPath} sentinel reachable inside an operator operand (directly, or
     * nested in a list/map such as {@code $in: [<ref>, ...]} or {@code $not: {...}}).
     */
    private static List<String> collectUnresolved(Object operand) {
        if (operand instanceof UnresolvedReference ref) {
            return List.of(ref.path());
        }
        if (operand instanceof List<?> list) {
            List<String> out = new ArrayList<>();
            for (Object element : list) out.addAll(collectUnresolved(element));
            return out;
        }
        if (operand instanceof Map<?, ?> map) {
            List<String> out = new ArrayList<>();
            for (Object value : map.values()) out.addAll(collectUnresolved(value));
            return out;
        }
        return List.of();
    }

    private InnerResult evaluateFieldQuery(Object val, Map<String, Object> queryMap, String path) {
        if (!(val instanceof Map)) {
            return InnerResult.notMatched();
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> valMap = (Map<String, Object>) val;
        return matchAllFields(valMap, queryMap, path);
    }

    private InnerResult matchAllFields(Map<String, Object> valMap, Map<String, Object> queryMap, String path) {
        List<String> missingPaths = new ArrayList<>();
        EvaluationState overallState = EvaluationState.MATCHED;
        String firstFailureReason = null;

        for (Map.Entry<String, Object> entry : queryMap.entrySet()) {
            String key = entry.getKey();
            Object subQuery = entry.getValue();
            String newPath = buildFieldPath(path, key);
            // Use navigate() to support dot notation (e.g., "address.city")
            Object subVal = navigate(valMap, key);

            InnerResult subResult = matchValue(subVal, subQuery, newPath);

            if (subResult.state != EvaluationState.MATCHED) {
                // Priority: UNDETERMINED > NOT_MATCHED
                if (subResult.state == EvaluationState.UNDETERMINED) {
                    overallState = EvaluationState.UNDETERMINED;
                    if (firstFailureReason == null) {
                        firstFailureReason = subResult.failureReason;
                    }
                } else if (overallState == EvaluationState.MATCHED) {
                    overallState = EvaluationState.NOT_MATCHED;
                }
                missingPaths.addAll(subResult.missingPaths);
            }
        }

        if (overallState == EvaluationState.UNDETERMINED) {
            return new InnerResult(EvaluationState.UNDETERMINED, missingPaths, firstFailureReason);
        } else if (overallState == EvaluationState.NOT_MATCHED) {
            return InnerResult.notMatched(missingPaths);
        } else {
            return InnerResult.matched();
        }
    }

    private String buildFieldPath(String path, String key) {
        return path.isEmpty() ? key : path + "." + key;
    }

    /**
     * Navigates through a nested map structure using dot notation.
     * For example, "address.city" navigates to map.get("address").get("city").
     *
     * @param map the map to navigate
     * @param path the dot-notation path (e.g., "address.city")
     * @return the value at the path, or null if not found
     */
    private Object navigate(Map<String, Object> map, String path) {
        if (path == null || path.isEmpty()) {
            return map;
        }

        // If path doesn't contain dots, simple lookup
        if (!path.contains(".")) {
            return map.get(path);
        }

        // Split path and navigate through nested maps
        String[] parts = path.split("\\.");
        Object current = map;

        for (String part : parts) {
            if (!(current instanceof Map)) {
                return null;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> currentMap = (Map<String, Object>) current;
            current = currentMap.get(part);
            if (current == null) {
                return null;
            }
        }

        return current;
    }
}
