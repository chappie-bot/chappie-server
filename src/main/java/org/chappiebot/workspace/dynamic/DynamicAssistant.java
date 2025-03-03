package org.chappiebot.workspace.dynamic;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

// TODO: Allow content here....
public interface DynamicAssistant {

    static final String SYSTEM_MESSAGE = """
                You are an AI assistant assisting in {{programmingLanguage}} {{programmingLanguageVersion}} code from a {{product}} {{productVersion}} application.
                                         
                {{systemmessage}}
            """;
    
    static final String USER_MESSAGE = """
                    {{usermessage}} 
                """;
    
    @SystemMessage(SYSTEM_MESSAGE)
    @UserMessage(USER_MESSAGE)
    public DynamicOutput dynamic(@V("programmingLanguage")String programmingLanguage, 
                        @V("programmingLanguageVersion")String programmingLanguageVersion, 
                        @V("product")String product, 
                        @V("productVersion")String version, 
                        @V("systemmessage")String systemmessage, 
                        @V("usermessage")String usermessage);
    
}
