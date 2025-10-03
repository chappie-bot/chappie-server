package org.chappiebot.assist;

import jakarta.inject.Inject;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Response;
import java.util.Map;
import java.util.UUID;
import org.chappiebot.rag.RagRequestContext;
import org.chappiebot.store.StoreManager;

/**
 * The Endpoint for dynamic queries
 * @author Phillip Kruger (phillip.kruger@gmail.com)
 */
@Path("/api/assist")
public class AssistantEndpoint {
    
    @Inject
    Assistant dynamicAssistant;
    
    @Inject 
    RagRequestContext ragRequestContext;
    
    @Inject
    StoreManager storeManager;
    
    @POST
    public Response assist(AssistInput input,
            @HeaderParam(HEADER_MEMORY_ID) String memoryId) {
        
            if(memoryId==null || memoryId.isBlank()){
                memoryId = UUID.randomUUID().toString();
            }
        
            ragRequestContext.setVariables(input.genericInput().variables());
            
            Map<String,Object> r = dynamicAssistant.assist(input.genericInput().programmingLanguage(),
                        input.genericInput().programmingLanguageVersion(),
                        input.genericInput().quarkusVersion(),
                        input.genericInput().getSystemMessage(), 
                        input.genericInput().getUserMessage(),
                        memoryId);
            
            if(r.containsKey(NICE_NAME)){
                String niceName = String.valueOf(r.get(NICE_NAME));
                if(storeManager.getJdbcChatMemoryStore().isPresent() && niceName!=null && !niceName.isBlank()){
                    storeManager.getJdbcChatMemoryStore().get().setNiceName(memoryId, niceName);
                }
            }
            
            return Response
                    .ok(r)
                    .header(HEADER_MEMORY_ID, memoryId)
                    .build();
    }
    
    private static final String NICE_NAME = "nice_name";
    private static final String HEADER_MEMORY_ID = "X-Chappie-MemoryId";
}