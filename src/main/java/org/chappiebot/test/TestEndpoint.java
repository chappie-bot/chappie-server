package org.chappiebot.test;

import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import jakarta.inject.Inject;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;

/**
 * The Endpoint for test creation
 * @author Phillip Kruger (phillip.kruger@gmail.com)
 */
@Path("/api/testing")
public class TestEndpoint {
    
    @Inject TestAssistant testAssistant;

    @POST
    public Uni<TestOutput> suggesttest(TestInput testInput) {
        
        return Uni.createFrom().item(() -> testAssistant.suggestTest(
                testInput.commonInput().programmingLanguage(), 
                testInput.commonInput().programmingLanguageVersion(), 
                testInput.commonInput().product(), 
                testInput.commonInput().productVersion(), 
                testInput.extraContext().orElse(""), 
                testInput.source()))
            .runSubscriptionOn(Infrastructure.getDefaultWorkerPool());    
                        
    }
}
