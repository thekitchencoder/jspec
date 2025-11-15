package uk.codery.jspec.result;

import uk.codery.jspec.model.Junction;

import java.util.List;
import java.util.stream.Collectors;

public record CriteriaGroupResult(
        String id,
        Junction junction,
        List<EvaluationResult> evaluationResults,
        boolean matched) implements Result {

    @Override
    public String reason() {
        return evaluationResults.stream().map(EvaluationResult::reason).collect(Collectors.joining(", "));
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("  - ").append(id).append(":\n");
        sb.append("    ").append("match: ").append(matched).append("\n");
        sb.append("    ").append("junction: ").append(junction).append("\n");
        sb.append("    criteria:\n");
        evaluationResults.forEach(result -> {
            sb.append("      - ").append(result.criterion().id()).append(": ").append(result.matched()).append("\n");
            if (!result.missingPaths().isEmpty()) {
                sb.append("        missing: ").append(String.join(", ", result.missingPaths())).append("\n");
            }
        });
        return sb.toString();
    }
}
