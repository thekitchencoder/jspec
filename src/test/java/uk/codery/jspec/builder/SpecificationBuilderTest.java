package uk.codery.jspec.builder;

import org.junit.jupiter.api.Test;
import uk.codery.jspec.evaluator.SpecificationEvaluator;
import uk.codery.jspec.model.CompositeCriterion;
import uk.codery.jspec.model.QueryCriterion;
import uk.codery.jspec.model.Specification;
import uk.codery.jspec.result.EvaluationOutcome;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for SpecificationBuilder.
 */
class SpecificationBuilderTest {

    // ==================== Basic Builder Tests ====================

    @Test
    void builder_createsSpecificationWithId() {
        QueryCriterion criterion = QueryCriterion.builder()
                .id("test")
                .field("field").eq("value")
                .build();

        Specification spec = Specification.builder()
                .id("test-spec")
                .addCriterion(criterion)
                .build();

        assertThat(spec.id()).isEqualTo("test-spec");
        assertThat(spec.criteria()).hasSize(1);
    }

    @Test
    void builder_createsSpecificationWithMultipleCriteria() {
        QueryCriterion criterion1 = QueryCriterion.builder()
                .id("check1")
                .field("age").gte(18)
                .build();

        QueryCriterion criterion2 = QueryCriterion.builder()
                .id("check2")
                .field("status").eq("active")
                .build();

        Specification spec = Specification.builder()
                .id("multi-spec")
                .addCriterion(criterion1)
                .addCriterion(criterion2)
                .build();

        assertThat(spec.criteria()).hasSize(2);
        assertThat(spec.criteria().get(0).id()).isEqualTo("check1");
        assertThat(spec.criteria().get(1).id()).isEqualTo("check2");
    }

    @Test
    void builder_criteriaVarargs() {
        QueryCriterion criterion1 = QueryCriterion.builder()
                .id("check1")
                .field("age").gte(18)
                .build();

        QueryCriterion criterion2 = QueryCriterion.builder()
                .id("check2")
                .field("status").eq("active")
                .build();

        Specification spec = Specification.builder()
                .id("varargs-spec")
                .criteria(criterion1, criterion2)
                .build();

        assertThat(spec.criteria()).hasSize(2);
    }

    @Test
    void builder_criteriaList() {
        QueryCriterion criterion1 = QueryCriterion.builder()
                .id("check1")
                .field("age").gte(18)
                .build();

        QueryCriterion criterion2 = QueryCriterion.builder()
                .id("check2")
                .field("status").eq("active")
                .build();

        List<uk.codery.jspec.model.Criterion> criteriaList = List.of(criterion1, criterion2);

        Specification spec = Specification.builder()
                .id("list-spec")
                .criteria(criteriaList)
                .build();

        assertThat(spec.criteria()).hasSize(2);
    }

    @Test
    void builder_criteriaReplacesPreviousCriteria() {
        QueryCriterion criterion1 = QueryCriterion.builder()
                .id("check1")
                .field("age").gte(18)
                .build();

        QueryCriterion criterion2 = QueryCriterion.builder()
                .id("check2")
                .field("status").eq("active")
                .build();

        Specification spec = Specification.builder()
                .id("replace-spec")
                .addCriterion(criterion1)
                .criteria(criterion2)  // This should replace, not add
                .build();

        assertThat(spec.criteria()).hasSize(1);
        assertThat(spec.criteria().getFirst().id()).isEqualTo("check2");
    }

    @Test
    void builder_criteriaVarargsReplacePreviousCriteria() {
        QueryCriterion criterion1 = QueryCriterion.builder()
                .id("check1")
                .field("age").gte(18)
                .build();

        QueryCriterion criterion2 = QueryCriterion.builder()
                .id("check2")
                .field("status").eq("active")
                .build();

        QueryCriterion criterion3 = QueryCriterion.builder()
                .id("check3")
                .field("name").exists(true)
                .build();

        Specification spec = Specification.builder()
                .id("replace-varargs-spec")
                .addCriterion(criterion1)
                .criteria(criterion2, criterion3)  // This should replace
                .build();

        assertThat(spec.criteria()).hasSize(2);
        assertThat(spec.criteria().get(0).id()).isEqualTo("check2");
        assertThat(spec.criteria().get(1).id()).isEqualTo("check3");
    }

    @Test
    void builder_emptyCriteriaList() {
        Specification spec = Specification.builder()
                .id("empty-spec")
                .build();

        assertThat(spec.criteria()).isEmpty();
    }

    // ==================== Composite Criterion Tests ====================

    @Test
    void builder_withCompositeCriterion() {
        QueryCriterion query1 = QueryCriterion.builder()
                .id("age-check")
                .field("age").gte(18)
                .build();

        QueryCriterion query2 = QueryCriterion.builder()
                .id("status-check")
                .field("status").eq("active")
                .build();

        CompositeCriterion composite = CompositeCriterion.builder()
                .id("eligibility")
                .and()
                .criteria(query1, query2)
                .build();

        Specification spec = Specification.builder()
                .id("composite-spec")
                .addCriterion(query1)
                .addCriterion(query2)
                .addCriterion(composite)
                .build();

        assertThat(spec.criteria()).hasSize(3);
        assertThat(spec.criteria().get(2)).isInstanceOf(CompositeCriterion.class);
    }

    // ==================== Validation Tests ====================

    @Test
    void builder_throwsOnNullId() {
        assertThatThrownBy(() ->
                Specification.builder()
                        .id(null)
        ).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("null or empty");
    }

    @Test
    void builder_throwsOnEmptyId() {
        assertThatThrownBy(() ->
                Specification.builder()
                        .id("")
        ).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("null or empty");
    }

    @Test
    void builder_throwsOnBuildWithoutId() {
        QueryCriterion criterion = QueryCriterion.builder()
                .id("test")
                .field("field").eq("value")
                .build();

        assertThatThrownBy(() ->
                Specification.builder()
                        .addCriterion(criterion)
                        .build()
        ).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("must be set");
    }

    @Test
    void builder_throwsOnNullCriterion() {
        assertThatThrownBy(() ->
                Specification.builder()
                        .id("test-spec")
                        .addCriterion(null)
        ).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot be null");
    }

    @Test
    void builder_handlesNullVarargs() {
        // Passing null to varargs should not add anything
        Specification spec = Specification.builder()
                .id("null-varargs-spec")
                .criteria((uk.codery.jspec.model.Criterion[]) null)
                .build();

        assertThat(spec.criteria()).isEmpty();
    }

    @Test
    void builder_handlesNullList() {
        // Passing null list should not add anything
        Specification spec = Specification.builder()
                .id("null-list-spec")
                .criteria((List<uk.codery.jspec.model.Criterion>) null)
                .build();

        assertThat(spec.criteria()).isEmpty();
    }

    // ==================== buildEvaluator Tests ====================

    @Test
    void buildEvaluator_createsEvaluatorDirectly() {
        QueryCriterion criterion = QueryCriterion.builder()
                .id("age-check")
                .field("age").gte(18)
                .build();

        SpecificationEvaluator evaluator = Specification.builder()
                .id("direct-spec")
                .addCriterion(criterion)
                .buildEvaluator();

        assertThat(evaluator).isNotNull();

        // Test that the evaluator works
        Map<String, Object> document = Map.of("age", 25);
        EvaluationOutcome outcome = evaluator.evaluate(document);

        assertThat(outcome.specificationId()).isEqualTo("direct-spec");
        assertThat(outcome.matched("age-check")).isTrue();
    }

    @Test
    void buildEvaluator_throwsOnBuildWithoutId() {
        QueryCriterion criterion = QueryCriterion.builder()
                .id("test")
                .field("field").eq("value")
                .build();

        assertThatThrownBy(() ->
                Specification.builder()
                        .addCriterion(criterion)
                        .buildEvaluator()
        ).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("must be set");
    }

    @Test
    void buildEvaluator_withComplexSpecification() {
        QueryCriterion query1 = QueryCriterion.builder()
                .id("age-check")
                .field("age").gte(18)
                .build();

        QueryCriterion query2 = QueryCriterion.builder()
                .id("status-check")
                .field("status").eq("active")
                .build();

        CompositeCriterion composite = CompositeCriterion.builder()
                .id("eligibility")
                .and()
                .criteria(query1, query2)
                .build();

        SpecificationEvaluator evaluator = Specification.builder()
                .id("complex-spec")
                .addCriterion(query1)
                .addCriterion(query2)
                .addCriterion(composite)
                .buildEvaluator();

        Map<String, Object> document = Map.of(
                "age", 25,
                "status", "active"
        );

        EvaluationOutcome outcome = evaluator.evaluate(document);

        assertThat(outcome.matched("age-check")).isTrue();
        assertThat(outcome.matched("status-check")).isTrue();
        assertThat(outcome.matched("eligibility")).isTrue();
        assertThat(outcome.allMatched()).isTrue();
    }

    // ==================== Chaining Tests ====================

    @Test
    void builder_methodChaining() {
        QueryCriterion criterion1 = QueryCriterion.builder()
                .id("check1")
                .field("a").eq(1)
                .build();

        QueryCriterion criterion2 = QueryCriterion.builder()
                .id("check2")
                .field("b").eq(2)
                .build();

        // Ensure all methods return the builder for chaining
        SpecificationBuilder builder = Specification.builder();

        SpecificationBuilder result1 = builder.id("chaining-spec");
        assertThat(result1).isSameAs(builder);

        SpecificationBuilder result2 = builder.addCriterion(criterion1);
        assertThat(result2).isSameAs(builder);

        SpecificationBuilder result3 = builder.addCriterion(criterion2);
        assertThat(result3).isSameAs(builder);

        Specification spec = builder.build();
        assertThat(spec.criteria()).hasSize(2);
    }

    // ==================== Constructor Test ====================

    @Test
    void constructor_createsEmptyBuilder() {
        SpecificationBuilder builder = new SpecificationBuilder();

        // Should be able to set id and build
        Specification spec = builder.id("empty").build();
        assertThat(spec.id()).isEqualTo("empty");
        assertThat(spec.criteria()).isEmpty();
    }
}
