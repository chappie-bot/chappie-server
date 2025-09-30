package org.chappiebot.search;

import io.quarkus.logging.Log;
import jakarta.inject.Inject;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import org.chappiebot.rag.RetrievalProvider;

import java.util.List;
import java.util.Objects;


@Path("/api/search")
public class SearchEndpoint {

    @Inject
    RetrievalProvider retrievalProvider;

    @POST
    public SearchResponse search(SearchRequest query) {
        Log.info("Search request: " + query.queryMessage());
        String queryMessage = query.queryMessage();
        int maxResults = Objects.requireNonNullElse(query.maxResults(), retrievalProvider.getRagMaxResults());
        String restrictToExtension = query.extension();

        List<SearchMatch> search = retrievalProvider.search(queryMessage, maxResults, restrictToExtension);
        return new SearchResponse(search);
    }

}
