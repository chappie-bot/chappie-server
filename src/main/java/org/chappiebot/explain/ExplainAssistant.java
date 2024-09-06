package org.chappiebot.explain;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;


public interface ExplainAssistant {

    static final String SYSTEM_MESSAGE = """
                You are an AI assistant helping to explain source code in {{programmingLanguage}} code from a {{product}} {{version}} application.
                You will receive the code that needs to be explained.

                Approach this task step-by-step, take your time and do not skip steps.
               
                Respond with valid markdown content, so that it can be rendered to the user. 
                
            """;
    
    @SystemMessage(SYSTEM_MESSAGE)
    @UserMessage("""
                I have the following {{programmingLanguage}} class:
                ```
                {{source}}
                ```
                
                {{extraContext}}
                 
                Please explain it to me.
            """)
    public String explain(@V("programmingLanguage")String programmingLanguage, 
                            @V("product")String product, 
                            @V("version")String version, 
                            @V("extraContext")String extraContext, 
                            @V("source")String source);
    
}