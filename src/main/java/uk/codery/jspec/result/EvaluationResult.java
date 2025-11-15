package uk.codery.jspec.result;

import uk.codery.jspec.model.Criterion;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Result of evaluating a single criterion against a document.
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
 */
public record EvaluationResult(
        Criterion criterion,
        EvaluationState state,
        List<String> missingPaths,
        String failureReason) implements Result {

    public EvaluationResult {
        missingPaths = Optional.ofNullable(missingPaths)
                .map(Collections::unmodifiableList)
                .orElseGet(Collections::emptyList);
    }

    /**
     * Creates an UNDETERMINED result for a missing criterion definition.
     */
    public static EvaluationResult missing(Criterion criterion){
        return missing(criterion.id());
    }

    /**
     * Creates an UNDETERMINED result for a missing criterion definition.
     */
    public static EvaluationResult missing(String id){
        return new EvaluationResult(
                new Criterion(id),
                EvaluationState.UNDETERMINED,
                Collections.singletonList("criterion definition"),
                "Criterion definition not found"
        );
    }

    /**
     * Implements the Result interface contract.
     *
     * @return true only if state is MATCHED, false otherwise
     */
    @Override
    public boolean matched() {
        return state == EvaluationState.MATCHED;
    }

    /**
     * Returns true if the evaluation was deterministic (not UNDETERMINED).
     *
     * @return true if state is MATCHED or NOT_MATCHED
     */
    public boolean isDetermined() {
        return state != EvaluationState.UNDETERMINED;
    }

    @Override
    public String id(){
        return criterion.id();
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
        sb.append("  match: ").append(matched()).append("\n");
        sb.append("  query: ").append(criterion.query()).append("\n");
        sb.append("  state: ").append(state).append("\n");

        if(!missingPaths.isEmpty()) {
            sb.append("  missing: [").append(String.join(", ", missingPaths)).append("]\n");
        }

        Optional.ofNullable(reason()).ifPresent(reason ->
            sb.append("  reason: \"").append(reason).append("\"\n")
        );
        return sb.toString();
    }

}
