package org.chappiebot.test;

import io.quarkiverse.jsonrpc.runtime.api.JsonRPCApi;
import jakarta.inject.Inject;
/**
 * The JsonRPC Endpoint for test creation
 * @author Phillip Kruger (phillip.kruger@gmail.com)
 */
@JsonRPCApi("testing")
public class TestEndpoint {
    
    @Inject TestAssistant testAssistant;

    public SuggestedTest suggesttest(String programmingLanguage, 
                                    String product, 
                                    String version,
                                    String source) {
        
        return testAssistant.suggestTest(programmingLanguage, product, version, source);
    }
}
