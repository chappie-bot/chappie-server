package org.chappiebot.explain;

import io.quarkiverse.jsonrpc.runtime.api.JsonRPCApi;
import jakarta.inject.Inject;
/**
 * The JsonRPC Endpoint for explanation
 * @author Phillip Kruger (phillip.kruger@gmail.com)
 */
@JsonRPCApi("explanation")
public class ExplainEndpoint {
    
    @Inject ExplainAssistant explainAssistant;

    public String explain(String programmingLanguage, 
                                    String product, 
                                    String version,
                                    String extraContext,
                                    String source) {
        
        return explainAssistant.explain(programmingLanguage, product, version, extraContext, source);
    }
}
