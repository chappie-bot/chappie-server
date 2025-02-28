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
import org.chappiebot.exception.ExceptionAssistant;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.time.Duration;
import java.util.Optional;
import org.chappiebot.workspace.dynamic.DynamicAssistant;
import org.chappiebot.workspace.create.CreateAssistant;
import org.chappiebot.workspace.update.UpdateAssistant;
import org.chappiebot.workspace.read.ReadAssistant;

/**
 * The Chappie Server
 *
 * @author Phillip Kruger (phillip.kruger@gmail.com)
 */
@Singleton
@Startup
public class ChappieService {

    private ChatLanguageModel chatLanguageModel;

    @ConfigProperty(name = "chappie.log.request", defaultValue = "true")
    boolean logRequest;

    @ConfigProperty(name = "chappie.log.response", defaultValue = "false")
    boolean logResponse;

    @ConfigProperty(name = "chappie.timeout")
    Optional<Duration> timeout;

    // OpenAI
    @ConfigProperty(name = "chappie.openai.api-key")
    Optional<String> openaiKey;

    @ConfigProperty(name = "chappie.openai.base-url")
    Optional<String> openaiBaseUrl;

    @ConfigProperty(name = "chappie.openai.model-name", defaultValue = "gpt-4o-mini")
    String openAiModelName;

    // Ollama

    @ConfigProperty(name = "chappie.ollama.base-url", defaultValue = "http://localhost:11434")
    String ollamaBaseUrl;

    @ConfigProperty(name = "chappie.ollama.model-name", defaultValue = "codellama")
    String ollamaModelName;

    @PostConstruct
    public void init() {
        if (openaiKey.isPresent() || openaiBaseUrl.isPresent()) {
            loadOpenAiModel();
        } else {
            loadOllamaModel();
        }
    }

    private void loadOpenAiModel() {

        openaiBaseUrl.ifPresentOrElse(
                burl -> Log.info("Using OpenAI (" + burl + ") "),
                () -> Log.info("Using OpenAI " + openAiModelName)
        );

        OpenAiChatModel.OpenAiChatModelBuilder builder = OpenAiChatModel.builder();
        builder = builder.logRequests(logRequest).logResponses(logResponse);
        if (openaiKey.isPresent()) {
            builder = builder.apiKey(openaiKey.get());
        } else {
            builder = builder.apiKey("demo");
        }
        if (openaiBaseUrl.isPresent()) {
            builder = builder.baseUrl(openaiBaseUrl.get());
        }

        builder = builder.modelName(openAiModelName);

        if (timeout.isPresent()) {
            builder = builder.timeout(timeout.get());
        }

        // more focused output
        builder.temperature(0.2);
        builder.responseFormat("json_object");

        // TODO: Tune the other setting ?
        this.chatLanguageModel = builder.build();
    }

    private void loadOllamaModel() {
        Log.info("Using Ollama (" + ollamaBaseUrl + ") " + ollamaModelName);
        OllamaChatModel.OllamaChatModelBuilder builder = OllamaChatModel.builder();
        builder = builder.logRequests(logRequest).logResponses(logResponse);
        builder = builder.baseUrl(ollamaBaseUrl);
        builder = builder.modelName(ollamaModelName);
        if (timeout.isPresent()) {
            builder = builder.timeout(timeout.get());
        }
        // TODO: Tune the other setting ?
        this.chatLanguageModel = builder.build();
    }

    @Produces
    public UpdateAssistant getManipulationAssistant() {
        return AiServices.create(UpdateAssistant.class, chatLanguageModel);
    }

    @Produces
    public CreateAssistant getGenerationAssistant() {
        return AiServices.create(CreateAssistant.class, chatLanguageModel);
    }
    
    @Produces
    public ReadAssistant getInterpretationAssistant() {
        return AiServices.create(ReadAssistant.class, chatLanguageModel);
    }
    
    @Produces
    public ExceptionAssistant getExceptionAssistant() {
        return AiServices.create(ExceptionAssistant.class, chatLanguageModel);
    }
    
    @Produces
    public DynamicAssistant getDynamicAssistant() {
        return AiServices.create(DynamicAssistant.class, chatLanguageModel);
    }
}
