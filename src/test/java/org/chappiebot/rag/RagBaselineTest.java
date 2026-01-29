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

/**
 * Captures baseline RAG retrieval scores for comparison testing.
 *
 * This test generates a baseline.json file that records:
 * - Top scores for each query
 * - Retrieved document paths
 * - Timestamp and image version
 *
 * Use this before making changes to the ingestion pipeline,
 * then use RagComparisonTest to see if your changes improved results.
 */
@QuarkusTest
@QuarkusTestResource(
    value = RagImageDbResource.class,
    initArgs = {
        @ResourceArg(name = "image", value = "ghcr.io/quarkusio/chappie-ingestion-quarkus:3.31.1"),
        @ResourceArg(name = "dim", value = "384")
    }
)
public class RagBaselineTest {

    @Inject RetrievalProvider retrievalProvider;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void capture_baseline() throws Exception {
        List<RagEvalCase> cases = loadCases("rag-eval.json");

        Baseline baseline = new Baseline();
        baseline.timestamp = LocalDateTime.now().toString();
        baseline.dockerImage = System.getProperty("rag.image", "ghcr.io/quarkusio/chappie-ingestion-quarkus:3.31.1");
        baseline.testCount = cases.size();
        baseline.results = new ArrayList<>();

        System.out.println("\n========================================");
        System.out.println("CAPTURING RAG BASELINE");
        System.out.println("========================================");
        System.out.println("Image: " + baseline.dockerImage);
        System.out.println("Test cases: " + baseline.testCount);
        System.out.println("========================================\n");

        for (RagEvalCase c : cases) {
            int maxResults = (c.maxResults != null ? c.maxResults : 10);
            List<SearchMatch> matches = retrievalProvider.search(
                    c.query,
                    maxResults,
                    c.restrictToExtension
            );

            BaselineResult result = new BaselineResult();
            result.id = c.id;
            result.query = c.query;
            result.topScore = matches.isEmpty() ? 0.0 : matches.get(0).score();
            result.avgTopN = calculateAvgScore(matches, 5);
            result.matchCount = matches.size();
            result.topPaths = extractTopPaths(matches, 5);

            baseline.results.add(result);

            System.out.printf("[%s] top=%.4f avg5=%.4f matches=%d%n",
                c.id, result.topScore, result.avgTopN, result.matchCount);
        }

        // Save baseline to target directory
        Path outputPath = Paths.get("target/rag-baseline.json");
        String json = MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(baseline);
        Files.writeString(outputPath, json);

        System.out.println("\n========================================");
        System.out.println("Baseline saved to: " + outputPath.toAbsolutePath());
        System.out.println("========================================\n");
    }

    private List<RagEvalCase> loadCases(String resourceName) throws Exception {
        try (InputStream in = Thread.currentThread().getContextClassLoader().getResourceAsStream(resourceName)) {
            if (in == null) throw new IllegalStateException("Missing test resource: " + resourceName);
            return MAPPER.readValue(in, new TypeReference<List<RagEvalCase>>() {});
        }
    }

    private double calculateAvgScore(List<SearchMatch> matches, int topN) {
        if (matches.isEmpty()) return 0.0;
        return matches.stream()
                .limit(topN)
                .mapToDouble(SearchMatch::score)
                .average()
                .orElse(0.0);
    }

    private List<String> extractTopPaths(List<SearchMatch> matches, int topN) {
        return matches.stream()
                .limit(topN)
                .map(m -> {
                    Object rp = m.metadata().get("repo_path");
                    return rp == null ? "unknown" : rp.toString();
                })
                .toList();
    }

    // Data classes for JSON serialization
    public static class Baseline {
        public String timestamp;
        public String dockerImage;
        public int testCount;
        public List<BaselineResult> results;
    }

    public static class BaselineResult {
        public String id;
        public String query;
        public double topScore;
        public double avgTopN;
        public int matchCount;
        public List<String> topPaths;
    }
}
