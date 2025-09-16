package org.chappiebot.store;

import dev.langchain4j.store.embedding.pgvector.PgVectorEmbeddingStore;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import dev.langchain4j.store.memory.chat.InMemoryChatMemoryStore;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.Optional;
import javax.sql.DataSource;

/**
 * This store are used for both RAG and memory
 * @author Phillip Kruger (phillip.kruger@gmail.com)
 */
@ApplicationScoped
public class StoreCreator {
    
    @Inject Instance<DataSource> chappieDs;

    private static final String DOCUMENTS_TABLE = "rag_documents";
    private static final String MEMORY_TABLE = "chappie_chat_messages";
    private static final int DIM = 1536;
    
    private volatile Optional<PgVectorEmbeddingStore> cached;

    private volatile ChatMemoryStore chatMemoryStore;
    
    public java.util.Optional<PgVectorEmbeddingStore> getStore() {
        if (this.cached != null) return this.cached;
        synchronized (this) {
            if (this.cached != null) return this.cached;
            DataSource ds = resolveDataSource();
            cached = (ds == null)
                    ? Optional.empty()
                    : Optional.of(PgVectorEmbeddingStore.datasourceBuilder()
                        .datasource(ds)
                        .table(DOCUMENTS_TABLE)
                        .dimension(DIM)
                        .build());
            return cached;
        }
    }
    
    private DataSource resolveDataSource() {
        if (chappieDs != null && chappieDs.isResolvable()) {
            DataSource ds = chappieDs.get();
            if(ensureChatTableExists(ds, MEMORY_TABLE)){
                chatMemoryStore = new JdbcChatMemoryStore(ds, MEMORY_TABLE);
            }else{
                chatMemoryStore = new InMemoryChatMemoryStore();
            }
            return ds;
        } else {
            Log.warn("RAG is disabled");
            return null;
        }
    }
    
    private boolean ensureChatTableExists(DataSource ds, String table) {
        String ddl = """
            CREATE TABLE IF NOT EXISTS %s (
              memory_id    VARCHAR(200) NOT NULL,
              msg_index    INTEGER      NOT NULL,
              created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
              message_json JSONB        NOT NULL,
              PRIMARY KEY (memory_id, msg_index)
            )
            """.formatted(table);

        String idx = "CREATE INDEX IF NOT EXISTS idx_%s_mid ON %s(memory_id)"
                .formatted(table, table);

        try (var c = ds.getConnection(); var st = c.createStatement()) {
            st.execute(ddl);
            st.execute(idx);
        } catch (Exception e) {
            Log.warn("No datasource available - Will use InMemoryChatMemoryStore");
            return false;
        }
        return true;
    }

    
    @Produces
    @Singleton
    ChatMemoryStore chatMemoryStore() {
        return chatMemoryStore;
    }
}