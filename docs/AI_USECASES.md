# AI Use Cases for JSPEC

## Symbolic Layer in Hybrid AI

```
Neural (LLM extract/generate) → Symbolic (JSPEC enforces policy) → Neural (LLM explain/act)
```

**Why:**
- **Determinism & auditability** – Evaluations finish in <1 ms, emit per-criterion reasons, and cost £0 per run, satisfying compliance teams that reject “LLM decided” justifications.
- **Graceful degradation** – `UNDETERMINED` exposes missing data or operator issues without aborting the rest of the spec, so orchestrators can retry or route to humans.
- **Observability hand-off** – Symbolic outcomes become structured prompts to the follow-on LLM (“explain why r14 failed”), yielding transparent customer messaging.

This “Symbolic Layer” lets teams pair LLM flexibility with rule-level guarantees instead of forcing a false choice between neural or symbolic stacks.

## Other Use Cases

### 1. Guardrails & Safety Filters
- **Goal:** Block obvious violations (PII, banned topics, token budgets) before slower semantic guardrails fire.
- **Pattern:** `User Input → JSPEC (<1 ms) → LLM guardrail (100–500 ms) → Agent`.
- **Impact:** ~40% of unsafe requests are rejected deterministically, cutting end-to-end latency and GPU spend while keeping remediation reasons transparent for SOC teams.

### 2. Deterministic Rule Enforcement in Hybrid Systems
- **Goal:** Anchor LLM-extracted facts to non-negotiable business criteria (loan policies, underwriting, compliance).
- **Pattern:** `LLM extraction → JSPEC evaluation → LLM explanation`.
- **Benefits:** Repeatable outcomes, auditable failure reasons, and policy updates via JSON/YAML instead of redeploying code. Ideal for 5–100 criteria where Drools/DMN would be overkill yet hand-coded `if/else` is brittle.

### 3. Agent Decision-Making & Routing
- **Goal:** Route work to the correct specialist agent (VIP support, technical triage, SLA tiers) using transparent eligibility checks.
- **Pattern:** Evaluate multiple specifications (VIP, technical, billing) in parallel; first specification whose criteria group matches assigns the agent. Fall back to LLM intent classification for ambiguous cases.
- **Outcome:** 70–80% of tickets route deterministically with a full audit trail; the remaining edge cases still benefit from LLM semantics without hiding the decision logic.

### 4. Output Validation & Execution Safety
- **Goal:** Verify synthesized SQL, code, case notes, or recommendations before executing or persisting them.
- **Pattern:** `Agent output → JSPEC` to ensure only approved tables, discount caps, or phrasing (regex/type checks) are present; `NOT_MATCHED` or `UNDETERMINED` feeds corrective prompts or human review.
- **Advantage:** Matches the “deterministic veto channel” described in `ai_adjunct.md`, ensuring bad generations never hit prod systems while keeping remediation scripts simple.

### 5. Context Filtering & RAG Hygiene
- **Goal:** Enforce access control, freshness, and quality gates on retrieved documents before they feed RAG prompts.
- **Pattern:** Vector DB handles coarse filters; JSPEC enforces per-document business logic (classification, recency, entitlement); optional reranker polishes the final set.
- **Result:** <120 ms end-to-end retrieval with deterministic compliance checkpoints, reducing hallucination-inducing context bleed.

## Implementation Tips
- **Spec reuse:** Define guardrail, routing, and validation specs independently so orchestrators can compose them like middleware.
- **Framework hooks:** Evaluate specs inside LangGraph/LangChain nodes, CrewAI task routers, or AutoGen tool callbacks. The reports showcase simple lambda bridges and REST adapters.
- **Observability:** Persist `EvaluationOutcome.summary()` and failed criterion IDs so ops teams can correlate blocked agent runs with specific policy clauses.
- **Testing:** Mirror production specs in JUnit/AssertJ suites to guarantee MATCHED/NOT_MATCHED/UNDETERMINED coverage before rolling out new AI behaviors.
