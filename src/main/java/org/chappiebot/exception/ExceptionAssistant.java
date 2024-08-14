package org.chappiebot.exception;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;

@RegisterAiService
public interface ExceptionAssistant {

    static final String SYSTEM_MESSAGE = """
                You are an AI assistant helping to debug {programmingLanguage} exceptions in a {product} {version} application.
                You will receive the exception stacktrace and the relevant source that caused the exception.

                Approach this task step-by-step, take your time and do not skip steps.

                Respond with a json file, and only a json file, that is valid, and can be parsed in {programmingLanguage}. Make sure to not include the ``` as the start of the response. The json must contain the following fields from the ingested document:
                    - response (your reply)
                    - explanation (about the diff)
                    - diff (between source and suggested source, to show the changes)
                    - suggestedSource
            """;
    
    @SystemMessage(SYSTEM_MESSAGE)
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
    
    @SystemMessage(SYSTEM_MESSAGE)
    @UserMessage("""
                I have the following java exception:
                ```
                {stacktrace}
                ```
                Please help me fix it.
            """)
    public SuggestedFix suggestFix(String programmingLanguage, String product, String version, String stacktrace);
    
}
