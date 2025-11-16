package uk.codery.jspec.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.codery.jspec.evaluator.SpecificationEvaluator;
import uk.codery.jspec.model.*;
import uk.codery.jspec.result.EvaluationOutcome;
import uk.codery.jspec.result.EvaluationResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end integration tests simulating real-world scenarios:
 * - E-commerce order validation
 * - User access control
 * - Complex business criteria
 * - Multi-level nested documents
 */
class EndToEndTest {

    private SpecificationEvaluator evaluator;

    @BeforeEach
    void setUp() {
        evaluator = new SpecificationEvaluator();
    }

    // ========== Order Validation Scenario ==========

    @Test
    void orderValidation_qualifiedOrder_shouldMatch() {
        // Real-world scenario: Checking eligibility for express shipping
        Map<String, Object> order = Map.of(
                "customer", Map.of(
                        "verified", true,
                        "membership", "PRIME"
                ),
                "cart", Map.of(
                        "total", 125.00,
                        "items_count", 5
                ),
                "shipping", Map.of(
                        "country", "US",
                        "method_requested", "EXPRESS"
                )
        );

        // Specification: Must be verified, meet minimum order, and ship to eligible country
        List<uk.codery.jspec.model.Criterion> criteria = List.of(
                new QueryCriterion("customer-verified", Map.of("customer", Map.of(
                        "verified", Map.of("$eq", true)
                ))),
                new QueryCriterion("minimum-order", Map.of("cart", Map.of(
                        "total", Map.of("$gte", 25.00)
                ))),
                new QueryCriterion("eligible-country", Map.of("shipping", Map.of(
                        "country", Map.of("$in", List.of("US", "CA", "UK", "AU"))
                ))),
                new QueryCriterion("has-items", Map.of("cart", Map.of(
                        "items_count", Map.of("$gt", 0)
                )))
        );

        CompositeCriterion eligibilityComposite = new CompositeCriterion(
                "express-shipping-eligible",
                Junction.AND,
                List.of(new CriterionReference("customer-verified"), new CriterionReference("minimum-order"),
                        new CriterionReference("eligible-country"), new CriterionReference("has-items"))
        );

        List<uk.codery.jspec.model.Criterion> allCriteria = new ArrayList<>(criteria);
        allCriteria.add(eligibilityComposite);
        Specification spec = new Specification("express-shipping-eligibility", allCriteria);

        EvaluationOutcome outcome = evaluator.evaluate(order, spec);

        assertThat(outcome.summary().matched()).isEqualTo(5);
        assertThat(outcome.summary().fullyDetermined()).isTrue();
        assertThat(outcome.get("express-shipping-eligible").matched()).isTrue();
    }

    @Test
    void orderValidation_unqualifiedOrder_shouldNotMatch() {
        Map<String, Object> order = Map.of(
                "customer", Map.of(
                        "verified", true,
                        "membership", "STANDARD"
                ),
                "cart", Map.of(
                        "total", 15.00,  // Below minimum of $25
                        "items_count", 2
                ),
                "shipping", Map.of(
                        "country", "US",
                        "method_requested", "EXPRESS"
                )
        );

        List<uk.codery.jspec.model.Criterion> criteria = List.of(
                QueryCriterion.builder()
                        .id("customer-verified")
                        .field("customer.verified")
                        .eq(true)
                        .build(),
                QueryCriterion.builder()
                        .id("minimum-order")
                        .field("cart.total")
                        .gte(25.00)
                        .build(),
                QueryCriterion.builder()
                        .id("eligible-country")
                        .field("shipping.country")
                        .in("US", "CA", "UK", "AU")
                        .build(),
                QueryCriterion.builder()
                        .id("has-items")
                        .field("cart.items_count")
                        .gt( 0)
                        .build()
        );

        CompositeCriterion eligibilityComposite = new CompositeCriterion(
                "express-shipping-eligible",
                Junction.AND,
                List.of(new CriterionReference("customer-verified"), new CriterionReference("minimum-order"),
                        new CriterionReference("eligible-country"), new CriterionReference("has-items"))
        );

        List<uk.codery.jspec.model.Criterion> allCriteria = new ArrayList<>(criteria);
        allCriteria.add(eligibilityComposite);
        Specification spec = new Specification("express-shipping-eligibility", allCriteria);

        EvaluationOutcome outcome = evaluator.evaluate(order, spec);

        outcome.results().stream().map(r -> r.id() + ":" + r.state() + ":" + r.reason()).forEach(System.out::println);

        assertThat(outcome.summary().matched()).isEqualTo(3);
        assertThat(outcome.summary().notMatched()).isEqualTo(2);
        assertThat(outcome.compositeResults().getFirst().matched()).isFalse();
        assertThat(outcome.get("eligible-country"))
                .isNotNull()
                .extracting(EvaluationResult::matched)
                .isEqualTo(true);
    }

    @Test
    void orderValidation_incompleteData_shouldBeUndetermined() {
        Map<String, Object> order = Map.of(
                "customer", Map.of(
                        "verified", true
                        // Missing membership
                ),
                "cart", Map.of(
                        "items_count", 3
                        // Missing total
                )
                // Missing shipping section
        );

        List<uk.codery.jspec.model.Criterion> criteria = List.of(
                new QueryCriterion("customer-verified", Map.of("customer", Map.of(
                        "verified", Map.of("$eq", true)
                ))),
                new QueryCriterion("minimum-order", Map.of("cart", Map.of(
                        "total", Map.of("$gte", 25.00)
                ))),
                new QueryCriterion("eligible-country", Map.of("shipping", Map.of(
                        "country", Map.of("$in", List.of("US", "CA", "UK", "AU"))
                )))
        );

        Specification spec = new Specification("express-shipping-eligibility", criteria);

        EvaluationOutcome outcome = evaluator.evaluate(order, spec);

        assertThat(outcome.summary().matched()).isEqualTo(1);
        assertThat(outcome.summary().undetermined()).isEqualTo(2);
        assertThat(outcome.summary().fullyDetermined()).isFalse();
    }

    // ========== User Access Control Scenario ==========

    @Test
    void userAccessControl_adminUser_shouldHaveAccess() {
        Map<String, Object> user = Map.of(
                "username", "admin@example.com",
                "roles", List.of("admin", "user"),
                "permissions", List.of("read", "write", "delete"),
                "status", "ACTIVE",
                "mfa_enabled", true
        );

        List<uk.codery.jspec.model.Criterion> criteria = List.of(
                new QueryCriterion("has-admin-role", Map.of("roles", Map.of("$elemMatch", Map.of("$in", List.of("admin", "superuser"))))),
                new QueryCriterion("account-active", Map.of("status", Map.of("$eq", "ACTIVE"))),
                new QueryCriterion("mfa-enabled", Map.of("mfa_enabled", Map.of("$eq", true)))
        );

        CompositeCriterion adminAccessSet = new CompositeCriterion(
                "admin-access",
                Junction.AND,
                List.of(new QueryCriterion("has-admin-role"), new QueryCriterion("account-active"), new QueryCriterion("mfa-enabled"))
        );

        Specification spec = new Specification(
                "admin-access-control",
                combine(criteria, adminAccessSet));

        EvaluationOutcome outcome = evaluator.evaluate(user, spec);

        assertThat(outcome.compositeResults().getFirst().matched()).isTrue();
    }

    @Test
    void userAccessControl_regularUser_shouldUseFallback() {
        Map<String, Object> user = Map.of(
                "username", "user@example.com",
                "roles", List.of("user"),
                "permissions", List.of("read"),
                "status", "ACTIVE",
                "verified_email", true
        );

        List<uk.codery.jspec.model.Criterion> criteria = List.of(
                new QueryCriterion("has-admin-role", Map.of("roles", Map.of("$in", List.of("admin", "superuser")))),
                new QueryCriterion("has-user-role", Map.of("roles", Map.of("$elemMatch", Map.of("$in", List.of("user"))))),
                new QueryCriterion("account-active", Map.of("status", Map.of("$eq", "ACTIVE"))),
                new QueryCriterion("email-verified", Map.of("verified_email", Map.of("$eq", true)))
        );

        CompositeCriterion adminAccessSet = new CompositeCriterion(
                "admin-access",
                Junction.AND,
                List.of(new QueryCriterion("has-admin-role"), new QueryCriterion("account-active"))
        );

        CompositeCriterion userAccessSet = new CompositeCriterion(
                "user-access",
                Junction.AND,
                List.of(new QueryCriterion("has-user-role"), new QueryCriterion("account-active"), new QueryCriterion("email-verified"))
        );

        Specification spec = new Specification(
                "access-control",
                combineAll(criteria, List.of(adminAccessSet, userAccessSet)));

        EvaluationOutcome outcome = evaluator.evaluate(user, spec);

        // Admin access should fail, user access should succeed
        assertThat(outcome.compositeResults()).hasSize(2);
        assertThat(outcome.get("admin-access").matched()).isFalse(); // admin-access
        assertThat(outcome.get("user-access").matched()).isTrue();  // user-access
    }

    // ========== E-commerce Discount Scenario ==========

    @Test
    void discountEligibility_loyalCustomer_shouldGetDiscount() {
        Map<String, Object> order = Map.of(
                "customer", Map.of(
                        "member_since_years", 3,
                        "loyalty_tier", "GOLD",
                        "email_verified", true
                ),
                "cart", Map.of(
                        "total", 150.00,
                        "items", List.of(
                                Map.of("category", "electronics", "price", 100.00),
                                Map.of("category", "books", "price", 50.00)
                        )
                ),
                "promotion_code", "SUMMER2024"
        );

        List<uk.codery.jspec.model.Criterion> criteria = List.of(
                new QueryCriterion("loyal-customer", Map.of("customer", Map.of(
                        "member_since_years", Map.of("$gte", 2)
                ))),
                new QueryCriterion("minimum-purchase", Map.of("cart", Map.of(
                        "total", Map.of("$gte", 100.00)
                ))),
                new QueryCriterion("valid-promo", Map.of("promotion_code", Map.of("$exists", true)))
        );

        CompositeCriterion discountSet = new CompositeCriterion(
                "loyalty-discount",
                Junction.AND,
                List.of(new QueryCriterion("loyal-customer"), new QueryCriterion("minimum-purchase"), new QueryCriterion("valid-promo"))
        );

        Specification spec = new Specification(
                "discount-eligibility",
                combine(criteria, discountSet));

        EvaluationOutcome outcome = evaluator.evaluate(order, spec);

        assertThat(outcome.compositeResults().getFirst().matched()).isTrue();
    }

    // ========== Content Moderation Scenario ==========

    @Test
    void contentModeration_flaggedContent_shouldBeDetected() {
        Map<String, Object> post = Map.of(
                "content", "This is a test post with suspicious keywords",
                "author", Map.of(
                        "reputation_score", 15,
                        "account_age_days", 5,
                        "verified", false
                ),
                "metadata", Map.of(
                        "reports", 3,
                        "contains_links", true,
                        "word_count", 8
                )
        );

        List<uk.codery.jspec.model.Criterion> criteria = List.of(
                new QueryCriterion("low-reputation", Map.of("author", Map.of(
                        "reputation_score", Map.of("$lt", 50)
                ))),
                new QueryCriterion("new-account", Map.of("author", Map.of(
                        "account_age_days", Map.of("$lt", 30)
                ))),
                new QueryCriterion("has-reports", Map.of("metadata", Map.of(
                        "reports", Map.of("$gt", 0)
                ))),
                new QueryCriterion("suspicious-content", Map.of("content", Map.of(
                        "$regex", ".*(suspicious|test|spam).*"
                )))
        );

        CompositeCriterion moderationSet = new CompositeCriterion(
                "needs-review",
                Junction.OR,
                List.of(new QueryCriterion("low-reputation"), new QueryCriterion("new-account"), new QueryCriterion("has-reports"), new QueryCriterion("suspicious-content"))
        );

        Specification spec = new Specification(
                "content-moderation",
                combine(criteria, moderationSet));

        EvaluationOutcome outcome = evaluator.evaluate(post, spec);

        // Should match because multiple flags are triggered
        assertThat(outcome.compositeResults().getFirst().matched()).isTrue();
        assertThat(outcome.summary().matched()).isGreaterThan(1);
    }

    // ========== Complex Nested Document Scenario ==========

    @Test
    void complexNestedDocument_deepStructure_shouldEvaluate() {
        Map<String, Object> application = Map.of(
                "applicant", Map.of(
                        "personal", Map.of(
                                "age", 35,
                                "address", Map.of(
                                        "country", "UK",
                                        "postcode", Map.of(
                                                "outward", "SW1A",
                                                "inward", "1AA"
                                        )
                                )
                        ),
                        "financial", Map.of(
                                "income", Map.of(
                                        "annual_salary", 45000,
                                        "verified", true
                                ),
                                "credit_score", 720
                        )
                ),
                "loan", Map.of(
                        "amount", 25000,
                        "term_months", 60,
                        "purpose", "home_improvement"
                )
        );

        List<uk.codery.jspec.model.Criterion> criteria = List.of(
                new QueryCriterion("age-requirement", Map.of("applicant", Map.of(
                        "personal", Map.of("age", Map.of("$gte", 18, "$lte", 65))
                ))),
                new QueryCriterion("uk-resident", Map.of("applicant", Map.of(
                        "personal", Map.of("address", Map.of("country", Map.of("$eq", "UK")))
                ))),
                new QueryCriterion("income-verified", Map.of("applicant", Map.of(
                        "financial", Map.of("income", Map.of("verified", Map.of("$eq", true)))
                ))),
                new QueryCriterion("minimum-income", Map.of("applicant", Map.of(
                        "financial", Map.of("income", Map.of("annual_salary", Map.of("$gte", 25000)))
                ))),
                new QueryCriterion("credit-score-check", Map.of("applicant", Map.of(
                        "financial", Map.of("credit_score", Map.of("$gte", 650))
                ))),
                new QueryCriterion("loan-amount-reasonable", Map.of("loan", Map.of(
                        "amount", Map.of("$lte", 50000)
                )))
        );

        CompositeCriterion approvalSet = new CompositeCriterion(
                "loan-approval",
                Junction.AND,
                List.of(new QueryCriterion("age-requirement"), new QueryCriterion("uk-resident"), new QueryCriterion("income-verified"),
                        new QueryCriterion("minimum-income"), new QueryCriterion("credit-score-check"), new QueryCriterion("loan-amount-reasonable"))
        );

        Specification spec = new Specification(
                "loan-application-review",
                combine(criteria, approvalSet));

        EvaluationOutcome outcome = evaluator.evaluate(application, spec);

        assertThat(outcome.summary().matched()).isEqualTo(7);
        assertThat(outcome.summary().fullyDetermined()).isTrue();
        assertThat(outcome.get("loan-approval").matched()).isTrue();
    }

    // ========== Mixed Success/Failure Scenario ==========

    @Test
    void mixedScenario_partialSuccess_shouldTrackAccurately() {
        Map<String, Object> data = Map.of(
                "field1", "value1",
                "field2", 100,
                "field3", List.of("a", "b", "c")
        );

        List<uk.codery.jspec.model.Criterion> criteria = List.of(
                new QueryCriterion("match1", Map.of("field1", Map.of("$eq", "value1"))),
                new QueryCriterion("match2", Map.of("field2", Map.of("$gte", 50))),
                new QueryCriterion("no-match", Map.of("field2", Map.of("$lt", 50))),
                new QueryCriterion("undetermined", Map.of("field4", Map.of("$eq", "value4"))),
                new QueryCriterion("match3", Map.of("field3", Map.of("$size", 3)))
        );

        Specification spec = new Specification("mixed-spec", criteria);

        EvaluationOutcome outcome = evaluator.evaluate(data, spec);

        assertThat(outcome.summary().total()).isEqualTo(5);
        assertThat(outcome.summary().matched()).isEqualTo(3);
        assertThat(outcome.summary().notMatched()).isEqualTo(1);
        assertThat(outcome.summary().undetermined()).isEqualTo(1);
        assertThat(outcome.summary().fullyDetermined()).isFalse();
    }

    // ========== Performance Test with Many Criteria ==========

    @Test
    void performance_withManyCriteria_shouldEvaluateEfficiently() {
        Map<String, Object> doc = Map.of(
                "value", 50,
                "status", "ACTIVE",
                "tags", List.of("tag1", "tag2", "tag3")
        );

        // Create 20 criteria to test parallel evaluation
        List<uk.codery.jspec.model.Criterion> criteria = List.of(
                new QueryCriterion("r1", Map.of("value", Map.of("$gte", 0))),
                new QueryCriterion("r2", Map.of("value", Map.of("$lte", 100))),
                new QueryCriterion("r3", Map.of("value", Map.of("$gt", 25))),
                new QueryCriterion("r4", Map.of("value", Map.of("$lt", 75))),
                new QueryCriterion("r5", Map.of("status", Map.of("$eq", "ACTIVE"))),
                new QueryCriterion("r6", Map.of("status", Map.of("$ne", "INACTIVE"))),
                new QueryCriterion("r7", Map.of("status", Map.of("$in", List.of("ACTIVE", "PENDING")))),
                new QueryCriterion("r8", Map.of("tags", Map.of("$size", 3))),
                new QueryCriterion("r9", Map.of("tags", Map.of("$all", List.of("tag1")))),
                new QueryCriterion("r10", Map.of("tags", Map.of("$in", List.of("tag2")))),
                new QueryCriterion("r11", Map.of("value", Map.of("$eq", 50))),
                new QueryCriterion("r12", Map.of("value", Map.of("$ne", 0))),
                new QueryCriterion("r13", Map.of("status", Map.of("$exists", true))),
                new QueryCriterion("r14", Map.of("status", Map.of("$type", "string"))),
                new QueryCriterion("r15", Map.of("value", Map.of("$type", "number"))),
                new QueryCriterion("r16", Map.of("tags", Map.of("$type", "array"))),
                new QueryCriterion("r17", Map.of("status", Map.of("$regex", "ACTIVE"))),
                new QueryCriterion("r18", Map.of("value", Map.of("$gte", 1, "$lte", 99))),
                new QueryCriterion("r19", Map.of("tags", Map.of("$all", List.of("tag1", "tag2")))),
                new QueryCriterion("r20", Map.of("status", Map.of("$nin", List.of("DELETED", "ARCHIVED"))))
        );

        Specification spec = new Specification("performance-spec", criteria);

        EvaluationOutcome outcome = evaluator.evaluate(doc, spec);

        assertThat(outcome.queryResults()).hasSize(20);
        assertThat(outcome.summary().fullyDetermined()).isTrue();
        // Most should match given the document structure
        assertThat(outcome.summary().matched()).isGreaterThan(15);
    }

    // Helper methods to combine criteria and composites into a single list
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
