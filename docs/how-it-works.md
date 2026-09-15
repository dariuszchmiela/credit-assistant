# Credit Assistant — How It Works

## Current Architecture

The application is currently a Spring Boot application using LangChain4j with a locally running Ollama model.

The current request flow is:

```text
Client
  |
  | POST /api/chat
  v
ChatController
  |
  v
CreditAssistant (@AiService)
  |
  v
LangChain4j
  |
  v
OllamaChatModel
  |
  v
Local LLM
  |
  | decides whether a tool is required
  v
ContractTools
  |
  v
ContractStatusService
  |
  v
ContractRepository
  |
  v
InMemoryContractRepository
```

## 1. REST API

`ChatController` exposes:

```text
POST /api/chat
```

Example request:

```json
{
  "message": "What is the status of contract CTR-1001?"
}
```

The controller passes the user message to `CreditAssistant`.

The controller itself does not contain AI or business logic.

---

## 2. AI Service

`CreditAssistant` is a LangChain4j AI Service declared with:

```java
@AiService
public interface CreditAssistant {
    String chat(String message);
}
```

LangChain4j generates the runtime implementation of this interface.

The application therefore does not manually call the LLM HTTP API.

Spring injects the generated `CreditAssistant` implementation into `ChatController`.

The `@SystemMessage` defines the basic behavior of the assistant and instructs the model not to invent deterministic credit data.

---

## 3. Chat Model

For local development the application uses Ollama.

Configuration:

```yaml
langchain4j:
  ollama:
    chat-model:
      base-url: ${OLLAMA_BASE_URL:http://localhost:11434}
      model-name: ${OLLAMA_MODEL:llama3.2}
```

LangChain4j creates an `OllamaChatModel` from this configuration.

Ollama exposes a local HTTP API, while the selected LLM runs locally.

This means development does not require paid LLM API calls.

---

## 4. Tool Calling

Some questions cannot safely be answered using only the LLM.

For example:

```text
What is the status of contract CTR-1001?
```

The LLM must not invent the contract status.

Instead, LangChain4j exposes the following Java method to the model as a tool:

```java
@Tool
public String getContractStatus(String contractNumber)
```

The tool description tells the LLM what the function does.

The parameter description tells the model what value should be passed to it.

The LLM decides that it needs the tool and requests:

```text
getContractStatus("CTR-1001")
```

LangChain4j executes the Java method.

---

## 5. Tool Adapter

`ContractTools` is an adapter between the AI layer and the deterministic application layer.

It contains LangChain4j-specific annotations such as:

```java
@Tool
@P
```

It does not contain the actual contract business logic.

Instead it delegates to:

```text
ContractStatusService
```

This separation prevents the credit domain from depending on LangChain4j.

---

## 6. Application Service

`ContractStatusService` contains the application use case for retrieving a contract status.

Its dependency is the abstraction:

```text
ContractRepository
```

The service does not know whether contract information comes from:

* memory,
* PostgreSQL,
* another microservice,
* an external API.

It only knows the repository interface.

---

## 7. Repository

`ContractRepository` defines the contract:

```java
Optional<CreditContract> findByContractNumber(String contractNumber);
```

The current MVP implementation is:

```text
InMemoryContractRepository
```

It contains several deterministic test contracts:

```text
CTR-1001 -> ACTIVE
CTR-1002 -> CLOSED
CTR-1003 -> OVERDUE
```

Later this implementation can be replaced without changing the AI tool or application service.

---

## 8. Complete Tool Call Example

User sends:

```text
What is the status of contract CTR-1001?
```

The flow is:

```text
1. ChatController receives the HTTP request.

2. ChatController calls:
   CreditAssistant.chat(...)

3. LangChain4j sends the conversation and available tool definitions
   to the local Ollama model.

4. The model determines that contract status is deterministic data
   and selects:
   getContractStatus

5. LangChain4j executes:
   ContractTools.getContractStatus("CTR-1001")

6. ContractTools calls:
   ContractStatusService.getContractStatus("CTR-1001")

7. ContractStatusService calls:
   ContractRepository.findByContractNumber("CTR-1001")

8. InMemoryContractRepository returns:
   CreditContract("CTR-1001", ACTIVE)

9. ContractStatusService returns:
   ACTIVE

10. ContractTools returns:
    "ACTIVE"

11. LangChain4j gives the tool result back to the LLM.

12. The LLM produces a natural-language answer:

    "The current status of contract CTR-1001 is Active."

13. ChatController returns the answer as JSON.
```

## Key Architectural Rule

The LLM is responsible for:

```text
understanding user intent
choosing tools
combining information
generating natural-language answers
```

Java application services are responsible for:

```text
contract facts
calculations
eligibility decisions
business rules
```

The LLM must never become the source of truth for deterministic business data.

## Why This Architecture Matters

The same application services can later be exposed through multiple interfaces:

```text
LangChain4j Tool
        |
        v
Application Service
        ^
        |
MCP Tool
```

The AI integration and MCP integration remain adapters.

The underlying credit-domain logic stays independent from both technologies.
