package org.chappiebot.explain;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;

@RegisterAiService
@SystemMessage("""
                You are an AI assistant helping to explain source code in {programmingLanguage} code from a {product} {version} application.
                You will receive the code that needs to be explained.

                Approach this task step-by-step, take your time and do not skip steps.
               
                Respond with valid markdown content, so that it can be rendered to the user. 
                
            """)
public interface ExplainAssistant {
    
    @UserMessage("""
                I have the following {programmingLanguage} class:
                ```
                {source}
                ```
                
                {extraContext}
                 
                Please explain it to me.
            """)
    public String explain(String programmingLanguage, String product, String version, String extraContext, String source);
    
}