package uk.codery.jspec.evaluator;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.codery.jspec.model.QueryCriterion;
import uk.codery.jspec.result.EvaluationResult;
import uk.codery.jspec.result.EvaluationState;

import java.util.Collection;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for EvaluationContext.
 */
class EvaluationContextTest {

    private EvaluationContext context;
    private CriterionEvaluator evaluator;

    @BeforeEach
    void setUp() {
        evaluator = new CriterionEvaluator();
        context = new EvaluationContext(evaluator);
    }

    // ==================== Constructor Tests ====================

    @Test
    void constructor_createsEmptyContext() {
        assertThat(context.cacheSize()).isZero();
    }

    @Test
    void evaluator_returnsEvaluator() {
        assertThat(context.evaluator()).isSameAs(evaluator);
    }

    // ==================== getOrEvaluate Tests ====================

    @Test
    void getOrEvaluate_evaluatesAndCaches() {
        QueryCriterion criterion = new QueryCriterion("test",
                Map.of("field", Map.of("$eq", "value")));
        Map<String, Object> document = Map.of("field", "value");

        EvaluationResult result = context.getOrEvaluate(criterion, document);

        assertThat(result.state()).isEqualTo(EvaluationState.MATCHED);
        assertThat(context.cacheSize()).isEqualTo(1);
        assertThat(context.isCached("test")).isTrue();
    }

    @Test
    void getOrEvaluate_returnsCachedResult() {
        QueryCriterion criterion = new QueryCriterion("test",
                Map.of("field", Map.of("$eq", "value")));
        Map<String, Object> document = Map.of("field", "value");

        EvaluationResult result1 = context.getOrEvaluate(criterion, document);
        EvaluationResult result2 = context.getOrEvaluate(criterion, document);

        // Should return same cached object
        assertThat(result1).isSameAs(result2);
        assertThat(context.cacheSize()).isEqualTo(1);
    }

    @Test
    void getOrEvaluate_multipleCriteria() {
        QueryCriterion criterion1 = new QueryCriterion("test1",
                Map.of("field1", Map.of("$eq", "value1")));
        QueryCriterion criterion2 = new QueryCriterion("test2",
                Map.of("field2", Map.of("$eq", "value2")));

        Map<String, Object> document = Map.of(
                "field1", "value1",
                "field2", "value2"
        );

        context.getOrEvaluate(criterion1, document);
        context.getOrEvaluate(criterion2, document);

        assertThat(context.cacheSize()).isEqualTo(2);
        assertThat(context.isCached("test1")).isTrue();
        assertThat(context.isCached("test2")).isTrue();
    }

    // ==================== getCached Tests ====================

    @Test
    void getCached_returnsNull_whenNotCached() {
        assertThat(context.getCached("nonexistent")).isNull();
    }

    @Test
    void getCached_returnsResult_whenCached() {
        QueryCriterion criterion = new QueryCriterion("test",
                Map.of("field", Map.of("$eq", "value")));
        Map<String, Object> document = Map.of("field", "value");

        context.getOrEvaluate(criterion, document);

        EvaluationResult cached = context.getCached("test");
        assertThat(cached).isNotNull();
        assertThat(cached.state()).isEqualTo(EvaluationState.MATCHED);
    }

    // ==================== isCached Tests ====================

    @Test
    void isCached_returnsFalse_whenNotCached() {
        assertThat(context.isCached("nonexistent")).isFalse();
    }

    @Test
    void isCached_returnsTrue_whenCached() {
        QueryCriterion criterion = new QueryCriterion("test",
                Map.of("field", Map.of("$eq", "value")));
        Map<String, Object> document = Map.of("field", "value");

        context.getOrEvaluate(criterion, document);

        assertThat(context.isCached("test")).isTrue();
    }

    // ==================== getAllResults Tests ====================

    @Test
    void getAllResults_returnsEmpty_whenNoResults() {
        Collection<EvaluationResult> results = context.getAllResults();

        assertThat(results).isEmpty();
    }

    @Test
    void getAllResults_returnsAllCachedResults() {
        QueryCriterion criterion1 = new QueryCriterion("test1",
                Map.of("field", Map.of("$eq", "value")));
        QueryCriterion criterion2 = new QueryCriterion("test2",
                Map.of("field", Map.of("$ne", "other")));

        Map<String, Object> document = Map.of("field", "value");

        context.getOrEvaluate(criterion1, document);
        context.getOrEvaluate(criterion2, document);

        Collection<EvaluationResult> results = context.getAllResults();

        assertThat(results).hasSize(2);
    }

    // ==================== clearCache Tests ====================

    @Test
    void clearCache_removesAllCachedResults() {
        QueryCriterion criterion = new QueryCriterion("test",
                Map.of("field", Map.of("$eq", "value")));
        Map<String, Object> document = Map.of("field", "value");

        context.getOrEvaluate(criterion, document);
        assertThat(context.cacheSize()).isEqualTo(1);

        context.clearCache();

        assertThat(context.cacheSize()).isZero();
        assertThat(context.isCached("test")).isFalse();
        assertThat(context.getCached("test")).isNull();
    }

    @Test
    void clearCache_allowsNewEvaluations() {
        QueryCriterion criterion = new QueryCriterion("test",
                Map.of("field", Map.of("$eq", "value")));
        Map<String, Object> document = Map.of("field", "value");

        EvaluationResult result1 = context.getOrEvaluate(criterion, document);
        context.clearCache();

        // Can evaluate again and cache new result
        EvaluationResult result2 = context.getOrEvaluate(criterion, document);

        assertThat(context.cacheSize()).isEqualTo(1);
        // New evaluation, so different object (though same content)
        assertThat(result1).isNotSameAs(result2);
    }

    // ==================== cacheSize Tests ====================

    @Test
    void cacheSize_reflectsNumberOfCachedResults() {
        assertThat(context.cacheSize()).isZero();

        QueryCriterion criterion1 = new QueryCriterion("test1",
                Map.of("field", Map.of("$eq", "value")));
        context.getOrEvaluate(criterion1, Map.of("field", "value"));
        assertThat(context.cacheSize()).isEqualTo(1);

        QueryCriterion criterion2 = new QueryCriterion("test2",
                Map.of("field", Map.of("$eq", "value")));
        context.getOrEvaluate(criterion2, Map.of("field", "value"));
        assertThat(context.cacheSize()).isEqualTo(2);

        context.clearCache();
        assertThat(context.cacheSize()).isZero();
    }

    // ==================== Thread Safety Tests ====================

    @Test
    void getOrEvaluate_threadSafe() throws InterruptedException {
        QueryCriterion criterion = new QueryCriterion("test",
                Map.of("field", Map.of("$eq", "value")));
        Map<String, Object> document = Map.of("field", "value");

        int threadCount = 10;
        Thread[] threads = new Thread[threadCount];
        EvaluationResult[] results = new EvaluationResult[threadCount];

        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            threads[i] = new Thread(() -> results[index] = context.getOrEvaluate(criterion, document));
            threads[i].start();
        }

        for (Thread thread : threads) {
            thread.join();
        }

        // All results should be the same cached instance
        EvaluationResult first = results[0];
        for (int i = 1; i < threadCount; i++) {
            assertThat(results[i]).isSameAs(first);
        }

        // Only one entry in cache
        assertThat(context.cacheSize()).isEqualTo(1);
    }
}
