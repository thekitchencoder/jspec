package uk.codery.jspec.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.codery.jspec.evaluator.SpecificationEvaluator;
import uk.codery.jspec.model.CriteriaGroup;
import uk.codery.jspec.model.Criterion;
import uk.codery.jspec.model.Junction;
import uk.codery.jspec.model.Specification;
import uk.codery.jspec.result.EvaluationOutcome;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end integration tests simulating real-world scenarios:
 * - Employment eligibility checks
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
        List<Criterion> criteria = List.of(
            new Criterion("uc-active", Map.of("benefits", Map.of(
                "universal_credit", Map.of("status", Map.of("$eq", "ACTIVE"))
            ))),
            new Criterion("uc-duration", Map.of("benefits", Map.of(
                "universal_credit", Map.of("duration_months", Map.of("$gte", 6))
            ))),
            new Criterion("unemployed", Map.of("employment", Map.of(
                "status", Map.of("$eq", "UNEMPLOYED")
            ))),
            new Criterion("seeking-work", Map.of("employment", Map.of(
                "seeking_work", Map.of("$eq", true)
            )))
        );

        CriteriaGroup eligibilitySet = new CriteriaGroup(
            "restart-eligible",
            Junction.AND,
            List.of(new Criterion("uc-active"), new Criterion("uc-duration"), new Criterion("unemployed"), new Criterion("seeking-work"))
        );

        Specification spec = new Specification(
            "restart-programme-eligibility",
                criteria,
            List.of(eligibilitySet)
        );

        EvaluationOutcome outcome = evaluator.evaluate(citizen, spec);

        assertThat(outcome.summary().matched()).isEqualTo(4);
        assertThat(outcome.summary().fullyDetermined()).isTrue();
        assertThat(outcome.criteriaGroupResults().getFirst().matched()).isTrue();
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

        List<Criterion> criteria = List.of(
            new Criterion("uc-active", Map.of("benefits", Map.of(
                "universal_credit", Map.of("status", Map.of("$eq", "ACTIVE"))
            ))),
            new Criterion("uc-duration", Map.of("benefits", Map.of(
                "universal_credit", Map.of("duration_months", Map.of("$gte", 6))
            ))),
            new Criterion("unemployed", Map.of("employment", Map.of(
                "status", Map.of("$eq", "UNEMPLOYED")
            ))),
            new Criterion("seeking-work", Map.of("employment", Map.of(
                "seeking_work", Map.of("$eq", true)
            )))
        );

        CriteriaGroup eligibilitySet = new CriteriaGroup(
            "restart-eligible",
            Junction.AND,
            List.of(new Criterion("uc-active"), new Criterion("uc-duration"), new Criterion("unemployed"), new Criterion("seeking-work"))
        );

        Specification spec = new Specification(
            "restart-programme-eligibility",
                criteria,
            List.of(eligibilitySet)
        );

        EvaluationOutcome outcome = evaluator.evaluate(citizen, spec);

        assertThat(outcome.summary().matched()).isEqualTo(3);
        assertThat(outcome.summary().notMatched()).isEqualTo(1);
        assertThat(outcome.criteriaGroupResults().getFirst().matched()).isFalse();
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

        List<Criterion> criteria = List.of(
            new Criterion("uc-active", Map.of("benefits", Map.of(
                "universal_credit", Map.of("status", Map.of("$eq", "ACTIVE"))
            ))),
            new Criterion("uc-duration", Map.of("benefits", Map.of(
                "universal_credit", Map.of("duration_months", Map.of("$gte", 6))
            ))),
            new Criterion("unemployed", Map.of("employment", Map.of(
                "status", Map.of("$eq", "UNEMPLOYED")
            )))
        );

        Specification spec = new Specification(
            "restart-programme-eligibility",
                criteria,
            List.of()
        );

        EvaluationOutcome outcome = evaluator.evaluate(citizen, spec);

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

        List<Criterion> criteria = List.of(
            new Criterion("has-admin-role", Map.of("roles", Map.of("$elemMatch", Map.of("$in", List.of("admin", "superuser"))))),
            new Criterion("account-active", Map.of("status", Map.of("$eq", "ACTIVE"))),
            new Criterion("mfa-enabled", Map.of("mfa_enabled", Map.of("$eq", true)))
        );

        CriteriaGroup adminAccessSet = new CriteriaGroup(
            "admin-access",
            Junction.AND,
            List.of(new Criterion("has-admin-role"), new Criterion("account-active"), new Criterion("mfa-enabled"))
        );

        Specification spec = new Specification(
            "admin-access-control",
                criteria,
            List.of(adminAccessSet)
        );

        EvaluationOutcome outcome = evaluator.evaluate(user, spec);

        assertThat(outcome.criteriaGroupResults().getFirst().matched()).isTrue();
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

        List<Criterion> criteria = List.of(
            new Criterion("has-admin-role", Map.of("roles", Map.of("$in", List.of("admin", "superuser")))),
            new Criterion("has-user-role", Map.of("roles", Map.of("$elemMatch", Map.of("$in", List.of("user"))))),
            new Criterion("account-active", Map.of("status", Map.of("$eq", "ACTIVE"))),
            new Criterion("email-verified", Map.of("verified_email", Map.of("$eq", true)))
        );

        CriteriaGroup adminAccessSet = new CriteriaGroup(
            "admin-access",
            Junction.AND,
            List.of(new Criterion("has-admin-role"), new Criterion("account-active"))
        );

        CriteriaGroup userAccessSet = new CriteriaGroup(
            "user-access",
            Junction.AND,
            List.of(new Criterion("has-user-role"), new Criterion("account-active"), new Criterion("email-verified"))
        );

        Specification spec = new Specification(
            "access-control",
                criteria,
            List.of(adminAccessSet, userAccessSet)
        );

        EvaluationOutcome outcome = evaluator.evaluate(user, spec);

        // Admin access should fail, user access should succeed
        assertThat(outcome.criteriaGroupResults()).hasSize(2);
        assertThat(outcome.criteriaGroupResults().get(0).matched()).isFalse(); // admin-access
        assertThat(outcome.criteriaGroupResults().get(1).matched()).isTrue();  // user-access
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

        List<Criterion> criteria = List.of(
            new Criterion("loyal-customer", Map.of("customer", Map.of(
                "member_since_years", Map.of("$gte", 2)
            ))),
            new Criterion("minimum-purchase", Map.of("cart", Map.of(
                "total", Map.of("$gte", 100.00)
            ))),
            new Criterion("valid-promo", Map.of("promotion_code", Map.of("$exists", true)))
        );

        CriteriaGroup discountSet = new CriteriaGroup(
            "loyalty-discount",
            Junction.AND,
            List.of(new Criterion("loyal-customer"), new Criterion("minimum-purchase"), new Criterion("valid-promo"))
        );

        Specification spec = new Specification(
            "discount-eligibility",
                criteria,
            List.of(discountSet)
        );

        EvaluationOutcome outcome = evaluator.evaluate(order, spec);

        assertThat(outcome.criteriaGroupResults().getFirst().matched()).isTrue();
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

        List<Criterion> criteria = List.of(
            new Criterion("low-reputation", Map.of("author", Map.of(
                "reputation_score", Map.of("$lt", 50)
            ))),
            new Criterion("new-account", Map.of("author", Map.of(
                "account_age_days", Map.of("$lt", 30)
            ))),
            new Criterion("has-reports", Map.of("metadata", Map.of(
                "reports", Map.of("$gt", 0)
            ))),
            new Criterion("suspicious-content", Map.of("content", Map.of(
                "$regex", ".*(suspicious|test|spam).*"
            )))
        );

        CriteriaGroup moderationSet = new CriteriaGroup(
            "needs-review",
            Junction.OR,
            List.of(new Criterion("low-reputation"), new Criterion("new-account"), new Criterion("has-reports"), new Criterion("suspicious-content"))
        );

        Specification spec = new Specification(
            "content-moderation",
                criteria,
            List.of(moderationSet)
        );

        EvaluationOutcome outcome = evaluator.evaluate(post, spec);

        // Should match because multiple flags are triggered
        assertThat(outcome.criteriaGroupResults().getFirst().matched()).isTrue();
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

        List<Criterion> criteria = List.of(
            new Criterion("age-requirement", Map.of("applicant", Map.of(
                "personal", Map.of("age", Map.of("$gte", 18, "$lte", 65))
            ))),
            new Criterion("uk-resident", Map.of("applicant", Map.of(
                "personal", Map.of("address", Map.of("country", Map.of("$eq", "UK")))
            ))),
            new Criterion("income-verified", Map.of("applicant", Map.of(
                "financial", Map.of("income", Map.of("verified", Map.of("$eq", true)))
            ))),
            new Criterion("minimum-income", Map.of("applicant", Map.of(
                "financial", Map.of("income", Map.of("annual_salary", Map.of("$gte", 25000)))
            ))),
            new Criterion("credit-score-check", Map.of("applicant", Map.of(
                "financial", Map.of("credit_score", Map.of("$gte", 650))
            ))),
            new Criterion("loan-amount-reasonable", Map.of("loan", Map.of(
                "amount", Map.of("$lte", 50000)
            )))
        );

        CriteriaGroup approvalSet = new CriteriaGroup(
            "loan-approval",
            Junction.AND,
            List.of(new Criterion("age-requirement"), new Criterion("uk-resident"), new Criterion("income-verified"),
                    new Criterion("minimum-income"), new Criterion("credit-score-check"), new Criterion("loan-amount-reasonable"))
        );

        Specification spec = new Specification(
            "loan-application-review",
                criteria,
            List.of(approvalSet)
        );

        EvaluationOutcome outcome = evaluator.evaluate(application, spec);

        assertThat(outcome.summary().matched()).isEqualTo(6);
        assertThat(outcome.summary().fullyDetermined()).isTrue();
        assertThat(outcome.criteriaGroupResults().getFirst().matched()).isTrue();
    }

    // ========== Mixed Success/Failure Scenario ==========

    @Test
    void mixedScenario_partialSuccess_shouldTrackAccurately() {
        Map<String, Object> data = Map.of(
            "field1", "value1",
            "field2", 100,
            "field3", List.of("a", "b", "c")
        );

        List<Criterion> criteria = List.of(
            new Criterion("match1", Map.of("field1", Map.of("$eq", "value1"))),
            new Criterion("match2", Map.of("field2", Map.of("$gte", 50))),
            new Criterion("no-match", Map.of("field2", Map.of("$lt", 50))),
            new Criterion("undetermined", Map.of("field4", Map.of("$eq", "value4"))),
            new Criterion("match3", Map.of("field3", Map.of("$size", 3)))
        );

        Specification spec = new Specification("mixed-spec", criteria, List.of());

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
        List<Criterion> criteria = List.of(
            new Criterion("r1", Map.of("value", Map.of("$gte", 0))),
            new Criterion("r2", Map.of("value", Map.of("$lte", 100))),
            new Criterion("r3", Map.of("value", Map.of("$gt", 25))),
            new Criterion("r4", Map.of("value", Map.of("$lt", 75))),
            new Criterion("r5", Map.of("status", Map.of("$eq", "ACTIVE"))),
            new Criterion("r6", Map.of("status", Map.of("$ne", "INACTIVE"))),
            new Criterion("r7", Map.of("status", Map.of("$in", List.of("ACTIVE", "PENDING")))),
            new Criterion("r8", Map.of("tags", Map.of("$size", 3))),
            new Criterion("r9", Map.of("tags", Map.of("$all", List.of("tag1")))),
            new Criterion("r10", Map.of("tags", Map.of("$in", List.of("tag2")))),
            new Criterion("r11", Map.of("value", Map.of("$eq", 50))),
            new Criterion("r12", Map.of("value", Map.of("$ne", 0))),
            new Criterion("r13", Map.of("status", Map.of("$exists", true))),
            new Criterion("r14", Map.of("status", Map.of("$type", "string"))),
            new Criterion("r15", Map.of("value", Map.of("$type", "number"))),
            new Criterion("r16", Map.of("tags", Map.of("$type", "array"))),
            new Criterion("r17", Map.of("status", Map.of("$regex", "ACTIVE"))),
            new Criterion("r18", Map.of("value", Map.of("$gte", 1, "$lte", 99))),
            new Criterion("r19", Map.of("tags", Map.of("$all", List.of("tag1", "tag2")))),
            new Criterion("r20", Map.of("status", Map.of("$nin", List.of("DELETED", "ARCHIVED"))))
        );

        Specification spec = new Specification("performance-spec", criteria, List.of());

        EvaluationOutcome outcome = evaluator.evaluate(doc, spec);

        assertThat(outcome.evaluationResults()).hasSize(20);
        assertThat(outcome.summary().fullyDetermined()).isTrue();
        // Most should match given the document structure
        assertThat(outcome.summary().matched()).isGreaterThan(15);
    }
}
