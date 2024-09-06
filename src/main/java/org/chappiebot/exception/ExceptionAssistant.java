package org.chappiebot.exception;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

public interface ExceptionAssistant {

    static final String SYSTEM_MESSAGE = """
                You are an AI assistant helping to debug {{programmingLanguage}} exceptions in a {{product}} {{version}} application.
                You will receive the exception stacktrace and the relevant source that caused the exception.

                Approach this task step-by-step, take your time and do not skip steps.
               
                Respond with a json file, and only a json file, that is valid, and can be parsed in {{programmingLanguage}}.
                
                You must respond in a valid JSON format.
                You must not wrap JSON response in backticks, markdown, or in any other way, but return it as plain text.
                The json response must contain the following fields:
                    - response (your reply)
                    - explanation
                    - diff (between source and suggested source, to show the changes)
                    - suggestedSource (this must be the full source code as provided, so do not truncate the source, with the error fixed. Note that this suggested source should compile, and should not include any diff plus or minus in the code)
               
               JSON Structure:
               {
                   'response': 'String',
                   'explanation': 'String',
                   'diff': 'String',
                   'suggestedSource: 'String'
               }
               
            """;
    
    @SystemMessage(SYSTEM_MESSAGE)
    @UserMessage("""
                I have the following {{programmingLanguage}} exception:
                ```
                {{stacktrace}}
                ```
                That comes from this code:
                ```
                {{source}}
                ```
                
                {{extraContext}}
                 
                Please help me fix it.
            """)
    public SuggestedFix suggestFix(@V("programmingLanguage")String programmingLanguage, 
                                    @V("product")String product, 
                                    @V("version")String version, 
                                    @V("extraContext")String extraContext, 
                                    @V("stacktrace")String stacktrace, 
                                    @V("source")String source);
    
    @SystemMessage(SYSTEM_MESSAGE)
    @UserMessage("""
                I have the following {{programmingLanguage}} exception:
                ```
                {{stacktrace}}
                ```
                
                {{extraContext}}
                 
                Please help me fix it.
            """)
    public SuggestedFix suggestFix(@V("programmingLanguage")String programmingLanguage, 
                                    @V("product")String product, 
                                    @V("version")String version, 
                                    @V("extraContext")String extraContext, 
                                    @V("stacktrace")String stacktrace);
    
}
