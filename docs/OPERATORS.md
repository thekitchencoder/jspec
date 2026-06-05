# Supported Operators

JSPEC supports 23 query operators out of the box, grouped into comparison, collection, advanced, string, date/range, and logical categories. Many use familiar MongoDB-style query syntax; `$contains`, `$startsWith`, `$endsWith`, `$between`, `$dateBefore`, and `$dateAfter` are jspec extensions, and `$and`/`$or`/`$not` are logical operators. Examples below use Java's `Map.of`/`List.of`; equivalent YAML/JSON works identically inside a `Specification`.

`OperatorRegistry.withDefaults()` seeds only the six overridable comparison operators (`$eq`, `$ne`, `$gt`, `$gte`, `$lt`, `$lte`); `CriterionEvaluator` owns and registers the other 17 (collection, advanced, string, date/range, and the `$not`/`$and`/`$or` logical operators), giving the 23 reported by `CriterionEvaluator.supportedOperators()` (the canonical source of truth).

## Comparison Operators (6)

These operators compare a value from the document with a given operand.

| Operator | Description                  | Example                               |
| :------- | :--------------------------- | :------------------------------------ |
| `$eq`    | Equal to                     | `Map.of("age", Map.of("$eq", 25))`     |
| `$ne`    | Not equal to                 | `Map.of("status", Map.of("$ne", "INACTIVE"))` |
| `$gt`    | Greater than                 | `Map.of("price", Map.of("$gt", 100.0))` |
| `$gte`   | Greater than or equal to     | `Map.of("age", Map.of("$gte", 18))`    |
| `$lt`    | Less than                    | `Map.of("stock", Map.of("$lt", 10))`   |
| `$lte`   | Less than or equal to        | `Map.of("rating", Map.of("$lte", 5))`  |

### Example:
```java
// Age must be 18 or older
Map.of("age", Map.of("$gte", 18))
```

## Collection Operators (4)

These operators work on array/list values in the document.

| Operator | Description                         | Example                               |
| :------- | :---------------------------------- | :------------------------------------ |
| `$in`    | Value is present in a list          | `Map.of("status", Map.of("$in", List.of("ACTIVE", "PENDING")))` |
| `$nin`   | Value is not present in a list      | `Map.of("role", Map.of("$nin", List.of("ADMIN", "OWNER")))` |
| `$all`   | Array contains all specified values | `Map.of("tags", Map.of("$all", List.of("urgent", "verified")))` |
| `$size`  | Array size equals the given value   | `Map.of("items", Map.of("$size", 3))`  |

### Example:
```java
// Status must be one of ACTIVE, PENDING
Map.of("status", Map.of("$in", List.of("ACTIVE", "PENDING")))

// Tags array must contain all of: urgent, verified
Map.of("tags", Map.of("$all", List.of("urgent", "verified")))
```

## Advanced Operators (4)

These operators provide more complex evaluation capabilities.

| Operator      | Description                             | Example                               |
| :------------ | :-------------------------------------- | :------------------------------------ |
| `$exists`     | Field exists (or does not exist)        | `Map.of("email", Map.of("$exists", true))` |
| `$type`       | Value is of a specified BSON type       | `Map.of("age", Map.of("$type", "number"))` |
| `$regex`      | String value matches a regex pattern    | `Map.of("name", Map.of("$regex", "^[A-Z].*"))` |
| `$elemMatch`  | An array element matches a sub-query    | `Map.of("addresses", Map.of("$elemMatch", Map.of("isPrimary", Map.of("$eq", true))))` |

### Supported Types for `$type`
The `$type` operator supports the following type strings, which correspond to BSON types:
- `null`
- `array`
- `string`
- `number`
- `boolean`
- `object`

### Example:
```java
// Email field must exist
Map.of("email", Map.of("$exists", true))

// Name must match pattern
Map.of("name", Map.of("$regex", "^[A-Z].*"))

// At least one address in the array must be the primary one
Map.of("addresses", Map.of("$elemMatch",
    Map.of("isPrimary", Map.of("$eq", true))))
```

## String Operators (3)

These operators perform substring and collection-membership checks.

| Operator       | Description                                              | Example                               |
| :------------- | :------------------------------------------------------ | :------------------------------------ |
| `$contains`    | String contains a substring, **or** a collection contains an element | `Map.of("title", Map.of("$contains", "urgent"))` |
| `$startsWith`  | String starts with the given prefix                     | `Map.of("sku", Map.of("$startsWith", "EU-"))` |
| `$endsWith`    | String ends with the given suffix                       | `Map.of("filename", Map.of("$endsWith", ".pdf"))` |

`$contains` is overloaded: when the document value is a `String` and the operand is a `String`, it checks substring containment; when the document value is a `Collection`, it checks whether that collection contains the operand element. `$startsWith` and `$endsWith` operate on `String` values only. A type mismatch (e.g. a non-string value) is treated as NOT_MATCHED, not UNDETERMINED.

### Example:
```java
// Title must contain the substring "urgent"
Map.of("title", Map.of("$contains", "urgent"))

// tags is a list; check it contains the element "vip"
Map.of("tags", Map.of("$contains", "vip"))

// SKU must start with "EU-"
Map.of("sku", Map.of("$startsWith", "EU-"))

// Filename must end with ".pdf"
Map.of("filename", Map.of("$endsWith", ".pdf"))
```

## Range & Date Operators (3)

These operators evaluate numeric ranges and date/time ordering.

| Operator      | Description                                            | Example                               |
| :------------ | :---------------------------------------------------- | :------------------------------------ |
| `$between`    | Value is within an **inclusive** `[min, max]` range   | `Map.of("price", Map.of("$between", List.of(100, 500)))` |
| `$dateBefore` | Date/time is strictly before the operand              | `Map.of("createdAt", Map.of("$dateBefore", "2025-01-01"))` |
| `$dateAfter`  | Date/time is strictly after the operand               | `Map.of("expiresAt", Map.of("$dateAfter", "now"))` |

`$between` expects a two-element `List` `[min, max]` and matches when `min <= value <= max` (both bounds inclusive). A list of any other size is treated as NOT_MATCHED.

`$dateBefore` and `$dateAfter` parse both the document value and the operand to an `Instant` and compare them. Accepted forms include ISO-8601 date-time strings (`"2025-01-01T00:00:00Z"`), ISO-8601 date strings (`"2025-01-01"`), epoch milliseconds (`Long`), `Instant` objects, and the literal keyword `"now"` (case-insensitive), which resolves to the current time. Values that cannot be parsed are treated as NOT_MATCHED.

### Example:
```java
// Price between 100 and 500 inclusive
Map.of("price", Map.of("$between", List.of(100, 500)))

// Created before 1 Jan 2025
Map.of("createdAt", Map.of("$dateBefore", "2025-01-01"))

// Expiry must be after the current moment
Map.of("expiresAt", Map.of("$dateAfter", "now"))
```

## Logical Operators (3)

These operators combine or invert query conditions for a single field. They differ from the `Junction` (AND/OR) that combines whole criteria within a `CompositeCriterion`.

| Operator | Description                                          | Example                               |
| :------- | :--------------------------------------------------- | :------------------------------------ |
| `$not`   | Inverts a nested sub-query                           | `Map.of("status", Map.of("$not", Map.of("$eq", "BANNED")))` |
| `$and`   | All nested sub-queries must match (Strong Kleene)    | `Map.of("age", Map.of("$and", List.of(Map.of("$gte", 18), Map.of("$lt", 65))))` |
| `$or`    | Any nested sub-query must match (Strong Kleene)      | `Map.of("score", Map.of("$or", List.of(Map.of("$eq", 0), Map.of("$gte", 80))))` |

`$not` takes a single nested query `Map` and inverts its result: a nested MATCHED becomes NOT_MATCHED and vice versa. A nested UNDETERMINED yields NOT_MATCHED — UNDETERMINED is not propagated through inversion. `$and`/`$or` take a `List` of nested query maps and combine their tri-state results using Strong Kleene (K3) logic — so `$or` short-circuits to MATCHED on any match, and `$and` short-circuits to NOT_MATCHED on any non-match, even when some sub-queries are UNDETERMINED.

### Example:
```java
// status must NOT equal "BANNED"
Map.of("status", Map.of("$not", Map.of("$eq", "BANNED")))

// age >= 18 AND age < 65
Map.of("age", Map.of("$and", List.of(
    Map.of("$gte", 18),
    Map.of("$lt", 65))))

// score == 0 OR score >= 80
Map.of("score", Map.of("$or", List.of(
    Map.of("$eq", 0),
    Map.of("$gte", 80))))
```
