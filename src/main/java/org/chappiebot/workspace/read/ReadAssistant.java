package org.chappiebot.workspace.read;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import org.chappiebot.workspace.WorkspaceOutput;

public interface ReadAssistant {

    static final String SYSTEM_MESSAGE = """
                You are an AI assistant assisting in a user's {{programmingLanguage}} {{programmingLanguageVersion}} project using {{product}} {{productVersion}}.
                You will receive content that needs to be interpreted . You will receive the path and the content, consider that when creating a response. 
                Also consider the file type of the provided content as it might not be {{programmingLanguage}}, but just part of a {{programmingLanguage}} project. 
                Example: You might receive HTML that is part of a Java project. Then interpret it as HTML (not Java)

                Approach this task step-by-step, take your time and do not skip steps.
               
                Respond with an interpretation in markdown format, but make sure this markdown in encoded such that it can be added to a json file. This response must be valid markdown. Only include the markdown content, no explanation or other text. 
                
                You must not wrap markdown content in backticks, or in any other way, but return it as plain markdown encoded for json. If the interpretation contains code, make sure to use the markdown format to display the code properly.
                
                The markdown content must be returned per path (the path will be provided in the provided content).
                                           
                {{systemmessage}}
            """;
    
    static final String USER_MESSAGE = """
        {{usermessage}}
                                       
        Here are the content:
        {{content}}
        """;
    
    @SystemMessage(SYSTEM_MESSAGE)
    @UserMessage(USER_MESSAGE)
    public WorkspaceOutput read(@V("programmingLanguage")String programmingLanguage, 
                        @V("programmingLanguageVersion")String programmingLanguageVersion, 
                        @V("product")String product, 
                        @V("productVersion")String version,
                        @V("content")String content,
                        @V("systemmessage")String systemmessage, 
                        @V("usermessage")String usermessage);
    
}

