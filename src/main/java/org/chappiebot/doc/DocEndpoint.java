package org.chappiebot.doc;

import io.quarkiverse.jsonrpc.runtime.api.JsonRPCApi;
import jakarta.inject.Inject;

/**
 * The JsonRPC Endpoint for doc creation
 * @author Phillip Kruger (phillip.kruger@gmail.com)
 */
@JsonRPCApi("doc")
public class DocEndpoint {
    
    @Inject DocAssistant docAssistant;

    public String addDoc(String programmingLanguage, 
                                    String product, 
                                    String version,
                                    String extraContext,
                                    String doc,
                                    String source) {
        
        return docAssistant.addDoc(programmingLanguage, product, version, extraContext, doc, source);
    }
}
