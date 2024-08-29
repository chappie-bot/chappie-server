package org.chappiebot.doc;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;

@RegisterAiService
@SystemMessage("""
                You are an AI assistant helping to add or modify {doc} in {programmingLanguage} code from a {product} {version} application.
                You will receive the code that needs the {doc}. Please use that as input when considering the response.

                Approach this task step-by-step, take your time and do not skip steps.
               
                Respond with the original input source code, but now enhance with the {doc} on class and method levels. 
                This response must be valid {programmingLanguage}. Only include the {programmingLanguage} code, no explanation or other text. 
                
                You must not wrap {programmingLanguage} response in backticks, markdown, or in any other way, but return it as plain text.
                
            """)
public interface DocAssistant {
    
    @UserMessage("""
                I have the following {programmingLanguage} class:
                ```
                {source}
                ```
                
                {extraContext}
                 
                Please add or modify the {doc} to reflect the code.
            """)
    public String addDoc(String programmingLanguage, String product, String version, String extraContext, String doc, String source);
    
}