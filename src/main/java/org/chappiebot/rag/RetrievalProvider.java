package org.chappiebot.rag;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageType;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.bgesmallenv15q.BgeSmallEnV15QuantizedEmbeddingModel;
import dev.langchain4j.rag.DefaultRetrievalAugmentor;
import dev.langchain4j.rag.RetrievalAugmentor;
import dev.langchain4j.rag.content.injector.ContentInjector;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.rag.query.Query;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.filter.Filter;
import dev.langchain4j.store.embedding.filter.comparison.ContainsString;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.ContentMetadata;
import io.quarkus.logging.Log;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.chappiebot.search.SearchMatch;
import org.chappiebot.store.StoreManager;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@ApplicationScoped
public class RetrievalProvider {

    @Inject
    StoreManager storeManager;

    EmbeddingModel embeddingModel;

    private EmbeddingStore<TextSegment> embeddingStore;

    @ConfigProperty(name = "chappie.rag.enabled", defaultValue = "true")
    boolean ragEnabled;

    @ConfigProperty(name = "chappie.rag.results.max", defaultValue = "4")
    int ragMaxResults;

    @ConfigProperty(name = "chappie.rag.score.min", defaultValue = "0.82")
    double ragMinScore;
    
    @PostConstruct
    public void init() {
        if (ragEnabled) {
            loadEmbeddingModel();
            loadVectorStore();
            if (embeddingStore == null) {
                Log.warn("RAG enabled but no embedding store available; disabling RAG for this run");
                ragEnabled = false;
            }
        }
    }

    public int getRagMaxResults() {
        return ragMaxResults;
    }

    private void loadVectorStore() {
        this.embeddingStore = storeManager.getStore().orElse(null);
    }

    private void loadEmbeddingModel() {
        embeddingModel = new BgeSmallEnV15QuantizedEmbeddingModel();
    }

    // TODO do we need to pad the extensions with commas to avoid partial matches?
    private Filter extensionFilter(String extension) {
        return new ContainsString("extensions_csv_padded", extension);
    }

    public List<SearchMatch> search(String queryMessage, int maxResults, String restrictToExtension) {
        Embedding embeddedQuery = embeddingModel.embed(queryMessage).content();

        EmbeddingSearchRequest.EmbeddingSearchRequestBuilder requestBuilder = EmbeddingSearchRequest.builder()
                .queryEmbedding(embeddedQuery)
                .maxResults(maxResults)
                .minScore(0.0);
        if (restrictToExtension != null) {
            Log.info("Restricting search to extension: " + restrictToExtension);
            requestBuilder.filter(extensionFilter(restrictToExtension));
        }
        EmbeddingSearchRequest searchRequest = requestBuilder.build();

        EmbeddingSearchResult<TextSegment> searchResult = embeddingStore.search(searchRequest);

        return searchResult.matches().stream()
                .map(RetrievalProvider::extractContent)
                .collect(Collectors.toList());
    }

    private static SearchMatch extractContent(EmbeddingMatch<TextSegment> embeddingMatch) {
        Map<String, Object> metadata = embeddingMatch.embedded().metadata().toMap();
        // Remove the actual embedding vector from metadata to reduce payload size
        metadata.remove("embedding");
        return new SearchMatch(embeddingMatch.embedded().text(), embeddingMatch.embeddingId(), embeddingMatch.score(),
                metadata);
    }

    public RetrievalAugmentor getRetrievalAugmentor(Function<Query, Filter> filterFunction) {
        if (ragEnabled && embeddingModel != null) {

            var retriever = EmbeddingStoreContentRetriever.builder()
                    .embeddingStore(embeddingStore)
                    .embeddingModel(embeddingModel)
                    .maxResults(ragMaxResults)
                    .minScore(ragMinScore)
                    .dynamicFilter(filterFunction)
                    .build();

            // TODO: Maybe skip RAG if the user message word count is less than 3 or something ?
        
            ContentInjector contentInjector = new ContentInjector(){
                @Override
                public ChatMessage inject(List<Content> contents, ChatMessage cm) {
                    if (cm == null) {
                        return UserMessage.from("");
                    }

                    if (cm.type() != ChatMessageType.USER) {
                        return cm;
                    }

                    if (contents == null || contents.isEmpty()) {
                        return cm;
                    }

                    String contextBlock = contents.stream()
                        .map(c -> {

                            Object score = c.metadata().getOrDefault(ContentMetadata.SCORE, 0);
                            // TODO: Can we surface the Score somehow ?
                            String t = c.textSegment().text();
                            if (t == null) return "";
                            if (t.length() > 1400) t = t.substring(0, 1400) + " …"; // TODO: Make 1400 a input option
                            return t;
                        })
                        .filter(s -> !s.isBlank())
                        .limit(ragMaxResults)
                        .collect(Collectors.joining("\n---\n"));

                    if (contextBlock.isBlank()) {
                        return cm;
                    }

                    String preface = """
                        [RAG CONTEXT]
                        Use this as a guide only. It may be incomplete or irrelevant.
                        If it conflicts with known facts or user intent, explain and prefer correctness.
                        If irrelevant, say so and answer without it.

                        <context>
                        %s
                        </context>
                        [/RAG CONTEXT]
                        """.formatted(contextBlock);

                    String userText = ((UserMessage) cm).singleText();
                    String combined = (userText == null || userText.isBlank())
                        ? preface
                        : userText + "\n\n" + preface;

                    return UserMessage.from(combined);
                }
            };

            Log.infof("CHAPPiE RAG is enabled with %d max results and min score %.2f", ragMaxResults, ragMinScore);
            
            return DefaultRetrievalAugmentor.builder()
                .contentInjector(contentInjector)
                .contentRetriever(retriever)
                .build();
        }
        return null;
    }
}
