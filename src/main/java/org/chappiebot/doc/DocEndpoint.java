package org.chappiebot.doc;

import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import jakarta.inject.Inject;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;

/**
 * The Endpoint for doc creation
 * @author Phillip Kruger (phillip.kruger@gmail.com)
 */
@Path("/api/doc")
public class DocEndpoint {
    
    @Inject
    DocAssistant docAssistant;
    
    @POST
    public Uni<DocOutput> addDoc(DocInput docInput) {
        return Uni.createFrom().item(() -> docAssistant.addDoc(docInput.commonInput().programmingLanguage(),
                docInput.commonInput().programmingLanguageVersion(), 
                docInput.commonInput().product(), 
                docInput.commonInput().productVersion(), 
                docInput.extraContext().orElse(""), 
                docInput.doc(), docInput.source()))
            .runSubscriptionOn(Infrastructure.getDefaultWorkerPool());
    }
    
}
