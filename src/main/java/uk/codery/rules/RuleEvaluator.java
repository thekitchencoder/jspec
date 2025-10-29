package uk.codery.rules;

import java.util.*;
import java.util.regex.Pattern;

class RuleEvaluator {
    private final Map<String, OperatorHandler> operators = new HashMap<>();

    interface OperatorHandler {
        boolean evaluate(Object val, Object operand);
    }

    private record InnerResult(boolean match, List<String> missingPaths){}

    public EvaluationResult evaluateRule(Object doc, Rule rule) {
        return Optional.of(rule)
                .map(Rule::query)
                .map(query -> matchValue(doc, query, ""))
                .map(result -> new EvaluationResult(rule, result.match, result.missingPaths))
                .orElseGet(() -> EvaluationResult.missing(rule));
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
        List<?> list = (List<?>) operand;
        return list.contains(val);
    }

    private boolean evaluateNotInOperator(Object val, Object operand) {
        List<?> list = (List<?>) operand;
        return !list.contains(val);
    }

    private boolean evaluateExistsOperator(Object val, Object operand) {
        boolean query = (Boolean) operand;
        return query ? val != null : val == null;
    }

    private boolean evaluateRegexOperator(Object val, Object operand) {
        Pattern pattern = Pattern.compile((String) operand);
        return pattern.matcher(String.valueOf(val)).find();
    }

    private boolean evaluateSizeOperator(Object val, Object operand) {
        if (!(val instanceof List)) return false;
        return ((List<?>) val).size() == ((Number) operand).intValue();
    }

    private boolean evaluateElemMatchOperator(Object val, Object operand) {
        if (!(val instanceof List)) return false;
        Map<String, Object> queryMap = (Map<String, Object>) operand;
        for (Object item : (List<?>) val) {
            if (matchValue(item, queryMap, "").match) {
                return true;
            }
        }
        return false;
    }

    private boolean evaluateAllOperator(Object val, Object operand) {
        if (!(val instanceof List<?> valList)) return false;
        List<?> queryList = (List<?>) operand;
        return valList.containsAll(queryList);
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

        return matchMapValue(val, (Map<String, Object>) query, path);
    }

    private boolean isSimpleQuery(Object query) {
        return !(query instanceof Map) && !(query instanceof List);
    }

    private InnerResult createMissingResult(String path) {
        String missingPath = path.isEmpty() ? "root" : path;
        return new InnerResult(false, List.of(missingPath));
    }

    private InnerResult matchSimpleValue(Object val, Object query) {
        boolean match = Objects.equals(val, query);
        return new InnerResult(match, List.of());
    }

    private InnerResult matchListValue(Object val, List<?> queryList, String path) {
        if (!(val instanceof List<?> valList)) {
            return new InnerResult(false, List.of());
        }

        if (valList.size() != queryList.size()) {
            return new InnerResult(false, List.of());
        }

        return matchListElements(valList, queryList, path);
    }

    private InnerResult matchListElements(List<?> valList, List<?> queryList, String path) {
        List<String> missingPaths = new ArrayList<>();
        boolean match = true;

        for (int i = 0; i < queryList.size(); i++) {
            String newPath = buildArrayPath(path, i);
            InnerResult subResult = matchValue(valList.get(i), queryList.get(i), newPath);

            if (!subResult.match) {
                match = false;
                missingPaths.addAll(subResult.missingPaths);
            }
        }

        return new InnerResult(match, missingPaths);
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
        boolean match = true;

        for (Map.Entry<String, Object> entry : queryMap.entrySet()) {
            String op = entry.getKey();
            if (!op.startsWith("$")) continue;

            OperatorHandler handler = operators.get(op);
            if (handler == null) {
                System.err.println("Unknown operator: " + op);
                continue;
            }

            if (!handler.evaluate(val, entry.getValue())) {
                match = false;
                break;
            }
        }

        return new InnerResult(match, List.of());
    }

    private InnerResult evaluateFieldQuery(Object val, Map<String, Object> queryMap, String path) {
        if (!(val instanceof Map)) {
            return new InnerResult(false, List.of());
        }

        Map<String, Object> valMap = (Map<String, Object>) val;
        return matchAllFields(valMap, queryMap, path);
    }

    private InnerResult matchAllFields(Map<String, Object> valMap, Map<String, Object> queryMap, String path) {
        List<String> missingPaths = new ArrayList<>();
        boolean allMatch = true;

        for (Map.Entry<String, Object> entry : queryMap.entrySet()) {
            String key = entry.getKey();
            Object subQuery = entry.getValue();
            String newPath = buildFieldPath(path, key);
            Object subVal = valMap.get(key);

            InnerResult subResult = matchValue(subVal, subQuery, newPath);
            if (!subResult.match) {
                allMatch = false;
                missingPaths.addAll(subResult.missingPaths);
            }
        }

        return new InnerResult(allMatch, missingPaths);
    }

    private String buildFieldPath(String path, String key) {
        return path.isEmpty() ? key : path + "." + key;
    }
}