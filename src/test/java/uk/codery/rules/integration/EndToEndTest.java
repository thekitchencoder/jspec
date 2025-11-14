package uk.codery.rules.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.codery.rules.*;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end integration tests simulating real-world scenarios:
 * - Employment eligibility checks
 * - User access control
 * - Complex business rules
 * - Multi-level nested documents
 */
class EndToEndTest {

    private SpecificationEvaluator evaluator;

    @BeforeEach
    void setUp() {
        evaluator = new SpecificationEvaluator();
    }

    // ========== Employment Eligibility Scenario ==========

    @Test
    void employmentEligibility_qualifiedCandidate_shouldMatch() {
        // Real-world scenario: Checking eligibility for employment program
        Map<String, Object> citizen = Map.of(
            "age", 28,
            "benefits", Map.of(
                "universal_credit", Map.of(
                    "status", "ACTIVE",
                    "duration_months", 8
                )
            ),
            "employment", Map.of(
                "status", "UNEMPLOYED",
                "seeking_work", true
            )
        );

        // Specification: Must be on UC for 6+ months, unemployed, and seeking work
        List<Rule> rules = List.of(
            new Rule("uc-active", Map.of("benefits", Map.of(
                "universal_credit", Map.of("status", Map.of("$eq", "ACTIVE"))
            ))),
            new Rule("uc-duration", Map.of("benefits", Map.of(
                "universal_credit", Map.of("duration_months", Map.of("$gte", 6))
            ))),
            new Rule("unemployed", Map.of("employment", Map.of(
                "status", Map.of("$eq", "UNEMPLOYED")
            ))),
            new Rule("seeking-work", Map.of("employment", Map.of(
                "seeking_work", Map.of("$eq", true)
            )))
        );

        RuleSet eligibilitySet = new RuleSet(
            "restart-eligible",
            Operator.AND,
            List.of(new Rule("uc-active"), new Rule("uc-duration"), new Rule("unemployed"), new Rule("seeking-work"))
        );

        Specification spec = new Specification(
            "restart-programme-eligibility",
            rules,
            List.of(eligibilitySet)
        );

        EvaluationOutcome outcome = evaluator.evaluate(citizen, spec);

        assertThat(outcome.summary().matchedRules()).isEqualTo(4);
        assertThat(outcome.summary().fullyDetermined()).isTrue();
        assertThat(outcome.ruleSetResults().get(0).matched()).isTrue();
    }

    @Test
    void employmentEligibility_unqualifiedCandidate_shouldNotMatch() {
        Map<String, Object> citizen = Map.of(
            "age", 28,
            "benefits", Map.of(
                "universal_credit", Map.of(
                    "status", "ACTIVE",
                    "duration_months", 3  // Only 3 months, needs 6
                )
            ),
            "employment", Map.of(
                "status", "UNEMPLOYED",
                "seeking_work", true
            )
        );

        List<Rule> rules = List.of(
            new Rule("uc-active", Map.of("benefits", Map.of(
                "universal_credit", Map.of("status", Map.of("$eq", "ACTIVE"))
            ))),
            new Rule("uc-duration", Map.of("benefits", Map.of(
                "universal_credit", Map.of("duration_months", Map.of("$gte", 6))
            ))),
            new Rule("unemployed", Map.of("employment", Map.of(
                "status", Map.of("$eq", "UNEMPLOYED")
            ))),
            new Rule("seeking-work", Map.of("employment", Map.of(
                "seeking_work", Map.of("$eq", true)
            )))
        );

        RuleSet eligibilitySet = new RuleSet(
            "restart-eligible",
            Operator.AND,
            List.of(new Rule("uc-active"), new Rule("uc-duration"), new Rule("unemployed"), new Rule("seeking-work"))
        );

        Specification spec = new Specification(
            "restart-programme-eligibility",
            rules,
            List.of(eligibilitySet)
        );

        EvaluationOutcome outcome = evaluator.evaluate(citizen, spec);

        assertThat(outcome.summary().matchedRules()).isEqualTo(3);
        assertThat(outcome.summary().notMatchedRules()).isEqualTo(1);
        assertThat(outcome.ruleSetResults().get(0).matched()).isFalse();
    }

    @Test
    void employmentEligibility_incompleteData_shouldBeUndetermined() {
        Map<String, Object> citizen = Map.of(
            "age", 28,
            "benefits", Map.of(
                "universal_credit", Map.of(
                    "status", "ACTIVE"
                    // Missing duration_months
                )
            )
            // Missing employment section
        );

        List<Rule> rules = List.of(
            new Rule("uc-active", Map.of("benefits", Map.of(
                "universal_credit", Map.of("status", Map.of("$eq", "ACTIVE"))
            ))),
            new Rule("uc-duration", Map.of("benefits", Map.of(
                "universal_credit", Map.of("duration_months", Map.of("$gte", 6))
            ))),
            new Rule("unemployed", Map.of("employment", Map.of(
                "status", Map.of("$eq", "UNEMPLOYED")
            )))
        );

        Specification spec = new Specification(
            "restart-programme-eligibility",
            rules,
            List.of()
        );

        EvaluationOutcome outcome = evaluator.evaluate(citizen, spec);

        assertThat(outcome.summary().matchedRules()).isEqualTo(1);
        assertThat(outcome.summary().undeterminedRules()).isEqualTo(2);
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

        List<Rule> rules = List.of(
            new Rule("has-admin-role", Map.of("roles", Map.of("$elemMatch", Map.of("$in", List.of("admin", "superuser"))))),
            new Rule("account-active", Map.of("status", Map.of("$eq", "ACTIVE"))),
            new Rule("mfa-enabled", Map.of("mfa_enabled", Map.of("$eq", true)))
        );

        RuleSet adminAccessSet = new RuleSet(
            "admin-access",
            Operator.AND,
            List.of(new Rule("has-admin-role"), new Rule("account-active"), new Rule("mfa-enabled"))
        );

        Specification spec = new Specification(
            "admin-access-control",
            rules,
            List.of(adminAccessSet)
        );

        EvaluationOutcome outcome = evaluator.evaluate(user, spec);

        assertThat(outcome.ruleSetResults().get(0).matched()).isTrue();
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

        List<Rule> rules = List.of(
            new Rule("has-admin-role", Map.of("roles", Map.of("$in", List.of("admin", "superuser")))),
            new Rule("has-user-role", Map.of("roles", Map.of("$elemMatch", Map.of("$in", List.of("user"))))),
            new Rule("account-active", Map.of("status", Map.of("$eq", "ACTIVE"))),
            new Rule("email-verified", Map.of("verified_email", Map.of("$eq", true)))
        );

        RuleSet adminAccessSet = new RuleSet(
            "admin-access",
            Operator.AND,
            List.of(new Rule("has-admin-role"), new Rule("account-active"))
        );

        RuleSet userAccessSet = new RuleSet(
            "user-access",
            Operator.AND,
            List.of(new Rule("has-user-role"), new Rule("account-active"), new Rule("email-verified"))
        );

        Specification spec = new Specification(
            "access-control",
            rules,
            List.of(adminAccessSet, userAccessSet)
        );

        EvaluationOutcome outcome = evaluator.evaluate(user, spec);

        // Admin access should fail, user access should succeed
        assertThat(outcome.ruleSetResults()).hasSize(2);
        assertThat(outcome.ruleSetResults().get(0).matched()).isFalse(); // admin-access
        assertThat(outcome.ruleSetResults().get(1).matched()).isTrue();  // user-access
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

        List<Rule> rules = List.of(
            new Rule("loyal-customer", Map.of("customer", Map.of(
                "member_since_years", Map.of("$gte", 2)
            ))),
            new Rule("minimum-purchase", Map.of("cart", Map.of(
                "total", Map.of("$gte", 100.00)
            ))),
            new Rule("valid-promo", Map.of("promotion_code", Map.of("$exists", true)))
        );

        RuleSet discountSet = new RuleSet(
            "loyalty-discount",
            Operator.AND,
            List.of(new Rule("loyal-customer"), new Rule("minimum-purchase"), new Rule("valid-promo"))
        );

        Specification spec = new Specification(
            "discount-eligibility",
            rules,
            List.of(discountSet)
        );

        EvaluationOutcome outcome = evaluator.evaluate(order, spec);

        assertThat(outcome.ruleSetResults().get(0).matched()).isTrue();
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

        List<Rule> rules = List.of(
            new Rule("low-reputation", Map.of("author", Map.of(
                "reputation_score", Map.of("$lt", 50)
            ))),
            new Rule("new-account", Map.of("author", Map.of(
                "account_age_days", Map.of("$lt", 30)
            ))),
            new Rule("has-reports", Map.of("metadata", Map.of(
                "reports", Map.of("$gt", 0)
            ))),
            new Rule("suspicious-content", Map.of("content", Map.of(
                "$regex", ".*(suspicious|test|spam).*"
            )))
        );

        RuleSet moderationSet = new RuleSet(
            "needs-review",
            Operator.OR,
            List.of(new Rule("low-reputation"), new Rule("new-account"), new Rule("has-reports"), new Rule("suspicious-content"))
        );

        Specification spec = new Specification(
            "content-moderation",
            rules,
            List.of(moderationSet)
        );

        EvaluationOutcome outcome = evaluator.evaluate(post, spec);

        // Should match because multiple flags are triggered
        assertThat(outcome.ruleSetResults().get(0).matched()).isTrue();
        assertThat(outcome.summary().matchedRules()).isGreaterThan(1);
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

        List<Rule> rules = List.of(
            new Rule("age-requirement", Map.of("applicant", Map.of(
                "personal", Map.of("age", Map.of("$gte", 18, "$lte", 65))
            ))),
            new Rule("uk-resident", Map.of("applicant", Map.of(
                "personal", Map.of("address", Map.of("country", Map.of("$eq", "UK")))
            ))),
            new Rule("income-verified", Map.of("applicant", Map.of(
                "financial", Map.of("income", Map.of("verified", Map.of("$eq", true)))
            ))),
            new Rule("minimum-income", Map.of("applicant", Map.of(
                "financial", Map.of("income", Map.of("annual_salary", Map.of("$gte", 25000)))
            ))),
            new Rule("credit-score-check", Map.of("applicant", Map.of(
                "financial", Map.of("credit_score", Map.of("$gte", 650))
            ))),
            new Rule("loan-amount-reasonable", Map.of("loan", Map.of(
                "amount", Map.of("$lte", 50000)
            )))
        );

        RuleSet approvalSet = new RuleSet(
            "loan-approval",
            Operator.AND,
            List.of(new Rule("age-requirement"), new Rule("uk-resident"), new Rule("income-verified"),
                    new Rule("minimum-income"), new Rule("credit-score-check"), new Rule("loan-amount-reasonable"))
        );

        Specification spec = new Specification(
            "loan-application-review",
            rules,
            List.of(approvalSet)
        );

        EvaluationOutcome outcome = evaluator.evaluate(application, spec);

        assertThat(outcome.summary().matchedRules()).isEqualTo(6);
        assertThat(outcome.summary().fullyDetermined()).isTrue();
        assertThat(outcome.ruleSetResults().get(0).matched()).isTrue();
    }

    // ========== Mixed Success/Failure Scenario ==========

    @Test
    void mixedScenario_partialSuccess_shouldTrackAccurately() {
        Map<String, Object> data = Map.of(
            "field1", "value1",
            "field2", 100,
            "field3", List.of("a", "b", "c")
        );

        List<Rule> rules = List.of(
            new Rule("match1", Map.of("field1", Map.of("$eq", "value1"))),
            new Rule("match2", Map.of("field2", Map.of("$gte", 50))),
            new Rule("no-match", Map.of("field2", Map.of("$lt", 50))),
            new Rule("undetermined", Map.of("field4", Map.of("$eq", "value4"))),
            new Rule("match3", Map.of("field3", Map.of("$size", 3)))
        );

        Specification spec = new Specification("mixed-spec", rules, List.of());

        EvaluationOutcome outcome = evaluator.evaluate(data, spec);

        assertThat(outcome.summary().totalRules()).isEqualTo(5);
        assertThat(outcome.summary().matchedRules()).isEqualTo(3);
        assertThat(outcome.summary().notMatchedRules()).isEqualTo(1);
        assertThat(outcome.summary().undeterminedRules()).isEqualTo(1);
        assertThat(outcome.summary().fullyDetermined()).isFalse();
    }

    // ========== Performance Test with Many Rules ==========

    @Test
    void performance_withManyRules_shouldEvaluateEfficiently() {
        Map<String, Object> doc = Map.of(
            "value", 50,
            "status", "ACTIVE",
            "tags", List.of("tag1", "tag2", "tag3")
        );

        // Create 20 rules to test parallel evaluation
        List<Rule> rules = List.of(
            new Rule("r1", Map.of("value", Map.of("$gte", 0))),
            new Rule("r2", Map.of("value", Map.of("$lte", 100))),
            new Rule("r3", Map.of("value", Map.of("$gt", 25))),
            new Rule("r4", Map.of("value", Map.of("$lt", 75))),
            new Rule("r5", Map.of("status", Map.of("$eq", "ACTIVE"))),
            new Rule("r6", Map.of("status", Map.of("$ne", "INACTIVE"))),
            new Rule("r7", Map.of("status", Map.of("$in", List.of("ACTIVE", "PENDING")))),
            new Rule("r8", Map.of("tags", Map.of("$size", 3))),
            new Rule("r9", Map.of("tags", Map.of("$all", List.of("tag1")))),
            new Rule("r10", Map.of("tags", Map.of("$in", List.of("tag2")))),
            new Rule("r11", Map.of("value", Map.of("$eq", 50))),
            new Rule("r12", Map.of("value", Map.of("$ne", 0))),
            new Rule("r13", Map.of("status", Map.of("$exists", true))),
            new Rule("r14", Map.of("status", Map.of("$type", "string"))),
            new Rule("r15", Map.of("value", Map.of("$type", "number"))),
            new Rule("r16", Map.of("tags", Map.of("$type", "array"))),
            new Rule("r17", Map.of("status", Map.of("$regex", "ACTIVE"))),
            new Rule("r18", Map.of("value", Map.of("$gte", 1, "$lte", 99))),
            new Rule("r19", Map.of("tags", Map.of("$all", List.of("tag1", "tag2")))),
            new Rule("r20", Map.of("status", Map.of("$nin", List.of("DELETED", "ARCHIVED"))))
        );

        Specification spec = new Specification("performance-spec", rules, List.of());

        EvaluationOutcome outcome = evaluator.evaluate(doc, spec);

        assertThat(outcome.ruleResults()).hasSize(20);
        assertThat(outcome.summary().fullyDetermined()).isTrue();
        // Most should match given the document structure
        assertThat(outcome.summary().matchedRules()).isGreaterThan(15);
    }
}
