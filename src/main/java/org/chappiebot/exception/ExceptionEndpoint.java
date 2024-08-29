package org.chappiebot.exception;

import io.quarkiverse.jsonrpc.runtime.api.JsonRPCApi;
import jakarta.inject.Inject;
/**
 * The JsonRPC Endpoint for exceptions
 * @author Phillip Kruger (phillip.kruger@gmail.com)
 */
@JsonRPCApi("exception")
public class ExceptionEndpoint {
    
    @Inject ExceptionAssistant exceptionAssistant;

    public SuggestedFix suggestfix(String programmingLanguage, 
                                    String product, 
                                    String version,
                                    String extraContext,
                                    String stacktrace,
                                    String source) {
        
        return exceptionAssistant.suggestFix(programmingLanguage, product, version, extraContext, stacktrace, source);
    }
}
