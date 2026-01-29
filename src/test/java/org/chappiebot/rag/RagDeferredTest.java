package org.chappiebot.rag;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.common.ResourceArg;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.chappiebot.search.SearchMatch;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.*;
import java.util.stream.IntStream;


//@QuarkusTest
//@QuarkusTestResource(
//    value = RagImageDbResource.class,
//    initArgs = {
//        @ResourceArg(name = "image", value = "ghcr.io/quarkusio/chappie-ingestion-quarkus:3.31.1"),
//        @ResourceArg(name = "dim", value = "384")
//    }
//)
public class RagDeferredTest {

    @Inject RetrievalProvider retrievalProvider;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    //@Test
    void deferred_cases_with_docling() throws Exception {
        List<RagEvalCase> cases = loadCases("rag-eval-deferred.json");
        Assertions.assertFalse(cases.isEmpty(), "rag-eval-deferred.json is empty");

        System.out.println("\n========================================");
        System.out.println("Testing " + cases.size() + " Previously Failing Cases with Docling");
        System.out.println("========================================\n");

        List<String> passed = new ArrayList<>();
        List<String> failed = new ArrayList<>();

        for (RagEvalCase c : cases) {
            try {
                runCase(c);
                passed.add(c.id);
                System.out.println("✅ PASSED: " + c.id);
            } catch (AssertionError ae) {
                failed.add("[" + c.id + "] " + ae.getMessage());
                System.out.println("❌ FAILED: " + c.id + " - " + ae.getMessage());
            } catch (Exception e) {
                failed.add("[" + c.id + "] error: " + e.getClass().getSimpleName() + ": " + e.getMessage());
                System.out.println("❌ ERROR: " + c.id + " - " + e.getMessage());
            }
        }

        System.out.println("\n========================================");
        System.out.println("RESULTS SUMMARY");
        System.out.println("========================================");
        System.out.println("Passed: " + passed.size() + "/" + cases.size());
        System.out.println("Failed: " + failed.size() + "/" + cases.size());
        
        if (!passed.isEmpty()) {
            System.out.println("\n✅ Passed tests:");
            passed.forEach(id -> System.out.println("  - " + id));
        }
        
        if (!failed.isEmpty()) {
            System.out.println("\n❌ Failed tests:");
            failed.forEach(System.out::println);
            
            String msg = "\nDeferred test failures (" + failed.size() + "/" + cases.size() + "):\n"
                    + String.join("\n", failed);
            Assertions.fail(msg);
        }
        
        System.out.println("\n🎉 All " + cases.size() + " previously failing tests now PASS with Docling!");
    }

    private void runCase(RagEvalCase c) {
        int maxResults = (c.maxResults != null ? c.maxResults : 10);

        List<SearchMatch> matches = retrievalProvider.search(
                c.query,
                maxResults,
                c.restrictToExtension
        );

        dumpTop(matches, c.id, 10);

        RagEvalCase.Assertions a = (c.assertions != null ? c.assertions : new RagEvalCase.Assertions());

        if (a.minMatches != null) {
            Assertions.assertTrue(matches.size() >= a.minMatches,
                    "Expected at least " + a.minMatches + " matches but got " + matches.size());
        } else {
            Assertions.assertFalse(matches.isEmpty(), "Expected matches but got none");
        }

        // Extract repo_path (may be null)
        List<String> repoPaths = matches.stream()
                .map(m -> {
                    Object rp = m.metadata().get("repo_path");
                    return rp == null ? null : rp.toString();
                })
                .filter(Objects::nonNull)
                .toList();

        if (a.anyRepoPathEndsWith != null && !a.anyRepoPathEndsWith.isEmpty()) {
            boolean ok = repoPaths.stream().anyMatch(p ->
                    a.anyRepoPathEndsWith.stream().anyMatch(p::endsWith)
            );
            Assertions.assertTrue(ok,
                    "Expected any repo_path to end with one of " + a.anyRepoPathEndsWith + " but got top paths: " + sample(repoPaths));
        }

        if (a.anyRepoPathContains != null && !a.anyRepoPathContains.isEmpty()) {
            boolean ok = repoPaths.stream().anyMatch(p -> {
                String pl = p.toLowerCase(Locale.ROOT);
                return a.anyRepoPathContains.stream().anyMatch(s -> pl.contains(s.toLowerCase(Locale.ROOT)));
            });
            Assertions.assertTrue(ok,
                    "Expected any repo_path to contain one of " + a.anyRepoPathContains + " but got top paths: " + sample(repoPaths));
        }

        if (a.minScoreAtRankLe != null && a.minScoreAtRankLe.rank != null && a.minScoreAtRankLe.score != null) {
            int rankLimit = a.minScoreAtRankLe.rank;
            double minScore = a.minScoreAtRankLe.score;

            boolean ok = IntStream.range(0, Math.min(rankLimit, matches.size()))
                    .anyMatch(i -> matches.get(i).score() >= minScore);

            Assertions.assertTrue(ok,
                    "Expected some match within rank<=" + rankLimit + " to have score>=" + minScore
                            + " but top scores were: " + topScores(matches, Math.min(rankLimit, matches.size())));
        }
    }

    private static List<RagEvalCase> loadCases(String resourceName) throws Exception {
        try (InputStream in = Thread.currentThread().getContextClassLoader().getResourceAsStream(resourceName)) {
            if (in == null) throw new IllegalStateException("Missing test resource: " + resourceName);
            return MAPPER.readValue(in, new TypeReference<List<RagEvalCase>>() {});
        }
    }

    private static void dumpTop(List<SearchMatch> matches, String id, int n) {
        System.out.println("\n=== RAG CASE: " + id + " ===");
        for (int i = 0; i < Math.min(n, matches.size()); i++) {
            SearchMatch m = matches.get(i);
            Object rp = m.metadata().get("repo_path");
            System.out.printf(Locale.ROOT, "%02d score=%.4f repo_path=%s%n", i + 1, m.score(), String.valueOf(rp));
        }
    }

    private static String sample(List<String> paths) {
        return paths.stream().limit(5).toList().toString();
    }

    private static String topScores(List<SearchMatch> matches, int n) {
        return matches.stream().limit(n)
                .map(m -> String.format(Locale.ROOT, "%.4f", m.score()))
                .toList()
                .toString();
    }
}
