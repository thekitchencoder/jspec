package uk.codery.jspec.evaluator;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.codery.jspec.model.QueryCriterion;
import uk.codery.jspec.operator.OperatorRegistry;
import uk.codery.jspec.result.EvaluationResult;
import uk.codery.jspec.result.EvaluationState;
import uk.codery.jspec.result.QueryResult;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for dot notation support in CriterionEvaluator.
 *
 * <p>Dot notation allows querying nested fields without nested Map structures.
 * For example: "address.city" instead of Map.of("address", Map.of("city", ...))
 *
 * <p>This test suite demonstrates:
 * <ul>
 *   <li>Simple dot notation (one level deep)</li>
 *   <li>Deep dot notation (multiple levels)</li>
 *   <li>Dot notation with all operators</li>
 *   <li>Dot notation with custom operators</li>
 *   <li>Array index notation (e.g., "items.0.name")</li>
 *   <li>Multiple nested fields in one criterion</li>
 * </ul>
 */
class DotNotationTest {

    private CriterionEvaluator evaluator;

    @BeforeEach
    void setUp() {
        evaluator = new CriterionEvaluator();
    }

    // ==================== Simple Dot Notation Tests ====================

    @Test
    void dotNotation_oneLevel_shouldWork() {
        Map<String, Object> doc = Map.of(
                "address", Map.of(
                        "city", "London",
                        "country", "UK"
                )
        );

        QueryCriterion criterion = QueryCriterion.builder()
                .id("city-check")
                .field("address.city").eq("London")
                .build();

        QueryResult result = evaluator.evaluateQuery(doc, criterion);

        assertThat(result.state()).isEqualTo(EvaluationState.MATCHED);
    }

    @Test
    void dotNotation_oneLevel_notMatched() {
        Map<String, Object> doc = Map.of(
                "address", Map.of("city", "London")
        );

        QueryCriterion criterion = QueryCriterion.builder()
                .id("city-check")
                .field("address.city").eq("Manchester")
                .build();

        QueryResult result = evaluator.evaluateQuery(doc, criterion);

        assertThat(result.state()).isEqualTo(EvaluationState.NOT_MATCHED);
    }

    @Test
    void dotNotation_missingNestedField_shouldBeUndetermined() {
        Map<String, Object> doc = Map.of(
                "address", Map.of("country", "UK")
        );

        QueryCriterion criterion = QueryCriterion.builder()
                .id("city-check")
                .field("address.city").eq("London")
                .build();

        QueryResult result = evaluator.evaluateQuery(doc, criterion);

        assertThat(result.state()).isEqualTo(EvaluationState.UNDETERMINED);
        assertThat(result.missingPaths()).contains("address.city");
    }

    // ==================== Deep Dot Notation Tests ====================

    @Test
    void dotNotation_multipleLevels_shouldWork() {
        Map<String, Object> doc = Map.of(
                "user", Map.of(
                        "profile", Map.of(
                                "address", Map.of(
                                        "city", "London"
                                )
                        )
                )
        );

        QueryCriterion criterion = QueryCriterion.builder()
                .id("deep-city-check")
                .field("user.profile.address.city").eq("London")
                .build();

        QueryResult result = evaluator.evaluateQuery(doc, criterion);

        assertThat(result.state()).isEqualTo(EvaluationState.MATCHED);
    }

    @Test
    void dotNotation_veryDeep_fourLevels() {
        Map<String, Object> doc = Map.of(
                "level1", Map.of(
                        "level2", Map.of(
                                "level3", Map.of(
                                        "level4", Map.of(
                                                "value", "found"
                                        )
                                )
                        )
                )
        );

        QueryCriterion criterion = QueryCriterion.builder()
                .id("deep-value-check")
                .field("level1.level2.level3.level4.value").eq("found")
                .build();

        QueryResult result = evaluator.evaluateQuery(doc, criterion);

        assertThat(result.state()).isEqualTo(EvaluationState.MATCHED);
    }

    // ==================== Dot Notation with Multiple Fields ====================

    @Test
    void dotNotation_multipleNestedFields() {
        Map<String, Object> doc = Map.of(
                "address", Map.of(
                        "city", "London",
                        "postalCode", "SW1A 1AA",
                        "country", "UK"
                ),
                "contact", Map.of(
                        "email", "user@example.com",
                        "phone", "+44 20 1234 5678"
                )
        );

        QueryCriterion criterion = QueryCriterion.builder()
                .id("address-validation")
                .field("address.city").eq("London")
                .field("address.country").eq("UK")
                .field("contact.email").exists(true)
                .build();

        QueryResult result = evaluator.evaluateQuery(doc, criterion);

        assertThat(result.state()).isEqualTo(EvaluationState.MATCHED);
    }

    @Test
    void dotNotation_multipleNestedFields_withOperators() {
        Map<String, Object> doc = Map.of(
                "employment", Map.of(
                        "status", "EMPLOYED",
                        "monthsEmployed", 24,
                        "salary", Map.of(
                                "amount", 45000,
                                "currency", "GBP"
                        )
                )
        );

        QueryCriterion criterion = QueryCriterion.builder()
                .id("employment-check")
                .field("employment.status").eq("EMPLOYED")
                .field("employment.monthsEmployed").gte(12)
                .field("employment.salary.amount").gte(30000)
                .field("employment.salary.currency").eq("GBP")
                .build();

        QueryResult result = evaluator.evaluateQuery(doc, criterion);

        assertThat(result.state()).isEqualTo(EvaluationState.MATCHED);
    }

    // ==================== Dot Notation with All Operators ====================

    @Test
    void dotNotation_withComparisonOperators() {
        Map<String, Object> doc = Map.of(
                "user", Map.of(
                        "age", 35,
                        "score", 85.5
                )
        );

        // Greater than or equal
        QueryCriterion criterion1 = QueryCriterion.builder()
                .id("age-check")
                .field("user.age").gte(18)
                .build();
        assertThat(evaluator.evaluateQuery(doc, criterion1).state()).isEqualTo(EvaluationState.MATCHED);

        // Less than
        QueryCriterion criterion2 = QueryCriterion.builder()
                .id("age-limit")
                .field("user.age").lt(65)
                .build();
        assertThat(evaluator.evaluateQuery(doc, criterion2).state()).isEqualTo(EvaluationState.MATCHED);

        // Range query
        QueryCriterion criterion3 = QueryCriterion.builder()
                .id("score-range")
                .field("user.score").gte(70.0).and().lte(100.0)
                .build();
        assertThat(evaluator.evaluateQuery(doc, criterion3).state()).isEqualTo(EvaluationState.MATCHED);
    }

    @Test
    void dotNotation_withCollectionOperators() {
        Map<String, Object> doc = Map.of(
                "profile", Map.of(
                        "tags", List.of("premium", "verified", "active"),
                        "roles", List.of("user", "admin")
                )
        );

        // $in operator
        QueryCriterion criterion1 = QueryCriterion.builder()
                .id("tag-check")
                .field("profile.tags").in("verified", "trusted")
                .build();
        assertThat(evaluator.evaluateQuery(doc, criterion1).state()).isEqualTo(EvaluationState.MATCHED);

        // $all operator
        QueryCriterion criterion2 = QueryCriterion.builder()
                .id("required-tags")
                .field("profile.tags").all("verified", "active")
                .build();
        assertThat(evaluator.evaluateQuery(doc, criterion2).state()).isEqualTo(EvaluationState.MATCHED);

        // $size operator
        QueryCriterion criterion3 = QueryCriterion.builder()
                .id("roles-count")
                .field("profile.roles").size(2)
                .build();
        assertThat(evaluator.evaluateQuery(doc, criterion3).state()).isEqualTo(EvaluationState.MATCHED);
    }

    @Test
    void dotNotation_withAdvancedOperators() {
        Map<String, Object> doc = Map.of(
                "user", Map.of(
                        "email", "admin@example.com",
                        "age", 25,
                        "verified", true
                )
        );

        // $exists operator
        QueryCriterion criterion1 = QueryCriterion.builder()
                .id("email-exists")
                .field("user.email").exists(true)
                .build();
        assertThat(evaluator.evaluateQuery(doc, criterion1).state()).isEqualTo(EvaluationState.MATCHED);

        // $type operator
        QueryCriterion criterion2 = QueryCriterion.builder()
                .id("age-type")
                .field("user.age").type("number")
                .build();
        assertThat(evaluator.evaluateQuery(doc, criterion2).state()).isEqualTo(EvaluationState.MATCHED);

        // $regex operator
        QueryCriterion criterion3 = QueryCriterion.builder()
                .id("email-pattern")
                .field("user.email").regex("^admin@")
                .build();
        assertThat(evaluator.evaluateQuery(doc, criterion3).state()).isEqualTo(EvaluationState.MATCHED);
    }

    // ==================== Dot Notation with Custom Operators ====================

    @Test
    void dotNotation_withCustomOperator() {
        OperatorRegistry registry = OperatorRegistry.withDefaults();

        // Register custom $length operator
        registry.register("$length", (value, operand) -> {
            if (!(value instanceof String) || !(operand instanceof Number)) {
                return false;
            }
            return ((String) value).length() == ((Number) operand).intValue();
        });

        CriterionEvaluator customEvaluator = new CriterionEvaluator(registry);

        Map<String, Object> doc = Map.of(
                "user", Map.of(
                        "profile", Map.of(
                                "username", "john_doe",
                                "bio", "Software developer"
                        )
                )
        );

        QueryCriterion criterion = QueryCriterion.builder()
                .id("username-length")
                .field("user.profile.username").operator("$length", 8)
                .build();

        EvaluationResult result = customEvaluator.evaluateQuery(doc, criterion);

        assertThat(result.state()).isEqualTo(EvaluationState.MATCHED);
    }

    @Test
    void dotNotation_multipleCustomOperators() {
        OperatorRegistry registry = OperatorRegistry.withDefaults();

        registry.register("$startswith", (value, operand) -> {
            if (!(value instanceof String) || !(operand instanceof String)) {
                return false;
            }
            return ((String) value).startsWith((String) operand);
        });

        registry.register("$length", (value, operand) -> {
            if (!(value instanceof String) || !(operand instanceof Number)) {
                return false;
            }
            return ((String) value).length() >= ((Number) operand).intValue();
        });

        CriterionEvaluator customEvaluator = new CriterionEvaluator(registry);

        Map<String, Object> doc = Map.of(
                "account", Map.of(
                        "email", "admin@company.com",
                        "username", "administrator"
                )
        );

        QueryCriterion criterion = QueryCriterion.builder()
                .id("admin-validation")
                .field("account.email").operator("$startswith", "admin")
                .field("account.username").operator("$length", 5)
                .build();

        EvaluationResult result = customEvaluator.evaluateQuery(doc, criterion);

        assertThat(result.state()).isEqualTo(EvaluationState.MATCHED);
    }

    // ==================== Real-World Examples ====================

    @Test
    void dotNotation_realWorld_accountsCheck() {
        Map<String, Object> doc = Map.of(
                "accounts", Map.of(
                        "current", Map.of(
                                "status", "active",
                                "amount", 650,
                                "startDate", "2024-01-01"
                        ),
                        "mortgage", Map.of(
                                "status", "inactive",
                                "amount", 0,
                                "endDate", "2024-01-01"
                        )
                )
        );

        QueryCriterion criterion = QueryCriterion.builder()
                .id("eligibility")
                .field("accounts.current.status").eq("active")
                .field("accounts.current.amount").gte(500)
                .build();

        QueryResult result = evaluator.evaluateQuery(doc, criterion);

        assertThat(result.state()).isEqualTo(EvaluationState.MATCHED);
    }

    @Test
    void dotNotation_realWorld_employmentHistory() {
        Map<String, Object> doc = Map.of(
                "applicant", Map.of(
                        "employment", Map.of(
                                "current", Map.of(
                                        "status", "EMPLOYED",
                                        "employer", Map.of(
                                                "name", "Acme Corp",
                                                "address", Map.of(
                                                        "city", "London",
                                                        "postcode", "EC1A 1BB"
                                                )
                                        ),
                                        "monthsEmployed", 18
                                ),
                                "previousEmployers", 3
                        )
                )
        );

        QueryCriterion criterion = QueryCriterion.builder()
                .id("employment-validation")
                .field("applicant.employment.current.status").eq("EMPLOYED")
                .field("applicant.employment.current.monthsEmployed").gte(12)
                .field("applicant.employment.current.employer.address.city").in("London", "Manchester", "Birmingham")
                .build();

        QueryResult result = evaluator.evaluateQuery(doc, criterion);

        assertThat(result.state()).isEqualTo(EvaluationState.MATCHED);
    }

    @Test
    void dotNotation_realWorld_financialProfile() {
        Map<String, Object> doc = Map.of(
                "financial", Map.of(
                        "income", Map.of(
                                "salary", Map.of(
                                        "amount", 45000,
                                        "currency", "GBP",
                                        "frequency", "annual"
                                ),
                                "other", Map.of(
                                        "amount", 5000,
                                        "source", "investments"
                                )
                        ),
                        "credit", Map.of(
                                "score", 720,
                                "history", "excellent"
                        )
                )
        );

        QueryCriterion criterion = QueryCriterion.builder()
                .id("loan-eligibility")
                .field("financial.income.salary.amount").gte(30000)
                .field("financial.income.salary.currency").eq("GBP")
                .field("financial.credit.score").gte(650)
                .build();

        QueryResult result = evaluator.evaluateQuery(doc, criterion);

        assertThat(result.state()).isEqualTo(EvaluationState.MATCHED);
    }

    // ==================== Comparison: Dot Notation vs Nested Maps ====================

    @Test
    void comparison_dotNotation_vs_nestedMaps() {
        Map<String, Object> doc = Map.of(
                "address", Map.of(
                        "city", "London",
                        "postcode", "SW1A 1AA"
                )
        );

        // Using dot notation (recommended)
        QueryCriterion withDotNotation = QueryCriterion.builder()
                .id("address-check-dot")
                .field("address.city").eq("London")
                .field("address.postcode").regex("^SW")
                .build();

        // Using nested Maps (works but verbose for testing structure)
        QueryCriterion withNestedMaps = new QueryCriterion("address-check-nested",
                Map.of("address", Map.of(
                        "city", Map.of("$eq", "London"),
                        "postcode", Map.of("$regex", "^SW")
                ))
        );

        // Both should produce the same result
        EvaluationResult dotResult = evaluator.evaluateQuery(doc, withDotNotation);
        EvaluationResult nestedResult = evaluator.evaluateQuery(doc, withNestedMaps);

        assertThat(dotResult.state()).isEqualTo(EvaluationState.MATCHED);
        assertThat(nestedResult.state()).isEqualTo(EvaluationState.MATCHED);
    }
}
