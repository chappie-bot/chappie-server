package org.chappiebot.assist;

import jakarta.inject.Inject;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Response;
import java.util.UUID;
import org.chappiebot.rag.RagRequestContext;

/**
 * The Endpoint for dynamic queries
 * @author Phillip Kruger (phillip.kruger@gmail.com)
 */
@Path("/api/assist")
public class Endpoint {
    
    @Inject
    Assistant dynamicAssistant;
    
    @Inject 
    RagRequestContext ragRequestContext;
    
    @POST
    public Response assist(AssistInput input,
            @HeaderParam(HEADER_MEMORY_ID) String memoryId) {
        
            if(memoryId==null || memoryId.isBlank())memoryId = UUID.randomUUID().toString();
        
            ragRequestContext.setVariables(input.genericInput().variables());
            
            return Response
                    .ok(dynamicAssistant.assist(input.genericInput().programmingLanguage(),
                        input.genericInput().programmingLanguageVersion(),
                        input.genericInput().quarkusVersion(),
                        input.genericInput().getSystemMessage(), 
                        input.genericInput().getUserMessage(),
                        memoryId))
                    .header(HEADER_MEMORY_ID, memoryId)
                    .build();
    }
    
    private static final String HEADER_MEMORY_ID = "X-Chappie-MemoryId";
}