package org.chappiebot.rag;

import io.quarkus.test.common.QuarkusTestResourceConfigurableLifecycleManager;
import java.util.HashMap;
import java.util.Map;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

public class RagImageDbResource implements QuarkusTestResourceConfigurableLifecycleManager<RagImageDbConfig> {

    private RagImageDbConfig cfg;
    private PostgreSQLContainer<?> db;
    
    @Override
    public void init(RagImageDbConfig config) {
        this.cfg = config;
    }
    
    @Override
    public Map<String, String> start() {
        String rawImage = System.getProperty("rag.image",
                (cfg != null ? cfg.image() : "ghcr.io/quarkusio/chappie-ingestion-quarkus:3.30.6"));

        int dim = (cfg != null ? cfg.dim() : 384);

        DockerImageName image = DockerImageName.parse(rawImage)
                .asCompatibleSubstituteFor("postgres");

        db = new PostgreSQLContainer<>(image)
                .withDatabaseName("postgres")
                .withUsername("postgres")
                .withPassword("postgres");
        db.start();

        Map<String, String> props = new HashMap<>();

        // disable devservices so it doesn't start postgres:17
        props.put("quarkus.datasource.devservices.enabled", "false");

        String dsName = (cfg != null ? cfg.datasourceName() : "");
        if (dsName != null && !dsName.isBlank()) {
            // named datasource
            props.put("quarkus.datasource.\"" + dsName + "\".jdbc.url", db.getJdbcUrl());
            props.put("quarkus.datasource.\"" + dsName + "\".username", db.getUsername());
            props.put("quarkus.datasource.\"" + dsName + "\".password", db.getPassword());
        } else {
            // default datasource
            props.put("quarkus.datasource.jdbc.url", db.getJdbcUrl());
            props.put("quarkus.datasource.username", db.getUsername());
            props.put("quarkus.datasource.password", db.getPassword());
        }
        
        props.put("chappie.rag.pgvector.dimension", Integer.toString(dim));
        
        return props;
    }

    @Override
    public void stop() {
        if (db != null) db.stop();
    }
    
    
}
