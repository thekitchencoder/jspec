package uk.codery.rules;

import java.util.List;
import java.util.stream.Collectors;

public record RuleSetResult(
        String id,
        Operator operator,
        List<EvaluationResult> ruleResults,
        boolean matched) implements Result {

    @Override
    public String reason() {
        return ruleResults.stream().map(EvaluationResult::reason).collect(Collectors.joining(", "));
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("RuleSet ").append(id).append(" (").append(operator).append("): ");
        sb.append("Matched: ").append(matched).append("\n");
        ruleResults.forEach(result -> {
            sb.append("  ").append(result.rule().id()).append(": ").append(result.matched()).append("\n");
            if (!result.missingPaths().isEmpty()) {
                sb.append("    Missing: ").append(String.join(", ", result.missingPaths())).append("\n");
            }
        });
        return sb.toString();
    }
}
