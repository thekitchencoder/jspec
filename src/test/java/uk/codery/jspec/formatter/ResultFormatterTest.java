package uk.codery.jspec.formatter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.codery.jspec.model.CompositeCriterion;
import uk.codery.jspec.model.CriterionReference;
import uk.codery.jspec.model.QueryCriterion;
import uk.codery.jspec.result.*;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for result formatters (JSON, YAML, Text).
 */
class ResultFormatterTest {

    private EvaluationOutcome sampleOutcome;
    private EvaluationOutcome complexOutcome;

    @BeforeEach
    void setUp() {
        // Simple outcome with one matched query result
        QueryCriterion criterion = QueryCriterion.builder()
                .id("age-check")
                .field("age").gte(18)
                .build();
        QueryResult result = QueryResult.matched(criterion);
        EvaluationSummary summary = EvaluationSummary.from(List.of(result));
        sampleOutcome = new EvaluationOutcome("test-spec", List.of(result), summary);

        // Complex outcome with multiple result types
        QueryCriterion query1 = QueryCriterion.builder()
                .id("age-check")
                .field("age").gte(18)
                .build();
        QueryResult queryResult1 = QueryResult.matched(query1);

        QueryCriterion query2 = QueryCriterion.builder()
                .id("country-check")
                .field("country").eq("US")
                .build();
        QueryResult queryResult2 = QueryResult.notMatched(query2, List.of("country"));

        QueryCriterion query3 = QueryCriterion.builder()
                .id("email-check")
                .field("email").exists(true)
                .build();
        QueryResult queryResult3 = QueryResult.undetermined(query3, "Missing data", List.of("email"));

        CompositeCriterion composite = CompositeCriterion.builder()
                .id("eligibility")
                .and()
                .criteria(query1, query2)
                .build();
        CompositeResult compositeResult = new CompositeResult(composite, EvaluationState.NOT_MATCHED,
                List.of(queryResult1, queryResult2));

        EvaluationSummary complexSummary = EvaluationSummary.from(
                List.of(queryResult1, queryResult2, queryResult3, compositeResult));
        complexOutcome = new EvaluationOutcome("complex-spec",
                List.of(queryResult1, queryResult2, queryResult3, compositeResult), complexSummary);
    }

    // ==================== JSON Formatter Tests ====================

    @Test
    void jsonFormatter_defaultPrettyPrint() {
        JsonResultFormatter formatter = new JsonResultFormatter();

        String json = formatter.format(sampleOutcome);

        assertThat(json).isNotBlank();
        assertThat(json).contains("\"specificationId\" : \"test-spec\"");
        assertThat(json).contains("\"state\" : \"MATCHED\"");
        assertThat(json).contains("\"age-check\"");
        assertThat(formatter.formatType()).isEqualTo("json");
        assertThat(formatter.isPrettyPrint()).isTrue();
    }

    @Test
    void jsonFormatter_compact() {
        JsonResultFormatter formatter = new JsonResultFormatter(false);

        String json = formatter.format(sampleOutcome);

        assertThat(json).isNotBlank();
        assertThat(json).doesNotContain("\n  ");  // No indentation
        assertThat(json).contains("\"specificationId\":\"test-spec\"");
        assertThat(formatter.isPrettyPrint()).isFalse();
    }

    @Test
    void jsonFormatter_complexOutcome() {
        JsonResultFormatter formatter = new JsonResultFormatter(true);

        String json = formatter.format(complexOutcome);

        assertThat(json).isNotBlank();
        assertThat(json).contains("\"specificationId\" : \"complex-spec\"");
        assertThat(json).contains("\"age-check\"");
        assertThat(json).contains("\"country-check\"");
        assertThat(json).contains("\"email-check\"");
        assertThat(json).contains("\"eligibility\"");
        assertThat(json).contains("\"matched\" : 1");
        assertThat(json).contains("\"notMatched\" : 2");
        assertThat(json).contains("\"undetermined\" : 1");
    }

    @Test
    void jsonFormatter_canDeserialize() throws Exception {
        JsonResultFormatter formatter = new JsonResultFormatter();
        String json = formatter.format(sampleOutcome);

        ObjectMapper mapper = new ObjectMapper();
        Object result = mapper.readValue(json, Object.class);

        assertThat(result)
                .isNotNull()
                .isInstanceOf(Map.class)
                .extracting("summary.fullyDetermined")
                .isEqualTo(true);
    }

    @Test
    void jsonFormatter_customObjectMapper() {
        ObjectMapper customMapper = new ObjectMapper();
        JsonResultFormatter formatter = new JsonResultFormatter(customMapper);

        String json = formatter.format(sampleOutcome);

        assertThat(json).isNotBlank();
        assertThat(formatter.getObjectMapper()).isSameAs(customMapper);
    }

    // ==================== YAML Formatter Tests ====================

    @Test
    void yamlFormatter_default() {
        YamlResultFormatter formatter = new YamlResultFormatter();

        String yaml = formatter.format(sampleOutcome);

        assertThat(yaml).isNotBlank();
        assertThat(yaml).contains("specificationId: test-spec");
        assertThat(yaml).contains("state: MATCHED");
        assertThat(yaml).contains("age-check");
        assertThat(formatter.formatType()).isEqualTo("yaml");
    }

    @Test
    void yamlFormatter_complexOutcome() {
        YamlResultFormatter formatter = new YamlResultFormatter();

        String yaml = formatter.format(complexOutcome);

        assertThat(yaml).isNotBlank();
        assertThat(yaml).contains("specificationId: complex-spec");
        assertThat(yaml).contains("age-check");
        assertThat(yaml).contains("country-check");
        assertThat(yaml).contains("email-check");
        assertThat(yaml).contains("eligibility");
        assertThat(yaml).contains("matched: 1");
        assertThat(yaml).contains("notMatched: 2");
        assertThat(yaml).contains("undetermined: 1");
    }

    @Test
    void yamlFormatter_canDeserialize() throws Exception {
        YamlResultFormatter formatter = new YamlResultFormatter();
        String yaml = formatter.format(sampleOutcome);

        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        Object result = mapper.readValue(yaml, Object.class);

        assertThat(result)
                .isNotNull()
                .isInstanceOf(Map.class)
                .extracting("summary.fullyDetermined")
                .isEqualTo(true);

    }

    @Test
    void yamlFormatter_customObjectMapper() {
        ObjectMapper customMapper = new ObjectMapper(new YAMLFactory());
        YamlResultFormatter formatter = new YamlResultFormatter(customMapper);

        String yaml = formatter.format(sampleOutcome);

        assertThat(yaml).isNotBlank();
        assertThat(formatter.objectMapper()).isSameAs(customMapper);
    }

    // ==================== Text Formatter Tests ====================

    @Test
    void textFormatter_default() {
        TextResultFormatter formatter = new TextResultFormatter();

        String text = formatter.format(sampleOutcome);

        assertThat(text).isNotBlank();
        assertThat(text).contains("Specification: test-spec");
        assertThat(text).contains("SUMMARY");
        assertThat(text).contains("RESULTS");
        assertThat(text).contains("age-check");
        assertThat(text).contains("MATCHED");
        assertThat(text).contains("[✓]");
        assertThat(formatter.formatType()).isEqualTo("text");
        assertThat(formatter.verbose()).isFalse();
    }

    @Test
    void textFormatter_verbose() {
        TextResultFormatter formatter = new TextResultFormatter(true);

        String text = formatter.format(complexOutcome);

        assertThat(text).isNotBlank();
        assertThat(text).contains("Specification: complex-spec");
        assertThat(text).contains("age-check");
        assertThat(text).contains("country-check");
        assertThat(text).contains("email-check");
        assertThat(text).contains("eligibility");
        assertThat(formatter.verbose()).isTrue();
    }

    @Test
    void textFormatter_showsStateIcons() {
        TextResultFormatter formatter = new TextResultFormatter();

        String text = formatter.format(complexOutcome);

        assertThat(text).contains("[✓]");  // MATCHED
        assertThat(text).contains("[✗]");  // NOT_MATCHED
        assertThat(text).contains("[?]");  // UNDETERMINED
    }

    @Test
    void textFormatter_showsSummaryStats() {
        TextResultFormatter formatter = new TextResultFormatter();

        String text = formatter.format(complexOutcome);

        assertThat(text).contains("Total:");
        assertThat(text).contains("4 criteria");
        assertThat(text).contains("Matched:");
        assertThat(text).contains("1 (25.0%)");
        assertThat(text).contains("Not Matched:");
        assertThat(text).contains("2 (50.0%)");
        assertThat(text).contains("Undetermined:");
        assertThat(text).contains("1 (25.0%)");
        assertThat(text).contains("Status:");
    }

    @Test
    void textFormatter_showsQueryDetails() {
        TextResultFormatter formatter = new TextResultFormatter();

        String text = formatter.format(sampleOutcome);

        assertThat(text).contains("Type: Query");
        assertThat(text).contains("Query:");
        assertThat(text).contains("age");
        assertThat(text).contains("$gte");
    }

    @Test
    void textFormatter_showsCompositeDetails() {
        TextResultFormatter formatter = new TextResultFormatter();

        String text = formatter.format(complexOutcome);

        assertThat(text).contains("Type: Composite");
        assertThat(text).contains("AND");
        assertThat(text).contains("Children:");
        assertThat(text).contains("Statistics:");
    }

    @Test
    void textFormatter_showsReasons() {
        QueryCriterion criterion = QueryCriterion.builder()
                .id("test")
                .field("field").eq("value")
                .build();
        QueryResult result = QueryResult.undetermined(criterion, "Test failure reason", Collections.emptyList());
        EvaluationSummary summary = EvaluationSummary.from(List.of(result));
        EvaluationOutcome outcome = new EvaluationOutcome("test-spec", List.of(result), summary);

        TextResultFormatter formatter = new TextResultFormatter();
        String text = formatter.format(outcome);

        assertThat(text).contains("Reason: Test failure reason");
    }

    // ==================== Format Comparison Tests ====================

    @Test
    void allFormatters_produceNonEmptyOutput() {
        JsonResultFormatter jsonFormatter = new JsonResultFormatter();
        YamlResultFormatter yamlFormatter = new YamlResultFormatter();
        TextResultFormatter textFormatter = new TextResultFormatter();

        String json = jsonFormatter.format(sampleOutcome);
        String yaml = yamlFormatter.format(sampleOutcome);
        String text = textFormatter.format(sampleOutcome);

        assertThat(json).isNotBlank();
        assertThat(yaml).isNotBlank();
        assertThat(text).isNotBlank();
    }

    @Test
    void allFormatters_includeSpecificationId() {
        JsonResultFormatter jsonFormatter = new JsonResultFormatter();
        YamlResultFormatter yamlFormatter = new YamlResultFormatter();
        TextResultFormatter textFormatter = new TextResultFormatter();

        String json = jsonFormatter.format(sampleOutcome);
        String yaml = yamlFormatter.format(sampleOutcome);
        String text = textFormatter.format(sampleOutcome);

        assertThat(json).contains("test-spec");
        assertThat(yaml).contains("test-spec");
        assertThat(text).contains("test-spec");
    }

    @Test
    void allFormatters_includeState() {
        JsonResultFormatter jsonFormatter = new JsonResultFormatter();
        YamlResultFormatter yamlFormatter = new YamlResultFormatter();
        TextResultFormatter textFormatter = new TextResultFormatter();

        String json = jsonFormatter.format(sampleOutcome);
        String yaml = yamlFormatter.format(sampleOutcome);
        String text = textFormatter.format(sampleOutcome);

        assertThat(json).contains("MATCHED");
        assertThat(yaml).contains("MATCHED");
        assertThat(text).contains("MATCHED");
    }

    @Test
    void formatters_haveUniqueFormatTypes() {
        JsonResultFormatter jsonFormatter = new JsonResultFormatter();
        YamlResultFormatter yamlFormatter = new YamlResultFormatter();
        TextResultFormatter textFormatter = new TextResultFormatter();

        assertThat(jsonFormatter.formatType()).isEqualTo("json");
        assertThat(yamlFormatter.formatType()).isEqualTo("yaml");
        assertThat(textFormatter.formatType()).isEqualTo("text");
    }

    // ==================== Edge Cases and Error Handling ====================

    @Test
    void formatters_handleEmptyResults() {
        EvaluationSummary emptySummary = EvaluationSummary.from(Collections.emptyList());
        EvaluationOutcome emptyOutcome = new EvaluationOutcome("empty-spec", Collections.emptyList(), emptySummary);

        JsonResultFormatter jsonFormatter = new JsonResultFormatter();
        YamlResultFormatter yamlFormatter = new YamlResultFormatter();
        TextResultFormatter textFormatter = new TextResultFormatter();

        String json = jsonFormatter.format(emptyOutcome);
        String yaml = yamlFormatter.format(emptyOutcome);
        String text = textFormatter.format(emptyOutcome);

        assertThat(json).isNotBlank();
        assertThat(yaml).isNotBlank();
        assertThat(text).isNotBlank();

        assertThat(json).contains("\"total\" : 0");
        assertThat(yaml).contains("total: 0");
        assertThat(text).contains("Total:          0 criteria");
    }

    @Test
    void formatters_handleReferenceResults() {
        QueryCriterion query = QueryCriterion.builder()
                .id("original-check")
                .field("status").eq("active")
                .build();
        QueryResult queryResult = QueryResult.matched(query);

        CriterionReference reference = new CriterionReference("original-check");
        ReferenceResult refResult = new ReferenceResult(reference, queryResult);

        EvaluationSummary summary = EvaluationSummary.from(List.of(queryResult, refResult));
        EvaluationOutcome outcome = new EvaluationOutcome("ref-spec", List.of(queryResult, refResult), summary);

        JsonResultFormatter jsonFormatter = new JsonResultFormatter();
        YamlResultFormatter yamlFormatter = new YamlResultFormatter();
        TextResultFormatter textFormatter = new TextResultFormatter(true);  // Verbose to show referenced result

        String json = jsonFormatter.format(outcome);
        String yaml = yamlFormatter.format(outcome);
        String text = textFormatter.format(outcome);

        assertThat(json).isNotBlank();
        assertThat(yaml).isNotBlank();
        assertThat(text).isNotBlank();

        assertThat(text).contains("Type: Reference");
        assertThat(text).contains("References: original-check");
    }

    @Test
    void textFormatter_handlesSpecialCharactersInIds() {
        QueryCriterion criterion = QueryCriterion.builder()
                .id("check-with-special-chars-#@!")
                .field("field").eq("value")
                .build();
        QueryResult result = QueryResult.matched(criterion);
        EvaluationSummary summary = EvaluationSummary.from(List.of(result));
        EvaluationOutcome outcome = new EvaluationOutcome("special-chars-spec", List.of(result), summary);

        TextResultFormatter formatter = new TextResultFormatter();
        String text = formatter.format(outcome);

        assertThat(text).contains("check-with-special-chars-#@!");
    }

    @Test
    void jsonFormatter_handlesSpecialCharactersInValues() {
        QueryCriterion criterion = QueryCriterion.builder()
                .id("test")
                .field("message").eq("Line1\nLine2\tTabbed")
                .build();
        QueryResult result = QueryResult.matched(criterion);
        EvaluationSummary summary = EvaluationSummary.from(List.of(result));
        EvaluationOutcome outcome = new EvaluationOutcome("test-spec", List.of(result), summary);

        JsonResultFormatter formatter = new JsonResultFormatter();
        String json = formatter.format(outcome);

        assertThat(json).isNotBlank();
        assertThat(json).contains("\\n");  // Newlines should be escaped
        assertThat(json).contains("\\t");  // Tabs should be escaped
    }

    @Test
    void textFormatter_handlesSingleCriterion() {
        QueryCriterion criterion = QueryCriterion.builder()
                .id("single")
                .field("field").eq("value")
                .build();
        QueryResult result = QueryResult.matched(criterion);
        EvaluationSummary summary = EvaluationSummary.from(List.of(result));
        EvaluationOutcome outcome = new EvaluationOutcome("single-spec", List.of(result), summary);

        TextResultFormatter formatter = new TextResultFormatter();
        String text = formatter.format(outcome);

        assertThat(text).contains("Total:          1 criterion");  // Singular form
        assertThat(text).contains("100.0%");
    }

    @Test
    void textFormatter_handlesZeroDivision() {
        EvaluationSummary emptySummary = EvaluationSummary.from(Collections.emptyList());
        EvaluationOutcome emptyOutcome = new EvaluationOutcome("empty-spec", Collections.emptyList(), emptySummary);

        TextResultFormatter formatter = new TextResultFormatter();
        String text = formatter.format(emptyOutcome);

        // Should not throw ArithmeticException with 0 total
        assertThat(text).isNotBlank();
        assertThat(text).contains("Total:          0 criteria");
        assertThat(text).doesNotContain("NaN");
        assertThat(text).doesNotContain("Infinity");
    }

    @Test
    void textFormatter_verboseShowsChildResults() {
        QueryCriterion query1 = QueryCriterion.builder()
                .id("child1")
                .field("a").eq(1)
                .build();
        QueryCriterion query2 = QueryCriterion.builder()
                .id("child2")
                .field("b").eq(2)
                .build();

        QueryResult result1 = QueryResult.matched(query1);
        QueryResult result2 = QueryResult.notMatched(query2, List.of("b"));

        CompositeCriterion composite = CompositeCriterion.builder()
                .id("parent")
                .and()
                .criteria(query1, query2)
                .build();
        CompositeResult compositeResult = new CompositeResult(composite, EvaluationState.NOT_MATCHED,
                List.of(result1, result2));

        EvaluationSummary summary = EvaluationSummary.from(List.of(compositeResult));
        EvaluationOutcome outcome = new EvaluationOutcome("verbose-spec", List.of(compositeResult), summary);

        TextResultFormatter verboseFormatter = new TextResultFormatter(true);
        TextResultFormatter nonVerboseFormatter = new TextResultFormatter(false);

        String verboseText = verboseFormatter.format(outcome);
        String nonVerboseText = nonVerboseFormatter.format(outcome);

        // Verbose should show child results
        assertThat(verboseText).contains("Child Results:");
        assertThat(verboseText).contains("child1");
        assertThat(verboseText).contains("child2");

        // Non-verbose should not show child results
        assertThat(nonVerboseText).doesNotContain("Child Results:");
    }

    @Test
    void yamlFormatter_outputDoesNotHaveDocumentStartMarker() {
        YamlResultFormatter formatter = new YamlResultFormatter();
        String yaml = formatter.format(sampleOutcome);

        // Should not start with --- (document start marker is disabled)
        assertThat(yaml).doesNotStartWith("---");
    }

    @Test
    void allFormatters_handleFullyDeterminedStatus() {
        // All matched - fully determined
        QueryCriterion criterion = QueryCriterion.builder()
                .id("test")
                .field("field").eq("value")
                .build();
        QueryResult result = QueryResult.matched(criterion);
        EvaluationSummary summary = EvaluationSummary.from(List.of(result));
        EvaluationOutcome outcome = new EvaluationOutcome("test-spec", List.of(result), summary);

        TextResultFormatter formatter = new TextResultFormatter();
        String text = formatter.format(outcome);

        assertThat(text).contains("Status:         Fully Determined");
        assertThat(summary.fullyDetermined()).isTrue();
    }

    @Test
    void allFormatters_handlePartiallyDeterminedStatus() {
        // Has undetermined - not fully determined
        QueryCriterion criterion = QueryCriterion.builder()
                .id("test")
                .field("field").eq("value")
                .build();
        QueryResult result = QueryResult.undetermined(criterion, "Failed", Collections.emptyList());
        EvaluationSummary summary = EvaluationSummary.from(List.of(result));
        EvaluationOutcome outcome = new EvaluationOutcome("test-spec", List.of(result), summary);

        TextResultFormatter formatter = new TextResultFormatter();
        String text = formatter.format(outcome);

        assertThat(text).contains("Status:         Partially Determined");
        assertThat(summary.fullyDetermined()).isFalse();
    }

    // ==================== Summary Formatter Tests ====================

    @Test
    void summaryFormatter_default() {
        SummaryResultFormatter formatter = new SummaryResultFormatter();

        String summary = formatter.format(sampleOutcome);

        assertThat(summary).isNotBlank();
        assertThat(summary).contains("test-spec evaluation summary");
        assertThat(summary).contains("Queries:");
        assertThat(summary).contains("Composites:");
        assertThat(formatter.formatType()).isEqualTo("summary");
        assertThat(formatter.showFailures()).isFalse();
    }

    @Test
    void summaryFormatter_showsQueryCounts() {
        SummaryResultFormatter formatter = new SummaryResultFormatter();

        String summary = formatter.format(complexOutcome);

        assertThat(summary).contains("Queries: 3 total");
        assertThat(summary).contains("1 passed");
        assertThat(summary).contains("2 failed");
    }

    @Test
    void summaryFormatter_showsCompositeCounts() {
        SummaryResultFormatter formatter = new SummaryResultFormatter();

        String summary = formatter.format(complexOutcome);

        assertThat(summary).contains("Composites: 1 total");
        assertThat(summary).contains("0 passed");
        assertThat(summary).contains("1 failed");
    }

    @Test
    void summaryFormatter_withShowFailures() {
        SummaryResultFormatter formatter = new SummaryResultFormatter(true);

        String summary = formatter.format(complexOutcome);

        assertThat(summary).isNotBlank();
        assertThat(summary).contains("Failed Queries:");
        assertThat(summary).contains("country-check");
        assertThat(summary).contains("email-check");
        assertThat(summary).contains("Failed Composites:");
        assertThat(summary).contains("eligibility");
        assertThat(formatter.showFailures()).isTrue();
    }

    @Test
    void summaryFormatter_noFailures_showsNoFailuresSection() {
        SummaryResultFormatter formatter = new SummaryResultFormatter(true);

        String summary = formatter.format(sampleOutcome);

        // All passed, so no failures section
        assertThat(summary).doesNotContain("Failed Queries:");
        assertThat(summary).doesNotContain("Failed Composites:");
    }

    @Test
    void summaryFormatter_emptyResults() {
        EvaluationSummary emptySummary = EvaluationSummary.from(Collections.emptyList());
        EvaluationOutcome emptyOutcome = new EvaluationOutcome("empty-spec", Collections.emptyList(), emptySummary);

        SummaryResultFormatter formatter = new SummaryResultFormatter();
        String summary = formatter.format(emptyOutcome);

        assertThat(summary).contains("Queries: 0 total");
        assertThat(summary).contains("Composites: 0 total");
    }

    @Test
    void summaryFormatter_onlyQueries() {
        QueryCriterion query1 = QueryCriterion.builder()
                .id("q1")
                .field("a").eq(1)
                .build();
        QueryResult result1 = QueryResult.matched(query1);

        EvaluationSummary summary = EvaluationSummary.from(List.of(result1));
        EvaluationOutcome outcome = new EvaluationOutcome("query-only-spec", List.of(result1), summary);

        SummaryResultFormatter formatter = new SummaryResultFormatter();
        String output = formatter.format(outcome);

        assertThat(output).contains("Queries: 1 total");
        assertThat(output).contains("1 passed");
        assertThat(output).contains("0 failed");
        assertThat(output).contains("Composites: 0 total");
    }

    @Test
    void summaryFormatter_showFailures_displaysChildFailures() {
        QueryCriterion query1 = QueryCriterion.builder()
                .id("child-fail")
                .field("x").eq(1)
                .build();
        QueryResult childResult = QueryResult.notMatched(query1, List.of("x"));

        CompositeCriterion composite = CompositeCriterion.builder()
                .id("parent-fail")
                .and()
                .criteria(query1)
                .build();
        CompositeResult compositeResult = new CompositeResult(composite, EvaluationState.NOT_MATCHED, List.of(childResult));

        EvaluationSummary summary = EvaluationSummary.from(List.of(childResult, compositeResult));
        EvaluationOutcome outcome = new EvaluationOutcome("child-fail-spec", List.of(childResult, compositeResult), summary);

        SummaryResultFormatter formatter = new SummaryResultFormatter(true);
        String output = formatter.format(outcome);

        assertThat(output).contains("Failed Composites:");
        assertThat(output).contains("parent-fail");
        assertThat(output).contains("child-fail");
    }

    // ==================== Custom Formatter Tests ====================

    @Test
    void customFormatter_default() {
        CustomResultFormatter formatter = new CustomResultFormatter();

        String output = formatter.format(complexOutcome);

        assertThat(output).isNotBlank();
        assertThat(output).contains("specification: complex-spec");
        assertThat(formatter.formatType()).isEqualTo("text");
        assertThat(formatter.verbose()).isFalse();
    }

    @Test
    void customFormatter_showsCompositeResults() {
        CustomResultFormatter formatter = new CustomResultFormatter();

        String output = formatter.format(complexOutcome);

        assertThat(output).contains("eligibility:");
        assertThat(output).contains("junction: AND");
        assertThat(output).contains("match:");
        assertThat(output).contains("state:");
        assertThat(output).contains("stats:");
    }

    @Test
    void customFormatter_verbose_showsCriteria() {
        CustomResultFormatter formatter = new CustomResultFormatter(true);

        String output = formatter.format(complexOutcome);

        assertThat(output).contains("criteria:");
        assertThat(output).contains("age-check");
        assertThat(output).contains("country-check");
        assertThat(formatter.verbose()).isTrue();
    }

    @Test
    void customFormatter_showsQueryResults() {
        // Create outcome with just queries in a composite
        QueryCriterion query = QueryCriterion.builder()
                .id("test-query")
                .field("field").eq("value")
                .build();
        QueryResult queryResult = QueryResult.matched(query);

        CompositeCriterion composite = CompositeCriterion.builder()
                .id("parent")
                .and()
                .criteria(query)
                .build();
        CompositeResult compositeResult = new CompositeResult(composite, EvaluationState.MATCHED, List.of(queryResult));

        EvaluationSummary summary = EvaluationSummary.from(List.of(compositeResult));
        EvaluationOutcome outcome = new EvaluationOutcome("query-spec", List.of(compositeResult), summary);

        CustomResultFormatter formatter = new CustomResultFormatter(true);
        String output = formatter.format(outcome);

        assertThat(output).contains("test-query:");
        assertThat(output).contains("match: true");
        assertThat(output).contains("state: MATCHED");
    }

    @Test
    void customFormatter_showsReason() {
        QueryCriterion query = QueryCriterion.builder()
                .id("failing-query")
                .field("field").eq("value")
                .build();
        QueryResult queryResult = QueryResult.undetermined(query, "Test reason", List.of("field"));

        CompositeCriterion composite = CompositeCriterion.builder()
                .id("parent")
                .and()
                .criteria(query)
                .build();
        CompositeResult compositeResult = new CompositeResult(composite, EvaluationState.UNDETERMINED, List.of(queryResult));

        EvaluationSummary summary = EvaluationSummary.from(List.of(compositeResult));
        EvaluationOutcome outcome = new EvaluationOutcome("reason-spec", List.of(compositeResult), summary);

        CustomResultFormatter formatter = new CustomResultFormatter(true);
        String output = formatter.format(outcome);

        assertThat(output).contains("reason:");
    }

    @Test
    void customFormatter_showsMissingPaths_whenVerbose() {
        QueryCriterion query = QueryCriterion.builder()
                .id("missing-query")
                .field("field").eq("value")
                .build();
        QueryResult queryResult = QueryResult.undetermined(query, "Test reason", List.of("field", "nested.path"));

        CompositeCriterion composite = CompositeCriterion.builder()
                .id("parent")
                .and()
                .criteria(query)
                .build();
        CompositeResult compositeResult = new CompositeResult(composite, EvaluationState.UNDETERMINED, List.of(queryResult));

        EvaluationSummary summary = EvaluationSummary.from(List.of(compositeResult));
        EvaluationOutcome outcome = new EvaluationOutcome("missing-spec", List.of(compositeResult), summary);

        CustomResultFormatter formatter = new CustomResultFormatter(true);
        String output = formatter.format(outcome);

        assertThat(output).contains("missing:");
        assertThat(output).contains("field");
        assertThat(output).contains("nested.path");
    }

    @Test
    void customFormatter_showsReferenceResults() {
        QueryCriterion query = QueryCriterion.builder()
                .id("original")
                .field("field").eq("value")
                .build();
        QueryResult queryResult = QueryResult.matched(query);

        CriterionReference reference = new CriterionReference("original");
        ReferenceResult refResult = new ReferenceResult(reference, queryResult);

        CompositeCriterion composite = CompositeCriterion.builder()
                .id("parent")
                .and()
                .addReference(reference)
                .build();
        CompositeResult compositeResult = new CompositeResult(composite, EvaluationState.MATCHED, List.of(refResult));

        EvaluationSummary summary = EvaluationSummary.from(List.of(compositeResult));
        EvaluationOutcome outcome = new EvaluationOutcome("ref-spec", List.of(compositeResult), summary);

        CustomResultFormatter formatter = new CustomResultFormatter(true);
        String output = formatter.format(outcome);

        assertThat(output).contains("Type: Reference");
        assertThat(output).contains("References: original");
    }

    @Test
    void customFormatter_showsStatistics() {
        CustomResultFormatter formatter = new CustomResultFormatter();

        String output = formatter.format(complexOutcome);

        assertThat(output).contains("stats:");
        assertThat(output).contains("matched:");
        assertThat(output).contains("not_matched:");
        assertThat(output).contains("undetermined:");
    }

    @Test
    void customFormatter_emptyResults() {
        EvaluationSummary emptySummary = EvaluationSummary.from(Collections.emptyList());
        EvaluationOutcome emptyOutcome = new EvaluationOutcome("empty-spec", Collections.emptyList(), emptySummary);

        CustomResultFormatter formatter = new CustomResultFormatter();
        String output = formatter.format(emptyOutcome);

        assertThat(output).contains("specification: empty-spec");
    }

    @Test
    void customFormatter_nestedComposites() {
        QueryCriterion query1 = QueryCriterion.builder()
                .id("q1")
                .field("a").eq(1)
                .build();
        QueryResult result1 = QueryResult.matched(query1);

        CompositeCriterion inner = CompositeCriterion.builder()
                .id("inner")
                .and()
                .criteria(query1)
                .build();
        CompositeResult innerResult = new CompositeResult(inner, EvaluationState.MATCHED, List.of(result1));

        CompositeCriterion outer = CompositeCriterion.builder()
                .id("outer")
                .or()
                .addCriterion(inner)
                .build();
        CompositeResult outerResult = new CompositeResult(outer, EvaluationState.MATCHED, List.of(innerResult));

        EvaluationSummary summary = EvaluationSummary.from(List.of(outerResult));
        EvaluationOutcome outcome = new EvaluationOutcome("nested-spec", List.of(outerResult), summary);

        CustomResultFormatter formatter = new CustomResultFormatter(true);
        String output = formatter.format(outcome);

        assertThat(output).contains("outer:");
        assertThat(output).contains("inner:");
    }

    // ==================== FormatterException Tests ====================

    @Test
    void formatterException_withMessage() {
        FormatterException exception = new FormatterException("Test error message");

        assertThat(exception.getMessage()).isEqualTo("Test error message");
        assertThat(exception.getCause()).isNull();
    }

    @Test
    void formatterException_withMessageAndCause() {
        Throwable cause = new RuntimeException("Root cause");
        FormatterException exception = new FormatterException("Test error message", cause);

        assertThat(exception.getMessage()).isEqualTo("Test error message");
        assertThat(exception.getCause()).isSameAs(cause);
        assertThat(exception.getCause().getMessage()).isEqualTo("Root cause");
    }

    @Test
    void formatterException_isRuntimeException() {
        FormatterException exception = new FormatterException("Test");

        assertThat(exception).isInstanceOf(RuntimeException.class);
    }
}
