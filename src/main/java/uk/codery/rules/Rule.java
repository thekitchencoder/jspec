package uk.codery.rules;

import java.util.Collections;
import java.util.Map;

public record Rule(String id, Map<String, Object> query) {
    public Rule(String id) {
        this(id, Collections.emptyMap());
    }
}
