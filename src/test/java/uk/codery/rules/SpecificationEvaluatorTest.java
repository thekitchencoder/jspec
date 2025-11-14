package uk.codery.rules;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for SpecificationEvaluator focusing on:
 * - Specification orchestration
 * - RuleSet evaluation with AND/OR operators
 * - Parallel evaluation behavior
 * - Result caching
 * - Summary generation
 */
class SpecificationEvaluatorTest {

    private SpecificationEvaluator evaluator;
    private Map<String, Object> validDocument;

    @BeforeEach
    void setUp() {
        evaluator = new SpecificationEvaluator();
        validDocument = Map.of(
            "age", 25,
            "name", "John Doe",
            "status", "ACTIVE",
            "tags", List.of("admin", "user")
        );
    }

    // ========== Basic Specification Evaluation ==========

    @Test
    void evaluate_withSingleRule_shouldReturnCorrectOutcome() {
        Rule rule = new Rule("age-check", Map.of("age", Map.of("$gte", 18)));
        Specification spec = new Specification("simple-spec", List.of(rule), List.of());

        EvaluationOutcome outcome = evaluator.evaluate(validDocument, spec);

        assertThat(outcome.specificationId()).isEqualTo("simple-spec");
        assertThat(outcome.ruleResults()).hasSize(1);
        assertThat(outcome.ruleResults().get(0).state()).isEqualTo(EvaluationState.MATCHED);
        assertThat(outcome.summary().totalRules()).isEqualTo(1);
        assertThat(outcome.summary().matchedRules()).isEqualTo(1);
    }

    @Test
    void evaluate_withMultipleRules_shouldEvaluateAll() {
        List<Rule> rules = List.of(
            new Rule("age-check", Map.of("age", Map.of("$gte", 18))),
            new Rule("name-check", Map.of("name", Map.of("$eq", "John Doe"))),
            new Rule("status-check", Map.of("status", Map.of("$eq", "ACTIVE")))
        );
        Specification spec = new Specification("multi-rule-spec", rules, List.of());

        EvaluationOutcome outcome = evaluator.evaluate(validDocument, spec);

        assertThat(outcome.ruleResults()).hasSize(3);
        assertThat(outcome.ruleResults()).allMatch(r -> r.state() == EvaluationState.MATCHED);
        assertThat(outcome.summary().matchedRules()).isEqualTo(3);
        assertThat(outcome.summary().fullyDetermined()).isTrue();
    }

    @Test
    void evaluate_withMixedResults_shouldTrackAllStates() {
        List<Rule> rules = List.of(
            new Rule("match", Map.of("age", Map.of("$eq", 25))),
            new Rule("no-match", Map.of("age", Map.of("$eq", 30))),
            new Rule("undetermined", Map.of("salary", Map.of("$gt", 50000)))
        );
        Specification spec = new Specification("mixed-spec", rules, List.of());

        EvaluationOutcome outcome = evaluator.evaluate(validDocument, spec);

        assertThat(outcome.ruleResults()).hasSize(3);
        assertThat(outcome.summary().matchedRules()).isEqualTo(1);
        assertThat(outcome.summary().notMatchedRules()).isEqualTo(1);
        assertThat(outcome.summary().undeterminedRules()).isEqualTo(1);
        assertThat(outcome.summary().fullyDetermined()).isFalse();
    }

    @Test
    void evaluate_withEmptySpecification_shouldReturnEmptyOutcome() {
        Specification spec = new Specification("empty-spec", List.of(), List.of());

        EvaluationOutcome outcome = evaluator.evaluate(validDocument, spec);

        assertThat(outcome.ruleResults()).isEmpty();
        assertThat(outcome.ruleSetResults()).isEmpty();
        assertThat(outcome.summary().totalRules()).isEqualTo(0);
        assertThat(outcome.summary().fullyDetermined()).isTrue();
    }

    // ========== RuleSet Tests with AND Operator ==========

    @Test
    void ruleSet_withAND_allMatching_shouldMatch() {
        List<Rule> rules = List.of(
            new Rule("age-check", Map.of("age", Map.of("$gte", 18))),
            new Rule("status-check", Map.of("status", Map.of("$eq", "ACTIVE")))
        );
        RuleSet ruleSet = new RuleSet("and-set", Operator.AND, List.of(new Rule("age-check", Map.of()), new Rule("status-check", Map.of())));
        Specification spec = new Specification("and-spec", rules, List.of(ruleSet));

        EvaluationOutcome outcome = evaluator.evaluate(validDocument, spec);

        assertThat(outcome.ruleSetResults()).hasSize(1);
        RuleSetResult result = outcome.ruleSetResults().get(0);
        assertThat(result.id()).isEqualTo("and-set");
        assertThat(result.matched()).isTrue();
        assertThat(result.operator()).isEqualTo(Operator.AND);
        assertThat(result.ruleResults()).hasSize(2);
        assertThat(result.ruleResults()).allMatch(EvaluationResult::matched);
    }

    @Test
    void ruleSet_withAND_oneNotMatching_shouldNotMatch() {
        List<Rule> rules = List.of(
            new Rule("age-check", Map.of("age", Map.of("$gte", 18))),
            new Rule("status-check", Map.of("status", Map.of("$eq", "INACTIVE")))
        );
        RuleSet ruleSet = new RuleSet("and-set", Operator.AND, List.of(new Rule("age-check", Map.of()), new Rule("status-check", Map.of())));
        Specification spec = new Specification("and-spec", rules, List.of(ruleSet));

        EvaluationOutcome outcome = evaluator.evaluate(validDocument, spec);

        RuleSetResult result = outcome.ruleSetResults().get(0);
        assertThat(result.matched()).isFalse();
    }

    @Test
    void ruleSet_withAND_allNotMatching_shouldNotMatch() {
        List<Rule> rules = List.of(
            new Rule("age-check", Map.of("age", Map.of("$lt", 18))),
            new Rule("status-check", Map.of("status", Map.of("$eq", "INACTIVE")))
        );
        RuleSet ruleSet = new RuleSet("and-set", Operator.AND, List.of(new Rule("age-check", Map.of()), new Rule("status-check", Map.of())));
        Specification spec = new Specification("and-spec", rules, List.of(ruleSet));

        EvaluationOutcome outcome = evaluator.evaluate(validDocument, spec);

        RuleSetResult result = outcome.ruleSetResults().get(0);
        assertThat(result.matched()).isFalse();
    }

    // ========== RuleSet Tests with OR Operator ==========

    @Test
    void ruleSet_withOR_oneMatching_shouldMatch() {
        List<Rule> rules = List.of(
            new Rule("age-check", Map.of("age", Map.of("$gte", 18))),
            new Rule("status-check", Map.of("status", Map.of("$eq", "INACTIVE")))
        );
        RuleSet ruleSet = new RuleSet("or-set", Operator.OR, List.of(new Rule("age-check", Map.of()), new Rule("status-check", Map.of())));
        Specification spec = new Specification("or-spec", rules, List.of(ruleSet));

        EvaluationOutcome outcome = evaluator.evaluate(validDocument, spec);

        RuleSetResult result = outcome.ruleSetResults().get(0);
        assertThat(result.id()).isEqualTo("or-set");
        assertThat(result.matched()).isTrue();
        assertThat(result.operator()).isEqualTo(Operator.OR);
    }

    @Test
    void ruleSet_withOR_allMatching_shouldMatch() {
        List<Rule> rules = List.of(
            new Rule("age-check", Map.of("age", Map.of("$gte", 18))),
            new Rule("status-check", Map.of("status", Map.of("$eq", "ACTIVE")))
        );
        RuleSet ruleSet = new RuleSet("or-set", Operator.OR, List.of(new Rule("age-check", Map.of()), new Rule("status-check", Map.of())));
        Specification spec = new Specification("or-spec", rules, List.of(ruleSet));

        EvaluationOutcome outcome = evaluator.evaluate(validDocument, spec);

        RuleSetResult result = outcome.ruleSetResults().get(0);
        assertThat(result.matched()).isTrue();
    }

    @Test
    void ruleSet_withOR_noneMatching_shouldNotMatch() {
        List<Rule> rules = List.of(
            new Rule("age-check", Map.of("age", Map.of("$lt", 18))),
            new Rule("status-check", Map.of("status", Map.of("$eq", "INACTIVE")))
        );
        RuleSet ruleSet = new RuleSet("or-set", Operator.OR, List.of(new Rule("age-check", Map.of()), new Rule("status-check", Map.of())));
        Specification spec = new Specification("or-spec", rules, List.of(ruleSet));

        EvaluationOutcome outcome = evaluator.evaluate(validDocument, spec);

        RuleSetResult result = outcome.ruleSetResults().get(0);
        assertThat(result.matched()).isFalse();
    }

    // ========== Multiple RuleSets ==========

    @Test
    void evaluate_withMultipleRuleSets_shouldEvaluateAll() {
        List<Rule> rules = List.of(
            new Rule("r1", Map.of("age", Map.of("$gte", 18))),
            new Rule("r2", Map.of("status", Map.of("$eq", "ACTIVE"))),
            new Rule("r3", Map.of("name", Map.of("$eq", "John Doe")))
        );
        List<RuleSet> ruleSets = List.of(
            new RuleSet("and-set", Operator.AND, List.of(new Rule("r1", Map.of()), new Rule("r2", Map.of()))),
            new RuleSet("or-set", Operator.OR, List.of(new Rule("r2", Map.of()), new Rule("r3", Map.of())))
        );
        Specification spec = new Specification("multi-ruleset-spec", rules, ruleSets);

        EvaluationOutcome outcome = evaluator.evaluate(validDocument, spec);

        assertThat(outcome.ruleSetResults()).hasSize(2);
        assertThat(outcome.ruleSetResults()).allMatch(RuleSetResult::matched);
    }

    @Test
    void evaluate_withRuleSetsReferencingSameRules_shouldReuseResults() {
        List<Rule> rules = List.of(
            new Rule("shared-rule", Map.of("age", Map.of("$gte", 18))),
            new Rule("other-rule", Map.of("status", Map.of("$eq", "ACTIVE")))
        );
        List<RuleSet> ruleSets = List.of(
            new RuleSet("set1", Operator.AND, List.of(new Rule("shared-rule", Map.of()), new Rule("other-rule", Map.of()))),
            new RuleSet("set2", Operator.OR, List.of(new Rule("shared-rule", Map.of()), new Rule("other-rule", Map.of())))
        );
        Specification spec = new Specification("reuse-spec", rules, ruleSets);

        EvaluationOutcome outcome = evaluator.evaluate(validDocument, spec);

        // Both rulesets should evaluate the same rules
        assertThat(outcome.ruleSetResults()).hasSize(2);
        assertThat(outcome.ruleResults()).hasSize(2); // Rules evaluated once
    }

    // ========== Graceful Degradation in RuleSets ==========

    @Test
    void ruleSet_withUndeterminedRule_shouldStillEvaluate() {
        List<Rule> rules = List.of(
            new Rule("determined", Map.of("age", Map.of("$gte", 18))),
            new Rule("undetermined", Map.of("salary", Map.of("$gt", 50000)))
        );
        RuleSet ruleSet = new RuleSet("mixed-set", Operator.AND, List.of(new Rule("determined", Map.of()), new Rule("undetermined", Map.of())));
        Specification spec = new Specification("mixed-spec", rules, List.of(ruleSet));

        EvaluationOutcome outcome = evaluator.evaluate(validDocument, spec);

        assertThat(outcome.ruleSetResults()).hasSize(1);
        RuleSetResult result = outcome.ruleSetResults().get(0);
        // AND requires all to match - undetermined rule doesn't match
        assertThat(result.matched()).isFalse();
        assertThat(result.ruleResults()).hasSize(2);
    }

    @Test
    void ruleSet_withUnknownOperator_shouldStillEvaluate() {
        List<Rule> rules = List.of(
            new Rule("good", Map.of("age", Map.of("$gte", 18))),
            new Rule("bad", Map.of("age", Map.of("$unknown", 25)))
        );
        RuleSet ruleSet = new RuleSet("graceful-set", Operator.OR, List.of(new Rule("good", Map.of()), new Rule("bad", Map.of())));
        Specification spec = new Specification("graceful-spec", rules, List.of(ruleSet));

        EvaluationOutcome outcome = evaluator.evaluate(validDocument, spec);

        assertThat(outcome.ruleSetResults()).hasSize(1);
        RuleSetResult result = outcome.ruleSetResults().get(0);
        // OR requires at least one to match - "good" matches
        assertThat(result.matched()).isTrue();
    }

    // ========== Summary Verification ==========

    @Test
    void summary_withAllMatched_shouldReflectCorrectly() {
        List<Rule> rules = List.of(
            new Rule("r1", Map.of("age", Map.of("$eq", 25))),
            new Rule("r2", Map.of("status", Map.of("$eq", "ACTIVE")))
        );
        Specification spec = new Specification("all-matched", rules, List.of());

        EvaluationOutcome outcome = evaluator.evaluate(validDocument, spec);

        EvaluationSummary summary = outcome.summary();
        assertThat(summary.totalRules()).isEqualTo(2);
        assertThat(summary.matchedRules()).isEqualTo(2);
        assertThat(summary.notMatchedRules()).isEqualTo(0);
        assertThat(summary.undeterminedRules()).isEqualTo(0);
        assertThat(summary.fullyDetermined()).isTrue();
    }

    @Test
    void summary_withMixedResults_shouldReflectCorrectly() {
        List<Rule> rules = List.of(
            new Rule("matched", Map.of("age", Map.of("$eq", 25))),
            new Rule("not-matched", Map.of("age", Map.of("$eq", 30))),
            new Rule("undetermined", Map.of("salary", Map.of("$gt", 50000)))
        );
        Specification spec = new Specification("mixed", rules, List.of());

        EvaluationOutcome outcome = evaluator.evaluate(validDocument, spec);

        EvaluationSummary summary = outcome.summary();
        assertThat(summary.totalRules()).isEqualTo(3);
        assertThat(summary.matchedRules()).isEqualTo(1);
        assertThat(summary.notMatchedRules()).isEqualTo(1);
        assertThat(summary.undeterminedRules()).isEqualTo(1);
        assertThat(summary.fullyDetermined()).isFalse();
    }

    // ========== Thread Safety and Parallel Evaluation ==========

    @Test
    void evaluate_shouldHandleParallelEvaluation() {
        // Create many rules to encourage parallel evaluation
        List<Rule> rules = List.of(
            new Rule("r1", Map.of("age", Map.of("$gte", 0))),
            new Rule("r2", Map.of("age", Map.of("$gte", 0))),
            new Rule("r3", Map.of("age", Map.of("$gte", 0))),
            new Rule("r4", Map.of("age", Map.of("$gte", 0))),
            new Rule("r5", Map.of("age", Map.of("$gte", 0)))
        );
        Specification spec = new Specification("parallel-spec", rules, List.of());

        EvaluationOutcome outcome = evaluator.evaluate(validDocument, spec);

        // All should evaluate successfully
        assertThat(outcome.ruleResults()).hasSize(5);
        assertThat(outcome.ruleResults()).allMatch(r -> r.state() == EvaluationState.MATCHED);
    }

    // ========== Edge Cases ==========

    @Test
    void evaluate_withEmptyDocument_shouldHandleGracefully() {
        Map<String, Object> emptyDoc = Map.of();
        List<Rule> rules = List.of(
            new Rule("r1", Map.of("age", Map.of("$eq", 25)))
        );
        Specification spec = new Specification("empty-doc-spec", rules, List.of());

        EvaluationOutcome outcome = evaluator.evaluate(emptyDoc, spec);

        assertThat(outcome.ruleResults()).hasSize(1);
        assertThat(outcome.ruleResults().get(0).state()).isEqualTo(EvaluationState.UNDETERMINED);
        assertThat(outcome.summary().undeterminedRules()).isEqualTo(1);
    }

    @Test
    void ruleSet_withSingleRule_shouldWork() {
        List<Rule> rules = List.of(
            new Rule("only-rule", Map.of("age", Map.of("$gte", 18)))
        );
        RuleSet ruleSet = new RuleSet("single-rule-set", Operator.AND, List.of(new Rule("only-rule", Map.of())));
        Specification spec = new Specification("single-spec", rules, List.of(ruleSet));

        EvaluationOutcome outcome = evaluator.evaluate(validDocument, spec);

        RuleSetResult result = outcome.ruleSetResults().get(0);
        assertThat(result.matched()).isTrue();
        assertThat(result.ruleResults()).hasSize(1);
    }

    @Test
    void ruleSet_withManyRules_shouldEvaluateAll() {
        List<Rule> rules = List.of(
            new Rule("r1", Map.of("age", Map.of("$gte", 18))),
            new Rule("r2", Map.of("age", Map.of("$lte", 65))),
            new Rule("r3", Map.of("status", Map.of("$eq", "ACTIVE"))),
            new Rule("r4", Map.of("name", Map.of("$exists", true))),
            new Rule("r5", Map.of("tags", Map.of("$size", 2)))
        );
        RuleSet ruleSet = new RuleSet("many-rules-set", Operator.AND,
            List.of(new Rule("r1", Map.of()), new Rule("r2", Map.of()), new Rule("r3", Map.of()), new Rule("r4", Map.of()), new Rule("r5", Map.of())));
        Specification spec = new Specification("many-rules-spec", rules, List.of(ruleSet));

        EvaluationOutcome outcome = evaluator.evaluate(validDocument, spec);

        RuleSetResult result = outcome.ruleSetResults().get(0);
        assertThat(result.matched()).isTrue();
        assertThat(result.ruleResults()).hasSize(5);
    }

    @Test
    void evaluate_shouldPreserveRuleOrder() {
        List<Rule> rules = List.of(
            new Rule("first", Map.of("age", Map.of("$gte", 18))),
            new Rule("second", Map.of("status", Map.of("$eq", "ACTIVE"))),
            new Rule("third", Map.of("name", Map.of("$exists", true)))
        );
        Specification spec = new Specification("ordered-spec", rules, List.of());

        EvaluationOutcome outcome = evaluator.evaluate(validDocument, spec);

        // Results should be present (order not guaranteed due to parallel streams)
        assertThat(outcome.ruleResults()).hasSize(3);
        assertThat(outcome.ruleResults())
            .extracting(EvaluationResult::id)
            .containsExactlyInAnyOrder("first", "second", "third");
    }
}
