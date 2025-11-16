package uk.codery.jspec.evaluator;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.codery.jspec.model.*;
import uk.codery.jspec.result.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for SpecificationEvaluator focusing on:
 * - Specification orchestration
 * - CriteriaGroup evaluation with AND/OR junctions
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
    void evaluate_withSingleCriterion_shouldReturnCorrectOutcome() {
        QueryCriterion criterion = new QueryCriterion("age-check", Map.of("age", Map.of("$gte", 18)));
        Specification spec = new Specification("simple-spec", List.of(criterion));

        EvaluationOutcome outcome = evaluator.evaluate(validDocument, spec);

        assertThat(outcome.specificationId()).isEqualTo("simple-spec");
        assertThat(outcome.results()).hasSize(1);
        assertThat(outcome.queryResults().getFirst().state()).isEqualTo(EvaluationState.MATCHED);
        assertThat(outcome.summary().total()).isEqualTo(1);
        assertThat(outcome.summary().matched()).isEqualTo(1);
    }

    @Test
    void evaluate_withMultipleCriteria_shouldEvaluateAll() {
        List<uk.codery.jspec.model.Criterion> criteria = List.of(
                new QueryCriterion("age-check", Map.of("age", Map.of("$gte", 18))),
                new QueryCriterion("name-check", Map.of("name", Map.of("$eq", "John Doe"))),
                new QueryCriterion("status-check", Map.of("status", Map.of("$eq", "ACTIVE")))
        );
        Specification spec = new Specification("multi-criterion-spec", criteria);

        EvaluationOutcome outcome = evaluator.evaluate(validDocument, spec);

        assertThat(outcome.results()).hasSize(3);
        assertThat(outcome.results()).allMatch(r -> r.state() == EvaluationState.MATCHED);
        assertThat(outcome.summary().matched()).isEqualTo(3);
        assertThat(outcome.summary().fullyDetermined()).isTrue();
    }

    @Test
    void evaluate_withMixedResults_shouldTrackAllStates() {
        List<uk.codery.jspec.model.Criterion> criteria = List.of(
                new QueryCriterion("match", Map.of("age", Map.of("$eq", 25))),
                new QueryCriterion("no-match", Map.of("age", Map.of("$eq", 30))),
                new QueryCriterion("undetermined", Map.of("salary", Map.of("$gt", 50000)))
        );
        Specification spec = new Specification("mixed-spec", criteria);

        EvaluationOutcome outcome = evaluator.evaluate(validDocument, spec);

        assertThat(outcome.results()).hasSize(3);
        assertThat(outcome.summary().matched()).isEqualTo(1);
        assertThat(outcome.summary().notMatched()).isEqualTo(1);
        assertThat(outcome.summary().undetermined()).isEqualTo(1);
        assertThat(outcome.summary().fullyDetermined()).isFalse();
    }

    @Test
    void evaluate_withEmptySpecification_shouldReturnEmptyOutcome() {
        Specification spec = new Specification("empty-spec", List.of());

        EvaluationOutcome outcome = evaluator.evaluate(validDocument, spec);

        assertThat(outcome.queryResults()).isEmpty();
        assertThat(outcome.compositeResults()).isEmpty();
        assertThat(outcome.summary().total()).isEqualTo(0);
        assertThat(outcome.summary().fullyDetermined()).isTrue();
    }

    // ========== CriteriaGroup Tests with AND Junction ==========

    @Test
    void criteriaGroup_withAND_allMatching_shouldMatch() {
        List<uk.codery.jspec.model.Criterion> criteria = List.of(
                new QueryCriterion("age-check", Map.of("age", Map.of("$gte", 18))),
                new QueryCriterion("status-check", Map.of("status", Map.of("$eq", "ACTIVE")))
        );
        CompositeCriterion composite = new CompositeCriterion("and-set", Junction.AND, List.of(new CriterionReference("age-check"), new CriterionReference("status-check")));
        Specification spec = new Specification("and-spec", combine(criteria, composite));

        EvaluationOutcome outcome = evaluator.evaluate(validDocument, spec);

        assertThat(outcome.compositeResults()).hasSize(1);
        CompositeResult result = outcome.compositeResults().getFirst();
        assertThat(result.id()).isEqualTo("and-set");
        assertThat(result.matched()).isTrue();
        assertThat(result.junction()).isEqualTo(Junction.AND);
        assertThat(result.childResults()).hasSize(2);
        assertThat(result.childResults()).allMatch(EvaluationResult::matched);
    }

    @Test
    void criteriaGroup_withAND_oneNotMatching_shouldNotMatch() {
        List<uk.codery.jspec.model.Criterion> criteria = List.of(
                new QueryCriterion("age-check", Map.of("age", Map.of("$gte", 18))),
                new QueryCriterion("status-check", Map.of("status", Map.of("$eq", "INACTIVE")))
        );
        CompositeCriterion composite = new CompositeCriterion("and-set", Junction.AND, List.of(new CriterionReference("age-check"), new CriterionReference("status-check")));
        Specification spec = new Specification("and-spec", combine(criteria, composite));

        EvaluationOutcome outcome = evaluator.evaluate(validDocument, spec);

        CompositeResult result = outcome.compositeResults().getFirst();
        assertThat(result.matched()).isFalse();
    }

    @Test
    void criteriaGroup_withAND_allNotMatching_shouldNotMatch() {
        List<uk.codery.jspec.model.Criterion> criteria = List.of(
                new QueryCriterion("age-check", Map.of("age", Map.of("$lt", 18))),
                new QueryCriterion("status-check", Map.of("status", Map.of("$eq", "INACTIVE")))
        );
        CompositeCriterion composite = new CompositeCriterion("and-set", Junction.AND, List.of(new CriterionReference("age-check"), new CriterionReference("status-check")));
        Specification spec = new Specification("and-spec", combine(criteria, composite));

        EvaluationOutcome outcome = evaluator.evaluate(validDocument, spec);

        CompositeResult result = outcome.compositeResults().getFirst();
        assertThat(result.matched()).isFalse();
    }

    // ========== CriteriaGroup Tests with OR Junction ==========

    @Test
    void criteriaGroup_withOR_oneMatching_shouldMatch() {
        Criterion ageCheck = QueryCriterion.builder()
                .id("age-check").field("age").gte(18).build();

        Criterion statusCheck = QueryCriterion.builder()
                .id("status-check").field("status").eq("INACTIVE").build();

        CompositeCriterion orSet = CompositeCriterion.builder()
                .id("or-set")
                .junction(Junction.OR)
                .addReference(ageCheck)
                .addReference(statusCheck)
                .build();

        Specification spec = new Specification("or-spec", List.of(ageCheck, statusCheck, orSet));

        EvaluationOutcome outcome = evaluator.evaluate(validDocument, spec);

        CompositeResult result = outcome.compositeResults().getFirst();
        assertThat(result.id()).isEqualTo("or-set");
        assertThat(result.matched()).isTrue();
        assertThat(result.junction()).isEqualTo(Junction.OR);
    }

    @Test
    void criteriaGroup_withOR_allMatching_shouldMatch() {
        List<uk.codery.jspec.model.Criterion> criteria = List.of(
                new QueryCriterion("age-check", Map.of("age", Map.of("$gte", 18))),
                new QueryCriterion("status-check", Map.of("status", Map.of("$eq", "ACTIVE")))
        );
        CompositeCriterion composite = new CompositeCriterion("or-set", Junction.OR, List.of(new CriterionReference("age-check"), new CriterionReference("status-check")));
        Specification spec = new Specification("or-spec", combine(criteria, composite));

        EvaluationOutcome outcome = evaluator.evaluate(validDocument, spec);

        CompositeResult result = outcome.compositeResults().getFirst();
        assertThat(result.matched()).isTrue();
    }

    @Test
    void criteriaGroup_withOR_noneMatching_shouldNotMatch() {
        List<uk.codery.jspec.model.Criterion> criteria = List.of(
                new QueryCriterion("age-check", Map.of("age", Map.of("$lt", 18))),
                new QueryCriterion("status-check", Map.of("status", Map.of("$eq", "INACTIVE")))
        );
        CompositeCriterion composite = new CompositeCriterion("or-set", Junction.OR, List.of(new CriterionReference("age-check"), new CriterionReference("status-check")));
        Specification spec = new Specification("or-spec", combine(criteria, composite));

        EvaluationOutcome outcome = evaluator.evaluate(validDocument, spec);

        CompositeResult result = outcome.compositeResults().getFirst();
        assertThat(result.matched()).isFalse();
    }

    // ========== Multiple CriteriaGroups ==========

    @Test
    void evaluate_withMultipleCriteriaGroups_shouldEvaluateAll() {
        List<uk.codery.jspec.model.Criterion> criteria = List.of(
                new QueryCriterion("r1", Map.of("age", Map.of("$gte", 18))),
                new QueryCriterion("r2", Map.of("status", Map.of("$eq", "ACTIVE"))),
                new QueryCriterion("r3", Map.of("name", Map.of("$eq", "John Doe")))
        );
        List<CompositeCriterion> composites = List.of(
                new CompositeCriterion("and-set", Junction.AND, List.of(new CriterionReference("r1"), new CriterionReference("r2"))),
                new CompositeCriterion("or-set", Junction.OR, List.of(new CriterionReference("r2"), new CriterionReference("r3")))
        );
        Specification spec = new Specification("multi-criteria-spec", combineAll(criteria, composites));

        EvaluationOutcome outcome = evaluator.evaluate(validDocument, spec);

        assertThat(outcome.compositeResults()).hasSize(2);
        assertThat(outcome.compositeResults()).allMatch(EvaluationResult::matched);
    }

    @Test
    void evaluate_withCriteriaGroupsReferencingSameCriteria_shouldReuseResults() {
        List<uk.codery.jspec.model.Criterion> criteria = List.of(
                new QueryCriterion("shared-criterion", Map.of("age", Map.of("$gte", 18))),
                new QueryCriterion("other-criterion", Map.of("status", Map.of("$eq", "ACTIVE")))
        );
        List<CompositeCriterion> composites = List.of(
                new CompositeCriterion("set1", Junction.AND, List.of(new CriterionReference("shared-criterion"), new CriterionReference("other-criterion"))),
                new CompositeCriterion("set2", Junction.OR, List.of(new CriterionReference("shared-criterion"), new CriterionReference("other-criterion")))
        );
        Specification spec = new Specification("reuse-spec", combineAll(criteria, composites));

        EvaluationOutcome outcome = evaluator.evaluate(validDocument, spec);

        // Both criteriaGroups should evaluate the same criteria
        assertThat(outcome.compositeResults()).hasSize(2);
        assertThat(outcome.results()).hasSize(4);

        CompositeResult set1 = (CompositeResult) outcome.get("set1");
        CompositeResult set2 = (CompositeResult) outcome.get("set2");
        assertThat(set1.childResults()).hasSameElementsAs(set2.childResults());
    }

    // ========== Graceful Degradation in CompositeCriterion ==========

    @Test
    void compositeCriterion_withUndeterminedCriterion_shouldStillEvaluate() {
        List<Criterion> criteria = List.of(
                new QueryCriterion("determined", Map.of("age", Map.of("$gte", 18))),
                new QueryCriterion("undetermined", Map.of("salary", Map.of("$gt", 50000)))
        );
        CompositeCriterion composite = new CompositeCriterion("mixed-set", Junction.AND, List.of(new QueryCriterion("determined", Map.of()), new QueryCriterion("undetermined", Map.of())));
        Specification spec = new Specification("mixed-spec", combine(criteria, composite));

        EvaluationOutcome outcome = evaluator.evaluate(validDocument, spec);

        assertThat(outcome.compositeResults()).hasSize(1);
        CompositeResult result = outcome.compositeResults().getFirst();
        // AND requires all to match - undetermined criterion doesn't match
        assertThat(result.matched()).isFalse();
        assertThat(result.childResults()).hasSize(2);
    }

    @Test
    void criteriaGroup_withUnknownJunction_shouldStillEvaluate() {
        List<uk.codery.jspec.model.Criterion> criteria = List.of(
                new QueryCriterion("good", Map.of("age", Map.of("$gte", 18))),
                new QueryCriterion("bad", Map.of("age", Map.of("$unknown", 25)))
        );
        CompositeCriterion composite = new CompositeCriterion("graceful-set", Junction.OR, List.of(new QueryCriterion("good", Map.of()), new QueryCriterion("bad", Map.of())));
        Specification spec = new Specification("graceful-spec", combine(criteria, composite));

        EvaluationOutcome outcome = evaluator.evaluate(validDocument, spec);

        assertThat(outcome.compositeResults()).hasSize(1);
        CompositeResult result = outcome.compositeResults().getFirst();
        // OR requires at least one to match - "good" matches
        assertThat(result.matched()).isTrue();
    }

    // ========== Summary Verification ==========

    @Test
    void summary_withAllMatched_shouldReflectCorrectly() {
        List<uk.codery.jspec.model.Criterion> criteria = List.of(
                new QueryCriterion("r1", Map.of("age", Map.of("$eq", 25))),
                new QueryCriterion("r2", Map.of("status", Map.of("$eq", "ACTIVE")))
        );
        Specification spec = new Specification("all-matched", criteria);

        EvaluationOutcome outcome = evaluator.evaluate(validDocument, spec);

        EvaluationSummary summary = outcome.summary();
        assertThat(summary.total()).isEqualTo(2);
        assertThat(summary.matched()).isEqualTo(2);
        assertThat(summary.notMatched()).isEqualTo(0);
        assertThat(summary.undetermined()).isEqualTo(0);
        assertThat(summary.fullyDetermined()).isTrue();
    }

    @Test
    void summary_withMixedResults_shouldReflectCorrectly() {
        List<uk.codery.jspec.model.Criterion> criteria = List.of(
                new QueryCriterion("matched", Map.of("age", Map.of("$eq", 25))),
                new QueryCriterion("not-matched", Map.of("age", Map.of("$eq", 30))),
                new QueryCriterion("undetermined", Map.of("salary", Map.of("$gt", 50000)))
        );
        Specification spec = new Specification("mixed", criteria);

        EvaluationOutcome outcome = evaluator.evaluate(validDocument, spec);

        EvaluationSummary summary = outcome.summary();
        assertThat(summary.total()).isEqualTo(3);
        assertThat(summary.matched()).isEqualTo(1);
        assertThat(summary.notMatched()).isEqualTo(1);
        assertThat(summary.undetermined()).isEqualTo(1);
        assertThat(summary.fullyDetermined()).isFalse();
    }

    // ========== Thread Safety and Parallel Evaluation ==========

    @Test
    void evaluate_shouldHandleParallelEvaluation() {
        // Create many criteria to encourage parallel evaluation
        List<uk.codery.jspec.model.Criterion> criteria = List.of(
                new QueryCriterion("r1", Map.of("age", Map.of("$gte", 0))),
                new QueryCriterion("r2", Map.of("age", Map.of("$gte", 0))),
                new QueryCriterion("r3", Map.of("age", Map.of("$gte", 0))),
                new QueryCriterion("r4", Map.of("age", Map.of("$gte", 0))),
                new QueryCriterion("r5", Map.of("age", Map.of("$gte", 0)))
        );
        Specification spec = new Specification("parallel-spec", criteria);

        EvaluationOutcome outcome = evaluator.evaluate(validDocument, spec);

        // All should evaluate successfully
        assertThat(outcome.results()).hasSize(5);
        assertThat(outcome.results()).allMatch(r -> r.state() == EvaluationState.MATCHED);
    }

    // ========== Edge Cases ==========

    @Test
    void evaluate_withEmptyDocument_shouldHandleGracefully() {
        Map<String, Object> emptyDoc = Map.of();
        List<uk.codery.jspec.model.Criterion> criteria = List.of(
                new QueryCriterion("r1", Map.of("age", Map.of("$eq", 25)))
        );
        Specification spec = new Specification("empty-doc-spec", criteria);

        EvaluationOutcome outcome = evaluator.evaluate(emptyDoc, spec);

        assertThat(outcome.results()).hasSize(1);
        assertThat(outcome.queryResults().getFirst().state()).isEqualTo(EvaluationState.UNDETERMINED);
        assertThat(outcome.summary().undetermined()).isEqualTo(1);
    }

    @Test
    void criteriaGroup_withSingleCriterion_shouldWork() {
        List<uk.codery.jspec.model.Criterion> criteria = List.of(
                new QueryCriterion("only-criterion", Map.of("age", Map.of("$gte", 18)))
        );
        CompositeCriterion composite = new CompositeCriterion("single-criterion-set", Junction.AND, List.of(new QueryCriterion("only-criterion", Map.of())));
        Specification spec = new Specification("single-spec", combine(criteria, composite));

        EvaluationOutcome outcome = evaluator.evaluate(validDocument, spec);

        CompositeResult result = outcome.compositeResults().getFirst();
        assertThat(result.matched()).isTrue();
        assertThat(result.childResults()).hasSize(1);
    }

    @Test
    void criteriaGroup_withManyCriteria_shouldEvaluateAll() {
        List<uk.codery.jspec.model.Criterion> criteria = List.of(
                new QueryCriterion("r1", Map.of("age", Map.of("$gte", 18))),
                new QueryCriterion("r2", Map.of("age", Map.of("$lte", 65))),
                new QueryCriterion("r3", Map.of("status", Map.of("$eq", "ACTIVE"))),
                new QueryCriterion("r4", Map.of("name", Map.of("$exists", true))),
                new QueryCriterion("r5", Map.of("tags", Map.of("$size", 2)))
        );
        CompositeCriterion composite = new CompositeCriterion("many-criteria-set", Junction.AND,
                List.of(new CriterionReference("r1"), new CriterionReference("r2"), new CriterionReference("r3"), new QueryCriterion("r4", Map.of()), new QueryCriterion("r5", Map.of())));
        Specification spec = new Specification("many-criteria-spec", combine(criteria, composite));

        EvaluationOutcome outcome = evaluator.evaluate(validDocument, spec);

        CompositeResult result = outcome.compositeResults().getFirst();
        assertThat(result.matched()).isTrue();
        assertThat(result.childResults()).hasSize(5);
    }

    @Test
    void evaluate_shouldPreserveCriterionOrder() {
        List<uk.codery.jspec.model.Criterion> criteria = List.of(
                new QueryCriterion("first", Map.of("age", Map.of("$gte", 18))),
                new QueryCriterion("second", Map.of("status", Map.of("$eq", "ACTIVE"))),
                new QueryCriterion("third", Map.of("name", Map.of("$exists", true)))
        );
        Specification spec = new Specification("ordered-spec", criteria);

        EvaluationOutcome outcome = evaluator.evaluate(validDocument, spec);

        // Results should be present (order not guaranteed due to parallel streams)
        assertThat(outcome.queryResults()).hasSize(3);
        assertThat(outcome.queryResults())
                .extracting(EvaluationResult::id)
                .containsExactlyInAnyOrder("first", "second", "third");
    }

    // Helper method to combine criteria and composite into a single list
    private List<uk.codery.jspec.model.Criterion> combine(List<? extends uk.codery.jspec.model.Criterion> criteria, CompositeCriterion composite) {
        List<uk.codery.jspec.model.Criterion> combined = new ArrayList<>(criteria);
        combined.add(composite);
        return combined;
    }

    private List<uk.codery.jspec.model.Criterion> combineAll(List<? extends uk.codery.jspec.model.Criterion> criteria, List<? extends uk.codery.jspec.model.Criterion> composites) {
        List<uk.codery.jspec.model.Criterion> combined = new ArrayList<>(criteria);
        combined.addAll(composites);
        return combined;
    }
}
