package uk.codery.jspec.model;

import org.junit.jupiter.api.Test;
import uk.codery.jspec.evaluator.EvaluationContext;
import uk.codery.jspec.result.EvaluationResult;
import uk.codery.jspec.result.EvaluationState;
import uk.codery.jspec.result.ReferenceResult;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for model classes: Specification, CriterionReference, CompositeCriterion.
 */
class ModelTypesTest {

    // ==================== Specification Tests ====================

    @Test
    void specification_createWithIdAndCriteria() {
        QueryCriterion query = new QueryCriterion("test", Map.of("field", "value"));
        Specification spec = new Specification("test-spec", List.of(query));

        assertThat(spec.id()).isEqualTo("test-spec");
        assertThat(spec.criteria()).hasSize(1);
    }

    @Test
    void specification_makesCriteriaImmutable() {
        QueryCriterion query = new QueryCriterion("test");
        List<Criterion> mutableList = new ArrayList<>();
        mutableList.add(query);

        Specification spec = new Specification("test-spec", mutableList);

        // Modify original
        mutableList.add(new QueryCriterion("another"));

        // Spec should be unaffected
        assertThat(spec.criteria()).hasSize(1);
    }

    @Test
    void specification_handlesNullCriteria() {
        Specification spec = new Specification("test-spec", null);

        assertThat(spec.criteria()).isEmpty();
    }

    @Test
    void specification_nonReferenceCriteria_filtersOutReferences() {
        QueryCriterion query = new QueryCriterion("query");
        CompositeCriterion composite = CompositeCriterion.builder()
                .id("composite")
                .and()
                .addCriterion(query)
                .build();
        CriterionReference reference = new CriterionReference("ref");

        Specification spec = new Specification("test-spec",
                List.of(query, composite, reference));

        List<Criterion> nonRefs = spec.nonReferenceCriteria();

        assertThat(nonRefs).hasSize(2);
        assertThat(nonRefs).contains(query, composite);
        assertThat(nonRefs).doesNotContain(reference);
    }

    @Test
    void specification_references_returnsOnlyReferences() {
        QueryCriterion query = new QueryCriterion("query");
        CriterionReference ref1 = new CriterionReference("ref1");
        CriterionReference ref2 = new CriterionReference("ref2");

        Specification spec = new Specification("test-spec",
                List.of(query, ref1, ref2));

        List<CriterionReference> refs = spec.references();

        assertThat(refs).hasSize(2);
        assertThat(refs).contains(ref1, ref2);
    }

    @Test
    void specification_queries_returnsOnlyQueryCriteria() {
        QueryCriterion query1 = new QueryCriterion("q1");
        QueryCriterion query2 = new QueryCriterion("q2");
        CompositeCriterion composite = CompositeCriterion.builder()
                .id("composite")
                .and()
                .addCriterion(query1)
                .build();

        Specification spec = new Specification("test-spec",
                List.of(query1, composite, query2));

        List<QueryCriterion> queries = spec.queries();

        assertThat(queries).hasSize(2);
        assertThat(queries).contains(query1, query2);
    }

    @Test
    void specification_composites_returnsOnlyCompositeCriteria() {
        QueryCriterion query = new QueryCriterion("query");
        CompositeCriterion composite1 = CompositeCriterion.builder()
                .id("c1")
                .and()
                .addCriterion(query)
                .build();
        CompositeCriterion composite2 = CompositeCriterion.builder()
                .id("c2")
                .or()
                .addCriterion(query)
                .build();

        Specification spec = new Specification("test-spec",
                List.of(query, composite1, composite2));

        List<CompositeCriterion> composites = spec.composites();

        assertThat(composites).hasSize(2);
        assertThat(composites).contains(composite1, composite2);
    }

    @Test
    void specification_builder_returnsBuilder() {
        assertThat(Specification.builder()).isNotNull()
                .isInstanceOf(uk.codery.jspec.builder.SpecificationBuilder.class);
    }

    // ==================== CriterionReference Tests ====================

    @Test
    void criterionReference_createsWithId() {
        CriterionReference ref = new CriterionReference("my-id");

        assertThat(ref.id()).isEqualTo("my-id");
    }

    @Test
    void criterionReference_evaluate_returnsCachedResult() {
        // Create a criterion that will match
        QueryCriterion criterion = new QueryCriterion("target", Map.of("field", Map.of("$eq", "value")));
        Map<String, Object> document = Map.of("field", "value");

        EvaluationContext context = new EvaluationContext(new uk.codery.jspec.evaluator.CriterionEvaluator());
        // Populate cache by evaluating the criterion
        EvaluationResult cachedResult = context.getOrEvaluate(criterion, document);

        CriterionReference ref = new CriterionReference("target");
        EvaluationResult result = ref.evaluate(document, context);

        assertThat(result).isInstanceOf(ReferenceResult.class);
        assertThat(result.state()).isEqualTo(EvaluationState.MATCHED);
        assertThat(((ReferenceResult) result).referencedResult()).isEqualTo(cachedResult);
    }

    @Test
    void criterionReference_evaluate_returnsUndetermined_whenNotCached() {
        EvaluationContext context = new EvaluationContext(null);

        CriterionReference ref = new CriterionReference("missing");
        EvaluationResult result = ref.evaluate(Map.of(), context);

        assertThat(result).isInstanceOf(ReferenceResult.class);
        assertThat(result.state()).isEqualTo(EvaluationState.UNDETERMINED);
        assertThat(result.reason()).contains("not found");
    }

    // ==================== CompositeCriterion Tests ====================

    @Test
    void compositeCriterion_createsWithAndJunction() {
        QueryCriterion query = new QueryCriterion("q1");
        CompositeCriterion composite = CompositeCriterion.builder()
                .id("test")
                .and()
                .addCriterion(query)
                .build();

        assertThat(composite.junction()).isEqualTo(Junction.AND);
    }

    @Test
    void compositeCriterion_createsWithOrJunction() {
        QueryCriterion query = new QueryCriterion("q1");
        CompositeCriterion composite = CompositeCriterion.builder()
                .id("test")
                .or()
                .addCriterion(query)
                .build();

        assertThat(composite.junction()).isEqualTo(Junction.OR);
    }

    @Test
    void compositeCriterion_containsCriteria() {
        QueryCriterion q1 = new QueryCriterion("q1");
        QueryCriterion q2 = new QueryCriterion("q2");

        CompositeCriterion composite = CompositeCriterion.builder()
                .id("test")
                .and()
                .criteria(q1, q2)
                .build();

        assertThat(composite.criteria()).hasSize(2);
    }

    @Test
    void compositeCriterion_builder_returnsBuilder() {
        assertThat(CompositeCriterion.builder()).isNotNull()
                .isInstanceOf(uk.codery.jspec.builder.CompositeCriterionBuilder.class);
    }

    @Test
    void compositeCriterion_addCriterion_addsToList() {
        QueryCriterion q1 = new QueryCriterion("q1");

        CompositeCriterion composite = CompositeCriterion.builder()
                .id("test")
                .and()
                .addCriterion(q1)
                .build();

        assertThat(composite.criteria()).hasSize(1);
        assertThat(composite.criteria().getFirst().id()).isEqualTo("q1");
    }

    @Test
    void compositeCriterion_addReference_addsReferenceToList() {
        CriterionReference ref = new CriterionReference("some-id");

        CompositeCriterion composite = CompositeCriterion.builder()
                .id("test")
                .and()
                .addReference(ref)
                .build();

        assertThat(composite.criteria()).hasSize(1);
        assertThat(composite.criteria().getFirst()).isInstanceOf(CriterionReference.class);
    }

    @Test
    void compositeCriterion_evaluate_withContext() {
        QueryCriterion q1 = new QueryCriterion("q1", Map.of("field", Map.of("$eq", "value")));

        CompositeCriterion composite = CompositeCriterion.builder()
                .id("test")
                .and()
                .criteria(q1)
                .build();

        uk.codery.jspec.evaluator.CriterionEvaluator evaluator = new uk.codery.jspec.evaluator.CriterionEvaluator();
        EvaluationContext context = new EvaluationContext(evaluator);
        Map<String, Object> document = Map.of("field", "value");

        EvaluationResult result = composite.evaluate(document, context);

        assertThat(result.state()).isEqualTo(EvaluationState.MATCHED);
    }

    // ==================== QueryCriterion Tests ====================

    @Test
    void queryCriterion_createsWithIdOnly() {
        QueryCriterion criterion = new QueryCriterion("test-id");

        assertThat(criterion.id()).isEqualTo("test-id");
        assertThat(criterion.query()).isEmpty();
    }

    @Test
    void queryCriterion_createsWithIdAndQuery() {
        Map<String, Object> query = Map.of("field", Map.of("$eq", "value"));
        QueryCriterion criterion = new QueryCriterion("test-id", query);

        assertThat(criterion.id()).isEqualTo("test-id");
        assertThat(criterion.query()).isEqualTo(query);
    }

    @Test
    void queryCriterion_builder_returnsBuilder() {
        assertThat(QueryCriterion.builder()).isNotNull()
                .isInstanceOf(uk.codery.jspec.builder.CriterionBuilder.class);
    }

    @Test
    void queryCriterion_evaluate_returnsMatchedForMatchingQuery() {
        QueryCriterion criterion = new QueryCriterion("test",
                Map.of("field", Map.of("$eq", "value")));

        uk.codery.jspec.evaluator.CriterionEvaluator evaluator = new uk.codery.jspec.evaluator.CriterionEvaluator();
        EvaluationContext context = new EvaluationContext(evaluator);
        Map<String, Object> document = Map.of("field", "value");

        EvaluationResult result = criterion.evaluate(document, context);

        assertThat(result.state()).isEqualTo(EvaluationState.MATCHED);
    }

    @Test
    void queryCriterion_evaluate_returnsNotMatchedForNonMatchingQuery() {
        QueryCriterion criterion = new QueryCriterion("test",
                Map.of("field", Map.of("$eq", "expected")));

        uk.codery.jspec.evaluator.CriterionEvaluator evaluator = new uk.codery.jspec.evaluator.CriterionEvaluator();
        EvaluationContext context = new EvaluationContext(evaluator);
        Map<String, Object> document = Map.of("field", "actual");

        EvaluationResult result = criterion.evaluate(document, context);

        assertThat(result.state()).isEqualTo(EvaluationState.NOT_MATCHED);
    }

    // ==================== Junction Tests ====================

    @Test
    void junction_hasAndAndOr() {
        assertThat(Junction.AND).isNotNull();
        assertThat(Junction.OR).isNotNull();
        assertThat(Junction.values()).hasSize(2);
    }

    // ==================== CompositeCriterionBuilder Validation Tests ====================

    @Test
    void compositeCriterionBuilder_throwsOnNullId() {
        assertThatThrownBy(() ->
                CompositeCriterion.builder().id(null)
        ).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("null or empty");
    }

    @Test
    void compositeCriterionBuilder_throwsOnEmptyId() {
        assertThatThrownBy(() ->
                CompositeCriterion.builder().id("")
        ).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("null or empty");
    }

    @Test
    void compositeCriterionBuilder_throwsOnNullJunction() {
        assertThatThrownBy(() ->
                CompositeCriterion.builder()
                        .id("test")
                        .junction(null)
        ).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot be null");
    }

    @Test
    void compositeCriterionBuilder_junction_setsExplicitly() {
        CompositeCriterion composite = CompositeCriterion.builder()
                .id("test")
                .junction(Junction.OR)
                .addCriterion(new QueryCriterion("q1"))
                .build();

        assertThat(composite.junction()).isEqualTo(Junction.OR);
    }

    @Test
    void compositeCriterionBuilder_throwsOnNullCriterion() {
        assertThatThrownBy(() ->
                CompositeCriterion.builder()
                        .id("test")
                        .addCriterion(null)
        ).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot be null");
    }

    @Test
    void compositeCriterionBuilder_throwsOnNullCriteriaVarargs() {
        assertThatThrownBy(() ->
                CompositeCriterion.builder()
                        .id("test")
                        .criteria((Criterion[]) null)
        ).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("At least one");
    }

    @Test
    void compositeCriterionBuilder_throwsOnEmptyCriteriaVarargs() {
        assertThatThrownBy(() ->
                CompositeCriterion.builder()
                        .id("test")
                        .criteria()
        ).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("At least one");
    }

    @Test
    void compositeCriterionBuilder_throwsOnNullCriteriaList() {
        assertThatThrownBy(() ->
                CompositeCriterion.builder()
                        .id("test")
                        .criteria((List<Criterion>) null)
        ).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("At least one");
    }

    @Test
    void compositeCriterionBuilder_throwsOnEmptyCriteriaList() {
        assertThatThrownBy(() ->
                CompositeCriterion.builder()
                        .id("test")
                        .criteria(Collections.emptyList())
        ).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("At least one");
    }

    @Test
    void compositeCriterionBuilder_throwsOnBuildWithoutId() {
        assertThatThrownBy(() ->
                CompositeCriterion.builder()
                        .addCriterion(new QueryCriterion("q1"))
                        .build()
        ).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("must be set");
    }

    @Test
    void compositeCriterionBuilder_throwsOnBuildWithoutCriteria() {
        assertThatThrownBy(() ->
                CompositeCriterion.builder()
                        .id("test")
                        .build()
        ).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("At least one");
    }

    @Test
    void compositeCriterionBuilder_criteriaReplacePrevious() {
        QueryCriterion q1 = new QueryCriterion("q1");
        QueryCriterion q2 = new QueryCriterion("q2");
        QueryCriterion q3 = new QueryCriterion("q3");

        CompositeCriterion composite = CompositeCriterion.builder()
                .id("test")
                .and()
                .addCriterion(q1)
                .criteria(q2, q3)  // Should replace q1
                .build();

        assertThat(composite.criteria()).hasSize(2);
        assertThat(composite.criteria().get(0).id()).isEqualTo("q2");
        assertThat(composite.criteria().get(1).id()).isEqualTo("q3");
    }

    @Test
    void compositeCriterionBuilder_criteriaListReplacesPrevious() {
        QueryCriterion q1 = new QueryCriterion("q1");
        QueryCriterion q2 = new QueryCriterion("q2");

        CompositeCriterion composite = CompositeCriterion.builder()
                .id("test")
                .and()
                .addCriterion(q1)
                .criteria(List.of(q2))  // Should replace q1
                .build();

        assertThat(composite.criteria()).hasSize(1);
        assertThat(composite.criteria().getFirst().id()).isEqualTo("q2");
    }

    @Test
    void compositeCriterionBuilder_addReference_addsReference() {
        QueryCriterion q1 = new QueryCriterion("q1");

        CompositeCriterion composite = CompositeCriterion.builder()
                .id("test")
                .and()
                .addReference(q1)  // Adds as reference
                .build();

        assertThat(composite.criteria()).hasSize(1);
        assertThat(composite.criteria().getFirst()).isInstanceOf(uk.codery.jspec.model.CriterionReference.class);
        assertThat(composite.criteria().getFirst().id()).isEqualTo("q1");
    }

    @Test
    void compositeCriterionBuilder_constructor_createsBuilder() {
        uk.codery.jspec.builder.CompositeCriterionBuilder builder = new uk.codery.jspec.builder.CompositeCriterionBuilder();

        // Default junction is AND
        CompositeCriterion composite = builder
                .id("test")
                .addCriterion(new QueryCriterion("q1"))
                .build();

        assertThat(composite.junction()).isEqualTo(Junction.AND);
    }

    @Test
    void compositeCriterionBuilder_chaining() {
        uk.codery.jspec.builder.CompositeCriterionBuilder builder = CompositeCriterion.builder();

        // All methods should return the same builder for chaining
        assertThat(builder.id("test")).isSameAs(builder);
        assertThat(builder.and()).isSameAs(builder);
        assertThat(builder.or()).isSameAs(builder);
        assertThat(builder.junction(Junction.AND)).isSameAs(builder);
        assertThat(builder.addCriterion(new QueryCriterion("q1"))).isSameAs(builder);
    }
}
