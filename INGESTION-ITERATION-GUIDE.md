# RAG Ingestion Iteration Guide

This guide explains how to improve your RAG ingestion pipeline and validate that changes actually improve retrieval quality.

## Overview

The workflow is:
1. **Capture baseline** - Record current RAG performance
2. **Modify ingestion** - Change chunking, embeddings, metadata, etc.
3. **Build new image** - Create Docker image with new ingestion
4. **Compare results** - See if changes improved or degraded quality
5. **Iterate** - Keep improvements, discard regressions

## Step-by-Step Workflow

### Step 1: Capture Baseline

Before making any changes, capture the current RAG performance:

```bash
# Run baseline test with current image
./mvnw test -Dtest=RagBaselineTest

# This creates: target/rag-baseline.json
```

The baseline file contains:
- Top scores for all 36 test queries
- Average top-5 scores
- Retrieved document paths
- Timestamp and Docker image version

**Output Example:**
```
[devui-add-page] top=0.9444 avg5=0.9187 matches=10
[rest-create-endpoint] top=0.9234 avg5=0.9156 matches=10
[rest-json] top=0.9127 avg5=0.9085 matches=10
...
Baseline saved to: target/rag-baseline.json
```

### Step 2: Modify Ingestion Pipeline

Go to your ingestion repository: https://github.com/chappie-bot/chappie-quarkus-rag

Common improvements to try:

#### A. Chunking Strategy
```python
# Current: Fixed-size chunks
# Try: Semantic chunking, header-based splitting, etc.

# Example: Chunk by headers
def chunk_by_headers(document):
    chunks = []
    for section in document.sections:
        chunks.append({
            'text': section.content,
            'metadata': {
                'header': section.title,
                'level': section.level
            }
        })
    return chunks
```

#### B. Metadata Enhancement
```python
# Add more metadata for better filtering
chunk_metadata = {
    'repo_path': file_path,
    'extension': file_ext,
    'header_path': '/'.join(headers),  # NEW
    'quarkus_version': version,         # NEW
    'doc_type': classify_doc(content),  # NEW: guide, reference, tutorial
    'keywords': extract_keywords(text) # NEW
}
```

#### C. Overlap Strategy
```python
# Current: No overlap
# Try: Overlapping chunks for better context

chunk_size = 1000
overlap = 200  # 20% overlap

for i in range(0, len(text), chunk_size - overlap):
    chunk = text[i:i + chunk_size]
    # ... process chunk
```

#### D. Content Filtering
```python
# Skip low-value content
def should_index(chunk):
    # Skip very short chunks
    if len(chunk) < 100:
        return False

    # Skip code-only chunks (may want separate handling)
    if is_mostly_code(chunk):
        return False

    # Skip navigation/boilerplate
    if is_boilerplate(chunk):
        return False

    return True
```

#### E. Different Embedding Model
```python
# Try different models:
# - Different dimensions (384, 768, 1024)
# - Different models (BGE, E5, etc.)
# - Multilingual if needed

from sentence_transformers import SentenceTransformer

# Current
model = SentenceTransformer('BAAI/bge-small-en-v1.5')  # 384 dim

# Alternatives
# model = SentenceTransformer('BAAI/bge-base-en-v1.5')   # 768 dim
# model = SentenceTransformer('intfloat/e5-small-v2')    # 384 dim
```

### Step 3: Build and Tag New Image

After making changes to the ingestion pipeline:

```bash
# In the chappie-quarkus-rag repository

# Build with a test tag
docker build -t ghcr.io/quarkusio/chappie-ingestion-quarkus:test-semantic-chunking .

# Or push to your own registry
docker build -t your-registry/chappie-ingestion:experiment-1 .
docker push your-registry/chappie-ingestion:experiment-1
```

**Naming Convention:**
- `test-<description>` - Local testing
- `experiment-<number>` - Numbered experiments
- `v3.30.6-improved-chunking` - Version + improvement description

### Step 4: Run Comparison Test

Test your new image against the baseline:

```bash
# Test with your new image
./mvnw test -Dtest=RagComparisonTest \
  -Drag.image=ghcr.io/quarkusio/chappie-ingestion-quarkus:test-semantic-chunking

# This creates: target/rag-comparison-report.md
```

The test will:
1. Load your baseline scores
2. Run all 36 queries against the new image
3. Calculate score deltas for each query
4. Generate a detailed comparison report
5. **FAIL** if any query has >5% score regression

**Console Output Example:**
```
========================================
RAG COMPARISON REPORT
========================================
Baseline: ghcr.io/quarkusio/chappie-ingestion-quarkus:3.30.6
Current:  ghcr.io/quarkusio/chappie-ingestion-quarkus:test-semantic-chunking
========================================

✅ [devui-add-page] 0.9444 -> 0.9556 (+1.2%)
✅ [rest-create-endpoint] 0.9234 -> 0.9401 (+1.8%)
➖ [rest-json] 0.9127 -> 0.9131 (+0.0%)
❌ [panache-entity] 0.9017 -> 0.8512 (-5.6%)  # REGRESSION!
...

========================================
SUMMARY
========================================
✅ Improvements:  18
➖ Unchanged:     12
❌ Regressions:   6
========================================
Report: target/rag-comparison-report.md
========================================
```

### Step 5: Review the Report

Open `target/rag-comparison-report.md` to see detailed analysis:

```markdown
# RAG Comparison Report

## Summary
| Metric | Count |
|--------|-------|
| ✅ Improvements | 18 |
| ➖ Unchanged | 12 |
| ❌ Regressions | 6 |

## 🎉 Top Improvements
| Query | Baseline | Current | Δ | % |
|-------|----------|---------|---|---|
| kafka-config | 0.9297 | 0.9512 | +0.0215 | +2.3% |
| security-oidc | 0.9416 | 0.9588 | +0.0172 | +1.8% |
...

## ⚠️ Regressions (drops > 5%)
| Query | Baseline | Current | Δ | % | Baseline Doc | Current Doc |
|-------|----------|---------|---|---|--------------|-------------|
| panache-entity | 0.9017 | 0.8512 | -0.0505 | -5.6% | hibernate-orm-panache.adoc | mongodb-panache.adoc |
```

### Step 6: Analyze and Iterate

#### If Results Are Better:
```bash
# Keep the changes!
# Tag as official version
docker tag ghcr.io/quarkusio/chappie-ingestion-quarkus:test-semantic-chunking \
           ghcr.io/quarkusio/chappie-ingestion-quarkus:3.30.6

# Update baseline for future comparisons
./mvnw test -Dtest=RagBaselineTest \
  -Drag.image=ghcr.io/quarkusio/chappie-ingestion-quarkus:test-semantic-chunking
```

#### If Results Are Worse:
```bash
# Analyze why specific queries regressed
# Look at the "Current Doc" vs "Baseline Doc" columns
# Understand what changed in the chunking/embedding

# Try a different approach
# Maybe smaller chunks? Different overlap? Better metadata?
```

#### If Results Are Mixed:
```bash
# Some improvements, some regressions
# Ask: Are the improvements in more important queries?
# Consider: Can you fix regressions without losing improvements?

# Try hybrid approach or targeted fixes
```

## Advanced Workflows

### Testing Future Cases

Test whether your changes fix any of the 10 failing cases:

```bash
# 1. Copy a case from rag-eval-future.json to rag-eval.json
# 2. Run comparison
./mvnw test -Dtest=RagComparisonTest -Drag.image=your-new-image

# 3. If it passes, keep it in rag-eval.json
# 4. If it fails, move it back to rag-eval-future.json
```

### A/B Testing Multiple Approaches

```bash
# Capture baseline
./mvnw test -Dtest=RagBaselineTest

# Test approach A
./mvnw test -Dtest=RagComparisonTest -Drag.image=test-approach-a
cp target/rag-comparison-report.md target/report-approach-a.md

# Reset and test approach B
./mvnw test -Dtest=RagComparisonTest -Drag.image=test-approach-b
cp target/rag-comparison-report.md target/report-approach-b.md

# Compare the two reports
diff target/report-approach-a.md target/report-approach-b.md
```

### Testing Different Embedding Dimensions

If you change embedding dimensions, update the test:

```bash
# For 768-dimensional embeddings
./mvnw test -Dtest=RagComparisonTest \
  -Drag.image=your-image-768d \
  -Dchappie.rag.pgvector.dimension=768
```

### Continuous Improvement Tracking

Keep a log of improvements:

```bash
# experiments.md
## 2026-01-27: Semantic Chunking
- Image: test-semantic-chunking
- Change: Chunk by document headers instead of fixed size
- Results: +12 improvements, -6 regressions
- Decision: Rejected (too many regressions)

## 2026-01-28: 20% Chunk Overlap
- Image: test-overlap-20pct
- Change: Added 200-char overlap between chunks
- Results: +18 improvements, -0 regressions
- Decision: ✅ ACCEPTED - New baseline
```

## Common Improvement Patterns

### Pattern 1: Regressions in Specific Topics

**Symptom:** Some topics improve, others regress

**Diagnosis:**
```bash
# Look at which topics regressed
grep "❌" target/rag-comparison-report.md
```

**Solutions:**
- Topic-specific chunking strategies
- Better metadata to differentiate similar topics
- Increase chunk diversity (more chunks per document)

### Pattern 2: All Scores Slightly Lower

**Symptom:** Everything drops by 1-2%

**Diagnosis:** Likely caused by:
- Different embedding model
- Different normalization
- Different chunk boundaries

**Solutions:**
- This might actually be fine if relative rankings are preserved
- Check if previously failing queries now work
- Adjust regression threshold if needed

### Pattern 3: Empty Results

**Symptom:** matchCount drops to 0 for some queries

**Diagnosis:**
- Minimum score threshold too high
- Chunks not being indexed
- Embedding dimension mismatch

**Solutions:**
- Check ingestion logs for errors
- Verify chunk count in database
- Check pgvector dimension configuration

## Tips and Best Practices

### 1. Small, Incremental Changes
Don't change multiple things at once. Change one variable:
- ✅ "Add 20% overlap"
- ❌ "Add overlap + new embedding model + semantic chunking"

### 2. Document Everything
Keep notes on what you tried and why:
```bash
# Good commit message
git commit -m "Add 200-char overlap between chunks

Testing hypothesis that overlap improves context for technical queries.
Expected improvement in panache/hibernate queries where concepts span
multiple paragraphs.

Test results: +18 improvements, -0 regressions
Comparison: target/report-2026-01-27.md"
```

### 3. Watch for Overfitting
Don't optimize only for the golden set. The 36 test cases are representative but not exhaustive.

If you get 100% improvements suspiciously:
- Verify with manual testing
- Add new test cases
- Test with `rag-eval-future.json` cases

### 4. Consider the Trade-offs
- **Bigger chunks** = More context, but noisier
- **More overlap** = Better context, but more storage/slower search
- **Better embeddings** = Better quality, but slower/more expensive
- **More metadata** = Better filtering, but more complex ingestion

## Troubleshooting

### Baseline Test Fails
```
ERROR: No baseline found
```
**Solution:** Run `RagBaselineTest` first

### Comparison Test Always Passes
```
All unchanged: 36
```
**Problem:** Testing against same image
**Solution:** Verify `-Drag.image` parameter

### Huge Score Drops
```
All queries: -0.3 to -0.5 drop
```
**Problem:** Dimension mismatch or wrong embedding model
**Solution:** Check embedding dimension, verify model compatibility

### Docker Image Not Found
```
ERROR: Image not found: your-registry/chappie:test
```
**Solution:**
- Push image to registry first
- Or use local image with Testcontainers local mode
- Check image name spelling

## Next Steps

After finding a good ingestion strategy:
1. Update the official Docker image tag
2. Update application-dev.properties
3. Update test resource annotations to use new image
4. Document your changes in the ingestion repository
5. Share learnings with the team

## See Also

- [RAG Tests README](src/test/resources/RAG-TESTS-README.md) - Test infrastructure details
- [Chappie Quarkus RAG](https://github.com/chappie-bot/chappie-quarkus-rag) - Ingestion pipeline repo
- `RagBaselineTest.java` - Baseline capture test
- `RagComparisonTest.java` - Comparison test
- `RagGoldenSetTest.java` - Pass/fail validation test
