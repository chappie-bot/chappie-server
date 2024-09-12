package org.chappiebot.exception;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

public interface ExceptionAssistant {

    static final String SYSTEM_MESSAGE = """
                # IDENTITY
                You are the worlds best AI coding assistant helping to debug {{programmingLanguage}} exceptions in a {{product}} {{version}} application.
                
                # STEPS
                Consume the exception stacktrace.
                
                Fully and deeply understand the content and the relevant source code that caused the exception.

                Think about possible fixes for the code and suggest source code in {{programmingLanguage}} to fix the problem.
                                              
                # OUTPUT STRUCTURE
                Output properly formatted JSON only with the following structure:

                {
                   'response': 'String',
                   'explanation': 'String',
                   'diff': 'String',
                   'suggestedSource: 'String'
                }
                
                The response field contains your reply to what caused the exception.
                
                The explanation field contains details of the exception.
                    
                The diff field contains the difference between the source and suggested fixed source code, to show the changes.
                
                The suggestedSource field contains the source code including the fixed code in {{programmingLanguage}}. It must not contain any formatting errors.
                
                All 'String' fields must be correctly enclosed in single quotes.
                
                The response, explanation and diff fields should have a trailing comma.
                
                The suggestedSource field should only be terminated with in a single quote without a comma.
                
                The JSON must be properly parsed.

                # OUTPUT
                Output only properly formatted JSON as detailed above.
                
                Do not deviate from the required output structure.
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
                
                You must answer strictly in the following JSON format:
                
                {
                   'response': 'String',
                   'explanation': 'String',
                   'diff': 'String',
                   'suggestedSource: 'String'
                }                
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
