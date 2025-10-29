package uk.codery.rules;

public interface Result {
    String id();
    boolean matched();
    String reason();
}
