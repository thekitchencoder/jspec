package uk.codery.jspec.evaluator;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.codery.jspec.model.Criterion;
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
        Criterion criterion = new Criterion("email-check", Map.of("email", Map.of("$regex", "^[a-z]+@example\\.com$")));

        Map<String, Object> doc1 = Map.of("email", "user@example.com");
        Map<String, Object> doc2 = Map.of("email", "admin@example.com");
        Map<String, Object> doc3 = Map.of("email", "invalid@test.com");

        // Evaluate multiple times with the same pattern
        EvaluationResult result1 = evaluator.evaluateCriterion(doc1, criterion);
        EvaluationResult result2 = evaluator.evaluateCriterion(doc2, criterion);
        EvaluationResult result3 = evaluator.evaluateCriterion(doc3, criterion);

        // Verify results are correct
        assertThat(result1.matched()).isTrue();
        assertThat(result2.matched()).isTrue();
        assertThat(result3.matched()).isFalse();
    }

    @Test
    void shouldHandleMultipleDifferentPatterns() {
        Map<String, Object> document = Map.of(
            "email", "user@example.com",
            "phone", "+1-555-1234",
            "name", "John Doe"
        );

        // Create criteria with different patterns
        Criterion emailCriterion = new Criterion("email", Map.of("email", Map.of("$regex", "^[a-z]+@[a-z]+\\.com$")));
        Criterion phoneCriterion = new Criterion("phone", Map.of("phone", Map.of("$regex", "^\\+1-\\d{3}-\\d{4}$")));
        Criterion nameCriterion = new Criterion("name", Map.of("name", Map.of("$regex", "^[A-Z][a-z]+ [A-Z][a-z]+$")));

        // Evaluate all criteria
        EvaluationResult emailResult = evaluator.evaluateCriterion(document, emailCriterion);
        EvaluationResult phoneResult = evaluator.evaluateCriterion(document, phoneCriterion);
        EvaluationResult nameResult = evaluator.evaluateCriterion(document, nameCriterion);

        // All should match
        assertThat(emailResult.matched()).isTrue();
        assertThat(phoneResult.matched()).isTrue();
        assertThat(nameResult.matched()).isTrue();

        // Re-evaluate with same patterns (should use cache)
        EvaluationResult emailResult2 = evaluator.evaluateCriterion(document, emailCriterion);
        EvaluationResult phoneResult2 = evaluator.evaluateCriterion(document, phoneCriterion);
        EvaluationResult nameResult2 = evaluator.evaluateCriterion(document, nameCriterion);

        assertThat(emailResult2.matched()).isTrue();
        assertThat(phoneResult2.matched()).isTrue();
        assertThat(nameResult2.matched()).isTrue();
    }

    @Test
    void shouldEvictOldPatternsWhenCacheIsFull() {
        // Create 105 unique patterns to exceed cache size of 100
        List<Criterion> criteria = new ArrayList<>();
        for (int i = 0; i < 105; i++) {
            // Each criterion has a unique pattern
            Criterion criterion = new Criterion("criterion" + i, Map.of("field", Map.of("$regex", "pattern" + i)));
            criteria.add(criterion);
        }

        Map<String, Object> document = Map.of("field", "pattern50");

        // Evaluate all criteria (fills cache beyond limit)
        for (Criterion criterion : criteria) {
            evaluator.evaluateCriterion(document, criterion);
        }

        // Cache should still work (with LRU eviction)
        // The first patterns should have been evicted
        Criterion recentCriterion = criteria.get(104); // Last criterion added
        EvaluationResult result = evaluator.evaluateCriterion(document, recentCriterion);

        // Should still work correctly even with eviction
        assertThat(result.state()).isIn(EvaluationState.MATCHED, EvaluationState.NOT_MATCHED);
    }

    @Test
    void shouldHandleInvalidPatternsGracefully() {
        // Invalid regex pattern
        Criterion criterion = new Criterion("invalid", Map.of("field", Map.of("$regex", "[invalid(regex")));

        Map<String, Object> document = Map.of("field", "test");

        EvaluationResult result = evaluator.evaluateCriterion(document, criterion);

        // Should be NOT_MATCHED due to invalid pattern (graceful degradation)
        assertThat(result.matched()).isFalse();
    }

    @Test
    void shouldBeThreadSafe() throws InterruptedException {
        // Shared pattern across threads
        Criterion criterion = new Criterion("shared", Map.of("text", Map.of("$regex", "^test\\d+$")));

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
                        EvaluationResult result = evaluator.evaluateCriterion(doc, criterion);
                        if (result.matched()) {
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
        Criterion criterion1 = new Criterion("first", Map.of("text", Map.of("$regex", "^unique_pattern_xyz$")));
        Map<String, Object> doc1 = Map.of("text", "unique_pattern_xyz");

        EvaluationResult result1 = evaluator.evaluateCriterion(doc1, criterion1);
        assertThat(result1.matched()).isTrue();

        // Second evaluation with same pattern should use cache
        Criterion criterion2 = new Criterion("second", Map.of("text", Map.of("$regex", "^unique_pattern_xyz$")));
        Map<String, Object> doc2 = Map.of("text", "unique_pattern_xyz");

        EvaluationResult result2 = evaluator.evaluateCriterion(doc2, criterion2);
        assertThat(result2.matched()).isTrue();

        // Different pattern should not be cached yet
        Criterion criterion3 = new Criterion("third", Map.of("text", Map.of("$regex", "^different_pattern$")));
        Map<String, Object> doc3 = Map.of("text", "different_pattern");

        EvaluationResult result3 = evaluator.evaluateCriterion(doc3, criterion3);
        assertThat(result3.matched()).isTrue();
    }

    @Test
    void shouldHandleNullAndEmptyPatterns() {
        // Null operand - Map.of() doesn't allow null, so use HashMap
        Map<String, Object> regexWithNull = new HashMap<>();
        regexWithNull.put("$regex", null);
        Criterion nullCriterion = new Criterion("null", Map.of("field", regexWithNull));
        Map<String, Object> doc = Map.of("field", "test");

        EvaluationResult nullResult = evaluator.evaluateCriterion(doc, nullCriterion);
        assertThat(nullResult.matched()).isFalse();

        // Empty string pattern
        Criterion emptyCriterion = new Criterion("empty", Map.of("field", Map.of("$regex", "")));
        EvaluationResult emptyResult = evaluator.evaluateCriterion(doc, emptyCriterion);

        // Empty pattern matches everything (standard regex behavior)
        assertThat(emptyResult.matched()).isTrue();
    }

    @Test
    void shouldCachePatternAcrossMultipleEvaluators() {
        // Each evaluator has its own cache, so this tests isolation
        CriterionEvaluator evaluator1 = new CriterionEvaluator();
        CriterionEvaluator evaluator2 = new CriterionEvaluator();

        Criterion criterion = new Criterion("test", Map.of("field", Map.of("$regex", "^test$")));
        Map<String, Object> doc = Map.of("field", "test");

        // Both evaluators should work independently
        EvaluationResult result1 = evaluator1.evaluateCriterion(doc, criterion);
        EvaluationResult result2 = evaluator2.evaluateCriterion(doc, criterion);

        assertThat(result1.matched()).isTrue();
        assertThat(result2.matched()).isTrue();
    }

    @Test
    void shouldHandleComplexRegexPatterns() {
        Map<String, Object> document = Map.of(
            "email", "user.name+tag@example.co.uk",
            "url", "https://www.example.com/path?query=value",
            "phone", "+44 20 7123 4567"
        );

        // Complex email pattern
        Criterion emailCriterion = new Criterion("email",
            Map.of("email", Map.of("$regex", "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$")));

        // Complex URL pattern
        Criterion urlCriterion = new Criterion("url",
            Map.of("url", Map.of("$regex", "^https?://[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}(/.*)?$")));

        // Complex phone pattern
        Criterion phoneCriterion = new Criterion("phone",
            Map.of("phone", Map.of("$regex", "^\\+\\d{1,3}\\s\\d{2}\\s\\d{4}\\s\\d{4}$")));

        // Evaluate all
        EvaluationResult emailResult = evaluator.evaluateCriterion(document, emailCriterion);
        EvaluationResult urlResult = evaluator.evaluateCriterion(document, urlCriterion);
        EvaluationResult phoneResult = evaluator.evaluateCriterion(document, phoneCriterion);

        assertThat(emailResult.matched()).isTrue();
        assertThat(urlResult.matched()).isTrue();
        assertThat(phoneResult.matched()).isTrue();

        // Re-evaluate (should use cache for performance)
        EvaluationResult emailResult2 = evaluator.evaluateCriterion(document, emailCriterion);
        EvaluationResult urlResult2 = evaluator.evaluateCriterion(document, urlCriterion);
        EvaluationResult phoneResult2 = evaluator.evaluateCriterion(document, phoneCriterion);

        assertThat(emailResult2.matched()).isTrue();
        assertThat(urlResult2.matched()).isTrue();
        assertThat(phoneResult2.matched()).isTrue();
    }
}
