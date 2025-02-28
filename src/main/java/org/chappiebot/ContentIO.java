package org.chappiebot;

import java.io.IOException;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class ContentIO {

    private ContentIO(){}
    
    public static String toContent(Path ... path) {
        StringWriter sw = new StringWriter();
        
        for(Path p: path){
            sw.write(p.toString());
            sw.write(":");
            sw.write("\n```");
            sw.write(readContents(p));
            sw.write("```\n\n");
        }
        return sw.toString();
    }
    
    private static String readContents(Path filePath) {
        if (filePath != null && Files.exists(filePath)) {
            try {
                return Files.readString(filePath);
            } catch (IOException ex) {
                throw new UncheckedIOException(ex);
            }
        }
        throw new NullPointerException("filePath is null");
    }
    
}
