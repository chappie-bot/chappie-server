package org.chappiebot;

import java.time.Duration;
import java.util.Optional;

import org.chappiebot.assist.Assistant;
import org.chappiebot.exception.ExceptionAssistant;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.service.AiServices;
import io.quarkus.logging.Log;
import io.quarkus.runtime.Startup;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;

import jakarta.inject.Inject;
import dev.langchain4j.rag.RetrievalAugmentor;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.bgesmallenv15q.BgeSmallEnV15QuantizedEmbeddingModel;
import dev.langchain4j.store.embedding.filter.comparison.ContainsString;
import dev.langchain4j.rag.DefaultRetrievalAugmentor;
import dev.langchain4j.rag.query.Query;
import dev.langchain4j.store.embedding.filter.Filter;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import java.util.Map;
import org.chappiebot.rag.RagRequestContext;
import org.chappiebot.store.StoreCreator;

/**
 * The Chappie Server
 *
 * @author Phillip Kruger (phillip.kruger@gmail.com)
 */
@Singleton
@Startup
public class ChappieService {

    private ChatModel chatModel;

    @ConfigProperty(name = "chappie.log.request", defaultValue = "true")
    boolean logRequest;

    @ConfigProperty(name = "chappie.log.response", defaultValue = "true")
    boolean logResponse;

    @ConfigProperty(name = "chappie.timeout", defaultValue = "PT120S")
    Duration timeout;

    @ConfigProperty(name = "chappie.temperature", defaultValue = "0.2")
    double temperature;
    
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

    // RAG

    @ConfigProperty(name = "chappie.rag.enabled", defaultValue = "true")
    boolean ragEnabled;
    
    @ConfigProperty(name = "chappie.rag.results.max", defaultValue = "4")
    int ragMaxResults;
    
    // Store
    
    @ConfigProperty(name = "chappie.store.messages.max", defaultValue = "30")
    int maxMessages;
    
    @Inject
    StoreCreator storeCreator;

    @Inject
    ChatMemoryStore chatMemoryStore;
    
    @Inject 
    RagRequestContext ragRequestContext;
    
    private RetrievalAugmentor retrievalAugmentor;
    
    @PostConstruct
    public void init() {
        if (openaiKey.isPresent() || openaiBaseUrl.isPresent()) {
            loadOpenAiModel();
        } else {
            loadOllamaModel();
        }
        enableRagIfPossible();
    }

    private void loadOpenAiModel() {

        openaiBaseUrl.ifPresentOrElse(
                burl -> Log.info("CHAPPiE is using OpenAI " + openAiModelName + " (" + burl + ")"),
                () -> Log.info("CHAPPiE is using OpenAI " + openAiModelName)
        );

        Log.info("CHAPPiE timeout set to " + timeout);
        Log.info("CHAPPiE temperature set to " + temperature);
        if(openaiKey.isEmpty())Log.warn("CHAPPiE is using the default 'demo' api key");
        
        OpenAiChatModel.OpenAiChatModelBuilder builder = OpenAiChatModel.builder()
                .logRequests(logRequest)
                .logResponses(logResponse)
                .apiKey(openaiKey.orElse("demo"))
                .modelName(openAiModelName)
                .timeout(timeout)
                .temperature(temperature)
                .responseFormat("json_object");
        
        if (openaiBaseUrl.isPresent()) {
            builder = builder.baseUrl(openaiBaseUrl.get());
        }

        // TODO: Tune the other setting ?
        this.chatModel = builder.build();
    }

    private void loadOllamaModel() {
        Log.info("CHAPPiE is using Ollama " + ollamaModelName + "(" + ollamaBaseUrl + ")");
        Log.info("CHAPPiE timeout set to " + timeout);
        Log.info("CHAPPiE temperature set to " + temperature);
        
        OllamaChatModel.OllamaChatModelBuilder builder = OllamaChatModel.builder()
                .logRequests(logRequest)
                .logResponses(logResponse)
                .baseUrl(ollamaBaseUrl)
                .modelName(ollamaModelName)
                .timeout(timeout)
                .temperature(temperature)
                .responseFormat(ResponseFormat.JSON);
        
        this.chatModel = builder.build();
    }

    @Produces
    public ExceptionAssistant getExceptionAssistant() {
        if (retrievalAugmentor != null) {
            return AiServices.builder(ExceptionAssistant.class)
                    .chatModel(chatModel)
                    .retrievalAugmentor(retrievalAugmentor)
                    .build();
        }
        return AiServices.create(ExceptionAssistant.class, chatModel);
    }
    
    @Produces
    public Assistant getAssistant() {
        
        AiServices<Assistant> assistantBuilder = AiServices.builder(Assistant.class)
                .chatModel(chatModel)
                .chatMemoryProvider(chatMemoryProvider());
        
        if (retrievalAugmentor != null) {
            assistantBuilder.retrievalAugmentor(retrievalAugmentor);
        }
        return assistantBuilder.build();
    }

    private void enableRagIfPossible() {
        if (storeCreator.getStore().isEmpty()) {
            Log.info("CHAPPiE RAG not available; continuing without RAG.");
            return;
        }
        
        // TODO: This should use some local emmeding model
        if (openaiKey.isEmpty() && openaiBaseUrl.isEmpty()) {
            Log.warn("CHAPPiE RAG available but no OpenAI configuration for embeddings; continuing without RAG.");
            return;
        }

        if(!ragEnabled) {
            Log.warn("CHAPPiE RAG disabled by the user");
            return;
        }
        
        EmbeddingModel embeddingModel = new BgeSmallEnV15QuantizedEmbeddingModel();
        
        var retriever = EmbeddingStoreContentRetriever.builder()
                .embeddingStore(storeCreator.getStore().get())
                .embeddingModel(embeddingModel)
                .maxResults(ragMaxResults)
                .dynamicFilter((t) -> {
                    Map<String, String> variables = ragRequestContext.getVariables();
                    if(variables!=null && !variables.isEmpty() && variables.containsKey("extension")){
                        String extension = variables.get("extension");
                        if(extension!=null && !extension.equalsIgnoreCase("any")){
                            Log.info("Narrowing to [" + extension + "]");
                            return new ContainsString("extensions_csv_padded", "," + extension + ",");
                        }
                    }
                    return null;
                })
                .build();

        this.retrievalAugmentor = DefaultRetrievalAugmentor.builder()
            .contentRetriever(retriever)
            .build();
        
        Log.info("CHAPPiE RAG is enabled with " + ragMaxResults + " max results");
    }
    
    private ChatMemoryProvider chatMemoryProvider() {
        Log.info("CHAPPiE Chat Memory is enabled with " + maxMessages + " max messages");
        return memoryId -> MessageWindowChatMemory.builder()
            .id(memoryId)
            .maxMessages(maxMessages)
            .chatMemoryStore(chatMemoryStore)    
            .build();
    }
    
    private Filter buildExtensionOnlyFilter(Query q) {
        Log.info(">>>>>>>>> text = " + q.text());
        Log.info(">>>>>>>>> q.metadata() = " + q.metadata());
        return null;
    }
}
