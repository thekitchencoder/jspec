package uk.codery.jspec.demo;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import uk.codery.jspec.evaluator.SpecificationEvaluator;
import uk.codery.jspec.model.Specification;
import uk.codery.jspec.result.EvaluationOutcome;
import uk.codery.jspec.result.EvaluationResult;
import uk.codery.jspec.result.Result;
import uk.codery.jspec.result.CriteriaGroupResult;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import static java.util.function.Predicate.not;

/**
 * Alternative CLI that uses Jackson (jackson-dataformat-yaml) to parse both the
 * input document and the specification from YAML files. The command-line
 * interface mirrors YamlRuleEvaluatorCli.
 * Usage: java JacksonYamlRuleEvaluatorCli <criteria.yaml> <document.yaml> [--json] [--summary]
 */
public class Main {
    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("Usage: java JacksonYamlRuleEvaluatorCli <criteria.yaml> <document.yaml> [--json] [--summary]");
            System.err.println("\nOptions:");
            System.err.println("  --json     Output results in JSON format");
            System.err.println("  --summary  Only show summary of results");
            System.exit(1);
        }

        String specFile = args[0];
        String docFile = args[1];
        boolean jsonOutput = Arrays.asList(args).contains("--json");
        boolean summaryOnly = Arrays.asList(args).contains("--summary");

        try {
            ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());

            Object doc = yamlMapper.readValue(new File(docFile), Object.class);
            Specification specification = yamlMapper.readValue(new File(specFile), Specification.class);

            SpecificationEvaluator evaluator = new SpecificationEvaluator();
            EvaluationOutcome outcome = evaluator.evaluate(doc, specification);

            if (jsonOutput) {
                outputJson(outcome);
            } else if (summaryOnly) {
                outputSummary(outcome);
            } else {
                outputDetailed(outcome);
            }

            boolean allMatched = Stream.concat(outcome.evaluationResults().stream(), outcome.criteriaGroupResults().stream())
                    .allMatch(Result::matched);
            System.exit(allMatched ? 0 : 1);

        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static void outputDetailed(EvaluationOutcome outcome) {
        System.out.println(outcome.specificationId() + " evaluation results");
        Stream.concat(outcome.evaluationResults().stream(), outcome.criteriaGroupResults().stream())
                .forEach(result -> System.out.println(result.toString()));
    }

    private static void outputSummary(EvaluationOutcome outcome) {
        long passedRules = outcome.evaluationResults().stream()
                .filter(Result::matched)
                .count();
        long failedRules = outcome.evaluationResults().stream()
                .filter(not(Result::matched))
                .count();
        long passedSets = outcome.criteriaGroupResults().stream()
                .filter(Result::matched)
                .count();
        long failedSets = outcome.criteriaGroupResults().stream()
                .filter(not(Result::matched))
                .count();

        System.out.println(outcome.specificationId() + " evaluation summary.");
        System.out.println("Rules: " + (passedRules + failedRules) + " total, " + passedRules + " passed, " + failedRules + " failed");
        System.out.println("RuleSets: " + (passedSets + failedSets) + " total, " + passedSets + " passed, " + failedSets + " failed");

        if (failedRules > 0) {
            System.out.println("\nFailed Rules:");
            outcome.evaluationResults().stream()
                    .filter(not(Result::matched))
                    .forEach(r -> System.out.println("  " + r.id() + ": " + r.reason()));
        }

        if (failedSets > 0) {
            System.out.println("\nFailed RuleSets:");
            outcome.criteriaGroupResults().stream()
                    .filter(not(Result::matched))
                    .forEach(rs -> {
                        System.out.println("  " + rs.id() + " (" + rs.junction() + ")");
                        rs.evaluationResults().stream()
                                .filter(not(EvaluationResult::matched))
                                .forEach(rr -> System.out.println("    - " + rr.id() + ": " + rr.reason()));
                    });
        }
    }

    private static void outputJson(EvaluationOutcome outcome) {
        System.out.println("{");
        System.out.println("  \"specification\": \"" + outcome.specificationId() + "\",");
        System.out.println("  \"criteria\": [");
        List<EvaluationResult> evResultList = new ArrayList<>(outcome.evaluationResults());
        for (int i = 0; i < evResultList.size(); i++) {
            EvaluationResult r = evResultList.get(i);
            System.out.print("    {\"id\": \"" + r.id() + "\", \"matched\": " + r.matched());
            if (r.missingPaths() != null) {
                System.out.print(", \"missingPaths\": [" + String.join(", ", r.missingPaths().stream().map(p -> "\"" + p + "\"").toArray(String[]::new)) + "]");
            }
            System.out.print("}");
            if (i < evResultList.size() - 1) System.out.println(",");
            else System.out.println();
        }
        System.out.println("  ],");
        System.out.println("  \"criteriaGroups\": [");

        for (int i = 0; i < outcome.criteriaGroupResults().size(); i++) {
            CriteriaGroupResult rs = outcome.criteriaGroupResults().get(i);
            System.out.print("    {\"id\": \"" + rs.id() + "\", \"junction\": \"" + rs.junction() + "\", \"matched\": " + rs.matched() + ", \"criteria\": [");
            List<EvaluationResult> srr = new ArrayList<>(rs.evaluationResults());
            for (int j = 0; j < srr.size(); j++) {
                EvaluationResult r = srr.get(j);
                System.out.print("{\"id\": \"" + r.id() + "\", \"matched\": " + r.matched() + "}");
                if (j < srr.size() - 1) System.out.print(", ");
            }
            System.out.print("]}");
            if (i < outcome.criteriaGroupResults().size() - 1) System.out.println(",");
            else System.out.println();
        }
        System.out.println("  ]");
        System.out.println("}");
    }
}
