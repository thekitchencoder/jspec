package uk.codery.jspec.builder;

import org.junit.jupiter.api.Test;
import uk.codery.jspec.model.QueryCriterion;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/**
 * Comprehensive tests for CriterionBuilder.
 *
 * <p>Tests cover:
 * <ul>
 *   <li>Static builder() method on Criterion</li>
 *   <li>All comparison operators</li>
 *   <li>All collection operators</li>
 *   <li>All advanced operators</li>
 *   <li>Multiple fields</li>
 *   <li>Multiple conditions on same field</li>
 *   <li>Custom operators</li>
 *   <li>Error handling</li>
 * </ul>
 */
class CriterionBuilderTest {

    // ==================== Static Builder Method ====================

    @Test
    void testStaticBuilderMethod_shouldReturnBuilder() {
        CriterionBuilder builder = QueryCriterion.builder();

        assertThat(builder).isNotNull();
        assertThat(builder).isInstanceOf(CriterionBuilder.class);
    }

    // ==================== Basic Construction ====================

    @Test
    void testBuilder_simpleEquality() {
        QueryCriterion criterion = QueryCriterion.builder()
                .id("status-check")
                .field("status").eq("active")
                .build();

        assertThat(criterion.id()).isEqualTo("status-check");
        assertThat(criterion.query()).containsKey("status");

        @SuppressWarnings("unchecked")
        Map<String, Object> statusQuery = (Map<String, Object>) criterion.query().get("status");
        assertThat(statusQuery).containsEntry("$eq", "active");
    }

    @Test
    void testBuilder_withoutId_shouldThrowException() {
        assertThatThrownBy(() -> {
            QueryCriterion.builder()
                    .field("status").eq("active")
                    .build();
        })
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Criterion ID must be set");
    }

    @Test
    void testBuilder_withoutFields_shouldThrowException() {
        assertThatThrownBy(() -> {
            QueryCriterion.builder()
                    .id("test")
                    .build();
        })
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("At least one field condition must be defined");
    }

    @Test
    void testBuilder_nullId_shouldThrowException() {
        assertThatThrownBy(() -> {
            QueryCriterion.builder().id(null);
        })
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Criterion ID cannot be null or empty");
    }

    @Test
    void testBuilder_emptyId_shouldThrowException() {
        assertThatThrownBy(() -> {
            QueryCriterion.builder().id("");
        })
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Criterion ID cannot be null or empty");
    }

    @Test
    void testBuilder_nullFieldName_shouldThrowException() {
        assertThatThrownBy(() -> {
            QueryCriterion.builder().id("test").field(null);
        })
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Field name cannot be null or empty");
    }

    @Test
    void testBuilder_emptyFieldName_shouldThrowException() {
        assertThatThrownBy(() -> {
            QueryCriterion.builder().id("test").field("");
        })
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Field name cannot be null or empty");
    }

    // ==================== Comparison Operators ====================

    @Test
    void testBuilder_eq_operator() {
        QueryCriterion criterion = QueryCriterion.builder()
                .id("eq-test")
                .field("name").eq("John")
                .build();

        @SuppressWarnings("unchecked")
        Map<String, Object> query = (Map<String, Object>) criterion.query().get("name");
        assertThat(query).containsEntry("$eq", "John");
    }

    @Test
    void testBuilder_ne_operator() {
        QueryCriterion criterion = QueryCriterion.builder()
                .id("ne-test")
                .field("status").ne("inactive")
                .build();

        @SuppressWarnings("unchecked")
        Map<String, Object> query = (Map<String, Object>) criterion.query().get("status");
        assertThat(query).containsEntry("$ne", "inactive");
    }

    @Test
    void testBuilder_gt_operator() {
        QueryCriterion criterion = QueryCriterion.builder()
                .id("gt-test")
                .field("age").gt(18).build();

        @SuppressWarnings("unchecked")
        Map<String, Object> query = (Map<String, Object>) criterion.query().get("age");
        assertThat(query).containsEntry("$gt", 18);
    }

    @Test
    void testBuilder_gte_operator() {
        QueryCriterion criterion = QueryCriterion.builder()
                .id("gte-test")
                .field("age").gte(18).build();

        @SuppressWarnings("unchecked")
        Map<String, Object> query = (Map<String, Object>) criterion.query().get("age");
        assertThat(query).containsEntry("$gte", 18);
    }

    @Test
    void testBuilder_lt_operator() {
        QueryCriterion criterion = QueryCriterion.builder()
                .id("lt-test")
                .field("price").lt(100.0).build();

        @SuppressWarnings("unchecked")
        Map<String, Object> query = (Map<String, Object>) criterion.query().get("price");
        assertThat(query).containsEntry("$lt", 100.0);
    }

    @Test
    void testBuilder_lte_operator() {
        QueryCriterion criterion = QueryCriterion.builder()
                .id("lte-test")
                .field("price").lte(100.0).build();

        @SuppressWarnings("unchecked")
        Map<String, Object> query = (Map<String, Object>) criterion.query().get("price");
        assertThat(query).containsEntry("$lte", 100.0);
    }

    // ==================== Collection Operators ====================

    @Test
    void testBuilder_in_operator() {
        QueryCriterion criterion = QueryCriterion.builder()
                .id("in-test")
                .field("status").in("active", "pending", "approved")
                .build();

        @SuppressWarnings("unchecked")
        Map<String, Object> query = (Map<String, Object>) criterion.query().get("status");
        assertThat(query).containsEntry("$in", List.of("active", "pending", "approved"));
    }

    @Test
    void testBuilder_nin_operator() {
        QueryCriterion criterion = QueryCriterion.builder()
                .id("nin-test")
                .field("status").nin("deleted", "archived")
                .build();

        @SuppressWarnings("unchecked")
        Map<String, Object> query = (Map<String, Object>) criterion.query().get("status");
        assertThat(query).containsEntry("$nin", List.of("deleted", "archived"));
    }

    @Test
    void testBuilder_all_operator() {
        QueryCriterion criterion = QueryCriterion.builder()
                .id("all-test")
                .field("tags").all("important", "urgent")
                .build();

        @SuppressWarnings("unchecked")
        Map<String, Object> query = (Map<String, Object>) criterion.query().get("tags");
        assertThat(query).containsEntry("$all", List.of("important", "urgent"));
    }

    @Test
    void testBuilder_size_operator() {
        QueryCriterion criterion = QueryCriterion.builder()
                .id("size-test")
                .field("items").size(5)
                .build();

        @SuppressWarnings("unchecked")
        Map<String, Object> query = (Map<String, Object>) criterion.query().get("items");
        assertThat(query).containsEntry("$size", 5);
    }

    @Test
    void testBuilder_elemMatch_operator() {
        Map<String, Object> elementQuery = Map.of("status", Map.of("$eq", "active"));

        QueryCriterion criterion = QueryCriterion.builder()
                .id("elemMatch-test")
                .field("users").elemMatch(elementQuery)
                .build();

        @SuppressWarnings("unchecked")
        Map<String, Object> query = (Map<String, Object>) criterion.query().get("users");
        assertThat(query).containsEntry("$elemMatch", elementQuery);
    }

    // ==================== Advanced Operators ====================

    @Test
    void testBuilder_exists_operator_true() {
        QueryCriterion criterion = QueryCriterion.builder()
                .id("exists-test")
                .field("email").exists(true)
                .build();

        @SuppressWarnings("unchecked")
        Map<String, Object> query = (Map<String, Object>) criterion.query().get("email");
        assertThat(query).containsEntry("$exists", true);
    }

    @Test
    void testBuilder_exists_operator_false() {
        QueryCriterion criterion = QueryCriterion.builder()
                .id("not-exists-test")
                .field("deletedAt").exists(false)
                .build();

        @SuppressWarnings("unchecked")
        Map<String, Object> query = (Map<String, Object>) criterion.query().get("deletedAt");
        assertThat(query).containsEntry("$exists", false);
    }

    @Test
    void testBuilder_type_operator() {
        QueryCriterion criterion = QueryCriterion.builder()
                .id("type-test")
                .field("count").type("number")
                .build();

        @SuppressWarnings("unchecked")
        Map<String, Object> query = (Map<String, Object>) criterion.query().get("count");
        assertThat(query).containsEntry("$type", "number");
    }

    @Test
    void testBuilder_regex_operator() {
        QueryCriterion criterion = QueryCriterion.builder()
                .id("regex-test")
                .field("email").regex("^[\\w.]+@[\\w.]+\\.[a-z]+$")
                .build();

        @SuppressWarnings("unchecked")
        Map<String, Object> query = (Map<String, Object>) criterion.query().get("email");
        assertThat(query).containsEntry("$regex", "^[\\w.]+@[\\w.]+\\.[a-z]+$");
    }

    // ==================== Custom Operators ====================

    @Test
    void testBuilder_customOperator() {
        QueryCriterion criterion = QueryCriterion.builder()
                .id("custom-test")
                .field("username").operator("$length", 8)
                .build();

        @SuppressWarnings("unchecked")
        Map<String, Object> query = (Map<String, Object>) criterion.query().get("username");
        assertThat(query).containsEntry("$length", 8);
    }

    // ==================== Multiple Conditions on Same Field ====================

    @Test
    void testBuilder_rangeQuery_gte_and_lte() {
        QueryCriterion criterion = QueryCriterion.builder()
                .id("age-range")
                .field("age").gte(18).and().lte(65)
                .build();

        @SuppressWarnings("unchecked")
        Map<String, Object> query = (Map<String, Object>) criterion.query().get("age");
        assertThat(query).containsEntry("$gte", 18);
        assertThat(query).containsEntry("$lte", 65);
    }

    @Test
    void testBuilder_rangeQuery_gt_and_lt() {
        QueryCriterion criterion = QueryCriterion.builder()
                .id("price-range")
                .field("price").gt(10.0).and().lt(100.0)
                .build();

        @SuppressWarnings("unchecked")
        Map<String, Object> query = (Map<String, Object>) criterion.query().get("price");
        assertThat(query).containsEntry("$gt", 10.0);
        assertThat(query).containsEntry("$lt", 100.0);
    }

    // ==================== Multiple Fields ====================

    @Test
    void testBuilder_multipleFields() {
        QueryCriterion criterion = QueryCriterion.builder()
                .id("user-validation")
                .field("age").gte(18)
                .field("status").eq("active")
                .field("email").exists(true)
                .build();

        assertThat(criterion.query()).hasSize(3);
        assertThat(criterion.query()).containsKeys("age", "status", "email");

        @SuppressWarnings("unchecked")
        Map<String, Object> ageQuery = (Map<String, Object>) criterion.query().get("age");
        assertThat(ageQuery).containsEntry("$gte", 18);

        @SuppressWarnings("unchecked")
        Map<String, Object> statusQuery = (Map<String, Object>) criterion.query().get("status");
        assertThat(statusQuery).containsEntry("$eq", "active");

        @SuppressWarnings("unchecked")
        Map<String, Object> emailQuery = (Map<String, Object>) criterion.query().get("email");
        assertThat(emailQuery).containsEntry("$exists", true);
    }

    // ==================== Nested Fields (Dot Notation) ====================

    @Test
    void testBuilder_nestedField() {
        QueryCriterion criterion = QueryCriterion.builder()
                .id("address-check")
                .field("address.city").eq("London")
                .build();

        assertThat(criterion.query()).containsKey("address.city");

        @SuppressWarnings("unchecked")
        Map<String, Object> query = (Map<String, Object>) criterion.query().get("address.city");
        assertThat(query).containsEntry("$eq", "London");
    }

    @Test
    void testBuilder_multipleNestedFields() {
        QueryCriterion criterion = QueryCriterion.builder()
                .id("address-validation")
                .field("address.city").eq("London")
                .field("address.postalCode").regex("^[A-Z]{1,2}\\d")
                .field("address.country").eq("UK")
                .build();

        assertThat(criterion.query()).hasSize(3);
        assertThat(criterion.query()).containsKeys("address.city", "address.postalCode", "address.country");
    }

    // ==================== Real-World Examples ====================

    @Test
    void testBuilder_realWorldExample_employmentCheck() {
        QueryCriterion criterion = QueryCriterion.builder()
                .id("employment-check")
                .field("employment.status").eq("EMPLOYED")
                .field("employment.monthsEmployed").gte(12)
                .field("employment.employerName").exists(true)
                .build();

        assertThat(criterion.query()).hasSize(3);
    }

    @Test
    void testBuilder_realWorldExample_priceRange() {
        QueryCriterion criterion = QueryCriterion.builder()
                .id("affordable-products")
                .field("price").gte(25.0).and().lte(100.0)
                .field("status").eq("available")
                .field("tags").all("discount", "sale")
                .build();

        assertThat(criterion.query()).hasSize(3);

        @SuppressWarnings("unchecked")
        Map<String, Object> priceQuery = (Map<String, Object>) criterion.query().get("price");
        assertThat(priceQuery).containsEntry("$gte", 25.0);
        assertThat(priceQuery).containsEntry("$lte", 100.0);
    }

    @Test
    void testBuilder_realWorldExample_userActivity() {
        QueryCriterion criterion = QueryCriterion.builder()
                .id("active-user")
                .field("lastLoginDays").lte(30)
                .field("status").in("active", "premium")
                .field("email").regex("^[\\w.]+@[\\w.]+\\.[a-z]+$")
                .field("preferences.notifications").exists(true)
                .build();

        assertThat(criterion.query()).hasSize(4);
    }

    // ==================== Equivalence with Map-based Construction ====================

    @Test
    void testBuilder_equivalentToMapConstruction() {
        // Map-based construction
        QueryCriterion mapBased = new QueryCriterion("age-check",
                Map.of("age", Map.of("$gte", 18)));

        // Builder-based construction
        QueryCriterion builderBased = QueryCriterion.builder()
                .id("age-check")
                .field("age").gte(18)
                .build();

        assertThat(builderBased.id()).isEqualTo(mapBased.id());
        assertThat(builderBased.query()).isEqualTo(mapBased.query());
    }

    @Test
    void testBuilder_complexEquivalenceWithMapConstruction() {
        // Map-based construction
        QueryCriterion mapBased = new QueryCriterion("user-check",
                Map.of(
                        "age", Map.of("$gte", 18, "$lte", 65),
                        "status", Map.of("$eq", "active")
                ));

        // Builder-based construction
        QueryCriterion builderBased = QueryCriterion.builder()
                .id("user-check")
                .field("age").gte(18).and().lte(65)
                .field("status").eq("active")
                .build();

        assertThat(builderBased.id()).isEqualTo(mapBased.id());
        assertThat(builderBased.query()).isEqualTo(mapBased.query());
    }
}
