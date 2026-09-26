# Credit Assistant — Specification

## Technology Baseline

The MVP shall use the following technology baseline:

- Java 25
- Spring Boot 4.1.1
- Maven
- LangChain4j with Spring Boot 4 integration
- PostgreSQL with pgvector
- Testcontainers
- Docker
- Docker Compose
- no Lombok

The project shall prefer explicit Java code over generated boilerplate where practical.

## 1. Purpose

`credit-assistant` is a sample AI application demonstrating production-oriented
integration of Large Language Models with a Spring Boot application.

The application supports a credit advisor by answering product-related questions,
retrieving contract information, calculating installments and checking customer
eligibility.

The project demonstrates:

- LangChain4j integration with Spring Boot
- AI Services and tool calling
- agentic workflows
- MCP server integration
- Retrieval-Augmented Generation (RAG)
- PII protection
- AI observability and evaluation
- Specification-Driven Development

## 2. Business Context

A credit advisor interacts with the assistant through a chat API.

The assistant can:

1. answer questions about credit products using internal product documentation,
2. retrieve the status of a credit contract,
3. calculate an estimated installment,
4. check basic credit eligibility.

The assistant must use deterministic application services whenever business data
or calculations are required. The LLM must not invent contract data, eligibility
decisions or installment values.

## 3. Scope

### In scope

- REST chat endpoint
- LLM integration through LangChain4j
- tool calling
- MCP server exposing the same business tools
- RAG over product documentation
- PostgreSQL with pgvector
- PII masking before LLM calls
- prompt/response observability
- token and cost tracking
- automated AI evaluation set
- Docker Compose local environment
- Testcontainers integration tests

### Out of scope

- authentication and authorization
- real banking systems
- real customer data
- production-grade credit scoring
- frontend application

## 4. Functional Requirements

### FR-001 — Chat interaction

The system shall expose a REST API that allows a credit advisor to send a natural-language message and receive an AI-generated response.

The response may be based on:

- general LLM reasoning,
- retrieved product documentation,
- deterministic business tools.

### FR-002 — Product knowledge retrieval

When the advisor asks a question related to credit products, regulations or frequently asked questions, the system shall retrieve relevant fragments from the internal product knowledge base.

The retrieved context shall be provided to the LLM before the final response is generated.

The assistant shall prefer retrieved product documentation over unsupported model knowledge.

### FR-003 — Contract status lookup

The assistant shall be able to invoke the `getContractStatus` tool.

Input:

- contract number

Output shall contain at least:

- contract status,
- outstanding principal,
- next payment date.

Contract information shall be returned only by the deterministic application service.

The LLM shall not generate or infer contract state independently.

### FR-004 — Installment calculation

The assistant shall be able to invoke the `calculateInstallment` tool.

Input shall contain at least:

- principal amount,
- repayment period,
- annual interest rate.

Output shall contain at least:

- estimated monthly installment.

The calculation shall be performed by deterministic Java business logic rather than by the LLM.

### FR-005 — Eligibility check

The assistant shall be able to invoke the `checkEligibility` tool.

Input shall contain the minimum set of mock customer and application data required by the eligibility policy.

Output shall contain:

- eligibility decision,
- machine-readable reason code,
- human-readable explanation.

The eligibility decision shall be produced by deterministic business rules.

The LLM may explain the result but shall not modify the decision.

### FR-006 — MCP tool exposure

The business capabilities exposed to the LangChain4j agent shall also be available through an MCP server.

The MCP server shall expose at least:

- `getContractStatus`,
- `calculateInstallment`,
- `checkEligibility`.

Both the internal AI agent and MCP clients shall delegate to the same application services.

Business logic shall not be duplicated between the LangChain4j tools and MCP tools.

### FR-007 — PII masking

Before any user-provided content is sent to an external LLM, the system shall mask supported personally identifiable information.

The initial implementation shall mask at least:

- PESEL numbers,
- contract numbers.

PII masking shall occur before:

- prompt transmission,
- prompt logging.

The original value may be used by deterministic internal tools when required.

### FR-008 — AI observability

For every LLM interaction, the system shall persist observability data containing at least:

- timestamp,
- model identifier,
- masked user prompt,
- masked model response,
- input token count,
- output token count,
- estimated request cost,
- execution duration.

Observability failures shall not expose unmasked PII.

Implementation decisions:

- An LLM interaction is one physical chat model call, observed by a LangChain4j `ChatModelListener`.
  A request that involves a tool call therefore produces one record per model call (for example two).
- Each record also stores the names of the tools requested in that model response, never the tool arguments.
- The recorded prompt is the last user message actually sent to the model (already masked, possibly RAG-augmented).
  Prompt and response are masked again before persistence as defence in depth.
- Token counts come from the response metadata. If the provider does not report them, token counts and cost are
  stored as unknown (`NULL`), not as zero.
- A failed model call is recorded with status `ERROR` and the exception type only; it has no response, token counts
  or cost, and the provider's error message is not stored.
- Failure to persist a record is logged without content and never fails the chat request.

### FR-009 — Evaluation set

The repository shall contain a fixed evaluation dataset with at least 10 representative questions.

The evaluation set shall include scenarios covering:

- product knowledge,
- RAG correctness,
- tool selection,
- contract status lookup,
- installment calculation,
- eligibility checks,
- PII masking,
- unsupported or unanswerable questions.

The evaluation suite shall be executable as an automated test.

Implementation decisions:

- Dataset: `src/test/resources/evaluation/credit-assistant-evaluation.json` (synthetic data, 10 cases, one per
  SPEC 71 scenario). Evaluation code lives only in test sources (`pl.dch.creditassistant.evaluation`).
- `mvn test` validates the dataset (`EvaluationDatasetTest`) but does not run the real-model evaluation and needs no LLM.
- `mvn -Pai-evaluation test` runs only the evaluation (`CreditAssistantEvaluationTest`, JUnit tag `ai-evaluation`)
  against the real configured Ollama model, with PostgreSQL/pgvector from Testcontainers. It fails with a setup error
  if the configured model is not available, and fails the build if any case is `FAIL` or `ERROR`.

### FR-010 — Local development environment

The project shall provide:

- a `Dockerfile` for building and running the Spring Boot application,
- a Docker Compose configuration containing the application and PostgreSQL with the pgvector extension enabled.

The complete local stack shall be runnable with Docker Compose so that the application and database can be started together.

The application shall also remain runnable directly from IntelliJ or Maven for development purposes.

No external infrastructure shall be required other than the configured LLM API.

### FR-011 — Integration testing

Infrastructure-dependent integration tests shall use Testcontainers.

At minimum, integration tests shall verify:

- PostgreSQL connectivity,
- pgvector availability,
- vector storage and retrieval,
- persistence of AI observability records.

### FR-012 — Safe fallback behavior

If the assistant cannot answer a question using available product documentation or deterministic tools, it shall explicitly state that the information is unavailable.

The assistant shall not fabricate:

- contract information,
- financial calculations,
- eligibility decisions,
- product rules not present in the knowledge base.

## 5. Non-Functional Requirements

### NFR-001 — Maintainability

The codebase shall favor readability and explicitness over compactness.

Implementation shall follow these conventions:

- explicit Java types shall be used instead of `var`,
- methods shall have a single clear responsibility,
- complex logic shall be decomposed using extract-method,
- magic strings and magic numbers shall be replaced with named constants,
- public APIs and domain concepts shall use meaningful names,
- business logic shall not be placed in controllers or infrastructure adapters.

### NFR-002 — Modularity

The application shall be implemented as a modular monolith.

Modules shall communicate through explicit application-level APIs.

Infrastructure concerns shall not leak into domain logic.

Dependencies between modules shall remain intentional and one-directional.

### NFR-003 — Configuration

Environment-specific configuration shall be externalized using Spring Boot configuration properties.

Configuration shall include at least:

- LLM provider settings,
- model identifier,
- API credentials,
- database connection,
- embedding configuration,
- RAG parameters,
- observability cost parameters.

Secrets shall not be committed to the repository.

### NFR-004 — Logging

Application services, adapters and controllers shall use SLF4J for operational logging.

Logs shall:

- contain sufficient context for troubleshooting,
- avoid unmasked PII,
- avoid logging secrets or API credentials,
- use appropriate log levels.

### NFR-005 — Testability

Business rules shall be executable without starting the full Spring context wherever possible.

External infrastructure shall be isolated behind adapters.

Tests shall distinguish between:

- unit tests,
- integration tests,
- AI evaluation tests.

### NFR-006 — Reproducibility

The repository shall provide enough configuration and documentation to reproduce the local development environment.

The full local stack shall be startable through Docker Compose.

Docker Compose shall start at least:

- the `credit-assistant` application container,
- PostgreSQL with pgvector.

The application container shall be built from the repository `Dockerfile`.

Database integration tests shall use Testcontainers rather than relying on a developer-installed database.

### NFR-007 — AI safety boundary

LLM output shall be treated as non-deterministic and untrusted application input.

The model shall not be the source of truth for:

- contract state,
- financial calculations,
- eligibility decisions,
- product rules,
- customer identifiers.

Deterministic application services and retrieved internal documentation shall remain authoritative.

### NFR-008 — Observability

AI interactions shall be traceable sufficiently to understand:

- which model was called,
- what masked input was sent,
- what masked output was returned,
- how long the interaction took,
- how many tokens were consumed,
- what estimated cost was incurred,
- which tools were invoked when applicable.

Observability shall not weaken PII protection.

## 6. Architecture Constraints

### AC-001 — Modular monolith

The application shall be deployed as a single Spring Boot application while preserving explicit internal module boundaries.

The initial logical modules shall be:

- `chat` — advisor-facing AI interaction,
- `credit` — deterministic credit business capabilities,
- `knowledge` — document ingestion and RAG,
- `privacy` — PII detection and masking,
- `observability` — AI interaction tracking and cost calculation,
- `mcp` — MCP exposure of business capabilities.

Shared technical utilities shall be kept minimal.

### AC-002 — Dependency direction

Business modules shall not depend on AI infrastructure.

In particular:

- `credit` shall not depend on LangChain4j,
- `credit` shall not depend on MCP,
- `credit` shall not depend on the LLM provider,
- LangChain4j tools shall delegate to `credit` application services,
- MCP tools shall delegate to the same `credit` application services.

This ensures that AI and MCP are adapters around business capabilities rather than owners of business logic.

### AC-003 — AI orchestration

LangChain4j shall be responsible for AI orchestration, including:

- LLM integration,
- AI Service abstraction,
- tool calling,
- retrieval augmentation,
- model interaction hooks required for observability.

Framework-specific types shall be kept close to the AI adapter boundary.

### AC-004 — Persistence

PostgreSQL shall be used as the primary relational database.

The pgvector extension shall be used for vector storage and similarity search.

The same PostgreSQL instance may store:

- vector embeddings,
- AI observability records,
- mock application data required by the sample.

Persistence details shall not leak into domain services.

### AC-005 — RAG pipeline

Document ingestion shall be separated from runtime question answering.

The ingestion pipeline shall perform:

1. document loading,
2. text normalization,
3. chunking,
4. embedding generation,
5. vector persistence.

The runtime retrieval pipeline shall perform:

1. query normalization where required,
2. query embedding,
3. similarity search,
4. context selection,
5. context augmentation of the LLM request.

### AC-006 — PII boundary

PII protection shall be applied before content crosses the external LLM boundary.

Masking shall be implemented as a dedicated application capability rather than embedded directly in controllers or prompt templates.

Internal deterministic services may receive original identifiers when required to perform their operation.

### AC-007 — External integration boundaries

External systems shall be represented by explicit ports or adapters.

This includes:

- LLM provider,
- embedding model,
- PostgreSQL,
- MCP transport.

The project shall allow these integrations to be replaced without modifying credit business rules.

### AC-008 — Package visibility

Implementation details should use package-private visibility where appropriate.

Public visibility shall be reserved for APIs that represent intentional module boundaries.

### AC-009 — Framework isolation

Spring, LangChain4j and MCP annotations shall not be introduced into pure business model classes unless technically necessary.

Domain and application logic should remain understandable without knowledge of the AI framework.

### AC-010 — Specification traceability

Important implementation elements and tests should be traceable to requirements defined in this specification.

Where useful, test names or documentation may reference identifiers such as:

- `FR-003`,
- `FR-007`,
- `NFR-005`,
- `AC-002`.

The specification is the starting point for implementation decisions and should be updated when an intentional architectural decision changes.

## 7. Use Cases

### UC-001 — Answer a product question using RAG

**Actor:** Credit advisor

**Preconditions:**
- product documentation has been ingested,
- embeddings are available in pgvector.

**Example question:**

> Can the customer make an early repayment without additional fees?

**Expected flow:**

1. The advisor sends the question through the chat API.
2. The system identifies the question as product-knowledge related.
3. Relevant documentation chunks are retrieved from the vector store.
4. The retrieved context is added to the LLM request.
5. The LLM generates the final answer based on retrieved documentation.
6. The interaction is recorded by the observability module.

**Expected behavior:**

The assistant shall answer using the retrieved internal documentation.

If the retrieved documents do not contain sufficient information, the assistant shall explicitly state that the answer cannot be confirmed from the available knowledge base.

### UC-002 — Retrieve contract status using a tool

**Actor:** Credit advisor

**Example question:**

> What is the current status of contract CR-2026-000123?

**Expected flow:**

1. The advisor sends the question through the chat API.
2. PII masking protects the contract identifier before external model communication.
3. The LLM determines that contract information requires a deterministic tool.
4. The assistant invokes `getContractStatus`.
5. The tool delegates to the credit application service using the original internal identifier.
6. The credit service returns the contract data.
7. The tool result is returned to the LLM.
8. The LLM formats a human-readable answer without modifying factual values.
9. The interaction and tool invocation are recorded.

**Expected behavior:**

The LLM shall not attempt to determine contract status from its own knowledge.

### UC-003 — Calculate an installment

**Actor:** Credit advisor

**Example question:**

> Calculate the monthly installment for PLN 100,000 over 60 months with an annual interest rate of 8%.

**Expected flow:**

1. The advisor sends the calculation request.
2. The LLM recognizes that a deterministic calculation is required.
3. The assistant invokes `calculateInstallment`.
4. The credit application service performs the calculation.
5. The calculated result is returned to the LLM.
6. The LLM presents the result in a readable form.

**Expected behavior:**

The model shall not calculate the installment independently when the tool is available.

### UC-004 — Check credit eligibility

**Actor:** Credit advisor

**Example question:**

> The customer has PLN 8,000 monthly income, PLN 2,000 existing monthly obligations and wants a PLN 40,000 loan. Is the customer eligible?

**Expected flow:**

1. The advisor sends the request.
2. The LLM extracts the required structured parameters.
3. The assistant invokes `checkEligibility`.
4. The credit application service evaluates deterministic eligibility rules.
5. The service returns:
   - the decision,
   - the reason code,
   - the explanation data.
6. The LLM explains the decision in natural language.
7. The LLM shall preserve the returned decision and reason code.

**Expected behavior:**

The assistant may explain the decision but shall not override or reinterpret the deterministic eligibility result.

### UC-005 — Product question combined with a business tool

**Actor:** Credit advisor

**Example question:**

> Contract CR-2026-000123 is active. Can this customer repay it early without a fee?

**Expected flow:**

1. The assistant invokes `getContractStatus` to retrieve contract-specific information.
2. The assistant retrieves relevant early-repayment rules from the product knowledge base.
3. The LLM combines:
   - deterministic contract data,
   - retrieved product documentation.
4. The final response clearly distinguishes contract facts from product rules.

**Expected behavior:**

The final answer shall not introduce facts that are absent from either the tool result or retrieved knowledge.

### UC-006 — Unsupported information

**Actor:** Credit advisor

**Example question:**

> What interest rate will the bank offer this customer next month?

**Expected flow:**

1. The advisor sends the question.
2. No deterministic tool can provide the requested future value.
3. The knowledge base does not contain an authoritative answer.
4. The assistant returns a safe fallback response.

**Expected behavior:**

The assistant shall explicitly state that the requested information is unavailable.

The assistant shall not predict or invent a future offer.

### UC-007 — PII protection

**Actor:** Credit advisor

**Example question:**

> Check contract CR-2026-000123 for customer PESEL 90010112345.

**Expected flow:**

1. The request enters the application.
2. The privacy module detects:
   - the PESEL,
   - the contract number.
3. Sensitive values are masked before the prompt is sent to the external model.
4. Original values remain available only inside the trusted application boundary when required by internal tools.
5. Logs and observability records contain masked values only.

**Example external prompt representation:**

> Check contract [CONTRACT_NUMBER_1] for customer PESEL [PESEL_1].

**Expected behavior:**

The external LLM provider shall not receive supported raw PII values.

### UC-008 — MCP invocation

**Actor:** External MCP client

**Example operation:**

An MCP client invokes `calculateInstallment`.

**Expected flow:**

1. The MCP server receives the tool invocation.
2. MCP input is mapped to the shared credit application API.
3. The credit service performs the calculation.
4. The MCP adapter maps the result back to the MCP response.

**Expected behavior:**

The MCP adapter shall not contain separate business calculation logic.

The result shall be equivalent to invoking the same capability through the LangChain4j agent.

## 8. AI Decision Routing

The application shall conceptually distinguish between the following interaction types:

| Interaction type | Primary mechanism | Example |
|---|---|---|
| Product knowledge | RAG | "Can I repay the loan early?" |
| Contract data | Tool calling | "What is the status of contract X?" |
| Financial calculation | Tool calling | "Calculate the installment." |
| Eligibility decision | Tool calling | "Is this customer eligible?" |
| Combined question | RAG + tool calling | "Can this active contract be repaid early?" |
| Unsupported question | Safe fallback | "What rate will be offered next month?" |

The LLM acts as an orchestration and language layer.

It shall not replace deterministic business logic or authoritative internal knowledge sources.

## 9. Conversation Principles

The assistant shall follow these principles:

1. Use deterministic tools for business facts and calculations.
2. Use RAG for internal product knowledge.
3. Avoid relying on unsupported model knowledge for regulated or product-specific information.
4. Preserve tool outputs without changing factual values.
5. Prefer an explicit "information unavailable" response over hallucination.
6. Mask supported PII before external model communication.
7. Keep responses concise and useful for a credit advisor.

## 10. Core Data Model

The domain model shall remain independent from LangChain4j, MCP and external LLM provider APIs.

### 10.1 Credit contract

`CreditContract` represents mock contract data available inside the trusted application boundary.

Required fields:

- contract number,
- contract status,
- outstanding principal,
- next payment date.

Supported contract statuses shall initially include:

- `ACTIVE`,
- `PAID_OFF`,
- `OVERDUE`,
- `CANCELLED`.

### 10.2 Installment calculation

`InstallmentCalculationRequest` shall contain:

- principal amount,
- repayment period in months,
- annual interest rate.

`InstallmentCalculationResult` shall contain:

- monthly installment,
- total repayment (monthly installment multiplied by the repayment period in months).

The input values are not repeated in the result; callers already hold the request.

Financial values shall use decimal arithmetic appropriate for monetary calculations.

Floating-point types such as `double` and `float` shall not be used for monetary values.

### 10.3 Eligibility

`EligibilityRequest` shall initially contain:

- monthly income,
- existing monthly obligations,
- requested loan amount.

`EligibilityResult` shall contain:

- decision,
- reason code,
- human-readable explanation.

Supported decisions:

- `ELIGIBLE`,
- `NOT_ELIGIBLE`.

Reason codes shall be represented using explicit application-level values rather than free-form LLM-generated strings.

Supported reason codes:

- `ELIGIBLE`,
- `INCOME_TOO_LOW`,
- `OBLIGATIONS_TOO_HIGH`,
- `LOAN_AMOUNT_TOO_HIGH`.

The MVP uses the following deterministic mock eligibility policy:

- minimum monthly income: 3000,
- maximum existing monthly obligations: 50% of monthly income,
- monthly disposable income = monthly income - existing monthly obligations,
- maximum requested loan amount: 12 × monthly disposable income.

Rules shall be evaluated in this order:

1. minimum monthly income (`INCOME_TOO_LOW`),
2. obligations-to-income ratio (`OBLIGATIONS_TOO_HIGH`),
3. requested loan amount (`LOAN_AMOUNT_TOO_HIGH`).

The first failed rule determines the `NOT_ELIGIBLE` decision and its reason code.

Values exactly equal to a limit are allowed.

When all rules pass, the decision is `ELIGIBLE` with reason code `ELIGIBLE`.

### 10.4 Knowledge document

A source document represents authoritative product knowledge.

Required metadata shall include:

- document identifier,
- document title,
- document type,
- source version.

A document chunk shall contain at least:

- chunk identifier,
- source document identifier,
- normalized text,
- chunk sequence number,
- embedding vector.

Additional retrieval metadata may be added where useful.

### 10.5 AI interaction

`AiInteraction` represents one observable interaction with an external language model.

It shall contain at least:

- interaction identifier,
- timestamp,
- model identifier,
- masked input,
- masked output,
- input token count,
- output token count,
- estimated cost,
- execution duration,
- interaction status.

Where available, the interaction may additionally reference tool invocations.

## 11. Application APIs

Application APIs shall describe business capabilities without exposing framework-specific AI types.

### 11.1 Contract status

Conceptual API:

```java
public interface ContractQueryService {

    CreditContract getContractStatus(ContractNumber contractNumber);
}
```

Responsibilities:

- retrieve mock contract state,
- return deterministic contract data,
- fail explicitly when the contract does not exist.

It shall not:

- format AI responses,
- invoke an LLM,
- depend on LangChain4j,
- depend on MCP.

### 11.2 Installment calculation

Conceptual API:

```java
public interface InstallmentCalculator {

    InstallmentCalculationResult calculate(
            InstallmentCalculationRequest request);
}
```

Responsibilities:

- validate calculation input,
- perform deterministic installment calculation,
- return structured results.

The implementation shall use Java monetary-safe decimal arithmetic.

Input validation (rejected with `IllegalArgumentException`, for every adapter):

- principal shall be greater than zero,
- annual interest rate shall not be negative,
- repayment period in months shall be greater than zero.

A 0% annual interest rate is valid: the monthly installment is the principal divided by the number of months,
rounded half-up to 2 decimal places.

### 11.3 Eligibility

Conceptual API:

```java
public interface EligibilityService {

    EligibilityResult checkEligibility(EligibilityRequest request);
}
```

Responsibilities:

- execute explicit eligibility rules,
- return a deterministic decision,
- expose a stable reason code,
- provide explanation data suitable for presentation.

Eligibility rules shall be testable without Spring or an LLM.

### 11.4 PII masking

Conceptual API:

```java
public interface PiiMasker {

    PiiMaskingResult mask(String text);
}
```

`PiiMaskingResult` contains:

- `MaskedText`, which is safe to send to an LLM, log or persist,
- `ProtectedValues`, the internal placeholder-to-original mapping, used only inside the trusted boundary.
  Its string representation reveals no values.
  It retains only values that deterministic internal tools must resolve; currently contract numbers only.

`MaskedText` shall contain at least:

- masked text,
- detected PII categories.

The initial implementation shall support:

- PESEL,
- contract number.

Masking rules shall be deterministic and independently testable.

### 11.5 Knowledge retrieval

Conceptual API:

```java
public interface KnowledgeRetriever {

    List<KnowledgeChunk> retrieve(String query);
}
```

The maximum number of results and the minimum relevance score are configuration properties
(`knowledge.retrieval.max-results`, `knowledge.retrieval.min-relevance-score`) rather than call arguments.

Responsibilities:

- retrieve semantically relevant knowledge chunks,
- hide vector-store-specific details from the chat module,
- return document metadata together with retrieved text.

The interface shall not expose pgvector-specific types.

### 11.6 AI interaction persistence

Conceptual API:

```java
public interface AiInteractionRepository {

    void save(AiInteraction interaction);
}
```

The interaction identifier is generated in the domain (UUID), so `save` does not need to return the interaction.

The application shall persist only masked prompt and response content.

Raw supported PII values shall not be persisted as part of AI observability.

## 12. Adapter Mapping

The same application APIs shall be reusable from multiple adapters.

```text
                        +----------------------+
                        |   Credit Services    |
                        |                      |
                        | ContractQueryService |
                        | InstallmentCalculator|
                        | EligibilityService   |
                        +----------+-----------+
                                   ^
                    +--------------+--------------+
                    |                             |
          +---------+----------+        +---------+---------+
          | LangChain4j tools  |        |    MCP tools      |
          +--------------------+        +-------------------+
```

The adapters shall be responsible only for:

- framework-specific input mapping,
- application service invocation,
- framework-specific output mapping.

Business rules shall remain in the shared application services.

## 13. Domain Modeling Principles

1. Prefer dedicated domain types over passing unrelated primitive values between modules.
2. Use `BigDecimal` for money, rates and financial calculations where precision matters.
3. Use enums or explicit value types for bounded business concepts.
4. Keep framework annotations outside pure business types where practical.
5. Avoid exposing persistence entities directly from application services.
6. Keep LLM-specific request and response objects outside the `credit` module.
7. Keep MCP-specific request and response objects outside the `credit` module.
8. Business APIs shall remain usable from ordinary Java tests without Spring context.

## 14. Module and Package Structure

The application shall be implemented as a modular monolith inside a single Spring Boot project.

Each top-level business capability shall own its application logic, domain model and infrastructure adapters.

The proposed package structure is:

```text
<base-package>
├── CreditAssistantApplication
│
├── chat
│   ├── api
│   ├── application
│   └── infrastructure
│
├── credit
│   ├── contract
│   │   ├── application
│   │   ├── domain
│   │   └── infrastructure
│   ├── installment
│   │   ├── application
│   │   └── domain
│   └── eligibility
│       ├── application
│       └── domain
│
├── knowledge
│   ├── application
│   ├── domain
│   └── infrastructure
│
├── privacy
│   ├── application
│   └── domain
│
├── observability
│   ├── application
│   ├── domain
│   └── infrastructure
│
├── mcp
│   └── infrastructure
│
└── configuration
```

## 15. Module Responsibilities

### 15.1 `chat`

The `chat` module owns the advisor-facing AI interaction flow.

Responsibilities:

- expose the chat API,
- orchestrate interaction with the AI layer,
- make business tools available to the AI agent,
- integrate retrieved knowledge with the model interaction,
- coordinate privacy and observability concerns.

The module may depend on:

- `credit`,
- `knowledge`,
- `privacy`,
- `observability`.

The module shall not contain credit business rules.

### 15.2 `credit`

The `credit` module owns deterministic credit capabilities.

It shall be divided by business capability rather than by technical layer only.

Initial capabilities:

```text
credit
├── contract
├── installment
└── eligibility
```

The entire `credit` module shall remain independent from:

- LangChain4j,
- MCP,
- vector databases,
- external LLM APIs.

### 15.3 `knowledge`

The `knowledge` module owns product knowledge ingestion and retrieval.

Responsibilities:

- load source documents,
- normalize document text,
- split documents into chunks,
- generate embeddings,
- persist embeddings,
- perform semantic retrieval,
- return relevant knowledge chunks.

### 15.4 `privacy`

The `privacy` module owns detection and masking of supported sensitive information.

Responsibilities:

- detect PESEL numbers,
- detect contract numbers,
- replace detected values with stable placeholders,
- expose information about detected PII categories.

### 15.5 `observability`

The `observability` module owns persistence and calculation of AI telemetry.

Responsibilities:

- record AI interactions,
- record token usage,
- calculate estimated request cost,
- measure interaction duration,
- optionally record tool invocation metadata,
- persist only masked prompt and response content.

### 15.6 `mcp`

The `mcp` module is an infrastructure adapter exposing selected application capabilities through MCP.

It shall expose:

- `getContractStatus`,
- `calculateInstallment`,
- `checkEligibility`.

The module shall delegate directly to APIs owned by `credit`.

### 15.7 `configuration`

The `configuration` package shall contain cross-module Spring Boot configuration required to assemble the application.

Business logic shall not be placed in this package.

## 16. Dependency Rules

The intended dependency direction is:

```text
                     +-------------+
                     |    chat     |
                     +------+------+
                            |
          +-----------------+-----------------+
          |                 |                 |
          v                 v                 v
      +--------+       +-----------+     +-------------+
      | credit |       | knowledge |     |   privacy   |
      +---+----+       +-----------+     +-------------+
          ^
          |
      +---+---+
      |  mcp  |
      +-------+

          chat
            |
            v
     +---------------+
     | observability |
     +---------------+
```

Key rules:

1. `credit` shall not depend on `chat`.
2. `credit` shall not depend on `mcp`.
3. `credit` shall not depend on `knowledge`.
4. `credit` shall not depend on `observability`.
5. `mcp` may depend on `credit`.
6. `chat` may depend on application APIs exposed by other modules.
7. `knowledge` shall not depend on `chat`.
8. `privacy` shall remain independent from AI framework infrastructure.
9. `observability` shall observe interactions without becoming part of business decision logic.

## 17. Package Organization Rules

Packages shall be organized primarily around business capabilities.

Within a capability, technical subpackages may be used where they improve clarity:

```text
application
domain
infrastructure
api
```

Not every module is required to contain every layer.

Empty or artificial layers shall not be introduced solely to satisfy a template.

## 18. Example Credit Capability

```text
credit
└── contract
    ├── application
    │   └── ContractQueryService
    ├── domain
    │   ├── ContractNumber
    │   ├── ContractStatus
    │   └── CreditContract
    └── infrastructure
        └── InMemoryContractRepository
```

The corresponding LangChain4j adapter shall live outside this capability.

The MCP equivalent shall live in the `mcp` module.

Both adapters shall delegate to the same `ContractQueryService`.

## 19. Architecture Enforcement

Module boundaries should be verifiable through automated tests.

The project should include architecture tests verifying at least that:

- the `credit` module does not depend on LangChain4j packages,
- the `credit` module does not depend on MCP packages,
- domain packages do not depend on infrastructure packages,
- MCP adapters delegate to application services rather than business implementations directly.

## 20. Retrieval-Augmented Generation

The application shall use Retrieval-Augmented Generation for questions that require internal product knowledge.

The knowledge base shall initially contain synthetic credit product documentation, including:

- product regulations,
- FAQ,
- repayment rules,
- early repayment rules,
- eligibility-related product information,
- fees and charges.

The documents shall contain no real customer data.

## 21. Source Documents

Source documents shall be stored inside the repository.

Initial location:

```text
src/main/resources/knowledge/
```

Example documents:

```text
knowledge/
├── credit-product-regulations.md
├── credit-product-faq.md
└── early-repayment.md
```

Markdown shall be the preferred initial format.

PDF ingestion is outside the MVP.

## 22. Document Metadata

Each source document shall have stable metadata:

- `documentId`,
- `title`,
- `documentType`,
- `version`.

Supported initial document types:

- `REGULATION`,
- `FAQ`,
- `PRODUCT_DESCRIPTION`.

Metadata shall be propagated to generated chunks.

## 23. Ingestion Pipeline

```text
Source document
      |
      v
Document loading
      |
      v
Text normalization
      |
      v
Chunking
      |
      v
Embedding generation
      |
      v
Vector persistence
      |
      v
PostgreSQL / pgvector
```

Ingestion shall be idempotent for the same document version where practical.

## 24. Text Normalization

Normalization shall initially include:

1. converting line endings to a consistent format,
2. removing unnecessary repeated whitespace,
3. preserving paragraph boundaries,
4. preserving headings where they carry semantic meaning,
5. removing purely technical formatting that does not contribute to meaning.

Normalization shall not rewrite or reinterpret product rules.

## 25. Chunking

Documents shall be split into semantically useful chunks.

Chunking shall prefer logical boundaries such as:

- sections,
- headings,
- paragraphs.

Chunk size and overlap shall be configuration properties rather than hard-coded values.

## 26. Knowledge Chunk

Each persisted chunk shall contain at least:

- `chunkId`,
- `documentId`,
- `documentTitle`,
- `documentType`,
- `documentVersion`,
- `sequenceNumber`,
- `content`,
- `embedding`.

The vector dimension shall be determined by the selected embedding model.

## 27. Embedding Generation

The same embedding model configuration shall be used for:

- document chunks,
- runtime user queries.

The embedding provider shall remain an infrastructure concern.

## 28. Vector Persistence

Embeddings shall be stored in PostgreSQL using pgvector.

Vector persistence shall support:

- inserting document chunks,
- replacing outdated document versions,
- similarity search,
- retrieving chunk metadata together with text.

## 29. Runtime Retrieval

```text
User question
     |
     v
Query normalization
     |
     v
Query embedding
     |
     v
Vector similarity search
     |
     v
Top relevant chunks
     |
     v
Context construction
     |
     v
LLM request
```

The number of retrieved chunks and minimum relevance shall be configurable.

## 30. Retrieval Result

The `KnowledgeRetriever` shall return structured retrieval results rather than raw database entities.

Framework-specific vector-store objects shall not cross the `knowledge` module boundary.

## 31. Context Construction

Only relevant chunks shall be included in the LLM context.

The context shall clearly distinguish:

- system instructions,
- retrieved product knowledge,
- the advisor's question.

Retrieved documentation shall be treated as reference data, not executable instructions.

## 32. Source Attribution

Where practical, product-knowledge answers should expose the source document used to generate the answer.

Source attribution shall be derived from retrieved chunk metadata.

The LLM shall not invent source names.

## 33. RAG Failure Modes

### No relevant chunks

Return a safe fallback rather than fabricate product information.

### Conflicting chunks

Indicate that available documentation is inconsistent.

### Embedding provider unavailable

Do not generate an authoritative product answer without retrieved context.

### Vector database unavailable

Return a controlled failure or safe fallback.

## 34. RAG Configuration

Configuration shall include at least:

- chunk size,
- chunk overlap,
- maximum retrieval results,
- minimum accepted relevance,
- embedding model identifier.

## 35. RAG Test Strategy

### Unit tests

Verify normalization, chunk generation and metadata propagation.

### Integration tests

Using PostgreSQL with pgvector through Testcontainers, verify vector persistence and similarity search.

### AI evaluation tests

Verify representative RAG scenarios including semantic paraphrases and missing knowledge.

## 36. RAG Design Principle

```text
relevant knowledge available
        -> answer from retrieved context

relevant knowledge unavailable
        -> explicit fallback

never
        -> invent product-specific information
```

## 37. PII Protection

The application shall protect supported personally identifiable information before any content is sent to an external LLM provider.

The initial protected data types are:

- PESEL,
- credit contract number.

## 38. Trusted Boundary

Inside the trusted application boundary, original identifiers may be used when required by deterministic business services.

Outside the trusted boundary, supported PII shall be masked.

## 39. Supported PII Types

### 39.1 PESEL

The privacy module shall detect PESEL-like identifiers.

Initial detection shall support:

- exactly 11 consecutive digits that are not part of a longer digit sequence.

The PESEL checksum is deliberately not validated, so synthetic or mistyped PESEL numbers are masked as well.

PESEL masking is not reversible in the MVP: no tool needs the original value, so it is not retained after masking.

Detected values shall be replaced with numbered placeholders:

```text
[PESEL_1]
```

### 39.2 Contract number

The sample application's contract identifiers shall use a defined format.

Supported formats (case-insensitive, standalone tokens):

```text
CTR-1001          CTR-<digits>, used by the sample contract data
CR-2026-000123    CR-<year>-<6 digits>
```

Syntactically valid numbers are masked whether or not the contract exists.

Detected contract numbers are normalized to their canonical upper-case form (for example `ctr-1001` becomes `CTR-1001`)
before they are retained for internal resolution. Values that differ only in letter case share one placeholder.

Detected contract identifiers shall be replaced with numbered placeholders:

```text
[CONTRACT_NUMBER_1]
```

### 39.3 Placeholder numbering

Placeholders are numbered per category, starting at 1 for each input, in order of first appearance.
Repeated occurrences of the same value use the same placeholder. Placeholders never contain the original value.

## 40. Masking Result

PII masking shall return structured information, including:

- masked text,
- detected PII categories.

## 41. Masking Rules

1. Matching shall be deterministic.
2. Original content ordering shall be preserved.
3. Non-sensitive text shall remain unchanged.
4. Multiple occurrences shall all be masked.
5. Logs shall use masked representations.
6. Observability persistence shall use masked representations.
7. Exceptions shall not include raw PII where avoidable.
8. PII detection shall not depend on an LLM.

## 42. Tool Calling and Original Values

Some deterministic tools require original identifiers.

The application shall preserve original values inside the trusted boundary while masking them before external model communication.

The system shall not rely on the external LLM to reconstruct masked identifiers.

## 43. PII-Aware Tool Orchestration

When tool calling requires sensitive identifiers, the application shall use trusted internal state to resolve tool arguments.

The exact mechanism shall be selected during implementation after validating current LangChain4j capabilities.

Selected mechanism:

- the chat application service masks the advisor message and passes only the masked text to the AI service,
- `ProtectedValues` travel in LangChain4j `InvocationParameters`, which are not part of the prompt or the tool schema,
- `getContractStatus` receives a placeholder such as `[CONTRACT_NUMBER_1]`, resolves it inside Java and queries the credit service,
- the tool result identifies the contract by the same placeholder, so raw contract numbers never reach the LLM,
  including in the follow-up LLM call after tool execution,
- a placeholder that is not part of the current invocation resolves to nothing (`INVALID_CONTRACT_REFERENCE`),
- model answers keep the placeholders; original values are not restored into the response.

## 44. Prompt Logging

Raw user prompts containing supported PII shall not be persisted as AI observability data.

Raw supported PII shall not be deliberately written to:

- application logs,
- observability database tables,
- test snapshots,
- error messages.

## 45. Model Response Masking

Model responses shall also pass through PII masking before persistence.

## 46. Logging Rules

Logs shall never intentionally contain:

- raw PESEL,
- raw contract number,
- API keys,
- model provider credentials.

## 47. Privacy Failure Behavior

If the privacy module fails before an external LLM call:

- the external request shall not be sent,
- the failure shall be logged without raw PII,
- the application shall return a controlled error.

The system shall fail closed with respect to supported PII.

## 48. Privacy Test Strategy

### Unit tests

Verify PESEL masking, contract masking, multiple values and non-PII input.

### Integration tests

Verify model-facing prompts and observability storage contain masked values while internal tools still receive correct identifiers.

## 49. Privacy Design Principle

```text
inside trusted application
    -> original value allowed when required

crossing external AI boundary
    -> supported PII masked

observability and logs
    -> supported PII masked

masking failure
    -> do not call external model
```

## 50. AI Observability

The application shall provide observability for external LLM interactions and AI tool execution.

Observability shall make it possible to answer:

- which model was used,
- what masked input was sent,
- what masked output was returned,
- how long the interaction took,
- how many tokens were consumed,
- what estimated cost was incurred,
- which tools were invoked,
- whether the interaction completed successfully.

## 51. AI Interaction Lifecycle

A single advisor request may result in more than one model interaction.

The system shall distinguish between:

- overall advisor interaction,
- individual LLM calls,
- individual tool invocations.

Implemented model (all in the `observability` module):

| Concept | Domain class | Table | Meaning |
|---|---|---|---|
| Advisor interaction | `AdvisorInteraction` | `advisor_interaction` | one advisor request (`POST /api/chat`) |
| LLM call | `AiInteraction` | `ai_interaction` | one physical chat model call; the class keeps its FR-008 name |
| Tool invocation | `ToolInvocation` | `tool_invocation` | one actual execution of a Java agent tool |

## 52. Advisor Interaction

An `AdvisorInteraction` shall contain at least:

- interaction identifier,
- start timestamp,
- completion timestamp,
- masked advisor message,
- masked final response,
- final status,
- total execution duration.

Supported initial statuses:

- `SUCCESS`,
- `FAILED`,
- `REJECTED_PRIVACY`.

Lifecycle (owned by the chat application service):

- The interaction is persisted when the request starts, after successful masking, so that LLM calls and tool
  invocations can reference it by foreign key. While it is running, completion timestamp, status and duration are
  empty (`NULL`); there is no separate in-progress status.
- On success it is updated with the final model answer (masked again before persistence) and `SUCCESS`.
- If the model call fails it is updated to `FAILED` without a final response, and the original exception is rethrown.
- If masking itself fails, the model is not called and the interaction is stored as `REJECTED_PRIVACY` without any
  advisor message.

## 53. LLM Call

Each external model invocation shall be represented separately and contain at least:

- call identifier,
- advisor interaction identifier,
- model identifier,
- masked model input,
- masked model output,
- input token count,
- output token count,
- estimated cost,
- execution duration,
- completion status.

LLM calls also record the names of the tools the model **requested** in its response. This is distinct from
tool invocations, which record tools that were **actually executed**.

## 54. Tool Invocation

Each agent tool invocation shall be observable.

Initial tool names:

```text
getContractStatus
calculateInstallment
checkEligibility
```

Raw sensitive arguments shall not be persisted.

Each execution of a LangChain4j tool adapter is recorded with tool invocation identifier, advisor interaction
identifier, tool name, start timestamp, duration and status (`SUCCESS` or `ERROR`). Tool arguments and results are
not persisted. A failing tool is recorded as `ERROR` and its exception is propagated unchanged. MCP tool calls are not
part of an advisor interaction and are not recorded.

## 55. Correlation

Application logs, LLM calls and tool invocations shall be correlated using generated technical identifiers such as `interactionId`.

Implementation:

- The chat application service generates one UUID `interactionId` per advisor request.
- It is passed in LangChain4j `InvocationParameters` (next to the protected values), which tools receive and which
  are not part of the prompt or tool schemas.
- LangChain4j 1.20 AI services do not pass the invocation context to `ChatModelListener`s. The RAG retrieval
  augmentor therefore stamps the `interactionId` onto the (augmented) user message as a message attribute. That
  message is part of every model call of the request, and user message attributes are not sent to the model provider.
  The chat model listener reads the identifier from there.
- Lifecycle logs contain the `interactionId` (started, completed with status, failed, rejected) and record identifiers,
  never message contents.

## 56. Token Usage

Token usage shall be captured from provider or framework response metadata where available.

If token information is unavailable, absence shall be represented explicitly.

## 57. Cost Calculation

Request cost shall be calculated deterministically from token usage and configured model pricing.

## 58. Model Pricing Configuration

Model pricing shall be externalized through typed configuration.

Configured costs are estimates rather than billing-authoritative values.

Prices are configured per one million tokens, separately for input and output tokens
(`observability.cost.input-per-million-tokens`, `observability.cost.output-per-million-tokens`), without a currency.
The default is 0, matching a local Ollama model without API charges.

```text
estimated cost = inputTokens × inputPrice / 1 000 000 + outputTokens × outputPrice / 1 000 000
```

The result is rounded half-up to 10 decimal places. Negative prices are rejected.

## 59. Monetary Precision

Estimated cost shall use decimal arithmetic.

`double` and `float` shall not be used for persisted monetary cost calculations.

## 60. Persistence

Suggested conceptual tables:

```text
advisor_interaction
llm_call
tool_invocation
```

The exact schema shall be defined during implementation.

Implemented schema, created by the Flyway migration `V1__create_ai_interaction.sql`:

```text
advisor_interaction (interaction_id PK, started_at, completed_at, masked_advisor_message,
                     masked_final_response, status, duration_millis)
    1 ─── N  ai_interaction  (id PK, advisor_interaction_id FK, one row per LLM call - the conceptual llm_call,
                              requested tool names in a tool_names text array)
    1 ─── N  tool_invocation (tool_invocation_id PK, advisor_interaction_id FK NOT NULL, tool_name,
                              started_at, duration_millis, status)
```

`ai_interaction.advisor_interaction_id` is nullable for model calls made outside an advisor chat.

## 61. Persistence Failure

Observability persistence failure shall not silently alter deterministic business outcomes.

Privacy requirements take precedence over telemetry completeness.

## 62. Prompt and Response Storage

Stored prompts and responses shall contain masked content only.

## 63. Tool Observability

Tool execution timing shall be measured independently from model execution timing.

The duration is measured with a monotonic clock around the Java tool adapter execution only, so it contains neither
the preceding nor the following model call.

## 64. Logging

SLF4J logs should contain high-level lifecycle events and correlation identifiers.

Logs shall not duplicate full prompt and response bodies by default.

## 65. Observability Query Examples

The data model should support answering:

- average token usage per advisor request,
- most frequently invoked tool,
- number of LLM calls per interaction,
- estimated cost of the evaluation set,
- interaction failures,
- model time versus tool execution time.

These are answered by grouping `ai_interaction` and `tool_invocation` rows by `advisor_interaction_id`.

## 66. Observability Test Strategy

Verify:

- cost calculation,
- persistence,
- relationships,
- masked content storage,
- representative agent interaction tracing.

## 67. Observability Design Principle

Privacy takes precedence over telemetry completeness.

## 68. AI Evaluation

The project shall contain an automated evaluation suite covering representative advisor scenarios.

The purpose is to verify:

- correct routing between RAG and tools,
- factual grounding,
- deterministic tool usage,
- safe fallback behavior,
- PII protection,
- answer usefulness.

## 69. Evaluation Categories

The suite shall cover:

- product knowledge,
- semantic retrieval,
- contract lookup,
- installment calculation,
- eligibility,
- combined RAG and tool calling,
- PII handling,
- unsupported questions,
- hallucination prevention,
- tool selection.

## 70. Evaluation Dataset

Each evaluation case shall contain structured expectations and remain readable in Git.

A case has an `id`, a `category`, a `question` and only the expectations meaningful for it: `retrievalDocumentIds`,
`expectedTools`, `expectedLlmCallCount`, `requiredFacts` (exact values), `requiredConcepts` and `forbiddenConcepts`
(named lists of case-insensitive alternative patterns), `rawPiiValues` and `expectedPlaceholders`.

## 71. Initial Evaluation Set

The initial suite shall contain at least these 10 scenarios:

1. FAQ retrieval.
2. Semantic early-repayment retrieval.
3. Contract status tool call.
4. Installment calculation tool call.
5. Eligible customer.
6. Ineligible customer.
7. Combined RAG and tool calling.
8. PII masking.
9. Unsupported future information.
10. Missing product knowledge.

A scenario's question shall explicitly ask for every fact its expectations require. For example, the FAQ scenario
asks for the relevant operational conditions: how often the due date can be changed, which dates are available,
whether there is a fee, and how far in advance the change must be requested.

The combined scenario is one advisor question that explicitly requires both sources: deterministic contract-specific
facts (a contract tool) and a product rule (product documentation). A question that product documentation alone
can answer does not qualify, because it would not justify the tool call.

## 72. Deterministic Assertions

Prefer deterministic assertions where behavior is directly observable:

- expected tool invocation,
- business result,
- PII masking,
- retrieved source,
- observability persistence,
- fallback path.

## 73. Semantic Assertions

Natural-language output shall not generally be tested using exact string equality.

Critical business behavior shall never depend solely on subjective LLM grading.

## 74. RAG Evaluation

RAG evaluation shall distinguish between:

1. retrieval quality,
2. generation quality.

Retrieval quality is evaluated by calling `KnowledgeRetriever.retrieve` with the masked question and asserting the
expected source documents. Generation quality is evaluated on the final answer of the full chat flow.

## 75. Tool Selection Evaluation

Tool selection shall be verified explicitly, including expected tool and invocation count.

Executed tools are taken from the persisted `tool_invocation` records of the advisor interaction (not from the answer
text) and must match the expected tools exactly; the number of model calls is taken from `ai_interaction`.
All records must be correlated with the single advisor interaction of the case. Raw PII values of a case must not
appear in any persisted prompt, response, advisor message or final answer.

## 76. Stability

Tests shall ignore harmless wording differences but fail on material behavioral differences.

## 77. Evaluation Execution

AI evaluation tests shall be distinguishable from ordinary unit tests and shall not run accidentally as part of every local test invocation.

## 78. Evaluation Observability

Evaluation runs should reuse application observability where practical.

## 79. Evaluation Result

Supported statuses:

```text
PASS
FAIL
ERROR
```

## 80. Evaluation Design Principle

```text
business result
    -> deterministic assertion

tool selection
    -> deterministic assertion

PII protection
    -> deterministic assertion

retrieval source
    -> deterministic assertion

natural-language quality
    -> semantic assertion

LLM-as-a-judge
    -> optional supporting mechanism
```

The MVP uses no LLM-as-a-judge. Natural-language answers are checked with exact required facts and tolerant
required/forbidden concepts; only presentation (markdown emphasis, thousands separators) is normalized.

## 81. MVP Implementation Scope

The initial implementation is intentionally limited to a demonstrable end-to-end vertical slice.

### MVP includes

- one REST chat endpoint,
- one LangChain4j `AiService`,
- three deterministic tools:
  - `getContractStatus`,
  - `calculateInstallment`,
  - `checkEligibility`,
- the same three capabilities exposed through MCP,
- three small Markdown knowledge documents,
- one RAG pipeline using PostgreSQL and pgvector,
- PESEL and contract-number masking,
- basic AI interaction persistence,
- token and estimated cost tracking,
- 10 predefined evaluation scenarios,
- Dockerfile for the application,
- Docker Compose running the application and PostgreSQL/pgvector together,
- Testcontainers for PostgreSQL/pgvector,
- README with architecture diagram.

### MVP intentionally keeps simple

- contract data may be stored in memory,
- eligibility rules may consist of a few explicit Java rules,
- document ingestion may run at application startup,
- observability may use simple relational tables,
- MCP and LangChain4j tools may use thin adapters,
- evaluation output may be printed by tests rather than exposed through a UI.

### Out of MVP

- authentication and authorization,
- frontend,
- production credit scoring,
- multiple LLM providers,
- dynamic model routing,
- PDF ingestion,
- advanced document version management,
- reranking,
- hybrid search,
- LLM-as-a-judge infrastructure,
- observability dashboards,
- distributed tracing,
- production-grade MCP security,
- separate microservices.

### Scope principle

Every implemented feature should demonstrate at least one concept relevant to the target Senior Java / AI role.

Features that do not materially improve the technical demonstration should be deferred.

## 82. MCP Server

The application shall expose selected deterministic credit capabilities through Model Context Protocol.

The MCP adapter shall not contain credit business logic.

## 83. MCP Tools

The MVP shall expose exactly three tools:

```text
getContractStatus
calculateInstallment
checkEligibility
```

Each MCP tool shall delegate to the corresponding `credit` application service.

## 84. Shared Business Capabilities

LangChain4j tools and MCP tools shall use the same deterministic application services.

Business logic shall not be duplicated between these adapters.

## 85. Tool Contracts

### `getContractStatus`

Input:

- contract number.

Output:

- contract status,
- outstanding principal,
- next payment date.

### `calculateInstallment`

Input:

- principal,
- repayment period,
- annual interest rate.

Output:

- calculated monthly installment.

### `checkEligibility`

Input:

- monthly income,
- existing monthly obligations,
- requested loan amount.

Output:

- eligibility decision,
- reason code,
- explanation.

MCP contracts shall expose structured values rather than preformatted conversational responses.

## 86. Transport

The MVP shall support one MCP transport only.

The concrete Java MCP server implementation and transport shall be selected during implementation based on current documentation.

Selected implementation:

- the official MCP Java SDK (`io.modelcontextprotocol.sdk:mcp`), without an additional AI framework,
- the Streamable HTTP transport, served as a servlet by the application's embedded web server,
- endpoint path configured by `mcp.server.endpoint` (default `/mcp`), on the same HTTP port as the REST API.

Each tool declares an input schema and an output schema and returns structured content.
An unknown contract is a regular `getContractStatus` result with `found = false`, not a protocol error.
Invalid business input is reported as a tool error result (`isError = true`).

The HTTP transport validates `Host` and `Origin` headers with the SDK's `DefaultServerTransportSecurityValidator`
(DNS-rebinding protection). Allowed values are configured by `mcp.server.allowed-hosts` and
`mcp.server.allowed-origins` and default to localhost only. Authentication is not part of the MVP.

## 87. MCP Test

At least one automated integration test shall verify that an MCP tool invocation reaches the shared credit application service and returns the expected deterministic result.

## 88. Definition of Done

The MVP is considered complete when the core chat, tools, RAG, privacy, MCP, observability and evaluation paths work end to end and are documented.

Key completion criteria:

- application starts locally from IntelliJ or Maven,
- application can be built and run from the repository Dockerfile,
- application and PostgreSQL/pgvector can be started together through Docker Compose,
- chat endpoint works with a configured LLM,
- three deterministic tools work,
- product questions use RAG,
- supported PII is masked before external LLM calls,
- the same credit capabilities are exposed through MCP,
- AI interactions are observable,
- at least 10 evaluation scenarios are present,
- Testcontainers covers PostgreSQL/pgvector integration,
- README documents startup, architecture and Spring AI concept mapping.

## 89. Delivery Plan

### Evening 1 — Working AI skeleton

Deliverables:

- first commit containing `SPEC.md`,
- Spring Boot project skeleton,
- module/package structure,
- Dockerfile for the Spring Boot application,
- Docker Compose running the application and PostgreSQL/pgvector,
- typed application configuration,
- basic LangChain4j integration,
- one working chat endpoint,
- basic tests,
- initial README.

### Evening 2 — RAG and privacy

Deliverables:

- sample product documents,
- document normalization,
- chunking,
- embeddings,
- pgvector persistence,
- retrieval,
- RAG integration with chat,
- PESEL masking,
- contract-number masking,
- Testcontainers coverage.

### Evening 3 — Agent demo

Deliverables:

- three credit services,
- LangChain4j tool adapters,
- MCP tool adapters,
- AI observability persistence,
- token and estimated cost tracking,
- evaluation dataset,
- evaluation test,
- final architecture diagram,
- final README.

## 90. Scope Control

If implementation time becomes constrained, prioritize:

```text
1. working LangChain4j chat
2. tool calling
3. RAG with pgvector
4. PII masking
5. MCP exposure
6. observability
7. evaluation polish
```

A simple working implementation is preferred over an incomplete sophisticated implementation.

## 91. Git Workflow

Development shall follow specification-first delivery.

The first commit shall contain the initial specification.

```text
docs: add credit assistant specification
```

Every meaningful implementation step shall be committed separately using an English commit message.

## 92. Final Demo Scenario

The repository should support a short technical demonstration containing at least three interactions.

### Product knowledge

```text
Can the customer repay the loan early?
```

Demonstrates RAG, embeddings, pgvector and grounded generation.

### Business tool

```text
Calculate the installment for PLN 100,000 over 60 months at 8%.
```

Demonstrates agent orchestration, tool calling and deterministic Java business logic.

### Contract information

```text
What is the status of contract CR-2026-000123?
```

Demonstrates PII masking, tool calling, trusted-boundary separation and observability.

The technical talk should focus on the flow of one request through the architecture rather than on the number of implemented features.
