package org.chappiebot;

import dev.langchain4j.mcp.McpToolProvider;
import dev.langchain4j.mcp.client.DefaultMcpClient;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.mcp.client.transport.McpTransport;
import dev.langchain4j.mcp.client.transport.http.StreamableHttpMcpTransport;
import dev.langchain4j.mcp.client.transport.stdio.StdioMcpTransport;

import java.time.Duration;
import java.util.Optional;

import org.chappiebot.assist.Assistant;
import org.chappiebot.rag.RetrievalProvider;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.request.DefaultChatRequestParameters;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.request.ToolChoice;
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
import dev.langchain4j.store.embedding.filter.comparison.ContainsString;
import jakarta.annotation.PreDestroy;

import java.util.List;
import java.util.Map;

import org.chappiebot.rag.RagRequestContext;
import org.chappiebot.store.StoreManager;

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

    @Inject
    RetrievalProvider retrievalProvider;

    // Store

    @ConfigProperty(name = "chappie.store.messages.max", defaultValue = "30")
    int maxMessages;


    @ConfigProperty(name = "quarkus.application.version")
    String appVersion;

    // MCP
    @ConfigProperty(name = "chappie.mcp.servers")
    Optional<List<String>> mcpServers;

    @Inject
    StoreManager storeManager;

    @Inject
    RagRequestContext ragRequestContext;

    private RetrievalAugmentor retrievalAugmentor;
    private final List<McpClient> mcpClients = new java.util.concurrent.CopyOnWriteArrayList<>();
    private McpToolProvider mcpToolProvider = null;

    private ChatRequestParameters chatRequestParameters = DefaultChatRequestParameters.builder()
            .toolChoice(ToolChoice.AUTO)
            .responseFormat(ResponseFormat.JSON)
            .build();

    @PostConstruct
    public void init() {
        if (openaiKey.isPresent() || openaiBaseUrl.isPresent()) {
            loadOpenAiModel();
        } else {
            loadOllamaModel();
        }
        enableRagIfPossible();
        enableMcpIfConfigured();
    }

    @PreDestroy
    void shutdown() {
        // Be nice and close transports/clients
        for (McpClient c : mcpClients) {
            try {
                c.close();
            } catch (Exception ignored) {
            }
        }
    }

    private void loadOpenAiModel() {

        openaiBaseUrl.ifPresentOrElse(
                burl -> Log.info("CHAPPiE is using OpenAI " + openAiModelName + " (" + burl + ")"),
                () -> Log.info("CHAPPiE is using OpenAI " + openAiModelName)
        );

        Log.info("CHAPPiE timeout set to " + timeout);
        Log.info("CHAPPiE temperature set to " + temperature);
        if (openaiKey.isEmpty()) Log.warn("CHAPPiE is using the default 'demo' api key");


        OpenAiChatModel.OpenAiChatModelBuilder builder = OpenAiChatModel.builder()
                .logRequests(logRequest)
                .logResponses(logResponse)
                .apiKey(openaiKey.orElse("demo"))
                .modelName(openAiModelName)
                .timeout(timeout)
                .temperature(temperature)
                .responseFormat("json_object");

        if (!mcpServers.isEmpty() && !mcpServers.get().isEmpty()) {
            builder = builder
                    .defaultRequestParameters(chatRequestParameters)
                    .parallelToolCalls(false);
        }

        if (openaiBaseUrl.isPresent()) {
            builder = builder.baseUrl(openaiBaseUrl.get());
        }

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

        if (!mcpServers.isEmpty() && !mcpServers.get().isEmpty()) {
            builder = builder
                    .defaultRequestParameters(chatRequestParameters);
        }

        this.chatModel = builder.build();
    }

    @Produces
    public Assistant getAssistant() {

        AiServices<Assistant> assistantBuilder = AiServices.builder(Assistant.class)
                .chatModel(chatModel)
                .chatMemoryProvider(chatMemoryProvider());

        if (retrievalAugmentor != null) {
            assistantBuilder.retrievalAugmentor(retrievalAugmentor);
        }
        if (mcpToolProvider != null) {
            assistantBuilder.toolProvider(mcpToolProvider);
        }
        return assistantBuilder.build();
    }

    private void enableRagIfPossible() {
        if (storeManager.getStore().isEmpty()) {
            Log.info("CHAPPiE RAG not available; continuing without RAG.");
            return;
        }

        this.retrievalAugmentor = retrievalProvider.getRetrievalAugmentor((t) -> {
            Map<String, String> variables = ragRequestContext.getVariables();
            if (variables != null && !variables.isEmpty() && variables.containsKey("extension")) {
                String extension = variables.get("extension");
                if (extension != null && !extension.equalsIgnoreCase("any")) {
                    Log.info("Narrowing to [" + extension + "]");
                    return new ContainsString("extensions_csv_padded", "," + extension + ",");
                }
            }
            return null;
        });
    }

    private void enableMcpIfConfigured() {
        if (mcpServers.isEmpty() || mcpServers.get().isEmpty()) {
            Log.info("CHAPPiE MCP: no servers configured; continuing without MCP.");
            return;
        }
        List<McpTransport> transports = new java.util.ArrayList<>();

        for (String raw : mcpServers.get()) {
            String s = raw.trim();
            try {
                if (s.startsWith("stdio:")) {
                    String cmd = s.substring("stdio:".length()).trim();
                    List<String> command = java.util.Arrays.asList(cmd.split("\\s+"));
                    McpTransport t = new StdioMcpTransport.Builder()
                            .command(command)
                            .logEvents(false)
                            .build();
                    transports.add(t);
                    Log.infof("CHAPPiE MCP: added stdio server: %s", command);
                } else if (s.startsWith("http://") || s.startsWith("https://")) {
                    McpTransport t = new StreamableHttpMcpTransport.Builder()
                            .url(s)
                            .logRequests(true)
                            .logResponses(false)
                            .build();
                    transports.add(t);
                    Log.infof("CHAPPiE MCP: added HTTP server: %s", s);
                } else {
                    Log.warnf("CHAPPiE MCP: unsupported server spec '%s' (use http(s)://… or stdio:…); skipping.", s);
                }
            } catch (Exception e) {
                Log.warnf("CHAPPiE MCP: failed to add server '%s': %s", s, e.getMessage());
            }
        }

        if (transports.isEmpty()) {
            Log.warn("CHAPPiE MCP: no valid transports created; continuing without MCP.");
            return;
        }

        List<McpClient> clients = new java.util.ArrayList<>();
        int idx = 0;
        for (McpTransport t : transports) {
            McpClient client = new DefaultMcpClient.Builder()
                    .key("chappie-mcp-" + (idx++))
                    .clientName("CHAPPiE")
                    .clientVersion(versionOr("dev"))
                    .transport(t)
                    .build();
            clients.add(client);
        }
        mcpClients.addAll(clients);

        this.mcpToolProvider = McpToolProvider.builder()
                .mcpClients(clients)
                //.resourcesAsToolsPresenter(McpResourcesAsToolsPresenter.basic())
                .build();

        Log.infof("CHAPPiE MCP: enabled with %d server(s).", clients.size());
    }


    private ChatMemoryProvider chatMemoryProvider() {
        Log.info("CHAPPiE Chat Memory is enabled with " + maxMessages + " max messages");
        return memoryId -> MessageWindowChatMemory.builder()
                .id(memoryId)
                .maxMessages(maxMessages)
                .chatMemoryStore(storeManager.getJdbcChatMemoryStore().orElse(null))
                .build();
    }

    private String versionOr(String fallback) {
        String v = appVersion;
        if (v != null && !v.isBlank()) return v;

        String impl = ChappieService.class.getPackage().getImplementationVersion();
        return (impl != null && !impl.isBlank()) ? impl : fallback;
    }
}
