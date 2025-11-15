import uk.codery.jspec.evaluator.SpecificationEvaluator;
import uk.codery.jspec.model.Criterion;
import uk.codery.jspec.model.Specification;
import uk.codery.jspec.result.EvaluationOutcome;
import uk.codery.jspec.result.EvaluationResult;

import java.util.Map;

/**
 * Hello World - Simple demonstration of JSON Specification Evaluator
 *
 * This example shows the most basic usage with the fluent builder API:
 * 1. Create a document (Map)
 * 2. Define criteria using Criterion.builder()
 * 3. Create a specification using Specification.builder()
 * 4. Evaluate the document against the criteria
 * 5. Check the results
 */
public class HelloWorld {

    public static void main(String[] args) {
        System.out.println("=== JSON Specification Evaluator - Hello World ===\n");

        // Step 1: Create a simple document to evaluate
        Map<String, Object> document = Map.of(
            "name", "John Doe",
            "age", 25,
            "city", "London",
            "status", "ACTIVE"
        );

        System.out.println("Document:");
        System.out.println("  name: " + document.get("name"));
        System.out.println("  age: " + document.get("age"));
        System.out.println("  city: " + document.get("city"));
        System.out.println("  status: " + document.get("status"));
        System.out.println();

        // Step 2: Define criteria to check using the fluent builder API
        Criterion ageCheck = Criterion.builder()
            .id("age-check")
            .field("age").gte(18)  // Age must be >= 18
            .build();

        Criterion statusCheck = Criterion.builder()
            .id("status-check")
            .field("status").eq("ACTIVE")  // Status must be ACTIVE
            .build();

        Criterion cityCheck = Criterion.builder()
            .id("city-check")
            .field("city").in("London", "Paris", "Berlin")  // City must be in list
            .build();

        System.out.println("Criteria:");
        System.out.println("  ✓ age-check: age >= 18");
        System.out.println("  ✓ status-check: status == 'ACTIVE'");
        System.out.println("  ✓ city-check: city in ['London', 'Paris', 'Berlin']");
        System.out.println();

        // Step 3: Create a specification with the criteria using the builder
        Specification spec = Specification.builder()
            .id("hello-world-spec")
            .criteria(ageCheck, statusCheck, cityCheck)
            .build();

        // Step 4: Evaluate the document
        SpecificationEvaluator evaluator = new SpecificationEvaluator();
        EvaluationOutcome outcome = evaluator.evaluate(document, spec);

        // Step 5: Display results
        System.out.println("Results:");
        for (EvaluationResult result : outcome.evaluationResults()) {
            String status = result.matched() ? "✓ MATCHED" : "✗ NOT MATCHED";
            System.out.println("  " + status + " - " + result.id());
            if (!result.matched()) {
                System.out.println("    Reason: " + result.reason());
            }
        }
        System.out.println();

        // Step 6: Show summary
        System.out.println("Summary:");
        System.out.println("  Total criteria: " + outcome.summary().totalCriteria());
        System.out.println("  Matched: " + outcome.summary().matchedCriteria());
        System.out.println("  Not matched: " + outcome.summary().notMatchedCriteria());
        System.out.println("  Undetermined: " + outcome.summary().undeterminedCriteria());
        System.out.println();

        // Step 7: Overall result
        boolean allMatched = outcome.evaluationResults().stream()
            .allMatch(EvaluationResult::matched);

        if (allMatched) {
            System.out.println("🎉 All criteria matched! Document is valid.");
        } else {
            System.out.println("❌ Some criteria did not match. Document is invalid.");
        }
    }
}
