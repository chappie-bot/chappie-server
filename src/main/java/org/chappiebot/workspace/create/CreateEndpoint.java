package org.chappiebot.workspace.create;

import org.chappiebot.workspace.WorkspaceOutput;
import org.chappiebot.workspace.WorkspaceInput;
import jakarta.inject.Inject;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;

/**
 * The Endpoint for workspace content creation
 * @author Phillip Kruger (phillip.kruger@gmail.com)
 */
@Path("/api/workspaceCreate")
public class CreateEndpoint {
    
    @Inject
    CreateAssistant createAssistant;
    
    @POST
    public WorkspaceOutput workspaceCreate(WorkspaceInput input) {
        
        System.out.println("CREATE INPUT = " + input);
        
        return createAssistant.create(input.genericInput().programmingLanguage(),
                input.genericInput().programmingLanguageVersion(), 
                input.genericInput().product(), 
                input.genericInput().productVersion(), 
                input.content(), 
                input.genericInput().getSystemMessage(), 
                input.genericInput().getUserMessage());
    }
    
}
