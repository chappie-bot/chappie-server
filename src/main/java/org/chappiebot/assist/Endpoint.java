package org.chappiebot.assist;

import jakarta.inject.Inject;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import java.util.Map;

/**
 * The Endpoint for dynamic queries
 * @author Phillip Kruger (phillip.kruger@gmail.com)
 */
@Path("/api/assist")
public class Endpoint {
    
    @Inject
    Assistant dynamicAssistant;
    
    @POST
    public Map<String,Object> assist(AssistInput input) {
            return dynamicAssistant.assist(input.genericInput().programmingLanguage(),
                input.genericInput().programmingLanguageVersion(),
                input.genericInput().quarkusVersion(),
                input.genericInput().getSystemMessage(), 
                input.genericInput().getUserMessage());
    }
    
}