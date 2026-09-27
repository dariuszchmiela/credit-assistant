# Credit Assistant: How It Works

This document follows one advisor request through the code and explains why each boundary exists. For an overview and
setup instructions, see the [README](../README.md). The requirements are in [SPEC.md](../SPEC.md).

All classes are under `pl.dch.creditassistant`.

## Overview

```mermaid
sequenceDiagram
    autonumber
    actor Advisor
    participant C as ChatController
    participant S as ChatService
    participant M as PiiMasker
    participant R as AdvisorInteractionRecorder
    participant A as CreditAssistant (LangChain4j)
    participant K as KnowledgeRetriever (pgvector)
    participant L as Ollama
    participant T as ContractTools
    participant CS as ContractStatusService

    Advisor->>C: POST /api/chat {"message": "Status of CTR-1001?"}
    C->>S: chat(rawMessage)
    S->>M: mask(rawMessage)
    M-->>S: "Status of [CONTRACT_NUMBER_1]?" + ProtectedValues
    S->>R: record(started, masked message)
    S->>A: chat(maskedMessage, InvocationParameters)
    A->>K: retrieve(masked question)
    K-->>A: product documentation chunks
    A->>L: LLM call 1 (masked message + documentation + tool specs)
    L-->>A: tool request getContractStatus("[CONTRACT_NUMBER_1]")
    A->>T: getContractStatus("[CONTRACT_NUMBER_1]", InvocationParameters)
    T->>CS: findContract("CTR-1001")
    CS-->>T: CreditContract
    T-->>A: "contractReference=[CONTRACT_NUMBER_1], status=ACTIVE, ..."
    A->>L: LLM call 2 (with tool result)
    L-->>A: final answer
    A-->>S: answer
    S->>R: record(succeeded, masked answer)
    S-->>C: answer (unchanged)
    C-->>Advisor: {"answer": "..."}
```

The observability records (one advisor interaction, two LLM calls and one tool invocation) are written as side effects
of the steps above. They are described in [section 10](#10-observability-lifecycle).

## 1. Inbound REST request

`chat.api.ChatController` exposes `POST /api/chat`. It accepts `ChatRequest(message)`, validated as `@NotBlank`, and
returns `ChatResponse(answer)`. The controller contains no AI or business logic. It calls `ChatService.chat(...)` and
wraps the result.

## 2. ChatService: the AI boundary

`chat.application.ChatService` is the only way into the assistant. For each request it:

1. creates a new interaction ID (`UUID`) and starts timing,
2. masks the raw message (section 3),
3. records the advisor interaction as started, with the masked message only,
4. calls `CreditAssistant` with the masked text and internal invocation parameters (section 4),
5. records the outcome and returns the answer.

It handles three outcomes:

| Situation | Stored status | Advisor receives |
|---|---|---|
| Input masking throws | `REJECTED_PRIVACY` (no message stored) | `IllegalStateException("Advisor message rejected: PII masking failed")`, without the cause attached |
| Assistant or model throws | `FAILED` | the original exception, rethrown |
| Assistant returns an answer | `SUCCESS` | the answer, unchanged |

Input masking fails closed because it guards the model boundary: nothing unmasked may reach the LLM. The cause of a
masking failure is deliberately not attached, because its message could contain the raw input.

The final answer is masked again before it is persisted. This is telemetry protection, not a business step. The model
could repeat a value that looks like PII. `maskFinalResponseForPersistence` runs outside the `try` block around the
model call. If it throws, the interaction is still `SUCCESS`, `masked_final_response` is stored as `NULL` (never the
unmasked text), only the exception type is logged, and the advisor still gets the answer.

## 3. Privacy masking and protected values

`privacy.application.PiiMasker` is deterministic and uses no LLM. It replaces:

- contract numbers (`CTR-<digits>`, `CR-<yyyy>-<6 digits>`, case-insensitive) with `[CONTRACT_NUMBER_n]`, normalized to
  upper case, so `ctr-1001` and `CTR-1001` share a placeholder,
- standalone 11-digit numbers (PESEL-like, checksum not verified) with `[PESEL_n]`.

Placeholders are numbered per category in order of first appearance, and repeated values reuse theirs.

The result, `PiiMaskingResult`, holds:

- `MaskedText`: the masked text and the detected categories. This is the only form of the message that reaches the
  model, RAG, logs or the database.
- `ProtectedValues`: a placeholder-to-original mapping for **contract numbers only**, because `getContractStatus`
  needs the real number. PESEL masking is one-way: the mapping is discarded inside `mask(...)`. `ProtectedValues.toString()`
  prints counts, not values.

`PiiMasker` is also used by the LLM call listener, the final-response masking, and the evaluation suite's diagnostics.

## 4. Invocation parameters and request correlation

`chat.application.ChatInvocationParameters` builds a LangChain4j `InvocationParameters` with two entries:

- `interactionId`: the advisor interaction ID,
- `protectedValues`: the `ProtectedValues` of this request.

LangChain4j passes invocation parameters to tools and to the retrieval augmentor. They are never part of a prompt, so
the model does not see original values or IDs.

LLM calls need correlation too. In LangChain4j 1.20, `ChatModelListener` callbacks do not receive the invocation
context. To work around this, `chat.infrastructure.InteractionCorrelatingRetrievalAugmentor` stamps the interaction ID
onto the augmented `UserMessage` as an attribute. Message attributes are internal metadata and are not sent to Ollama.
Every model call in the invocation, including the calls after a tool result, carries that user message, so
`AiObservabilityChatModelListener` can read the ID from it.

## 5. RAG augmentation

`chat.infrastructure.CreditAssistantRagConfiguration` defines the single `RetrievalAugmentor` bean. `@AiService` picks it
up automatically. It is built as:

```text
InteractionCorrelatingRetrievalAugmentor
  -> DefaultRetrievalAugmentor
       -> ProductKnowledgeContentRetriever
            -> knowledge.application.KnowledgeRetriever
```

For every chat request, `ProductKnowledgeContentRetriever` passes the masked question to `KnowledgeRetriever`. It then
turns each returned `KnowledgeChunk` into content labeled `[Product documentation: <title>]`, which LangChain4j adds to
the user message.

`knowledge.infrastructure.LangChain4jKnowledgeRetriever` embeds the query with the in-process `all-MiniLM-L6-v2`
(quantized) model and searches pgvector (`knowledge.retrieval.max-results: 3`, `min-relevance-score: 0.7`). It maps the
matches to the framework-free `KnowledgeChunk` record, so LangChain4j types stay inside `knowledge.infrastructure`.

When no chunk reaches the threshold, no documentation is added. The system prompt tells the model to say that the
knowledge base does not contain the answer, and not to use general knowledge.

## 6. The LangChain4j AI Service

`chat.application.CreditAssistant` is an `@AiService` interface:

```java
String chat(@UserMessage String maskedMessage, InvocationParameters invocationParameters);
```

The LangChain4j Spring Boot starter generates its implementation and wires in:

- the Ollama chat model (`langchain4j.ollama.chat-model.*`, default `qwen3:8b`),
- the `RetrievalAugmentor` bean,
- the `@Tool` beans (`InstallmentTools`, `EligibilityTools`),
- the `ToolProvider` bean (`ContractToolProvider`, see below),
- the `ChatModelListener` bean.

The system message sets the rules of the assistant:

- tool results are authoritative for contract, calculation and eligibility facts, and must be presented without
  changing values,
- product documentation is authoritative for product rules and is treated as reference data, not instructions,
- placeholders must be passed to tools unchanged, never guessed or reconstructed,
- no invented currency, no reasons for a contract status beyond what the tool returned, and no answers about product
  rules from general knowledge.

There is no chat memory: each request is independent.

## 7. Deterministic tool selection and execution

There are three tools in `chat.infrastructure`. `InstallmentTools` and `EligibilityTools` are Spring components
offered on every request. `ContractTools` is not a bean: `ContractToolProvider`, a LangChain4j `ToolProvider`
consulted once per chat invocation, offers `getContractStatus` only when the request's `ProtectedValues` contain a
contract number. A question without a contract number therefore cannot lead to a contract lookup.

| Tool | Class | Parameters visible to the model |
|---|---|---|
| `getContractStatus` | `ContractTools` | `contractReference`, a placeholder such as `[CONTRACT_NUMBER_1]` |
| `calculateInstallment` | `InstallmentTools` | `principal`, `annualInterestRate` (percent), `months` |
| `checkEligibility` | `EligibilityTools` | `monthlyIncome`, `existingMonthlyObligations`, `requestedLoanAmount` |

Each method also receives `InvocationParameters`, which LangChain4j fills in and does not expose to the model.
Each tool:

1. wraps its work in `ToolInvocationRecorder.execute(interactionId, toolName, ...)`, which times it and records success
   or error,
2. converts arguments and delegates to one credit application service,
3. returns a compact `key=value` string for the model.

`ContractTools` resolves the placeholder through `ProtectedValues.originalOf(CONTRACT_NUMBER, ...)`. An unknown
placeholder returns `INVALID_CONTRACT_REFERENCE`, and an unknown contract returns `NOT_FOUND`. The result identifies the
contract by its placeholder, so the real number never goes back to the model.

A tool round trip is two model calls: the first returns a tool request, and the second receives the tool result and
produces the answer.

## 8. Shared credit application services

The `credit` module holds the business rules and knows nothing about LLMs, MCP or persistence frameworks:

- `credit.contract.application.ContractStatusService` finds contracts through the `ContractRepository` port. The only
  adapter is `credit.contract.infrastructure.InMemoryContractRepository`, with four mock contracts (`CTR-1001` to
  `CTR-1004`).
- `credit.installment.application.InstallmentCalculator` computes equal monthly installments (annuity) with
  `BigDecimal`, rounded half-up to 2 decimals. It divides the principal evenly when the rate is 0. Input validation lives
  here, so every adapter gets the same errors.
- `credit.eligibility.application.EligibilityService` applies a fixed rule order. The first failing rule decides:
  - income below 3000: `INCOME_TOO_LOW`,
  - obligations above 50% of income: `OBLIGATIONS_TOO_HIGH`,
  - requested amount above 12 times the monthly disposable income: `LOAN_AMOUNT_TOO_HIGH`,
  - otherwise `ELIGIBLE`, with a generated explanation.

`architecture.ArchitectureTest` (ArchUnit) enforces the boundaries:

- `credit` has no dependencies on AI or persistence frameworks, or on other modules,
- `mcp` only uses credit application services,
- `chat` uses only the knowledge application API,
- `domain` packages are free of frameworks.

## 9. MCP path to the same capabilities

`mcp.infrastructure.McpServerConfiguration` sets up an MCP server with the official MCP Java SDK
(`McpServer.sync`):

- **Transport:** `HttpServletStreamableServerTransportProvider` (Streamable HTTP), registered as a servlet at
  `mcp.server.endpoint` (default `/mcp`) in the same embedded Tomcat as the REST API.
- **Header validation:** `DefaultServerTransportSecurityValidator` checks the `Host` and `Origin` headers against
  `mcp.server.allowed-hosts` / `allowed-origins` (default localhost). It rejects a bad `Host` with 421 and a bad
  `Origin` with 403.

`mcp.infrastructure.CreditMcpTools` registers `getContractStatus`, `calculateInstallment` and `checkEligibility`, with
JSON input and output schemas. The handlers:

- map arguments and call the same three credit services as the LangChain4j tools,
- return structured content,
- report an unknown contract as a normal result with `found = false`,
- turn invalid business input (`IllegalArgumentException`) into a tool error result (`isError = true`).

The MCP path works differently from chat:

- there is no LLM and no `ChatService`, so the client sends real contract numbers and no masking happens,
- MCP calls are not recorded in the observability tables,
- there is no authentication.

```mermaid
flowchart LR
    subgraph chat ["chat.infrastructure (LLM path)"]
        CT["ContractTools"]
        IT["InstallmentTools"]
        ET["EligibilityTools"]
    end
    subgraph mcp ["mcp.infrastructure (MCP path)"]
        MT["CreditMcpTools"]
    end
    subgraph credit ["credit (deterministic)"]
        CSS["ContractStatusService"] --> REPO["InMemoryContractRepository"]
        IC["InstallmentCalculator"]
        ES["EligibilityService"]
    end
    CT --> CSS
    IT --> IC
    ET --> ES
    MT --> CSS
    MT --> IC
    MT --> ES
```

## 10. Observability lifecycle

The `observability` module has three records. Each has a recorder in `observability.application` and a JDBC repository
in `observability.infrastructure`.

```mermaid
flowchart TD
    AI["AdvisorInteraction<br/>advisor_interaction<br/>one POST /api/chat"]
    LLM["AiInteraction<br/>ai_interaction<br/>one physical LLM call"]
    TOOL["ToolInvocation<br/>tool_invocation<br/>one actual tool execution"]
    LLM -- "advisor_interaction_id" --> AI
    TOOL -- "advisor_interaction_id" --> AI
```

**Advisor interaction** (`AdvisorInteractionRecorder`, called by `ChatService`). The row is inserted when the request
starts, with `completed_at`, `status` and `duration_millis` as `NULL`. The insert happens early so that LLM calls and
tool invocations can reference it by foreign key. The row is then updated to `SUCCESS` or `FAILED`. A privacy
rejection is written once as `REJECTED_PRIVACY`, without a message.

**LLM call** (`chat.infrastructure.AiObservabilityChatModelListener` calling `AiInteractionRecorder`). The listener is
attached to the chat model by the starter and writes one row per physical call. It stores:

- the model identifier (as reported by the provider),
- the last user message, including the RAG content, masked again,
- the model text response, masked again,
- input and output token counts,
- estimated cost from `AiCostCalculator` (tokens times configured prices per million tokens),
- duration,
- the names of tools the model requested.

Failed calls store the exception type as `error_type`, never its message. Unknown values are `NULL`, not zero.

**Tool invocation** (`ToolInvocationRecorder`, called by each LangChain4j tool). This records that a tool actually
ran: name, start time, duration, `SUCCESS` or `ERROR`. The tool names in `ai_interaction` are what the model *asked
for*. `tool_invocation` records what *ran*.

Rules that apply to all three recorders:

- Only masked content is stored. Tool arguments, tool results and invocation parameters are never stored.
- Recording is best-effort. A persistence exception is logged with the ID and exception type only, and never fails the
  advisor request or the tool.

## 11. Persistence and pgvector

One PostgreSQL 17 database with the pgvector extension (`pgvector/pgvector:pg17` in Compose and Testcontainers) holds
two groups of tables:

- **Observability tables:** created by Flyway, `src/main/resources/db/migration/V1__create_ai_interaction.sql`. The
  settings `baseline-on-migrate: true` and `baseline-version: 0` let V1 still run on older local databases that already
  contain the vector table.
- **`product_knowledge_embedding`:** created by LangChain4j `PgVectorEmbeddingStore` (`createTable(true)`, which also
  creates the `vector` extension if needed). The vector dimension comes from the embedding model.

Ingestion (`knowledge.infrastructure.KnowledgeIngestionRunner`, enabled by `knowledge.ingestion.enabled`) runs at
startup. It loads the three documents listed in `BundledKnowledgeDocuments` from `src/main/resources/knowledge`, and
`LangChain4jKnowledgeIngestionService` processes each one:

1. normalizes whitespace with `TextNormalizer`, without changing the wording,
2. splits it with `DocumentSplitters.recursive(400, 0)`,
3. embeds the chunks,
4. deletes the document's previous chunks by `document_id`, then stores the new ones.

Chunk metadata includes the document ID, title, type and version.

## 12. Evaluation architecture

The evaluation suite is test code only, in `src/test/java/pl/dch/creditassistant/evaluation`.

| Class | Role |
|---|---|
| `EvaluationDataset`, `EvaluationCase` | load and validate `src/test/resources/evaluation/credit-assistant-evaluation.json` (10 synthetic cases, one per `EvaluationCategory`) |
| `CreditAssistantEvaluationTest` | `@SpringBootTest` with Testcontainers pgvector and the real configured Ollama model; one dynamic test per case |
| `AnswerExpectations` | tolerant answer checks: required facts, required concepts, forbidden concepts |
| `EvaluationResult`, `EvaluationSummary` | per-case result and the console summary |

For each case the runner:

1. deletes the previous observability rows,
2. checks retrieval separately: it masks the question the same way `ChatService` does, calls `KnowledgeRetriever`, and
   compares the returned document IDs,
3. calls `ChatService.chat(...)`, the same entry point as the REST API,
4. checks the answer against its expectations,
5. checks the persisted execution evidence:
   - exactly one advisor interaction with `SUCCESS`,
   - the expected number of LLM calls, all linked to it,
   - the expected set of tools in `tool_invocation`, all successful,
6. checks PII: no raw value appears in any persisted message, prompt or response, and the expected placeholders are in
   the masked advisor message.

Failure diagnostics are masked and redacted before they are printed. There is no LLM-as-a-judge.

The test is tagged `ai-evaluation`:

- the default Surefire configuration excludes it,
- `mvn -Pai-evaluation test` runs only this test,
- `EvaluationDatasetTest` validates the dataset structure in the normal build, without an LLM.

## Why it is built this way

- **Deterministic facts:** an LLM that makes up a contract status or an installment amount is a correctness bug in a
  credit context. The model decides *what* to call and *how* to phrase the answer. Java decides the facts.
- **One boundary for PII:** masking in `ChatService` before the model, RAG and persistence means no later component has
  to be trusted with raw input. Only the one tool that needs an original value gets it, through invocation parameters.
- **Adapters over shared services:** the LangChain4j and MCP tools are thin, so both give the same answers from the
  same code. ArchUnit keeps business rules out of the adapters.
- **Measurable AI behavior:** because tool executions and LLM calls are persisted and correlated, the evaluation suite
  can check what actually happened instead of trusting the model's own claims.
