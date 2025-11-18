# Use Cases

JSPEC's flexible and robust evaluation engine is well-suited for a variety of use cases where business logic needs to be decoupled from application code.

-   **E-commerce Order Validation**
    Validate incoming orders for shipping eligibility, promotional discounts, fraud detection, or stock requirements. For example, a criterion could check `order.total > 50.00 AND customer.is_premium == true` to qualify for free shipping.

-   **Benefits or Eligibility Determinations**
    In public-sector or insurance workflows, encode complex policy clauses. For example, determine if a citizen is eligible for a program by checking `claimant.age >= 65 AND claimant.residency_years >= 10`. The tri-state model gracefully handles cases where data is missing from a citizen's record.

-   **Financial Compliance & Risk Controls**
    Model AML, KYC, or broader regulatory criteria. Specifications can traverse deeply nested transaction documents to flag suspicious activity such as `transaction.amount > 10000 AND counterparty.risk_level == 'HIGH'`.

-   **Dynamic API Filtering & Personalization**
    Power user-facing filters, saved searches, or content personalization. A web application can store a user's preferences as a JSON specification and use it to filter a stream of results, ensuring only relevant content is shown.

-   **Document Validation & Intake Pipelines**
    In back-office data ingestion pipelines, validate structured payloads (like XML or JSON) before passing them to downstream systems. Ensure that `metadata.uploadedBy` exists, that numeric fields are within expected thresholds, and that required sub-objects are present.

-   **Feature-flag Style Experimentation**
    Use specifications to manage complex, criteria-driven feature toggles. A feature might be enabled for users where `user.location == 'DE' AND user.beta_tester == true`. Because specifications are just data (JSON/YAML), non-engineers can modify rollout criteria without changing application code.
