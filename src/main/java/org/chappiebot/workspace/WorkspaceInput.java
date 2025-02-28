package org.chappiebot.workspace;

import java.nio.file.Path;
import java.util.List;
import org.chappiebot.ContentIO;
import org.chappiebot.GenericInput;

public record WorkspaceInput(GenericInput genericInput, List<Path> paths){
    
    public String content() {
        return ContentIO.toContent(paths.toArray(Path[]::new));
    }
}
