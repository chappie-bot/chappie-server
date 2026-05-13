package org.chappiebot.rag;

import io.quarkus.logging.Log;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Instance;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import javax.sql.DataSource;

/**
 * Loads RAG SQL fragments from extension deployment JARs into the pgvector database.
 * Discovers {@code META-INF/quarkus-rag.sql} and {@code META-INF/quarkus-rag-data.sql}
 * from the aggregated {@code quarkus-documentation-core-rag} artifact or individual
 * extension JARs in the local Maven repository.
 */
@ApplicationScoped
public class RagSqlLoader {

    private static final String RAG_SQL_PATH = "META-INF/quarkus-rag.sql";
    private static final String RAG_DATA_SQL_PATH = "META-INF/quarkus-rag-data.sql";
    private static final String DEPLOYMENT_SUFFIX = "-deployment";

    private static final String CREATE_EXTENSION_DDL = "CREATE EXTENSION IF NOT EXISTS vector";
    private static final String CREATE_TABLE_DDL = """
            CREATE TABLE IF NOT EXISTS rag_documents (
                embedding_id UUID PRIMARY KEY,
                embedding vector(384),
                text TEXT,
                metadata JSONB
            )""";
    private static final String CREATE_INDEX_DDL = """
            CREATE INDEX IF NOT EXISTS idx_rag_embedding ON rag_documents
                USING ivfflat (embedding vector_cosine_ops) WITH (lists = 100)""";

    Instance<DataSource> dataSource;

    void onStart(@Observes StartupEvent event, Instance<DataSource> ds) {
        this.dataSource = ds;
        Thread t = new Thread(this::loadRagData, "rag-sql-loader");
        t.setDaemon(true);
        t.start();
    }

    private void loadRagData() {
        if (dataSource == null || !dataSource.isResolvable()) {
            Log.debug("No datasource available — skipping RAG SQL loading");
            return;
        }

        DataSource ds = dataSource.get();

        try (Connection conn = ds.getConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute(CREATE_EXTENSION_DDL);
            stmt.execute(CREATE_TABLE_DDL);

            try (ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM rag_documents")) {
                if (rs.next() && rs.getLong(1) > 0) {
                    Log.infof("RAG data already loaded (%d rows)", rs.getLong(1));
                    return;
                }
            }
        } catch (Exception e) {
            Log.debug("Could not check RAG table state: " + e.getMessage());
            return;
        }

        String quarkusVersion = detectQuarkusVersion();
        List<String> fragments = discoverSqlFragments(quarkusVersion);
        if (fragments.isEmpty()) {
            Log.info("No RAG SQL fragments found — documentation search may be limited");
            return;
        }

        Log.infof("Loading %d RAG SQL fragment(s) for Quarkus %s...", fragments.size(),
                quarkusVersion != null ? quarkusVersion : "unknown");

        try (Connection conn = ds.getConnection()) {
            conn.setAutoCommit(false);
            try (Statement stmt = conn.createStatement()) {
                for (String fragment : fragments) {
                    for (String sql : splitSqlStatements(fragment)) {
                        if (!sql.isBlank()) {
                            stmt.execute(sql);
                        }
                    }
                }
                stmt.execute(CREATE_INDEX_DDL);
            }
            conn.commit();

            try (Connection c2 = ds.getConnection();
                    Statement stmt = c2.createStatement();
                    ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM rag_documents")) {
                if (rs.next()) {
                    Log.infof("RAG data loaded: %d documents", rs.getLong(1));
                }
            }
        } catch (Exception e) {
            Log.errorf(e, "Failed to load RAG SQL");
        }
    }

    List<String> discoverSqlFragments(String quarkusVersion) {
        Path m2Repo = Path.of(System.getProperty("user.home"), ".m2", "repository");
        if (!Files.isDirectory(m2Repo)) {
            return List.of();
        }

        List<String> fragments = new ArrayList<>();

        if (quarkusVersion != null) {
            String aggregatedSql = readFromAggregatedArtifact(m2Repo, quarkusVersion);
            if (aggregatedSql != null) {
                fragments.add(aggregatedSql);
                Log.infof("Found aggregated RAG SQL for Quarkus %s", quarkusVersion);
                return fragments;
            }

            fragments.addAll(scanCoreExtensionJars(m2Repo, quarkusVersion));
        }

        Log.infof("Discovered %d RAG SQL fragment(s)", fragments.size());
        return fragments;
    }

    private String readFromAggregatedArtifact(Path m2Repo, String version) {
        Path aggregatedJar = m2Repo.resolve("io/quarkus/quarkus-documentation-core-rag")
                .resolve(version)
                .resolve("quarkus-documentation-core-rag-" + version + ".jar");

        if (!Files.isRegularFile(aggregatedJar)) {
            return null;
        }

        try (JarFile jar = new JarFile(aggregatedJar.toFile())) {
            JarEntry entry = jar.getJarEntry(RAG_DATA_SQL_PATH);
            if (entry == null) {
                entry = jar.getJarEntry(RAG_SQL_PATH);
            }
            if (entry == null) {
                return null;
            }
            try (InputStream is = jar.getInputStream(entry)) {
                return new String(is.readAllBytes(), StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            Log.debugf("Failed to read aggregated RAG artifact: %s", e.getMessage());
            return null;
        }
    }

    private List<String> scanCoreExtensionJars(Path m2Repo, String version) {
        Path quarkusDir = m2Repo.resolve("io/quarkus");
        if (!Files.isDirectory(quarkusDir)) {
            return List.of();
        }

        List<String> fragments = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(quarkusDir,
                entry -> Files.isDirectory(entry)
                        && entry.getFileName().toString().startsWith("quarkus-")
                        && entry.getFileName().toString().endsWith(DEPLOYMENT_SUFFIX))) {
            for (Path extDir : stream) {
                String deploymentArtifactId = extDir.getFileName().toString();
                Path deploymentJar = extDir.resolve(version)
                        .resolve(deploymentArtifactId + "-" + version + ".jar");
                if (!Files.isRegularFile(deploymentJar)) {
                    continue;
                }
                String sql = readSqlFromJar(deploymentJar);
                if (sql != null) {
                    fragments.add(sql);
                }
            }
        } catch (IOException e) {
            Log.debugf("Failed to scan extension JARs: %s", e.getMessage());
        }
        return fragments;
    }

    private String readSqlFromJar(Path jarPath) {
        try (JarFile jar = new JarFile(jarPath.toFile())) {
            JarEntry entry = jar.getJarEntry(RAG_DATA_SQL_PATH);
            if (entry == null) {
                entry = jar.getJarEntry(RAG_SQL_PATH);
            }
            if (entry == null) {
                return null;
            }
            try (InputStream is = jar.getInputStream(entry)) {
                return new String(is.readAllBytes(), StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            return null;
        }
    }

    static List<String> splitSqlStatements(String sql) {
        List<String> statements = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inSingleQuote = false;
        boolean inLineComment = false;

        for (int i = 0; i < sql.length(); i++) {
            char c = sql.charAt(i);

            if (c == '\n') {
                inLineComment = false;
                current.append(c);
                continue;
            }

            if (inLineComment) {
                continue;
            }

            if (c == '-' && i + 1 < sql.length() && sql.charAt(i + 1) == '-' && !inSingleQuote) {
                inLineComment = true;
                continue;
            }

            if (c == '\'' && (i == 0 || sql.charAt(i - 1) != '\'')) {
                inSingleQuote = !inSingleQuote;
            }

            if (c == ';' && !inSingleQuote) {
                String stmt = current.toString().trim();
                if (!stmt.isEmpty()) {
                    statements.add(stmt);
                }
                current = new StringBuilder();
            } else {
                current.append(c);
            }
        }

        String remaining = current.toString().trim();
        if (!remaining.isEmpty()) {
            statements.add(remaining);
        }

        return statements;
    }

    private String detectQuarkusVersion() {
        try {
            Class<?> versionClass = Class.forName("io.quarkus.builder.Version");
            return (String) versionClass.getMethod("getVersion").invoke(null);
        } catch (Exception e) {
            return detectLatestInstalledVersion();
        }
    }

    private String detectLatestInstalledVersion() {
        Path quarkusDir = Path.of(System.getProperty("user.home"), ".m2", "repository",
                "io", "quarkus", "quarkus-core");
        if (!Files.isDirectory(quarkusDir)) {
            return null;
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(quarkusDir, Files::isDirectory)) {
            String latest = null;
            for (Path versionDir : stream) {
                String v = versionDir.getFileName().toString();
                if (!v.contains("SNAPSHOT") && (latest == null || v.compareTo(latest) > 0)) {
                    latest = v;
                }
            }
            return latest;
        } catch (IOException e) {
            return null;
        }
    }
}
