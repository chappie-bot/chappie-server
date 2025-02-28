package org.chappiebot.workspace.update;

import jakarta.inject.Inject;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import org.chappiebot.workspace.WorkspaceInput;
import org.chappiebot.workspace.WorkspaceOutput;

/**
 * The Endpoint for workspace content update
 * @author Phillip Kruger (phillip.kruger@gmail.com)
 */
@Path("/api/workspaceUpdate")
public class UpdateEndpoint {
    
    @Inject
    UpdateAssistant updateAssistant;
    
    @POST
    public WorkspaceOutput workspaceUpdate(WorkspaceInput input) {
        return  updateAssistant.update(input.genericInput().programmingLanguage(),
                input.genericInput().programmingLanguageVersion(), 
                input.genericInput().product(), 
                input.genericInput().productVersion(), 
                input.content(), 
                input.genericInput().getSystemMessage(), 
                input.genericInput().getUserMessage());
    }
    
}
