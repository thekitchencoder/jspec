# Supported Operators

JSPEC ships with 14 MongoDB-style query operators out of the box, grouped into comparison, collection, and advanced categories. Examples below use Java's `Map.of`/`List.of`; equivalent YAML/JSON works identically inside a `Specification`.

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
