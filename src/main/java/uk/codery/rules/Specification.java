package uk.codery.rules;

import java.util.List;

public record Specification(String id, List<Rule> rules, List<RuleSet> ruleSets) {
}
