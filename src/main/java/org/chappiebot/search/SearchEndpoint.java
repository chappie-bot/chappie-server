package org.chappiebot.search;

import io.quarkus.logging.Log;
import jakarta.inject.Inject;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import org.chappiebot.rag.RetrievalProvider;

import java.util.List;
import java.util.Objects;

import jakarta.ws.rs.core.Response;


@Path("/api/search")
public class SearchEndpoint {

    @Inject
    RetrievalProvider retrievalProvider;

    @POST
    public Response search(SearchRequest query) {
        if (query == null || query.queryMessage() == null || query.queryMessage().isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new SearchResponse(List.of()))
                    .build();
        }
        Log.info("Search request: " + query.queryMessage());
        String queryMessage = query.queryMessage();
        int maxResults = Objects.requireNonNullElse(query.maxResults(), retrievalProvider.getRagMaxResults());
        String restrictToExtension = query.extension();

        List<SearchMatch> search = retrievalProvider.search(queryMessage, maxResults, restrictToExtension);
        return Response.ok(new SearchResponse(search)).build();
    }

}
