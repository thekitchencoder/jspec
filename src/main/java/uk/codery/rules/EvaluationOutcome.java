package uk.codery.rules;

import java.util.List;

public record EvaluationOutcome(String specificationId,
                                List<EvaluationResult> ruleResults,
                                List<RuleSetResult> ruleSetResults) {
}
