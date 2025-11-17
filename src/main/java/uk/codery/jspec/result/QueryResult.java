package uk.codery.jspec.result;

import uk.codery.jspec.model.QueryCriterion;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Result of evaluating a {@link QueryCriterion} against a document.
 *
 * <p>Uses a tri-state model to distinguish between:
 * <ul>
 *   <li>MATCHED - Criterion evaluated successfully and condition is true</li>
 *   <li>NOT_MATCHED - Criterion evaluated successfully and condition is false</li>
 *   <li>UNDETERMINED - Criterion could not be evaluated due to errors or missing data</li>
 * </ul>
 *
 * <p>The {@code failureReason} provides details when state is UNDETERMINED,
 * helping developers debug issues with criteria or data.
 *
 * @param criterion the query criterion that was evaluated
 * @param state the tri-state evaluation result
 * @param missingPaths list of document paths that were missing during evaluation
 * @param failureReason explanation of why evaluation failed (UNDETERMINED only)
 * @see QueryCriterion
 * @see EvaluationState
 * @since 0.2.0
 */
public record QueryResult(
        QueryCriterion criterion,
        EvaluationState state,
        List<String> missingPaths,
        String failureReason) implements EvaluationResult {

    public QueryResult {
        missingPaths = Optional.ofNullable(missingPaths)
                .map(Collections::unmodifiableList)
                .orElseGet(Collections::emptyList);
    }

    /**
     * Creates a MATCHED query result.
     */
    public static QueryResult matched(QueryCriterion criterion) {
        return new QueryResult(criterion, EvaluationState.MATCHED, Collections.emptyList(), null);
    }

    /**
     * Creates a NOT_MATCHED query result.
     */
    public static QueryResult notMatched(QueryCriterion criterion, List<String> missingPaths) {
        return new QueryResult(criterion, EvaluationState.NOT_MATCHED, missingPaths, null);
    }

    /**
     * Creates an UNDETERMINED query result.
     */
    public static QueryResult undetermined(QueryCriterion criterion, String reason, List<String> missingPaths) {
        return new QueryResult(criterion, EvaluationState.UNDETERMINED, missingPaths, reason);
    }

    /**
     * Creates an UNDETERMINED result for a missing criterion definition.
     */
    public static QueryResult missing(QueryCriterion criterion) {
        return missing(criterion.id());
    }

    /**
     * Creates an UNDETERMINED result for a missing criterion definition.
     */
    public static QueryResult missing(String id) {
        return new QueryResult(
                new QueryCriterion(id),
                EvaluationState.UNDETERMINED,
                Collections.singletonList("criterion definition"),
                "Criterion definition not found"
        );
    }

    @Override
    public String id() {
        return criterion.id();
    }

    /**
     * Returns true if the evaluation was deterministic (not UNDETERMINED).
     *
     * @return true if state is MATCHED or NOT_MATCHED
     */
    public boolean isDetermined() {
        return state.determined();
    }

    @Override
    public String reason() {
        return switch (state) {
            case MATCHED -> null;
            case UNDETERMINED -> failureReason != null ? failureReason :
                    (missingPaths.isEmpty() ? "Evaluation failed" : "Missing data at: " + String.join(", ", missingPaths));
            case NOT_MATCHED -> missingPaths.isEmpty() ?
                    String.format("Non-matching values at %s", criterion.query()) :
                    "Missing data at: " + String.join(", ", missingPaths);
        };
    }

    // TODO external formatters (YAML,JSON,Text,etc) rather than YAML embedded in the toString method
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(criterion.id()).append(":\n");
        sb.append("  match: ").append(state.matched()).append("\n");
        sb.append("  query: ").append(criterion.query()).append("\n");
        sb.append("  state: ").append(state).append("\n");

        if (!missingPaths.isEmpty()) {
            sb.append("  missing: [").append(String.join(", ", missingPaths)).append("]\n");
        }

        Optional.ofNullable(reason()).ifPresent(reason ->
                sb.append("  reason: \"").append(reason).append("\"\n")
        );
        return sb.toString();
    }
}
