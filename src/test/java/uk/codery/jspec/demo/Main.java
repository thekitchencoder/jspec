package uk.codery.jspec.demo;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import uk.codery.jspec.evaluator.SpecificationEvaluator;
import uk.codery.jspec.formatter.*;
import uk.codery.jspec.model.Specification;
import uk.codery.jspec.result.EvaluationOutcome;
import uk.codery.jspec.result.EvaluationResult;

import java.io.File;
import java.util.Arrays;

/**
 * Demo CLI application for JSON Specification Evaluator.
 *
 * <p>Demonstrates the use of formatters to output evaluation results in different formats:
 * <ul>
 *   <li>JSON - Structured JSON output</li>
 *   <li>YAML - Human-readable YAML output</li>
 *   <li>Text - Formatted text with summary and detailed results</li>
 * </ul>
 *
 * <p>Usage: java Main &lt;criteria.yaml&gt; &lt;document.yaml&gt; [options]
 *
 * <h2>Options</h2>
 * <ul>
 *   <li>--json     Output results in JSON format (pretty-printed)</li>
 *   <li>--yaml     Output results in YAML format</li>
 *   <li>--text     Output results in formatted text (showFailures mode)</li>
 *   <li>--summary  Only show summary of results (legacy format)</li>
 * </ul>
 *
 * <h2>Examples</h2>
 * <pre>{@code
 * # JSON output
 * java Main specification.yaml document.yaml --json
 *
 * # YAML output
 * java Main specification.yaml document.yaml --yaml
 *
 * # Text output (showFailures)
 * java Main specification.yaml document.yaml --text
 *
 * # Summary only
 * java Main specification.yaml document.yaml --summary
 * }</pre>
 */
public class Main {
    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("Usage: java Main <criteria.yaml> <document.yaml> [options]");
            System.err.println("\nOptions:");
            System.err.println("  --json     Output results in JSON format (pretty-printed)");
            System.err.println("  --yaml     Output results in YAML format");
            System.err.println("  --text     Output results in formatted text (showFailures mode)");
            System.err.println("  --custom   Output results using custom formatter");
            System.err.println("  --summary  Only show summary of results ");
            System.err.println("\nDefault: Formatted text output (non-showFailures)");
            System.exit(1);
        }

        String specFile = args[0];
        String docFile = args[1];
        boolean jsonOutput = Arrays.asList(args).contains("--json");
        boolean yamlOutput = Arrays.asList(args).contains("--yaml");
        boolean textOutput = Arrays.asList(args).contains("--text");
        boolean customOutput = Arrays.asList(args).contains("--custom");
        boolean summaryOnly = Arrays.asList(args).contains("--summary");

        try {
            ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());

            Object doc = yamlMapper.readValue(new File(docFile), Object.class);
            Specification specification = yamlMapper.readValue(new File(specFile), Specification.class);

            SpecificationEvaluator evaluator = new SpecificationEvaluator();
            EvaluationOutcome outcome = evaluator.evaluate(doc, specification);
            ResultFormatter formatter;
            if (jsonOutput) {
                formatter = new JsonResultFormatter();
            } else if (yamlOutput) {
                formatter = new YamlResultFormatter();
            } else if (textOutput) {
                formatter = new TextResultFormatter(true);
            } else if (summaryOnly) {
                formatter = new SummaryResultFormatter(true);
            } else if (customOutput) {
                formatter = new CustomResultFormatter(true);
            } else {
                // Default: formatted text (non-showFailures)
               formatter = new TextResultFormatter(false);
            }

            System.out.println(formatter.format(outcome));

            boolean allMatched = outcome.results().stream()
                    .allMatch(EvaluationResult::matched);
            System.exit(allMatched ? 0 : 1);

        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}
