package org.chappiebot.exception;

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
    public ExceptionOutput exception(ExceptionInput exceptionInput) {
        return exceptionAssistant.exception(
                    exceptionInput.genericInput().programmingLanguage(), 
                    exceptionInput.genericInput().programmingLanguageVersion(), 
                    exceptionInput.genericInput().quarkusVersion(), 
                    exceptionInput.stacktrace(), 
                    exceptionInput.path().toString(), 
                    exceptionInput.content(),
                    exceptionInput.genericInput().getSystemMessage(),
                    exceptionInput.genericInput().getUserMessage());
    }
}
