package org.chappiebot.store;

import dev.langchain4j.store.embedding.pgvector.PgVectorEmbeddingStore;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import java.util.Optional;
import javax.sql.DataSource;

/**
 * This store are used for both RAG and memory
 * @author Phillip Kruger (phillip.kruger@gmail.com)
 */
@ApplicationScoped
public class StoreManager {
    
    @Inject Instance<DataSource> chappieDs;

    private static final String DOCUMENTS_TABLE = "rag_documents";
    private static final String MEMORY_TABLE = "chappie_chat_messages";
    private static final String MEMORY_NAME_TABLE = "chappie_memory_names";
    
    private static final int DIM = 1536;
    
    private Optional<PgVectorEmbeddingStore> cached;

    private JdbcChatMemoryStore jdbcChatMemoryStore = null;
    
    public Optional<PgVectorEmbeddingStore> getStore() {
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
    
    public Optional<JdbcChatMemoryStore> getJdbcChatMemoryStore(){
        if(this.jdbcChatMemoryStore == null){
            resolveDataSource();
        }
        if(this.jdbcChatMemoryStore == null){
            return Optional.empty();
        }
        return Optional.of(this.jdbcChatMemoryStore);
    } 
    
    private DataSource resolveDataSource() {
        if (chappieDs != null && chappieDs.isResolvable()) {
            DataSource ds = chappieDs.get();
            if(ensureChatTableExists(ds, MEMORY_TABLE) && ensureNameTableExists(ds, MEMORY_NAME_TABLE)) {
                jdbcChatMemoryStore = new JdbcChatMemoryStore(ds, MEMORY_TABLE, MEMORY_NAME_TABLE);
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
              last_modified TIMESTAMPTZ  NOT NULL DEFAULT now(),
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

    private boolean ensureNameTableExists(DataSource ds, String table) {
        String ddl = """
            CREATE TABLE IF NOT EXISTS %s (
              memory_id    VARCHAR(200) PRIMARY KEY,
              nice_name    VARCHAR(200) NOT NULL
            )
            """.formatted(table);

        String uniqIdx = "CREATE UNIQUE INDEX IF NOT EXISTS ux_%s_nice_name_ci ON %s (LOWER(nice_name))"
                .formatted(table, table);

        try (var c = ds.getConnection(); var st = c.createStatement()) {
            st.execute(ddl);
            st.execute(uniqIdx);
        } catch (Exception e) {
            Log.warn("Could not create memory name table: " + e.getMessage());
            return false;
        }
        return true;
    }
    
}