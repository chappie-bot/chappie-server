package org.chappiebot.rag;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.common.ResourceArg;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.chappiebot.search.SearchMatch;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Compares current RAG retrieval performance against a baseline.
 *
 * Usage:
 * 1. Run RagBaselineTest to capture baseline with current ingestion
 * 2. Make changes to ingestion pipeline and build new Docker image
 * 3. Run this test with new image: mvn test -Dtest=RagComparisonTest -Drag.image=your-new-image
 * 4. Review the comparison report in target/rag-comparison-report.md
 *
 * The test will PASS if there are no regressions (score drops > 5%).
 * Check the report to see improvements and detailed score changes.
 */
@QuarkusTest
@QuarkusTestResource(
    value = RagImageDbResource.class,
    initArgs = {
        @ResourceArg(name = "image", value = "ghcr.io/quarkusio/chappie-ingestion-quarkus:3.31.1"),
        @ResourceArg(name = "dim", value = "384")
    }
)
public class RagComparisonTest {

    @Inject RetrievalProvider retrievalProvider;

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final double REGRESSION_THRESHOLD = 0.05; // 5% drop = regression

    @Test
    void compare_against_baseline() throws Exception {
        // Load baseline
        Path baselinePath = Paths.get("target/rag-baseline.json");
        if (!Files.exists(baselinePath)) {
            System.err.println("ERROR: No baseline found at " + baselinePath);
            System.err.println("Run RagBaselineTest first to capture baseline.");
            throw new IllegalStateException("Missing baseline file. Run RagBaselineTest first.");
        }

        RagBaselineTest.Baseline baseline = MAPPER.readValue(
            Files.readString(baselinePath),
            RagBaselineTest.Baseline.class
        );

        // Run current tests
        List<RagEvalCase> cases = loadCases("rag-eval.json");
        ComparisonReport report = new ComparisonReport();
        report.baselineTimestamp = baseline.timestamp;
        report.baselineImage = baseline.dockerImage;
        report.currentTimestamp = LocalDateTime.now().toString();
        report.currentImage = System.getProperty("rag.image", "ghcr.io/quarkusio/chappie-ingestion-quarkus:3.31.1");
        report.comparisons = new ArrayList<>();

        Map<String, RagBaselineTest.BaselineResult> baselineMap = baseline.results.stream()
                .collect(Collectors.toMap(r -> r.id, r -> r));

        System.out.println("\n========================================");
        System.out.println("RAG COMPARISON REPORT");
        System.out.println("========================================");
        System.out.println("Baseline: " + baseline.dockerImage + " (" + baseline.timestamp + ")");
        System.out.println("Current:  " + report.currentImage);
        System.out.println("========================================\n");

        int improvements = 0;
        int regressions = 0;
        int unchanged = 0;

        for (RagEvalCase c : cases) {
            int maxResults = (c.maxResults != null ? c.maxResults : 10);
            List<SearchMatch> matches = retrievalProvider.search(
                    c.query,
                    maxResults,
                    c.restrictToExtension
            );

            RagBaselineTest.BaselineResult baselineResult = baselineMap.get(c.id);
            if (baselineResult == null) {
                System.out.println("⚠️  [" + c.id + "] NEW TEST (not in baseline)");
                continue;
            }

            double currentScore = matches.isEmpty() ? 0.0 : matches.get(0).score();
            double baselineScore = baselineResult.topScore;
            double delta = currentScore - baselineScore;
            double percentChange = baselineScore > 0 ? (delta / baselineScore) * 100 : 0;

            Comparison comp = new Comparison();
            comp.id = c.id;
            comp.query = c.query;
            comp.baselineScore = baselineScore;
            comp.currentScore = currentScore;
            comp.delta = delta;
            comp.percentChange = percentChange;
            comp.baselineTopPath = baselineResult.topPaths.isEmpty() ? "none" : baselineResult.topPaths.get(0);
            comp.currentTopPath = matches.isEmpty() ? "none" :
                String.valueOf(matches.get(0).metadata().get("repo_path"));

            report.comparisons.add(comp);

            String status;
            if (Math.abs(delta) < 0.01) {
                status = "➖";
                unchanged++;
            } else if (delta > 0) {
                status = "✅";
                improvements++;
            } else {
                if (Math.abs(delta) > REGRESSION_THRESHOLD) {
                    status = "❌";
                    regressions++;
                } else {
                    status = "⚠️ ";
                    unchanged++;
                }
            }

            System.out.printf("%s [%s] %.4f -> %.4f (%.1f%%)%n",
                status, c.id, baselineScore, currentScore, percentChange);
        }

        // Generate markdown report
        generateMarkdownReport(report, improvements, regressions, unchanged);

        // Summary
        System.out.println("\n========================================");
        System.out.println("SUMMARY");
        System.out.println("========================================");
        System.out.printf("✅ Improvements:  %d%n", improvements);
        System.out.printf("➖ Unchanged:     %d%n", unchanged);
        System.out.printf("⚠️  Minor drops:  %d%n", regressions > 0 ? unchanged : 0);
        System.out.printf("❌ Regressions:   %d%n", regressions);
        System.out.println("========================================");
        System.out.println("Report: target/rag-comparison-report.md");
        System.out.println("========================================\n");

        // Fail test if there are significant regressions
        if (regressions > 0) {
            throw new AssertionError(
                String.format("Found %d regressions (score drops > %.0f%%). Check target/rag-comparison-report.md for details.",
                    regressions, REGRESSION_THRESHOLD * 100)
            );
        }
    }

    private void generateMarkdownReport(ComparisonReport report, int improvements, int regressions, int unchanged) throws Exception {
        StringBuilder md = new StringBuilder();

        md.append("# RAG Comparison Report\n\n");
        md.append("## Configuration\n\n");
        md.append("| | |\n");
        md.append("|---|---|\n");
        md.append("| **Baseline Image** | `").append(report.baselineImage).append("` |\n");
        md.append("| **Baseline Timestamp** | ").append(report.baselineTimestamp).append(" |\n");
        md.append("| **Current Image** | `").append(report.currentImage).append("` |\n");
        md.append("| **Current Timestamp** | ").append(report.currentTimestamp).append(" |\n");
        md.append("\n");

        md.append("## Summary\n\n");
        md.append("| Metric | Count |\n");
        md.append("|--------|-------|\n");
        md.append("| ✅ Improvements | ").append(improvements).append(" |\n");
        md.append("| ➖ Unchanged | ").append(unchanged).append(" |\n");
        md.append("| ❌ Regressions | ").append(regressions).append(" |\n");
        md.append("\n");

        // Top improvements
        List<Comparison> topImprovements = report.comparisons.stream()
                .filter(c -> c.delta > 0)
                .sorted(Comparator.comparingDouble((Comparison c) -> c.delta).reversed())
                .limit(10)
                .toList();

        if (!topImprovements.isEmpty()) {
            md.append("## 🎉 Top Improvements\n\n");
            md.append("| Query | Baseline | Current | Δ | % |\n");
            md.append("|-------|----------|---------|---|---|\n");
            for (Comparison c : topImprovements) {
                md.append("| ").append(c.id).append(" | ");
                md.append(String.format("%.4f", c.baselineScore)).append(" | ");
                md.append(String.format("%.4f", c.currentScore)).append(" | ");
                md.append(String.format("+%.4f", c.delta)).append(" | ");
                md.append(String.format("+%.1f%%", c.percentChange)).append(" |\n");
            }
            md.append("\n");
        }

        // Regressions
        List<Comparison> regList = report.comparisons.stream()
                .filter(c -> c.delta < -REGRESSION_THRESHOLD)
                .sorted(Comparator.comparingDouble((Comparison c) -> c.delta))
                .toList();

        if (!regList.isEmpty()) {
            md.append("## ⚠️ Regressions (drops > 5%)\n\n");
            md.append("| Query | Baseline | Current | Δ | % | Baseline Doc | Current Doc |\n");
            md.append("|-------|----------|---------|---|---|--------------|-------------|\n");
            for (Comparison c : regList) {
                md.append("| ").append(c.id).append(" | ");
                md.append(String.format("%.4f", c.baselineScore)).append(" | ");
                md.append(String.format("%.4f", c.currentScore)).append(" | ");
                md.append(String.format("%.4f", c.delta)).append(" | ");
                md.append(String.format("%.1f%%", c.percentChange)).append(" | ");
                md.append(c.baselineTopPath).append(" | ");
                md.append(c.currentTopPath).append(" |\n");
            }
            md.append("\n");
        }

        // All results
        md.append("## 📊 All Results\n\n");
        md.append("| Query | Baseline | Current | Δ | % |\n");
        md.append("|-------|----------|---------|---|---|\n");

        List<Comparison> sorted = new ArrayList<>(report.comparisons);
        sorted.sort(Comparator.comparingDouble((Comparison c) -> c.delta).reversed());

        for (Comparison c : sorted) {
            md.append("| ").append(c.id).append(" | ");
            md.append(String.format("%.4f", c.baselineScore)).append(" | ");
            md.append(String.format("%.4f", c.currentScore)).append(" | ");
            md.append(String.format("%+.4f", c.delta)).append(" | ");
            md.append(String.format("%+.1f%%", c.percentChange)).append(" |\n");
        }
        md.append("\n");

        Path reportPath = Paths.get("target/rag-comparison-report.md");
        Files.writeString(reportPath, md.toString());
    }

    private List<RagEvalCase> loadCases(String resourceName) throws Exception {
        try (InputStream in = Thread.currentThread().getContextClassLoader().getResourceAsStream(resourceName)) {
            if (in == null) throw new IllegalStateException("Missing test resource: " + resourceName);
            return MAPPER.readValue(in, new TypeReference<List<RagEvalCase>>() {});
        }
    }

    static class ComparisonReport {
        public String baselineTimestamp;
        public String baselineImage;
        public String currentTimestamp;
        public String currentImage;
        public List<Comparison> comparisons;
    }

    static class Comparison {
        public String id;
        public String query;
        public double baselineScore;
        public double currentScore;
        public double delta;
        public double percentChange;
        public String baselineTopPath;
        public String currentTopPath;
    }
}
