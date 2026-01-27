# RAG Evaluation Test Suite

This directory contains golden set tests for evaluating RAG (Retrieval Augmented Generation) quality.

## Test Files

### `rag-eval.json` (Active Tests)
**36 test cases** that currently pass with the `chappie-ingestion-quarkus:3.30.6` Docker image.

These tests validate that RAG retrieval works correctly across diverse Quarkus topics:
- REST/JAX-RS endpoints
- Panache ORM
- Kafka messaging
- Security (OIDC, JWT)
- Datasource configuration
- Testing
- Observability (health, metrics, tracing, logging)
- Database migrations (Flyway, Liquibase)
- Deployment (Kubernetes, containers, native builds)
- Advanced features (virtual threads, caching, WebSockets, gRPC)

**Test Criteria:**
- Minimum score threshold: 0.82
- Top 10 results evaluated
- All current tests score between 0.88-0.95

### `rag-eval-future.json` (Deferred Tests)
**10 test cases** that are currently failing due to missing or insufficient documentation in the vector database.

These represent valid Quarkus topics that should work once documentation coverage improves:
- Dev Mode
- GraphQL
- MongoDB (generic client, not Panache)
- Fault Tolerance / MicroProfile Fault Tolerance
- CORS configuration
- Bean Validation
- Qute templating
- REST Client (@RegisterRestClient)
- Application lifecycle events
- Reactive routes

**Migration Path:**
When you update the ingestion pipeline or Docker image:
1. Run `RagGoldenSetTest` with cases from `rag-eval-future.json`
2. Move passing cases back to `rag-eval.json`
3. Update `_note` fields with insights about why they now pass

### `other-eval-to-add.json` (Deprecated)
Original file with 2 test cases. These have been migrated:
- `dev-mode` → moved to `rag-eval-future.json` (failing)
- `panache-repository` → moved to `rag-eval.json` (passing)

This file can be deleted.

## Test Infrastructure

### `RagGoldenSetTest.java`
Main test class that:
- Loads test cases from JSON
- Executes RAG retrieval for each query
- Validates results against assertions
- Prints top 10 results with scores for debugging

### `RagEvalCase.java`
Data model for test cases supporting:
- Query text
- Max results (default: 10)
- Extension filtering (e.g., "java", "kt")
- Multiple assertion types:
  - `anyRepoPathContains`: Verify relevant docs in results
  - `anyRepoPathEndsWith`: Verify specific files
  - `minScoreAtRankLe`: Score quality thresholds
  - `minMatches`: Minimum result count

### `RagImageDbResource.java`
Quarkus test resource that:
- Spins up Testcontainers with the RAG Docker image
- Configures datasource to point to test container
- Uses PostgreSQL + pgvector with pre-ingested embeddings

### `RagImageDbConfig.java`
Annotation for configuring test database:
```java
@QuarkusTestResource(
    value = RagImageDbResource.class,
    initArgs = {
        @ResourceArg(name = "image", value = "ghcr.io/quarkusio/chappie-ingestion-quarkus:3.30.6"),
        @ResourceArg(name = "dim", value = "384")
    }
)
```

## Running Tests

```bash
# Run all RAG tests
./mvnw test -Dtest=RagGoldenSetTest

# Use a different Docker image
./mvnw test -Dtest=RagGoldenSetTest -Drag.image=ghcr.io/quarkusio/chappie-ingestion-quarkus:3.31.0

# View detailed output with scores
./mvnw test -Dtest=RagGoldenSetTest | grep "=== RAG CASE"
```

## Adding New Test Cases

1. **Create a test case** in `rag-eval.json`:
```json
{
  "id": "unique-id",
  "query": "Your question here",
  "assertions": {
    "anyRepoPathContains": ["keyword1", "keyword2"],
    "minScoreAtRankLe": { "rank": 10, "score": 0.82 }
  }
}
```

2. **Run the test** to verify it passes
3. **Review the output** to ensure correct documents are retrieved
4. **Adjust thresholds** if needed based on actual scores

## Best Practices

### Good Test Cases
✅ Clear, specific questions users would actually ask
✅ Cover diverse Quarkus topics
✅ Have objective pass/fail criteria
✅ Use realistic score thresholds based on observed performance

### Bad Test Cases
❌ Overly generic queries ("What is Quarkus?")
❌ Questions about non-Quarkus topics
❌ Unrealistic score thresholds (> 0.95)
❌ Topics with no documentation in the vector DB

## Score Interpretation

| Score Range | Interpretation |
|-------------|----------------|
| 0.95+ | Exceptional match (near-identical text) |
| 0.90-0.95 | Excellent match (highly relevant) |
| 0.85-0.90 | Very good match (clearly relevant) |
| 0.82-0.85 | Good match (relevant, acceptable quality) |
| 0.75-0.82 | Moderate match (may be relevant) |
| < 0.75 | Weak match (likely not relevant) |

**Default threshold: 0.82** - Conservative to avoid false positives

## Future Improvements

Potential enhancements to the test suite:
1. **Extension filtering tests** - Test language-specific queries (Java vs Kotlin)
2. **Query variation tests** - Test robustness to phrasing differences
3. **Edge case tests** - Very specific, very broad, and ambiguous queries
4. **Negative tests** - Queries that should have poor matches
5. **Score regression tracking** - Detect quality degradation over time
6. **Performance benchmarks** - Track query latency
7. **Full RAG integration tests** - Test complete flow with LLM responses

See main README or ask the team for implementation details.

## Version History

- **2026-01-27**: Initial golden set with 36 passing tests
- **2026-01-27**: Created future test cases file for deferred tests
