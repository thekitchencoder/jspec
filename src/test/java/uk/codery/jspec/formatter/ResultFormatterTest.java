package uk.codery.jspec.formatter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.codery.jspec.model.CompositeCriterion;
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
}
