package uk.codery.rules;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public class RuleEvaluator {
    private static final Logger logger = LoggerFactory.getLogger(RuleEvaluator.class);

    private final Map<String, OperatorHandler> operators = new HashMap<>();

    interface OperatorHandler {
        boolean evaluate(Object val, Object operand);
    }

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

    public EvaluationResult evaluateRule(Object doc, Rule rule) {
        logger.debug("Evaluating rule '{}' against document", rule.id());

        return Optional.of(rule)
                .map(Rule::query)
                .map(query -> matchValue(doc, query, ""))
                .map(result -> new EvaluationResult(rule, result.state, result.missingPaths, result.failureReason))
                .orElseGet(() -> {
                    logger.warn("Rule '{}' has no query defined", rule.id());
                    return EvaluationResult.missing(rule);
                });
    }

    public RuleEvaluator() {
        registerOperators();
    }

    private void registerOperators() {
        operators.put("$eq", Objects::equals);
        operators.put("$ne", (val, operand) -> !Objects.equals(val, operand));
        operators.put("$gt", (val, operand) -> compare(val, operand) > 0);
        operators.put("$gte", (val, operand) -> compare(val, operand) >= 0);
        operators.put("$lt", (val, operand) -> compare(val, operand) < 0);
        operators.put("$lte", (val, operand) -> compare(val, operand) <= 0);
        operators.put("$in", this::evaluateInOperator);
        operators.put("$nin", this::evaluateNotInOperator);
        operators.put("$exists", this::evaluateExistsOperator);
        operators.put("$type", (val, operand) -> getType(val).equals(operand));
        operators.put("$regex", this::evaluateRegexOperator);
        operators.put("$size", this::evaluateSizeOperator);
        operators.put("$elemMatch", this::evaluateElemMatchOperator);
        operators.put("$all", this::evaluateAllOperator);
    }

    private boolean evaluateInOperator(Object val, Object operand) {
        try {
            if (!(operand instanceof List)) {
                logger.warn("Operator $in expects List, got {} - treating as not matched",
                           operand == null ? "null" : operand.getClass().getSimpleName());
                return false;
            }
            List<?> list = (List<?>) operand;
            return list.contains(val);
        } catch (Exception e) {
            logger.warn("Error evaluating $in operator: {}", e.getMessage(), e);
            return false;
        }
    }

    private boolean evaluateNotInOperator(Object val, Object operand) {
        try {
            if (!(operand instanceof List)) {
                logger.warn("Operator $nin expects List, got {} - treating as not matched",
                           operand == null ? "null" : operand.getClass().getSimpleName());
                return false;
            }
            List<?> list = (List<?>) operand;
            return !list.contains(val);
        } catch (Exception e) {
            logger.warn("Error evaluating $nin operator: {}", e.getMessage(), e);
            return false;
        }
    }

    private boolean evaluateExistsOperator(Object val, Object operand) {
        try {
            if (!(operand instanceof Boolean)) {
                logger.warn("Operator $exists expects Boolean, got {} - treating as not matched",
                           operand == null ? "null" : operand.getClass().getSimpleName());
                return false;
            }
            boolean query = (Boolean) operand;
            return query ? val != null : val == null;
        } catch (Exception e) {
            logger.warn("Error evaluating $exists operator: {}", e.getMessage(), e);
            return false;
        }
    }

    private boolean evaluateRegexOperator(Object val, Object operand) {
        try {
            if (!(operand instanceof String)) {
                logger.warn("Operator $regex expects String pattern, got {} - treating as not matched",
                           operand == null ? "null" : operand.getClass().getSimpleName());
                return false;
            }
            Pattern pattern = Pattern.compile((String) operand);
            return pattern.matcher(String.valueOf(val)).find();
        } catch (PatternSyntaxException e) {
            logger.warn("Invalid regex pattern '{}': {} - treating as not matched", operand, e.getMessage());
            return false;
        } catch (Exception e) {
            logger.warn("Error evaluating $regex operator: {}", e.getMessage(), e);
            return false;
        }
    }

    private boolean evaluateSizeOperator(Object val, Object operand) {
        try {
            if (!(val instanceof List)) {
                logger.debug("Operator $size expects List value, got {} - treating as not matched",
                            val == null ? "null" : val.getClass().getSimpleName());
                return false;
            }
            if (!(operand instanceof Number)) {
                logger.warn("Operator $size expects Number operand, got {} - treating as not matched",
                           operand == null ? "null" : operand.getClass().getSimpleName());
                return false;
            }
            return ((List<?>) val).size() == ((Number) operand).intValue();
        } catch (Exception e) {
            logger.warn("Error evaluating $size operator: {}", e.getMessage(), e);
            return false;
        }
    }

    private boolean evaluateElemMatchOperator(Object val, Object operand) {
        try {
            if (!(val instanceof List)) {
                logger.debug("Operator $elemMatch expects List value, got {} - treating as not matched",
                            val == null ? "null" : val.getClass().getSimpleName());
                return false;
            }
            if (!(operand instanceof Map)) {
                logger.warn("Operator $elemMatch expects Map operand, got {} - treating as not matched",
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
            logger.warn("Error evaluating $elemMatch operator: {}", e.getMessage(), e);
            return false;
        }
    }

    private boolean evaluateAllOperator(Object val, Object operand) {
        try {
            if (!(val instanceof List<?> valList)) {
                logger.debug("Operator $all expects List value, got {} - treating as not matched",
                            val == null ? "null" : val.getClass().getSimpleName());
                return false;
            }
            if (!(operand instanceof List)) {
                logger.warn("Operator $all expects List operand, got {} - treating as not matched",
                           operand == null ? "null" : operand.getClass().getSimpleName());
                return false;
            }
            List<?> queryList = (List<?>) operand;
            return valList.containsAll(queryList);
        } catch (Exception e) {
            logger.warn("Error evaluating $all operator: {}", e.getMessage(), e);
            return false;
        }
    }

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
        if (val == null) return "null";
        if (val instanceof List) return "array";
        if (val instanceof String) return "string";
        if (val instanceof Number) return "number";
        if (val instanceof Boolean) return "boolean";
        if (val instanceof Map) return "object";
        return val.getClass().getSimpleName().toLowerCase();
    }

    private InnerResult matchValue(Object val, Object query, String path) {
        if (val == null) {
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

        logger.warn("Unknown query type: {} - treating as UNDETERMINED",
                   query == null ? "null" : query.getClass().getSimpleName());
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

    private InnerResult evaluateOperatorQuery(Object val, Map<String, Object> queryMap) {
        boolean allMatched = true;

        for (Map.Entry<String, Object> entry : queryMap.entrySet()) {
            String op = entry.getKey();
            if (!op.startsWith("$")) continue;

            OperatorHandler handler = operators.get(op);
            if (handler == null) {
                logger.warn("Unknown operator '{}' - marking rule as UNDETERMINED", op);
                return InnerResult.undetermined("Unknown operator: " + op);
            }

            try {
                if (!handler.evaluate(val, entry.getValue())) {
                    allMatched = false;
                    break;
                }
            } catch (Exception e) {
                logger.warn("Error evaluating operator '{}': {} - marking as UNDETERMINED", op, e.getMessage(), e);
                return InnerResult.undetermined("Error evaluating operator " + op + ": " + e.getMessage());
            }
        }

        return allMatched ? InnerResult.matched() : InnerResult.notMatched();
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
            Object subVal = valMap.get(key);

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
}