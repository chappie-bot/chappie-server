package org.chappiebot.exception;

import java.nio.file.Path;
import org.chappiebot.ContentIO;
import org.chappiebot.GenericInput;

public record ExceptionInput(GenericInput genericInput,
                        String stacktrace,
                        Path path){

    public String content() {
        return ContentIO.toContent(path);
    }
}
