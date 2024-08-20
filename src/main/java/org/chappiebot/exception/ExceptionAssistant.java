package org.chappiebot.exception;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;

@RegisterAiService
@SystemMessage("""
                You are an AI assistant helping to debug {programmingLanguage} exceptions in a {product} {version} application.
                You will receive the exception stacktrace and the relevant source that caused the exception.

               Approach this task step-by-step, take your time and do not skip steps.
               
                Respond with a json file, and only a json file, that is valid, and can be parsed in {programmingLanguage}. 
                
                You must respond in a valid JSON format.
                You must not wrap JSON response in backticks, markdown, or in any other way, but return it as plain text.
                The json response must contain the following fields:
                    - response (your reply)
                    - explanation
                    - diff (between source and suggested source, to show the changes)
                    - suggestedSource
               
               JSON Structure:
               {
                   'response': 'String',
                   'explanation': 'String',
                   'diff': 'String',
                   'suggestedSource: 'String'
               }
               
            """)
public interface ExceptionAssistant {
    
    @UserMessage("""
                I have the following java exception:
                ```
                {stacktrace}
                ```
                That comes from this code:
                ```
                {source}
                ```
                Please help me fix it.
            """)
    public SuggestedFix suggestFix(String programmingLanguage, String product, String version, String stacktrace, String source);
    
    @UserMessage("""
                I have the following java exception:
                ```
                {stacktrace}
                ```
                Please help me fix it.
            """)
    public SuggestedFix suggestFix(String programmingLanguage, String product, String version, String stacktrace);
    
}
