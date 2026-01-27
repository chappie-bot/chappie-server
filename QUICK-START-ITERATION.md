# Quick Start: RAG Ingestion Iteration

**Goal:** Improve RAG quality by iterating on the ingestion pipeline.

## The 5-Step Process

### 1️⃣ Capture Current Performance (DONE ✅)
```bash
./mvnw test -Dtest=RagBaselineTest
```
**Output:** `target/rag-baseline.json` (already created for you)

---

### 2️⃣ Modify Ingestion
Go to: https://github.com/chappie-bot/chappie-quarkus-rag

**Try one of these:**
- Change chunk size (e.g., 500 → 1000 chars)
- Add chunk overlap (e.g., 20%)
- Improve metadata (add headers, keywords)
- Different chunking strategy (semantic vs fixed)
- Better content filtering

---

### 3️⃣ Build New Image
```bash
# In the chappie-quarkus-rag repo
docker build -t ghcr.io/quarkusio/chappie-ingestion-quarkus:test-my-improvement .

# Or push to your registry
docker build -t your-registry/chappie:test1 .
docker push your-registry/chappie:test1
```

---

### 4️⃣ Compare Against Baseline
```bash
# Back in chappie-server repo
./mvnw test -Dtest=RagComparisonTest \
  -Drag.image=ghcr.io/quarkusio/chappie-ingestion-quarkus:test-my-improvement
```

**Output:**
- Console: Summary of improvements/regressions
- `target/rag-comparison-report.md`: Detailed analysis

---

### 5️⃣ Decide: Keep or Discard

**✅ If Better (more improvements than regressions):**
```bash
# Update baseline for next iteration
./mvnw test -Dtest=RagBaselineTest \
  -Drag.image=ghcr.io/quarkusio/chappie-ingestion-quarkus:test-my-improvement

# Tag as official
docker tag your-test-image official-image:version
```

**❌ If Worse:**
```bash
# Try a different approach
# Review the comparison report to understand why
```

**🤔 If Mixed:**
- Check if improvements are in more important queries
- See if regressions can be fixed
- Consider hybrid approach

---

## Example Session

```bash
# 1. Capture baseline (already done)
./mvnw test -Dtest=RagBaselineTest

# Output shows current scores:
# [devui-add-page] top=0.9444 avg5=0.9187 matches=10
# [rest-create-endpoint] top=0.9234 avg5=0.9156 matches=10
# ...

# 2. Make changes in chappie-quarkus-rag repo
cd ../chappie-quarkus-rag
# ... edit chunking strategy ...

# 3. Build new image
docker build -t ghcr.io/quarkusio/chappie-ingestion-quarkus:test-overlap .

# 4. Test it
cd ../chappie-server
./mvnw test -Dtest=RagComparisonTest \
  -Drag.image=ghcr.io/quarkusio/chappie-ingestion-quarkus:test-overlap

# Console shows:
# ✅ [devui-add-page] 0.9444 -> 0.9556 (+1.2%)
# ✅ [rest-create-endpoint] 0.9234 -> 0.9401 (+1.8%)
# ❌ [panache-entity] 0.9017 -> 0.8512 (-5.6%)
#
# SUMMARY
# ✅ Improvements:  18
# ❌ Regressions:   6

# 5. Review report
cat target/rag-comparison-report.md

# Decide: Keep or try again
```

---

## Quick Tips

**Start Small**
- Change ONE thing at a time
- Document what you changed and why

**Watch the Console**
- ✅ = Improvement
- ❌ = Regression (>5% drop)
- ➖ = No change

**Check the Report**
- See which queries improved most
- Understand why queries regressed
- Look at document path changes

**Iterate Quickly**
- Don't overthink, just try things
- Keep notes on what works
- Build on successful changes

---

## Current Baseline Stats

**Image:** `ghcr.io/quarkusio/chappie-ingestion-quarkus:3.30.6`
**Date:** 2026-01-27
**Test Cases:** 36 passing

**Score Distribution:**
- Top performers (>0.94): 7 queries
- Strong (0.90-0.94): 22 queries
- Good (0.85-0.90): 7 queries
- All above 0.82 threshold ✅

**Best Scores:**
- hibernate-search: 0.9529
- datasource-config: 0.9470
- kubernetes-deploy: 0.9469

**Lowest Scores (still good):**
- async-rest: 0.8831
- config-properties: 0.8931
- panache-repository: 0.8927

---

## Common Improvements That Work

✅ **20-30% chunk overlap** - Better context preservation
✅ **Semantic chunking by headers** - Natural boundaries
✅ **Rich metadata** - Better filtering and retrieval
✅ **Code block detection** - Separate handling
✅ **Remove boilerplate** - Skip navigation, footers

❌ **Very small chunks (<200 chars)** - Too fragmented
❌ **Very large chunks (>2000 chars)** - Too noisy
❌ **Changing embedding models** - Often causes regressions
❌ **Aggressive filtering** - Lose important content

---

## Need Help?

📖 **Full Guide:** See `INGESTION-ITERATION-GUIDE.md`
📊 **Test Details:** See `src/test/resources/RAG-TESTS-README.md`
🔬 **Test Code:** See `src/test/java/org/chappiebot/rag/`

---

## Your Current Status

✅ Baseline captured: `target/rag-baseline.json`
✅ 36 test cases passing
✅ Comparison tools ready

**Next Step:** Make your first improvement in chappie-quarkus-rag! 🚀
