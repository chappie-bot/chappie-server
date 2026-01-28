package org.chappiebot.rag;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.common.ResourceArg;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.chappiebot.search.SearchMatch;
import org.junit.jupiter.api.Test;

import java.util.List;

@QuarkusTest
@QuarkusTestResource(
    value = RagImageDbResource.class,
    initArgs = {
        @ResourceArg(name = "image", value = "ghcr.io/quarkusio/chappie-ingestion-quarkus:3.30"),
        @ResourceArg(name = "dim", value = "384")
    }
)
public class InvestigateDoclingTest {

    @Inject RetrievalProvider retrievalProvider;

    @Test
    void investigate_graphql() {
        System.out.println("\n=== INVESTIGATING: GraphQL ===");
        testQuery("graphql", 20);
        testQuery("smallrye-graphql", 20);
        testQuery("GraphQL endpoint", 20);
        testQuery("create GraphQL endpoint Quarkus", 20);
    }

    @Test
    void investigate_lifecycle() {
        System.out.println("\n=== INVESTIGATING: Lifecycle ===");
        testQuery("lifecycle", 20);
        testQuery("startup", 20);
        testQuery("application startup", 20);
        testQuery("run code on startup Quarkus", 20);
    }

    @Test
    void investigate_rest_client() {
        System.out.println("\n=== INVESTIGATING: REST Client ===");
        testQuery("rest-client", 20);
        testQuery("REST client", 20);
        testQuery("@RegisterRestClient", 20);
        testQuery("create REST client Quarkus", 20);
    }

    private void testQuery(String query, int limit) {
        System.out.println("\nQuery: \"" + query + "\"");
        List<SearchMatch> results = retrievalProvider.search(query, limit, null);
        
        int found = 0;
        for (int i = 0; i < Math.min(5, results.size()); i++) {
            SearchMatch m = results.get(i);
            Object repoPath = m.metadata().get("repo_path");
            String path = String.valueOf(repoPath);
            
            System.out.printf("  %02d. score=%.4f %s\n", i+1, m.score(), path);
            
            // Check if we found the right doc
            if (path.contains("graphql") || path.contains("lifecycle") || path.contains("rest-client")) {
                found++;
            }
        }
        
        if (found == 0) {
            System.out.println("  ⚠️ TARGET DOCUMENT NOT IN TOP 5!");
        }
    }
}
