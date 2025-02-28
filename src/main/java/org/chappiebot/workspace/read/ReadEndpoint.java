package org.chappiebot.workspace.read;

import io.quarkus.logging.Log;
import jakarta.inject.Inject;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import org.chappiebot.workspace.WorkspaceInput;
import org.chappiebot.workspace.WorkspaceOutput;

/**
 * The Endpoint for reading and interpreting content
 * @author Phillip Kruger (phillip.kruger@gmail.com)
 */
@Path("/api/workspaceRead")
public class ReadEndpoint {
    
    @Inject
    ReadAssistant readAssistant;
    
    @POST
    public WorkspaceOutput workspaceRead(WorkspaceInput input) {
        
        Log.info(input.genericInput().getUserMessage()  + "\n" + input.content());
        
        return readAssistant.read(input.genericInput().programmingLanguage(),
                input.genericInput().programmingLanguageVersion(), 
                input.genericInput().product(), 
                input.genericInput().productVersion(), 
                input.content(), 
                input.genericInput().getSystemMessage(), 
                input.genericInput().getUserMessage());
    }
    
}
