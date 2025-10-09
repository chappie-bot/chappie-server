package org.chappiebot.assist;

import java.nio.file.Path;
import java.util.List;
import org.chappiebot.ContentIO;
import org.chappiebot.GenericInput;

public record AssistInput(GenericInput genericInput, List<Path> paths, String responseSchemaPrompt){
    
    public String content() {
        return ContentIO.toContent(paths.toArray(Path[]::new));
    }
}
