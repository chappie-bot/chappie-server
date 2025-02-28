package org.chappiebot.workspace;

import java.util.Map;

// TODO: Once Jackson replaced Gson, change to Path
public record WorkspaceOutput(Map<String,String> pathAndContent){ 
}
