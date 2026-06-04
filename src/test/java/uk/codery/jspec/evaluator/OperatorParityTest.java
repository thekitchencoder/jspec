package uk.codery.jspec.evaluator;

import org.junit.jupiter.api.Test;
import uk.codery.jspec.model.QueryCriterion;
import uk.codery.jspec.operator.OperatorRegistry;
import uk.codery.jspec.result.EvaluationState;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The no-arg constructor and the OperatorRegistry.withDefaults() constructor
 * must expose the SAME operator set with the SAME behaviour.
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
    void comparisonOnIncomparableTypesIsNotMatchedOnBothConstructors() {
        // String value vs numeric operand: per the project contract, a type mismatch
        // is NOT_MATCHED (not UNDETERMINED). Both constructors must agree.
        Map<String, Object> q = Map.of("v", Map.of("$gt", 5)); // v = "hello"
        assertThat(eval(noArg, q)).isEqualTo(EvaluationState.NOT_MATCHED);
        assertThat(eval(viaRegistry, q)).isEqualTo(EvaluationState.NOT_MATCHED);
    }

    /**
     * Per-operator behavioural matrix: for a representative set of operators, the
     * no-arg and registry-backed constructors must produce identical tri-state
     * outcomes across match, no-match, and type-mismatch operands. This is the test
     * that actually defends the unification invariant — any divergence here is a real
     * bug, not a test problem. Document shape is {@code {"v":"hello","n":5}}.
     */
    @Test
    void perOperatorBehaviourMatrixIsIdenticalAcrossBothConstructors() {
        List<Map<String, Object>> queries = List.of(
                // $eq
                Map.of("v", Map.of("$eq", "hello")),   // match
                Map.of("v", Map.of("$eq", "nope")),    // no-match
                Map.of("n", Map.of("$eq", 5)),         // match (numeric)
                // $ne
                Map.of("v", Map.of("$ne", "nope")),    // match
                Map.of("v", Map.of("$ne", "hello")),   // no-match
                // $gt
                Map.of("n", Map.of("$gt", 1)),         // match
                Map.of("n", Map.of("$gt", 10)),        // no-match
                Map.of("v", Map.of("$gt", 5)),         // type-mismatch (String vs Number)
                // $gte
                Map.of("n", Map.of("$gte", 5)),        // match
                Map.of("n", Map.of("$gte", 6)),        // no-match
                Map.of("v", Map.of("$gte", 5)),        // type-mismatch
                // $lt
                Map.of("n", Map.of("$lt", 10)),        // match
                Map.of("n", Map.of("$lt", 1)),         // no-match
                Map.of("v", Map.of("$lt", 5)),         // type-mismatch
                // $lte
                Map.of("n", Map.of("$lte", 5)),        // match
                Map.of("n", Map.of("$lte", 4)),        // no-match
                Map.of("v", Map.of("$lte", 5)),        // type-mismatch
                // $in
                Map.of("v", Map.of("$in", List.of("hello", "world"))), // match
                Map.of("v", Map.of("$in", List.of("a", "b"))),         // no-match
                Map.of("v", Map.of("$in", "not-a-list")),              // type-mismatch
                // $contains
                Map.of("v", Map.of("$contains", "ell")),               // match
                Map.of("v", Map.of("$contains", "xyz")),               // no-match
                Map.of("n", Map.of("$contains", "x")),                 // type-mismatch (Number value)
                // $startsWith
                Map.of("v", Map.of("$startsWith", "he")),              // match
                Map.of("v", Map.of("$startsWith", "zz")),              // no-match
                Map.of("v", Map.of("$startsWith", 5)),                 // type-mismatch (Number operand)
                // $exists
                Map.of("v", Map.of("$exists", true)),                  // match
                Map.of("v", Map.of("$exists", false)),                 // no-match
                Map.of("v", Map.of("$exists", "yes")),                 // type-mismatch (non-Boolean operand)
                // $type
                Map.of("v", Map.of("$type", "string")),                // match
                Map.of("v", Map.of("$type", "number")),                // no-match
                Map.of("n", Map.of("$type", "number")),                // match (numeric)
                // $regex
                Map.of("v", Map.of("$regex", "^he")),                  // match
                Map.of("v", Map.of("$regex", "^zz")),                  // no-match
                Map.of("v", Map.of("$regex", 5)),                      // type-mismatch (Number operand)
                // $size
                Map.of("v", Map.of("$size", 3)),                       // no-match (String value, not a list)
                Map.of("n", Map.of("$size", 1))                        // no-match (Number value, not a list)
        );

        for (Map<String, Object> q : queries) {
            assertThat(eval(noArg, q))
                    .as(q.toString())
                    .isEqualTo(eval(viaRegistry, q));
        }
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
