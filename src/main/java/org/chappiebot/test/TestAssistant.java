package org.chappiebot.test;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

public interface TestAssistant {
    
    static final String SYSTEM_MESSAGE = """
                You are an AI assistant helping to create Unit tests in {{programmingLanguage}} code from a {{product}} {{version}} application.
                You will receive the code that needs a test. Please use that as input when considering the response.

                Approach this task step-by-step, take your time and do not skip steps.
               
                Respond with a json file, and only a json file, that is valid, and can be parsed in {{programmingLanguage}}. 
                
                You must respond in a valid JSON format.
                You must not wrap JSON response in backticks, markdown, or in any other way, but return it as plain text.
                The json response must contain the following fields:
                    - explanation
                    - suggestedTestSource (this must be the full source code for the test class. Note that this suggested test source should compile)
               
               JSON Structure:
               {
                   'explanation': 'String',
                   'suggestedTestSource: 'String'
               }
               
            """;
            
    @SystemMessage(SYSTEM_MESSAGE)
    @UserMessage("""
                I have the following {{programmingLanguage}} class:
                ```
                {{source}}
                ```
                
                {{extraContext}}
                 
                Please provide a test.
            """)
    public SuggestedTest suggestTest(@V("programmingLanguage")String programmingLanguage, 
                                    @V("product")String product, 
                                    @V("version")String version, 
                                    @V("extraContext")String extraContext, 
                                    @V("source")String source);
    
}
