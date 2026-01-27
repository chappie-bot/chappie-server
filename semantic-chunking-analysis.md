# Semantic Chunking Analysis
**Date:** 2026-01-27
**Baseline:** Fixed-size chunks (1000/300)
**Experiment:** AsciiDoc header-based semantic chunking (1000 max/300 overlap)

## Executive Summary

Semantic chunking by AsciiDoc headers shows **strong improvements for tutorial-style documentation (+3.4% Kafka)** but **significant degradation for API reference queries (-3.7% Panache)**. This creates a clear quality split between document types.

**Verdict:** ⚠️ **NEEDS DISCUSSION** - Consider hybrid approach

## Results Breakdown

### By Performance Change

| Category | Count | Queries |
|----------|-------|---------|
| **Strong Improvements** (2%+) | 4 | kafka-producer (+3.4%), kafka-consumer (+2.7%), command-mode (+2.6%), multipart-upload (+1.7%) |
| **Good Improvements** (1-2%) | 6 | hibernate-search, rest-create-endpoint, metrics, liquibase-migration, virtual-threads, panache-entity |
| **Marginal Changes** (< 1%) | 20 | Most queries stayed roughly the same |
| **Minor Drops** (1-2%) | 3 | websocket (-1.7%), cdi-injection (-2.0%), devui-add-page (-2.2%) |
| **Significant Drops** (2%+) | 3 | cache-annotation (-2.3%), grpc-service (-2.5%), panache-repository (-3.7%) |

### By Document Type

#### ✅ Tutorial & Guide Documentation (Improved)

| Query | Baseline | Semantic | Δ | Why It Helped |
|-------|----------|----------|---|---------------|
| kafka-producer | 0.9088 | 0.9395 | +3.4% | Multi-step guide with clear sections: Setup → Config → Producer Code → Testing |
| kafka-consumer | 0.9132 | 0.9382 | +2.7% | Similar structure: Setup → Config → Consumer Code → Error Handling |
| command-mode | 0.9145 | 0.9381 | +2.6% | Tutorial sections: Creating App → PicoCli → Arguments → Examples |
| liquibase-migration | 0.9291 | 0.9402 | +1.2% | Step-by-step: Installation → Configuration → Creating Migrations → Running |
| rest-create-endpoint | 0.9220 | 0.9346 | +1.4% | Progressive tutorial: Basic Endpoint → Path Params → Request Body → Response |

**Pattern:** Documentation with clear hierarchical structure where each section is self-contained and builds on the previous.

#### ❌ API Reference Documentation (Degraded)

| Query | Baseline | Semantic | Δ | Why It Hurt |
|-------|----------|----------|---|-------------|
| panache-repository | 0.8975 | 0.8644 | -3.7% | Small fragmented sections: "Repository Interface" → "Methods" → "Queries" lose context |
| grpc-service | 0.9277 | 0.9043 | -2.5% | Protobuf definition in one section, service impl in another - need both together |
| cache-annotation | 0.9353 | 0.9136 | -2.3% | Annotations scattered across sections: @CacheResult, @CacheInvalidate, @CacheKey |
| cdi-injection | 0.9277 | 0.9090 | -2.0% | Basic injection vs qualifiers vs producers split - API patterns need context |
| devui-add-page | 0.9212 | 0.9006 | -2.2% | Extension API spans multiple tiny sections |

**Pattern:** API documentation where concepts span multiple small sections, or where code examples need surrounding context from adjacent sections.

#### ➖ Neutral (No Clear Pattern)

Mixed results for:
- Configuration queries (some improved, some dropped slightly)
- Testing documentation (minor improvement)
- Security documentation (slight drop)
- Observability (metrics improved, tracing dropped)

## Root Cause Analysis

### Why Tutorials Improved

1. **Natural Boundaries:** Tutorial sections have clear start/end points
   ```asciidoc
   == Setting Up Kafka
   [Complete, self-contained instructions]

   == Configuring the Producer
   [Complete, self-contained config]
   ```

2. **Progressive Learning:** Each section builds on the previous in a logical flow
3. **Section Titles Add Context:** Metadata like "Setting Up Kafka" helps retrieval
4. **Reduced Noise:** No mid-sentence splits or incomplete thoughts

### Why API References Suffered

1. **Fragmented Examples:**
   ```asciidoc
   === Repository Interface
   [Just the interface declaration]

   === Finding Entities
   [Just find() methods - but needs interface context from above]

   === Custom Queries
   [Uses methods from above - context lost]
   ```

2. **Small Sections:** API docs often have many small sections (100-300 chars)
   - These get embedded separately
   - Fixed-size chunks would have bundled related concepts together
   - 30% overlap not enough when sections are tiny

3. **Cross-Section Dependencies:**
   - Annotation reference + usage example split across sections
   - Interface definition + implementation guidance split
   - Configuration + code example split

## Comparison: Tutorial vs API Reference

### Kafka Producer Tutorial (✅ +3.4%)

**Semantic chunking created:**
- Section 1: "Setting Up Kafka" → Complete setup instructions
- Section 2: "Configuring the Producer" → application.properties + explanations
- Section 3: "Producing Messages" → Complete code example + explanation
- Section 4: "Error Handling" → Complete error patterns

Each section is **self-sufficient** for a specific query.

### Panache Repository API (❌ -3.7%)

**Semantic chunking created:**
- Section 1: "PanacheRepository Interface" → Just `extends PanacheRepository<Entity>`
- Section 2: "Basic Finders" → `findById()`, `listAll()` - no interface context
- Section 3: "Custom Queries" → Uses methods from Section 2 - no example from Section 1
- Section 4: "Repository Pattern" → High-level explanation - no code

Fixed-size chunks would have **bundled Sections 1+2** and **Sections 2+3**, preserving the relationship between interface, methods, and usage.

## Metrics Analysis

### Average Improvement by Category

| Document Type | Avg Δ | Count |
|---------------|-------|-------|
| Tutorials & Guides | +2.1% | 5 |
| Configuration | +0.4% | 8 |
| Testing | +0.3% | 3 |
| API References | -2.2% | 6 |
| Extensions/Plugins | -2.2% | 3 |

### Distribution

- **Positive impact:** 10 queries (28%)
- **Neutral:** 20 queries (55%)
- **Negative impact:** 6 queries (17%)

## Decision Framework

### REJECT if:
- ❌ Panache/CDI documentation is critical (core Quarkus features)
- ❌ API reference quality is more important than tutorial quality
- ❌ -3.7% drop is unacceptable for any query

### ACCEPT if:
- ✅ Tutorial/getting-started experience is priority
- ✅ Willing to sacrifice API reference quality for better onboarding
- ✅ Kafka improvements justify Panache drops

### HYBRID if:
- 🔀 Want best of both worlds
- 🔀 Can classify documents by type
- 🔀 Willing to implement dual ingestion pipeline

## Hybrid Approach Design

### Semantic Chunking For:
- `/getting-started*.adoc` - Tutorials
- `/kafka*.adoc` - Messaging guides
- `/*-tutorial.adoc` - Tutorials
- `/migration*.adoc` - Migration guides
- `/flyway.adoc`, `/liquibase.adoc` - Tool guides

### Fixed-Size Chunking For:
- `/hibernate-orm-panache*.adoc` - API references
- `/cdi*.adoc` - CDI documentation
- `/cache*.adoc` - Caching APIs
- `/*-reference.adoc` - Reference docs
- `/writing-extensions.adoc` - Extension APIs

### Implementation:
```java
if (docPath.matches(".*(getting-started|tutorial|migration|kafka).*")) {
    splitter = new AsciiDocSemanticSplitter(1000, 300);
} else {
    splitter = DocumentSplitters.recursive(1000, 300);
}
```

Add to manifest enrichment:
```json
{
  "document_type": "tutorial",  // or "reference", "guide", "config"
  "chunking_strategy": "semantic" // or "fixed-size"
}
```

## Recommendations

### Short-term (Immediate):
**KEEP BASELINE** - Semantic chunking doesn't justify -3.7% Panache drop

### Medium-term (Next Iteration):
1. **Refine Splitter:** Merge sections < 200 chars with next section
2. **Minimum Chunk Size:** Don't create chunks smaller than 400 chars
3. **Context Preservation:** When splitting oversized sections, include section header in all chunks

### Long-term (Future Work):
1. **Document Classification:** Classify docs as tutorial/reference/guide
2. **Hybrid Pipeline:** Different chunking strategies per document type
3. **Smart Merging:** Detect related sections (e.g., "Interface" + "Methods") and merge them
4. **Custom Overlap:** More overlap for API docs, less for tutorials

## Conclusion

Semantic chunking is a **valuable technique** but needs refinement:
- ✅ Excellent for tutorial documentation
- ❌ Problematic for API references
- 🔀 Hybrid approach is the best path forward

**Current Status:** Keep fixed-size baseline (1000/300) until hybrid approach is implemented.

**Next Experiment:** Try refining the semantic splitter with minimum section size and smart merging rules.
