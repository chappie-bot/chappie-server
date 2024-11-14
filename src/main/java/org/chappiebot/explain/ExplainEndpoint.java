package org.chappiebot.explain;

import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import jakarta.inject.Inject;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;

/**
 * The Endpoint for explanation
 * @author Phillip Kruger (phillip.kruger@gmail.com)
 */
@Path("/api/explanation")
public class ExplainEndpoint {
    
    @Inject ExplainAssistant explainAssistant;

    @POST
    public Uni<ExplainOutput> explain(ExplainInput explainInput) {
        
        return Uni.createFrom().item(() -> explainAssistant.explain(
                explainInput.commonInput().programmingLanguage(), 
                explainInput.commonInput().programmingLanguageVersion(), 
                explainInput.commonInput().product(), 
                explainInput.commonInput().productVersion(), 
                explainInput.extraContext().orElse(""), 
                explainInput.source()))
            .runSubscriptionOn(Infrastructure.getDefaultWorkerPool());
    }
}
