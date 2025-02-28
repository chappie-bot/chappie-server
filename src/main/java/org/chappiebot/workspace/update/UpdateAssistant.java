package org.chappiebot.workspace.update;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import org.chappiebot.workspace.WorkspaceOutput;

public interface UpdateAssistant {

    static final String SYSTEM_MESSAGE = """
                You are an AI assistant assisting in {{programmingLanguage}} {{programmingLanguageVersion}} code from a {{product}} {{productVersion}} application.
                You will receive content that needs to be manipulated. Use the content received as input when considering the response. 
                Also consider the path of the content to determine the file type of the provided content.

                Approach this task step-by-step, take your time and do not skip steps.
               
                Respond with the manipulated content. This response must be valid. Only include the manipulated content, no explanation or other text. 
                
                You must not wrap manipulated content in backticks, markdown, or in any other way, but return it as plain text.
                
                The updated content must be returned per path (the path will be provided in the provided content).  
                                         
                {{systemmessage}}
            """;
        
    static final String USER_MESSAGE = """
        I have the following content in this {{product}} project:
                    ```
                    {{content}}
                    ```
                    
                    {{usermessage}} 
                """;
    
    @SystemMessage(SYSTEM_MESSAGE)
    @UserMessage(USER_MESSAGE)
    public WorkspaceOutput update(@V("programmingLanguage")String programmingLanguage, 
                        @V("programmingLanguageVersion")String programmingLanguageVersion, 
                        @V("product")String product, 
                        @V("productVersion")String version,
                        @V("content")String content,
                        @V("systemmessage")String systemmessage, 
                        @V("usermessage")String usermessage);
    
}
