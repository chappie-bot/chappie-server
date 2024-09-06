package org.chappiebot;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.service.AiServices;
import io.quarkus.logging.Log;
import io.quarkus.runtime.Startup;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;
import java.time.Duration;
import java.util.Optional;
import org.chappiebot.doc.DocAssistant;
import org.chappiebot.exception.ExceptionAssistant;
import org.chappiebot.explain.ExplainAssistant;
import org.chappiebot.test.TestAssistant;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * The Chappie Server
 * @author Phillip Kruger (phillip.kruger@gmail.com)
 */
@Singleton
@Startup
public class ChappieService {

    private ChatLanguageModel chatLanguageModel;
    
    @ConfigProperty(name = "chappie.log.request", defaultValue = "false")
    boolean logRequest;
    
    @ConfigProperty(name = "chappie.log.response", defaultValue = "false")
    boolean logResponse;
    
    // OpenAI
    
    @ConfigProperty(name = "chappie.openai.api-key") 
    Optional<String> openaiKey;
    
    @ConfigProperty(name = "chappie.openai.model-name", defaultValue = "gpt-4o-mini") 
    String openAiModelName;
    
    // Ollama
    
    @ConfigProperty(name = "chappie.ollama.base-url", defaultValue = "http://localhost:11434") 
    String baseUrl;
    
    @ConfigProperty(name = "chappie.ollama.model-name", defaultValue = "codellama")
    String ollamaModelName;
    
    @ConfigProperty(name = "chappie.ollama.timeout", defaultValue = "PT60S")
    String timeout;
    
    @PostConstruct
    public void init(){
        if(openaiKey.isPresent()){
            loadOpenAiModel();
        }else{
            loadOllamaModel();
        }
    }
    
    private void loadOpenAiModel(){
        Log.info("Using OpenAI " + openAiModelName);
        
        OpenAiChatModel.OpenAiChatModelBuilder builder = OpenAiChatModel.builder();
        builder = builder.logRequests(logRequest).logResponses(logResponse);
        builder = builder.apiKey(openaiKey.get());
        builder = builder.modelName(openAiModelName);
        // TODO: Tune the other setting ?
        this.chatLanguageModel = builder.build();
        
    }
    
    private void loadOllamaModel(){
        Log.info("Using Ollama (" + baseUrl + ") " + ollamaModelName);
        OllamaChatModel.OllamaChatModelBuilder builder = OllamaChatModel.builder();
        builder = builder.logRequests(logRequest).logResponses(logResponse);
        builder = builder.baseUrl(baseUrl);
        builder = builder.modelName(ollamaModelName);
        builder = builder.timeout(Duration.parse(timeout));
        // TODO: Tune the other setting ?
        this.chatLanguageModel = builder.build();
    }
    
    @Produces
    public DocAssistant getDocAssistant(){
        return AiServices.create(DocAssistant.class, chatLanguageModel);
    }
    
    @Produces
    public ExceptionAssistant getExceptionAssistant(){
        return AiServices.create(ExceptionAssistant.class, chatLanguageModel);
    }
    
    @Produces
    public ExplainAssistant getExplainAssistant(){
        return AiServices.create(ExplainAssistant.class, chatLanguageModel);
    }
    
    @Produces
    public TestAssistant getTestAssistant(){
        return AiServices.create(TestAssistant.class, chatLanguageModel);
    }
    

    
}
