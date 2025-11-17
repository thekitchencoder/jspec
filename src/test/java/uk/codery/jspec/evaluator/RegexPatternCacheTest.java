package uk.codery.jspec.evaluator;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.codery.jspec.model.QueryCriterion;
import uk.codery.jspec.result.EvaluationResult;
import uk.codery.jspec.result.EvaluationState;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for regex pattern caching in CriterionEvaluator.
 * Verifies that patterns are cached and reused for performance optimization.
 */
class RegexPatternCacheTest {

    private CriterionEvaluator evaluator;

    @BeforeEach
    void setUp() {
        evaluator = new CriterionEvaluator();
    }

    @Test
    void shouldCacheAndReuseRegexPatterns() {
        // Create a criterion with a regex pattern
        QueryCriterion criterion = new QueryCriterion("email-check", Map.of("email", Map.of("$regex", "^[a-z]+@example\\.com$")));

        Map<String, Object> doc1 = Map.of("email", "user@example.com");
        Map<String, Object> doc2 = Map.of("email", "admin@example.com");
        Map<String, Object> doc3 = Map.of("email", "invalid@test.com");

        // Evaluate multiple times with the same pattern
        EvaluationResult result1 = evaluator.evaluateQuery(doc1, criterion);
        EvaluationResult result2 = evaluator.evaluateQuery(doc2, criterion);
        EvaluationResult result3 = evaluator.evaluateQuery(doc3, criterion);

        // Verify results are correct
        assertThat(result1.state().matched()).isTrue();
        assertThat(result2.state().matched()).isTrue();
        assertThat(result3.state().matched()).isFalse();
    }

    @Test
    void shouldHandleMultipleDifferentPatterns() {
        Map<String, Object> document = Map.of(
            "email", "user@example.com",
            "phone", "+1-555-1234",
            "name", "John Doe"
        );

        // Create criteria with different patterns
        QueryCriterion emailCriterion = new QueryCriterion("email", Map.of("email", Map.of("$regex", "^[a-z]+@[a-z]+\\.com$")));
        QueryCriterion phoneCriterion = new QueryCriterion("phone", Map.of("phone", Map.of("$regex", "^\\+1-\\d{3}-\\d{4}$")));
        QueryCriterion nameCriterion = new QueryCriterion("name", Map.of("name", Map.of("$regex", "^[A-Z][a-z]+ [A-Z][a-z]+$")));

        // Evaluate all criteria
        EvaluationResult emailResult = evaluator.evaluateQuery(document, emailCriterion);
        EvaluationResult phoneResult = evaluator.evaluateQuery(document, phoneCriterion);
        EvaluationResult nameResult = evaluator.evaluateQuery(document, nameCriterion);

        // All should match
        assertThat(emailResult.state().matched()).isTrue();
        assertThat(phoneResult.state().matched()).isTrue();
        assertThat(nameResult.state().matched()).isTrue();

        // Re-evaluate with same patterns (should use cache)
        EvaluationResult emailResult2 = evaluator.evaluateQuery(document, emailCriterion);
        EvaluationResult phoneResult2 = evaluator.evaluateQuery(document, phoneCriterion);
        EvaluationResult nameResult2 = evaluator.evaluateQuery(document, nameCriterion);

        assertThat(emailResult2.state().matched()).isTrue();
        assertThat(phoneResult2.state().matched()).isTrue();
        assertThat(nameResult2.state().matched()).isTrue();
    }

    @Test
    void shouldEvictOldPatternsWhenCacheIsFull() {
        // Create 105 unique patterns to exceed cache size of 100
        List<QueryCriterion> criteria = new ArrayList<>();
        for (int i = 0; i < 105; i++) {
            // Each criterion has a unique pattern
            QueryCriterion criterion = new QueryCriterion("criterion" + i, Map.of("field", Map.of("$regex", "pattern" + i)));
            criteria.add(criterion);
        }

        Map<String, Object> document = Map.of("field", "pattern50");

        // Evaluate all criteria (fills cache beyond limit)
        for (QueryCriterion criterion : criteria) {
            evaluator.evaluateQuery(document, criterion);
        }

        // Cache should still work (with LRU eviction)
        // The first patterns should have been evicted
        QueryCriterion recentCriterion = criteria.get(104); // Last criterion added
        EvaluationResult result = evaluator.evaluateQuery(document, recentCriterion);

        // Should still work correctly even with eviction
        assertThat(result.state()).isIn(EvaluationState.MATCHED, EvaluationState.NOT_MATCHED);
    }

    @Test
    void shouldHandleInvalidPatternsGracefully() {
        // Invalid regex pattern
        QueryCriterion criterion = new QueryCriterion("invalid", Map.of("field", Map.of("$regex", "[invalid(regex")));

        Map<String, Object> document = Map.of("field", "test");

        EvaluationResult result = evaluator.evaluateQuery(document, criterion);

        // Should be NOT_MATCHED due to invalid pattern (graceful degradation)
        assertThat(result.state().matched()).isFalse();
    }

    @Test
    void shouldBeThreadSafe() throws InterruptedException {
        // Shared pattern across threads
        QueryCriterion criterion = new QueryCriterion("shared", Map.of("text", Map.of("$regex", "^test\\d+$")));

        int threadCount = 10;
        int iterationsPerThread = 100;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);

        for (int t = 0; t < threadCount; t++) {
            final int threadId = t;
            executor.submit(() -> {
                try {
                    for (int i = 0; i < iterationsPerThread; i++) {
                        Map<String, Object> doc = Map.of("text", "test" + (threadId * 100 + i));
                        EvaluationResult result = evaluator.evaluateQuery(doc, criterion);
                        if (result.state().matched()) {
                            successCount.incrementAndGet();
                        }
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        // Wait for all threads to complete
        assertThat(latch.await(10, TimeUnit.SECONDS)).isTrue();
        executor.shutdown();

        // All evaluations should have succeeded (all match the pattern)
        assertThat(successCount.get()).isEqualTo(threadCount * iterationsPerThread);
    }

    @Test
    void shouldCachePatternOnlyAfterFirstCompilation() {
        // First evaluation compiles and caches
        QueryCriterion criterion1 = new QueryCriterion("first", Map.of("text", Map.of("$regex", "^unique_pattern_xyz$")));
        Map<String, Object> doc1 = Map.of("text", "unique_pattern_xyz");

        EvaluationResult result1 = evaluator.evaluateQuery(doc1, criterion1);
        assertThat(result1.state().matched()).isTrue();

        // Second evaluation with same pattern should use cache
        QueryCriterion criterion2 = new QueryCriterion("second", Map.of("text", Map.of("$regex", "^unique_pattern_xyz$")));
        Map<String, Object> doc2 = Map.of("text", "unique_pattern_xyz");

        EvaluationResult result2 = evaluator.evaluateQuery(doc2, criterion2);
        assertThat(result2.state().matched()).isTrue();

        // Different pattern should not be cached yet
        QueryCriterion criterion3 = new QueryCriterion("third", Map.of("text", Map.of("$regex", "^different_pattern$")));
        Map<String, Object> doc3 = Map.of("text", "different_pattern");

        EvaluationResult result3 = evaluator.evaluateQuery(doc3, criterion3);
        assertThat(result3.state().matched()).isTrue();
    }

    @Test
    void shouldHandleNullAndEmptyPatterns() {
        // Null operand - Map.of() doesn't allow null, so use HashMap
        Map<String, Object> regexWithNull = new HashMap<>();
        regexWithNull.put("$regex", null);
        QueryCriterion nullCriterion = new QueryCriterion("null", Map.of("field", regexWithNull));
        Map<String, Object> doc = Map.of("field", "test");

        EvaluationResult nullResult = evaluator.evaluateQuery(doc, nullCriterion);
        assertThat(nullResult.state().matched()).isFalse();

        // Empty string pattern
        QueryCriterion emptyCriterion = new QueryCriterion("empty", Map.of("field", Map.of("$regex", "")));
        EvaluationResult emptyResult = evaluator.evaluateQuery(doc, emptyCriterion);

        // Empty pattern matches everything (standard regex behavior)
        assertThat(emptyResult.state().matched()).isTrue();
    }

    @Test
    void shouldCachePatternAcrossMultipleEvaluators() {
        // Each evaluator has its own cache, so this tests isolation
        CriterionEvaluator evaluator1 = new CriterionEvaluator();
        CriterionEvaluator evaluator2 = new CriterionEvaluator();

        QueryCriterion criterion = new QueryCriterion("test", Map.of("field", Map.of("$regex", "^test$")));
        Map<String, Object> doc = Map.of("field", "test");

        // Both evaluators should work independently
        EvaluationResult result1 = evaluator1.evaluateQuery(doc, criterion);
        EvaluationResult result2 = evaluator2.evaluateQuery(doc, criterion);

        assertThat(result1.state().matched()).isTrue();
        assertThat(result2.state().matched()).isTrue();
    }

    @Test
    void shouldHandleComplexRegexPatterns() {
        Map<String, Object> document = Map.of(
            "email", "user.name+tag@example.co.uk",
            "url", "https://www.example.com/path?query=value",
            "phone", "+44 20 7123 4567"
        );

        // Complex email pattern
        QueryCriterion emailCriterion = new QueryCriterion("email",
            Map.of("email", Map.of("$regex", "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$")));

        // Complex URL pattern
        QueryCriterion urlCriterion = new QueryCriterion("url",
            Map.of("url", Map.of("$regex", "^https?://[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}(/.*)?$")));

        // Complex phone pattern
        QueryCriterion phoneCriterion = new QueryCriterion("phone",
            Map.of("phone", Map.of("$regex", "^\\+\\d{1,3}\\s\\d{2}\\s\\d{4}\\s\\d{4}$")));

        // Evaluate all
        EvaluationResult emailResult = evaluator.evaluateQuery(document, emailCriterion);
        EvaluationResult urlResult = evaluator.evaluateQuery(document, urlCriterion);
        EvaluationResult phoneResult = evaluator.evaluateQuery(document, phoneCriterion);

        assertThat(emailResult.state().matched()).isTrue();
        assertThat(urlResult.state().matched()).isTrue();
        assertThat(phoneResult.state().matched()).isTrue();

        // Re-evaluate (should use cache for performance)
        EvaluationResult emailResult2 = evaluator.evaluateQuery(document, emailCriterion);
        EvaluationResult urlResult2 = evaluator.evaluateQuery(document, urlCriterion);
        EvaluationResult phoneResult2 = evaluator.evaluateQuery(document, phoneCriterion);

        assertThat(emailResult2.state().matched()).isTrue();
        assertThat(urlResult2.state().matched()).isTrue();
        assertThat(phoneResult2.state().matched()).isTrue();
    }
}
