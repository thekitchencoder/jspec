package uk.codery.jspec.demo;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import uk.codery.jspec.evaluator.SpecificationEvaluator;
import uk.codery.jspec.model.Specification;
import uk.codery.jspec.result.EvaluationOutcome;
import uk.codery.jspec.result.EvaluationResult;
import uk.codery.jspec.result.CompositeResult;
import uk.codery.jspec.result.QueryResult;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import static java.util.function.Predicate.not;

/**
 * Alternative CLI that uses Jackson (jackson-dataformat-yaml) to parse both the
 * input document and the specification from YAML files. The command-line
 * interface mirrors YamlCriterionEvaluatorCli.
 * Usage: java JacksonYamlCriterionEvaluatorCli <criteria.yaml> <document.yaml> [--json] [--summary]
 */
public class Main {
    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("Usage: java JacksonYamlCriterionEvaluatorCli <criteria.yaml> <document.yaml> [--json] [--summary]");
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

            boolean allMatched = outcome.results().stream()
                    .allMatch(EvaluationResult::matched);
            System.exit(allMatched ? 0 : 1);

        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static void outputDetailed(EvaluationOutcome outcome) {
        System.out.println(outcome.specificationId() + " evaluation results");
        outcome.results().forEach(result -> System.out.println(result.toString()));
    }

    private static void outputSummary(EvaluationOutcome outcome) {
        long passedQueries = outcome.queryResults().stream()
                .filter(EvaluationResult::matched)
                .count();
        long failedQueries = outcome.queryResults().stream()
                .filter(not(EvaluationResult::matched))
                .count();
        long passedComposites = outcome.compositeResults().stream()
                .filter(EvaluationResult::matched)
                .count();
        long failedComposites = outcome.compositeResults().stream()
                .filter(not(EvaluationResult::matched))
                .count();

        System.out.println(outcome.specificationId() + " evaluation summary.");
        System.out.println("Queries: " + (passedQueries + failedQueries) + " total, " + passedQueries + " passed, " + failedQueries + " failed");
        System.out.println("Composites: " + (passedComposites + failedComposites) + " total, " + passedComposites + " passed, " + failedComposites + " failed");

        if (failedQueries > 0) {
            System.out.println("\nFailed Queries:");
            outcome.queryResults().stream()
                    .filter(not(EvaluationResult::matched))
                    .forEach(r -> System.out.println("  " + r.id() + ": " + r.reason()));
        }

        if (failedComposites > 0) {
            System.out.println("\nFailed Composites:");
            outcome.compositeResults().stream()
                    .filter(not(EvaluationResult::matched))
                    .forEach(composite -> {
                        System.out.println("  " + composite.id() + " (" + composite.junction() + ")");
                        composite.childResults().stream()
                                .filter(not(EvaluationResult::matched))
                                .forEach(child -> System.out.println("    - " + child.id() + ": " + child.reason()));
                    });
        }
    }

    private static void outputJson(EvaluationOutcome outcome) {
        System.out.println("{");
        System.out.println("  \"specification\": \"" + outcome.specificationId() + "\",");
        System.out.println("  \"queries\": [");
        List<QueryResult> queryResultList = new ArrayList<>(outcome.queryResults());
        for (int i = 0; i < queryResultList.size(); i++) {
            QueryResult r = queryResultList.get(i);
            System.out.print("    {\"id\": \"" + r.id() + "\", \"matched\": " + r.matched());
            if (r.missingPaths() != null && !r.missingPaths().isEmpty()) {
                System.out.print(", \"missingPaths\": [" + String.join(", ", r.missingPaths().stream().map(p -> "\"" + p + "\"").toArray(String[]::new)) + "]");
            }
            System.out.print("}");
            if (i < queryResultList.size() - 1) System.out.println(",");
            else System.out.println();
        }
        System.out.println("  ],");
        System.out.println("  \"composites\": [");

        for (int i = 0; i < outcome.compositeResults().size(); i++) {
            CompositeResult composite = outcome.compositeResults().get(i);
            System.out.print("    {\"id\": \"" + composite.id() + "\", \"junction\": \"" + composite.junction() + "\", \"matched\": " + composite.matched() + ", \"children\": [");
            List<EvaluationResult> children = new ArrayList<>(composite.childResults());
            for (int j = 0; j < children.size(); j++) {
                EvaluationResult child = children.get(j);
                System.out.print("{\"id\": \"" + child.id() + "\", \"matched\": " + child.matched() + "}");
                if (j < children.size() - 1) System.out.print(", ");
            }
            System.out.print("]}");
            if (i < outcome.compositeResults().size() - 1) System.out.println(",");
            else System.out.println();
        }
        System.out.println("  ]");
        System.out.println("}");
    }
}
