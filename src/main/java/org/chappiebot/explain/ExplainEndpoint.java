package org.chappiebot.explain;

import dev.langchain4j.service.V;
import io.quarkiverse.jsonrpc.runtime.api.JsonRPCApi;
import jakarta.inject.Inject;
/**
 * The JsonRPC Endpoint for explanation
 * @author Phillip Kruger (phillip.kruger@gmail.com)
 */
@JsonRPCApi("explanation")
public class ExplainEndpoint {
    
    @Inject ExplainAssistant explainAssistant;

    public String explain(@V("programmingLanguage")String programmingLanguage, 
                            @V("product")String product, 
                            @V("version")String version,
                            @V("extraContext")String extraContext,
                            @V("source")String source) {
        
        return explainAssistant.explain(programmingLanguage, product, version, extraContext, source);
    }
}
