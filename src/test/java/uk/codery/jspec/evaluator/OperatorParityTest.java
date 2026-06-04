package uk.codery.jspec.evaluator;

import org.junit.jupiter.api.Test;
import uk.codery.jspec.model.QueryCriterion;
import uk.codery.jspec.operator.OperatorRegistry;
import uk.codery.jspec.result.EvaluationState;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The no-arg constructor and the OperatorRegistry.withDefaults() constructor
 * must expose the SAME operator set with the SAME behaviour. See
 * docs/plans/2026-06-04-operator-registry-unification.md.
 */
class OperatorParityTest {

    private final CriterionEvaluator noArg = new CriterionEvaluator();
    private final CriterionEvaluator viaRegistry =
            new CriterionEvaluator(OperatorRegistry.withDefaults());

    private static EvaluationState eval(CriterionEvaluator e, Map<String, Object> query) {
        return e.evaluateQuery(Map.of("v", "hello", "n", 5),
                new QueryCriterion("probe", query)).state();
    }

    @Test
    void stringOperatorsPresentInBothConstructors() {
        Map<String, Object> q = Map.of("v", Map.of("$startsWith", "he"));
        assertThat(eval(noArg, q)).isEqualTo(EvaluationState.MATCHED);
        assertThat(eval(viaRegistry, q)).isEqualTo(EvaluationState.MATCHED);
    }

    @Test
    void comparisonOperatorsBehaveIdenticallyInBothConstructors() {
        Map<String, Object> q = Map.of("n", Map.of("$gte", 5));
        assertThat(eval(noArg, q)).isEqualTo(eval(viaRegistry, q));
    }

    @Test
    void bothConstructorsExposeIdenticalOperatorKeySet() {
        assertThat(viaRegistry.supportedOperators())
                .isEqualTo(noArg.supportedOperators());
    }

    @Test
    void supportedOperatorsIncludesLogicalCombinators() {
        assertThat(noArg.supportedOperators()).contains("$and", "$or");
    }
}
