package org.chappiebot.exception;

import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import jakarta.inject.Inject;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;

/**
 * The Endpoint for exceptions
 * @author Phillip Kruger (phillip.kruger@gmail.com)
 */
@Path("/api/exception")
public class ExceptionEndpoint {
    
    @Inject ExceptionAssistant exceptionAssistant;

    @POST
    public Uni<ExceptionOutput> suggestfix(ExceptionInput exceptionInput) {
        
        return Uni.createFrom().item(() -> exceptionAssistant.suggestFix(
                    exceptionInput.commonInput().programmingLanguage(), 
                    exceptionInput.commonInput().programmingLanguageVersion(), 
                    exceptionInput.commonInput().product(), 
                    exceptionInput.commonInput().productVersion(), 
                    exceptionInput.extraContext().orElse(""), 
                    exceptionInput.stacktrace(), 
                    exceptionInput.source()))
                .runSubscriptionOn(Infrastructure.getDefaultWorkerPool());
    }
}
