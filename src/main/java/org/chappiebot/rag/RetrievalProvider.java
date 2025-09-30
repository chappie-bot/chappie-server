package org.chappiebot.rag;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.bgesmallenv15q.BgeSmallEnV15QuantizedEmbeddingModel;
import dev.langchain4j.rag.DefaultRetrievalAugmentor;
import dev.langchain4j.rag.RetrievalAugmentor;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.rag.query.Query;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.filter.Filter;
import dev.langchain4j.store.embedding.filter.comparison.ContainsString;
import io.quarkus.logging.Log;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.chappiebot.search.SearchMatch;
import org.chappiebot.store.StoreCreator;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@ApplicationScoped
public class RetrievalProvider {

    @Inject
    StoreCreator storeCreator;

    EmbeddingModel embeddingModel;

    private EmbeddingStore<TextSegment> embeddingStore;

    @ConfigProperty(name = "chappie.rag.enabled", defaultValue = "true")
    boolean ragEnabled;

    @ConfigProperty(name = "chappie.rag.results.max", defaultValue = "4")
    int ragMaxResults;

    @PostConstruct
    public void init() {
        if (ragEnabled) {
            loadEmbeddingModel();
            loadVectorStore();
        }
    }

    public int getRagMaxResults() {
        return ragMaxResults;
    }

    private void loadVectorStore() {
        embeddingStore = storeCreator.getStore().get();
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
            Log.info("CHAPPiE RAG is enabled with " + ragMaxResults + " max results");

            var retriever = EmbeddingStoreContentRetriever.builder()
                    .embeddingStore(embeddingStore)
                    .embeddingModel(embeddingModel)
                    .maxResults(ragMaxResults)
                    .dynamicFilter(filterFunction)
                    .build();

            return DefaultRetrievalAugmentor.builder()
                    .contentRetriever(retriever)
                    .build();
        }
        return null;
    }
}
