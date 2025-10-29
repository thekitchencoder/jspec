package uk.codery.rules;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static java.util.function.Predicate.not;

public record EvaluationResult(
        Rule rule,
        boolean matched,
        List<String> missingPaths) implements Result {

    public EvaluationResult {
        missingPaths = Optional.ofNullable(missingPaths)
                .map(Collections::unmodifiableList)
                .orElseGet(Collections::emptyList);
    }

    public static EvaluationResult missing(Rule rule){
        return missing(rule.id());
    }

    public static EvaluationResult missing(String id){
        return new EvaluationResult(new Rule(id), false, Collections.singletonList("rule definition"));
    }

    @Override
    public String id(){
        return rule.id();
    }

    @Override
    public String reason() {
        return matched
                ? null
                : Optional.ofNullable(missingPaths)
                .filter(not(List::isEmpty))
                .map(x -> "Missing data")
                .orElseGet(() -> String.format("Non-matching values at %s", rule.query()));
    }


    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Rule ").append(rule).append("\n");
        sb.append("  Matched: ").append(matched).append("\n");

        if(!missingPaths.isEmpty()) {
            sb.append("  Missing paths: ").append(String.join(", ", missingPaths)).append("\n");
        }

        Optional.ofNullable(reason()).ifPresent(reason ->
            sb.append("  Reason: ").append(reason).append("\n")
        );
        return sb.toString();
    }

}
