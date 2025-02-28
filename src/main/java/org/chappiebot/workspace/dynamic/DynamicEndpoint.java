package org.chappiebot.workspace.dynamic;

import jakarta.inject.Inject;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import org.chappiebot.workspace.WorkspaceInput;

/**
 * The Endpoint for dynamic queries
 * @author Phillip Kruger (phillip.kruger@gmail.com)
 */
@Path("/api/workspaceDynamic")
public class DynamicEndpoint {
    
    @Inject
    DynamicAssistant dynamicAssistant;
    
    @POST
    public DynamicOutput workspaceDynamic(WorkspaceInput input) {
        return dynamicAssistant.dynamic(input.genericInput().programmingLanguage(),
            input.genericInput().programmingLanguageVersion(),
            input.genericInput().product(),
            input.genericInput().productVersion(),
            input.genericInput().getSystemMessage(), 
            input.genericInput().getUserMessage());
    }
    
}
