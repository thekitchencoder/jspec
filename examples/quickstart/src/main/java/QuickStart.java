/*
 * QuickStart.java - JSpec Quick Start Example
 *
 * This is a standalone, copy-paste ready example demonstrating basic
 * usage of the JSON Specification Evaluator (jspec) library.
 *
 * To run:
 * 1. Add jspec dependency to your project (see README.md)
 * 2. Copy this file to your project
 * 3. Run: java QuickStart.java
 *
 * Maven dependency:
 * <dependency>
 *     <groupId>uk.codery</groupId>
 *     <artifactId>jspec</artifactId>
 *     <version>0.5.2</version>
 * </dependency>
 */

// Required imports
import uk.codery.jspec.builder.CriterionBuilder;
import uk.codery.jspec.builder.CompositeCriterionBuilder;
import uk.codery.jspec.builder.SpecificationBuilder;
import uk.codery.jspec.evaluator.SpecificationEvaluator;
import uk.codery.jspec.model.CompositeCriterion;
import uk.codery.jspec.model.QueryCriterion;
import uk.codery.jspec.model.Specification;
import uk.codery.jspec.result.EvaluationOutcome;
import uk.codery.jspec.result.EvaluationResult;
import uk.codery.jspec.result.EvaluationState;

import java.util.List;
import java.util.Map;

/**
 * Quick Start example for jspec - JSON Specification Evaluator.
 *
 * This example demonstrates:
 * - Creating criteria with the fluent builder API
 * - Evaluating JSON documents against specifications
 * - Inspecting evaluation results
 */
public class QuickStart {

    public static void main(String[] args) {
        System.out.println("=== JSpec Quick Start Example ===\n");

        // Step 1: Create a sample document (could be parsed from JSON/YAML)
        Map<String, Object> user = Map.of(
            "name", "Alice",
            "age", 28,
            "email", "alice@example.com",
            "status", "active",
            "roles", List.of("user", "admin"),
            "address", Map.of(
                "city", "London",
                "country", "UK"
            )
        );

        System.out.println("Document to evaluate:");
        System.out.println("  " + user + "\n");

        // Step 2: Create criteria using the fluent builder API

        // Criterion 1: User must be at least 18 years old
        QueryCriterion ageCheck = QueryCriterion.builder()
            .id("age-check")
            .field("age").gte(18)
            .build();

        // Criterion 2: Status must be "active"
        QueryCriterion statusCheck = QueryCriterion.builder()
            .id("status-check")
            .field("status").eq("active")
            .build();

        // Criterion 3: User must have "admin" role
        QueryCriterion adminRole = QueryCriterion.builder()
            .id("admin-role")
            .field("roles").in("admin")
            .build();

        // Criterion 4: Email must match pattern
        QueryCriterion emailFormat = QueryCriterion.builder()
            .id("email-format")
            .field("email").regex("^[\\w.]+@[\\w.]+\\.[a-z]+$")
            .build();

        // Criterion 5: Nested field - must be in UK
        QueryCriterion ukResident = QueryCriterion.builder()
            .id("uk-resident")
            .field("address.country").eq("UK")
            .build();

        // Criterion 6: Composite - combine criteria with AND logic
        CompositeCriterion eligibility = CompositeCriterion.builder()
            .id("eligibility")
            .and()
            .criteria(ageCheck, statusCheck, adminRole)
            .build();

        // Step 3: Build a specification with all criteria
        Specification spec = Specification.builder()
            .id("user-validation")
            .addCriterion(ageCheck)
            .addCriterion(statusCheck)
            .addCriterion(adminRole)
            .addCriterion(emailFormat)
            .addCriterion(ukResident)
            .addCriterion(eligibility)
            .build();

        // Step 4: Create evaluator and evaluate the document
        SpecificationEvaluator evaluator = new SpecificationEvaluator(spec);
        EvaluationOutcome outcome = evaluator.evaluate(user);

        // Step 5: Inspect results
        System.out.println("Evaluation Results:");
        System.out.println("-------------------");

        for (EvaluationResult result : outcome.results()) {
            String status = switch (result.state()) {
                case MATCHED -> "PASSED";
                case NOT_MATCHED -> "FAILED";
                case UNDETERMINED -> "UNKNOWN";
            };
            System.out.printf("  %-15s : %s%n", result.id(), status);
        }

        // Step 6: Check summary statistics
        System.out.println("\nSummary:");
        System.out.println("  Matched:      " + outcome.summary().matched());
        System.out.println("  Not Matched:  " + outcome.summary().notMatched());
        System.out.println("  Undetermined: " + outcome.summary().undetermined());

        // Step 7: Quick check if all passed
        boolean allPassed = outcome.results().stream()
            .allMatch(r -> r.state().matched());
        System.out.println("\nAll criteria passed: " + allPassed);

        // Step 8: Demonstrate looking up specific results
        System.out.println("\nLooking up specific results:");

        // Using Optional-based API (recommended)
        outcome.find("eligibility").ifPresent(result -> {
            System.out.println("  Eligibility check: " + result.state());
        });

        // Direct lookup using asMap()
        EvaluationResult ageResult = outcome.asMap().get("age-check");
        if (ageResult != null) {
            System.out.println("  Age check passed: " + ageResult.state().matched());
        }

        // Step 9: Demonstrate handling missing data
        System.out.println("\n--- Testing with missing data ---");

        Map<String, Object> incompleteUser = Map.of(
            "name", "Bob",
            "age", 16  // Too young, missing other fields
        );

        EvaluationOutcome incompleteOutcome = evaluator.evaluate(incompleteUser);

        System.out.println("\nIncomplete document results:");
        for (EvaluationResult result : incompleteOutcome.results()) {
            String status = switch (result.state()) {
                case MATCHED -> "PASSED";
                case NOT_MATCHED -> "FAILED";
                case UNDETERMINED -> "UNKNOWN (missing data)";
            };
            System.out.printf("  %-15s : %s%n", result.id(), status);

            // Show reason for undetermined results
            if (result.state() == EvaluationState.UNDETERMINED) {
                System.out.printf("    Reason: %s%n", result.reason());
            }
        }

        System.out.println("\n=== Quick Start Complete ===");
    }
}
