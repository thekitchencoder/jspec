import uk.codery.jspec.evaluator.SpecificationEvaluator;
import uk.codery.jspec.model.Criterion;
import uk.codery.jspec.model.Specification;
import uk.codery.jspec.result.EvaluationOutcome;
import uk.codery.jspec.result.EvaluationResult;

import java.util.List;
import java.util.Map;

/**
 * Hello World - Simple demonstration of JSON Specification Evaluator
 *
 * This example shows the most basic usage:
 * 1. Create a document (Map)
 * 2. Define criteria to evaluate
 * 3. Evaluate the document against the criteria
 * 4. Check the results
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

        // Step 2: Define criteria to check
        Criterion ageCheck = new Criterion(
            "age-check",
            Map.of("age", Map.of("$gte", 18))  // Age must be >= 18
        );

        Criterion statusCheck = new Criterion(
            "status-check",
            Map.of("status", Map.of("$eq", "ACTIVE"))  // Status must be ACTIVE
        );

        Criterion cityCheck = new Criterion(
            "city-check",
            Map.of("city", Map.of("$in", List.of("London", "Paris", "Berlin")))  // City must be in list
        );

        System.out.println("Criteria:");
        System.out.println("  ✓ age-check: age >= 18");
        System.out.println("  ✓ status-check: status == 'ACTIVE'");
        System.out.println("  ✓ city-check: city in ['London', 'Paris', 'Berlin']");
        System.out.println();

        // Step 3: Create a specification with the criteria
        Specification spec = new Specification(
            "hello-world-spec",
            List.of(ageCheck, statusCheck, cityCheck),
            List.of()  // No criteria groups for this simple example
        );

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
