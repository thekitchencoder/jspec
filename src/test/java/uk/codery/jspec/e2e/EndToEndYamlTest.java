package uk.codery.jspec.e2e;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.codery.jspec.evaluator.SpecificationEvaluator;
import uk.codery.jspec.model.Specification;
import uk.codery.jspec.result.EvaluationOutcome;
import uk.codery.jspec.result.EvaluationResult;
import uk.codery.jspec.result.EvaluationState;
import uk.codery.jspec.result.CompositeResult;
import uk.codery.jspec.result.QueryResult;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-End test demonstrating complete YAML-based workflow:
 * 1. Load specification from YAML file
 * 2. Load test documents from YAML files
 * 3. Evaluate specifications against documents
 * 4. Verify results
 * This test exercises:
 * - All operator types (comparison, collection, advanced)
 * - Dot notation for nested fields
 * - Composite criteria with AND/OR junctions
 * - Tri-state evaluation (MATCHED/NOT_MATCHED/UNDETERMINED)
 * - Real-world loan eligibility scenario
 */
class EndToEndYamlTest {

    private ObjectMapper yamlMapper;
    private SpecificationEvaluator evaluator;
    private Specification specification;

    @BeforeEach
    void setUp() throws IOException {
        yamlMapper = new ObjectMapper(new YAMLFactory());

        // Load specification from YAML
        specification = loadSpecification("/e2e/loan-eligibility-spec.yaml");
        assertThat(specification).isNotNull();
        assertThat(specification.id()).isEqualTo("loan-eligibility-specification");

        // Create evaluator bound to specification
        evaluator = new SpecificationEvaluator(specification);
    }

    // ==================== Qualified Applicant Tests ====================

    @Test
    void qualifiedApplicant_shouldMatchAllCriteria() throws IOException {
        // Given: A fully qualified applicant
        Map<String, Object> applicant = loadDocument("/e2e/applicant-qualified.yaml");

        // When: Evaluating against loan eligibility specification
        EvaluationOutcome outcome = evaluator.evaluate(applicant);

        // Then: Summary shows high match rate
        assertThat(outcome.summary().total()).isEqualTo(22);
        assertThat(outcome.summary().matched()).isGreaterThan(13);
        assertThat(outcome.summary().undetermined()).isEqualTo(0);

        // And: All critical criteria match
        assertCriterionMatched(outcome, "age-minimum");
        assertCriterionMatched(outcome, "age-maximum");
        assertCriterionMatched(outcome, "employment-status");
        assertCriterionMatched(outcome, "not-bankrupt");
        assertCriterionMatched(outcome, "credit-score-good");
        assertCriterionMatched(outcome, "residence-in-approved-states");
        assertCriterionMatched(outcome, "no-excluded-industries");

        // And: Dot notation criteria work correctly
        assertCriterionMatched(outcome, "deep-nested-city");
        assertCriterionMatched(outcome, "employer-name");

        // And: Collection operators work
        assertCriterionMatched(outcome, "income-sources-diverse");
        assertCriterionMatched(outcome, "references-count");
        assertCriterionMatched(outcome, "has-previous-loans");

        // And: Advanced operators work
        assertCriterionMatched(outcome, "email-exists");
        assertCriterionMatched(outcome, "email-format-valid");
        assertCriterionMatched(outcome, "phone-type-check");

        // And: Multiple conditions on same field work
        assertCriterionMatched(outcome, "income-range");
        assertCriterionMatched(outcome, "employment-duration");
    }

    @Test
    void qualifiedApplicant_criteriaGroups_shouldMatch() throws IOException {
        // Given: A fully qualified applicant
        Map<String, Object> applicant = loadDocument("/e2e/applicant-qualified.yaml");

        // When: Evaluating against loan eligibility specification
        EvaluationOutcome outcome = evaluator.evaluate(applicant);

        // Then: All criteria groups match
        assertThat(outcome.compositeResults()).hasSize(5);

        // Basic eligibility (AND) - all must match
        assertCompositeMatched(outcome, "basic-eligibility");

        // Financial strength (OR) - at least one matches
        assertCompositeMatched(outcome, "financial-strength");

        // Location and industry checks (AND)
        assertCompositeMatched(outcome, "location-industry-checks");

        // Contact validation (AND)
        assertCompositeMatched(outcome, "contact-validation");

        // Premium indicators (OR)
        assertCompositeMatched(outcome, "premium-indicators");
    }

    // ==================== Rejected Applicant Tests ====================

    @Test
    void rejectedApplicant_shouldFailMultipleCriteria() throws IOException {
        // Given: An unqualified applicant
        Map<String, Object> applicant = loadDocument("/e2e/applicant-rejected.yaml");

        // When: Evaluating against loan eligibility specification
        EvaluationOutcome outcome = evaluator.evaluate(applicant);

        // Then: Multiple criteria fail
        assertCriterionNotMatched(outcome, "age-minimum");  // Too young
        assertCriterionNotMatched(outcome, "employment-status");  // Unemployed
        assertCriterionNotMatched(outcome, "not-bankrupt");  // Has bankruptcy
        assertCriterionNotMatched(outcome, "residence-in-approved-states");  // NV not approved
        assertCriterionNotMatched(outcome, "no-excluded-industries");  // Gambling excluded
        assertCriterionNotMatched(outcome, "credit-score-good");  // Score too low
        assertCriterionNotMatched(outcome, "email-format-valid");  // Invalid email
        assertCriterionNotMatched(outcome, "references-count");  // Only 1 reference
        assertCriterionNotMatched(outcome, "income-range");  // Income too low
        assertCriterionNotMatched(outcome, "employment-duration");  // Only 6 months

        // And: Summary reflects failures
        assertThat(outcome.summary().notMatched()).isGreaterThan(8);
    }

    @Test
    void rejectedApplicant_criteriaGroups_shouldFail() throws IOException {
        // Given: An unqualified applicant
        Map<String, Object> applicant = loadDocument("/e2e/applicant-rejected.yaml");

        // When: Evaluating against loan eligibility specification
        EvaluationOutcome outcome = evaluator.evaluate(applicant);

        // Then: Critical criteria groups fail
        assertCompositeNotMatched(outcome, "basic-eligibility");  // Fails age, employment, bankruptcy
        assertCompositeNotMatched(outcome, "location-industry-checks");  // Fails state and industry
        assertCompositeNotMatched(outcome, "contact-validation");  // Fails email format
    }

    // ==================== Partial Data Tests ====================

    @Test
    void partialApplicant_shouldHaveUndeterminedCriteria() throws IOException {
        // Given: An applicant with missing data
        Map<String, Object> applicant = loadDocument("/e2e/applicant-partial.yaml");

        // When: Evaluating against loan eligibility specification
        EvaluationOutcome outcome = evaluator.evaluate(applicant);

        // Then: Some criteria are undetermined due to missing data
        assertThat(outcome.summary().undetermined()).isGreaterThan(0);

        // And: These specific criteria are undetermined
        assertCriterionUndetermined(outcome, "phone-type-check");  // Missing phone
        assertCriterionUndetermined(outcome, "references-count");  // Missing references
        assertCriterionUndetermined(outcome, "not-bankrupt");  // Missing bankruptcy_flag
        assertCriterionUndetermined(outcome, "has-previous-loans");  // Missing loan_history

        // But: Available data is still evaluated
        assertCriterionMatched(outcome, "age-minimum");
        assertCriterionMatched(outcome, "age-maximum");
        assertCriterionMatched(outcome, "employment-status");
        assertCriterionMatched(outcome, "credit-score-good");
        assertCriterionMatched(outcome, "residence-in-approved-states");
        assertCriterionMatched(outcome, "email-exists");
        assertCriterionMatched(outcome, "email-format-valid");
    }

    @Test
    void partialApplicant_missingPathsAreTracked() throws IOException {
        // Given: An applicant with missing data
        Map<String, Object> applicant = loadDocument("/e2e/applicant-partial.yaml");

        // When: Evaluating against loan eligibility specification
        EvaluationOutcome outcome = evaluator.evaluate(applicant);

        // Then: Missing paths are tracked for debugging
        QueryResult phoneCheck = (QueryResult) findCriterionResult(outcome, "phone-type-check");
        assertThat(phoneCheck.missingPaths()).contains("applicant.contact.phone");

        QueryResult referencesCheck = (QueryResult) findCriterionResult(outcome, "references-count");
        assertThat(referencesCheck.missingPaths()).contains("applicant.references");

        QueryResult bankruptcyCheck = (QueryResult) findCriterionResult(outcome, "not-bankrupt");
        assertThat(bankruptcyCheck.missingPaths()).contains("financial.bankruptcy_flag");
    }

    // ==================== Specification Validation Tests ====================

    @Test
    void specification_shouldLoadAllCriteria() {
        // Then: All criteria are loaded
        assertThat(specification.criteria()).hasSize(22);

        // And: Criteria have correct IDs
        assertThat(specification.criteria())
                .extracting("id")
                .contains(
                        "age-minimum",
                        "age-maximum",
                        "credit-score-good",
                        "employment-status",
                        "residence-in-approved-states",
                        "email-format-valid",
                        "has-previous-loans",
                        "deep-nested-city",
                        "employer-name"
                );
    }

    @Test
    void specification_shouldLoadAllComposites() {
        // Then: All composites are loaded (filter criteria to get only CompositeCriterion instances)
        long compositeCount = specification.criteria().stream()
                .filter(c -> c instanceof uk.codery.jspec.model.CompositeCriterion)
                .count();
        assertThat(compositeCount).isEqualTo(5);

        // And: Composites have correct structure
        assertThat(specification.criteria().stream()
                .filter(c -> c instanceof uk.codery.jspec.model.CompositeCriterion)
                .toList())
                .extracting("id")
                .contains(
                        "basic-eligibility",
                        "financial-strength",
                        "location-industry-checks",
                        "contact-validation",
                        "premium-indicators"
                );
    }

    // ==================== Helper Methods ====================

    private Specification loadSpecification(String resourcePath) throws IOException {
        try (InputStream is = getClass().getResourceAsStream(resourcePath)) {
            if (is == null) {
                throw new IOException("Resource not found: " + resourcePath);
            }
            return yamlMapper.readValue(is, Specification.class);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> loadDocument(String resourcePath) throws IOException {
        try (InputStream is = getClass().getResourceAsStream(resourcePath)) {
            if (is == null) {
                throw new IOException("Resource not found: " + resourcePath);
            }
            return yamlMapper.readValue(is, Map.class);
        }
    }

    private void assertCriterionMatched(EvaluationOutcome outcome, String criterionId) {
        EvaluationResult result = findCriterionResult(outcome, criterionId);
        assertThat(result.state())
                .as("Criterion '%s' should be MATCHED", criterionId)
                .isEqualTo(EvaluationState.MATCHED);
    }

    private void assertCriterionNotMatched(EvaluationOutcome outcome, String criterionId) {
        EvaluationResult result = findCriterionResult(outcome, criterionId);
        assertThat(result.state())
                .as("Criterion '%s' should be NOT_MATCHED", criterionId)
                .isEqualTo(EvaluationState.NOT_MATCHED);
    }

    private void assertCriterionUndetermined(EvaluationOutcome outcome, String criterionId) {
        QueryResult result = (QueryResult) findCriterionResult(outcome, criterionId);
        assertThat(result.state())
                .as("Criterion '%s' should be UNDETERMINED (reason: %s)", criterionId, result.failureReason())
                .isEqualTo(EvaluationState.UNDETERMINED);
    }

    private void assertCompositeMatched(EvaluationOutcome outcome, String compositeId) {
        CompositeResult result = findCompositeResult(outcome, compositeId);
        assertThat(result.state().matched())
                .as("Composite '%s' should be matched", compositeId)
                .isTrue();
    }

    private void assertCompositeNotMatched(EvaluationOutcome outcome, String compositeId) {
        CompositeResult result = findCompositeResult(outcome, compositeId);
        assertThat(result.state().matched())
                .as("Composite '%s' should not be matched", compositeId)
                .isFalse();
    }

    private EvaluationResult findCriterionResult(EvaluationOutcome outcome, String criterionId) {
        return outcome.queryResults().stream()
                .filter(r -> r.id().equals(criterionId))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Criterion not found: " + criterionId));
    }

    private CompositeResult findCompositeResult(EvaluationOutcome outcome, String compositeId) {
        return outcome.compositeResults().stream()
                .filter(r -> r.id().equals(compositeId))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Composite not found: " + compositeId));
    }
}
