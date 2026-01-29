package org.chappiebot.rag;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.common.ResourceArg;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.chappiebot.search.SearchMatch;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;

@QuarkusTest
@QuarkusTestResource(
    value = RagImageDbResource.class,
    initArgs = {
        @ResourceArg(name = "image", value = "ghcr.io/quarkusio/chappie-ingestion-quarkus:3.31.1"),
        @ResourceArg(name = "dim", value = "384")
    }
)
public class InvestigateFailuresTest {

    @Inject
    RetrievalProvider retrievalProvider;

    @Test
    void investigate_dev_mode() {
        System.out.println("\n=== DEV-MODE INVESTIGATION ===");
        String query = "What is Quarkus Dev Mode and how does it work?";
        testQuery(query, 20);

        // Try variations
        System.out.println("\n--- Trying shorter query ---");
        testQuery("Quarkus Dev Mode", 20);

        System.out.println("\n--- Trying exact match ---");
        testQuery("dev mode continuous testing", 20);
    }

    @Test
    void investigate_application_lifecycle() {
        System.out.println("\n=== APPLICATION-LIFECYCLE INVESTIGATION ===");
        String query = "How do I run code on application startup in Quarkus?";
        testQuery(query, 20);

        // Try variations
        System.out.println("\n--- Trying keywords ---");
        testQuery("application startup lifecycle", 20);

        System.out.println("\n--- Trying @Startup ---");
        testQuery("@Startup annotation Quarkus", 20);
    }

    @Test
    void investigate_cdi_injection() {
        System.out.println("\n=== CDI-INJECTION INVESTIGATION ===");
        String query = "How do I inject beans using CDI in Quarkus?";
        testQuery(query, 20);

        // Try variations
        System.out.println("\n--- Trying CDI keywords ---");
        testQuery("CDI dependency injection @Inject", 20);

        System.out.println("\n--- Trying just CDI ---");
        testQuery("CDI", 20);
    }

    @Test
    void check_if_docs_exist() {
        System.out.println("\n=== CHECKING IF EXPECTED DOCS EXIST ===");

        System.out.println("\n--- Searching for 'dev-mode' ---");
        testQuery("dev-mode", 20);

        System.out.println("\n--- Searching for 'lifecycle' ---");
        testQuery("lifecycle", 20);

        System.out.println("\n--- Searching for 'cdi' ---");
        testQuery("cdi", 20);

        System.out.println("\n--- Searching for 'continuous-testing' ---");
        testQuery("continuous-testing", 20);
    }

    private void testQuery(String query, int limit) {
        List<SearchMatch> results = retrievalProvider.search(query, limit, null);

        System.out.println("Query: \"" + query + "\"");
        System.out.println("Results (" + results.size() + "):");

        for (int i = 0; i < Math.min(limit, results.size()); i++) {
            SearchMatch m = results.get(i);
            Object repoPath = m.metadata().get("repo_path");
            Object title = m.metadata().get("title");
            double score = m.score();

            System.out.printf(Locale.ROOT, "%02d score=%.4f repo_path=%-55s title=%s%n",
                i + 1, score, String.valueOf(repoPath), String.valueOf(title));
        }
        System.out.println();
    }
}
