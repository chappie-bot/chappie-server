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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.sql.DataSource;

/**
 * Loads RAG SQL fragments from extension deployment JARs into the pgvector database.
 * Discovers {@code META-INF/quarkus-rag.sql} and {@code META-INF/quarkus-rag-data.sql}
 * from the aggregated {@code quarkus-documentation-core-rag} artifact or individual
 * extension JARs in the local Maven repository.
 * <p>
 * Supports incremental loading: only fragments whose source is not already in the
 * database are loaded, so new extensions added during dev mode are picked up on restart.
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

    private static final Pattern SOURCE_PATTERN = Pattern.compile(
            "metadata\\s*->>\\s*'source'\\s*=\\s*'([^']+)'");

    record RagFragment(String source, String sql) {
    }

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

        // Ensure schema exists
        try (Connection conn = ds.getConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute(CREATE_EXTENSION_DDL);
            stmt.execute(CREATE_TABLE_DDL);
        } catch (Exception e) {
            Log.debug("Could not initialize RAG schema: " + e.getMessage());
            return;
        }

        // Query which sources are already loaded (handles Dev Services container reuse)
        Set<String> existingSources = queryExistingSources(ds);

        String quarkusVersion = detectQuarkusVersion();
        List<RagFragment> allFragments = discoverSqlFragments(quarkusVersion);
        if (allFragments.isEmpty()) {
            Log.info("No RAG SQL fragments found — documentation search may be limited");
            return;
        }

        List<RagFragment> newFragments = allFragments.stream()
                .filter(f -> !existingSources.contains(f.source()))
                .toList();

        if (newFragments.isEmpty()) {
            Log.infof("All %d RAG source(s) already loaded", allFragments.size());
            return;
        }

        Log.infof("Loading %d new RAG SQL fragment(s) for Quarkus %s...", newFragments.size(),
                quarkusVersion != null ? quarkusVersion : "unknown");

        try (Connection conn = ds.getConnection()) {
            conn.setAutoCommit(false);
            try (Statement stmt = conn.createStatement()) {
                for (RagFragment fragment : newFragments) {
                    for (String sql : splitSqlStatements(fragment.sql())) {
                        if (!sql.isBlank()) {
                            stmt.execute(sql);
                        }
                    }
                    Log.debugf("Loaded RAG source: %s", fragment.source());
                }
                stmt.execute(CREATE_INDEX_DDL);
            }
            conn.commit();

            try (Connection c2 = ds.getConnection();
                    Statement stmt = c2.createStatement();
                    ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM rag_documents")) {
                if (rs.next()) {
                    Log.infof("RAG data loaded: %d total documents", rs.getLong(1));
                }
            }
        } catch (Exception e) {
            Log.errorf(e, "Failed to load RAG SQL");
        }
    }

    private Set<String> queryExistingSources(DataSource ds) {
        Set<String> sources = new HashSet<>();
        try (Connection conn = ds.getConnection();
                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(
                        "SELECT DISTINCT metadata->>'source' FROM rag_documents")) {
            while (rs.next()) {
                String source = rs.getString(1);
                if (source != null) {
                    sources.add(source);
                }
            }
            if (!sources.isEmpty()) {
                Log.infof("Container already has RAG data for %d source(s)", sources.size());
            }
        } catch (Exception e) {
            Log.debug("Could not query existing RAG sources: " + e.getMessage());
        }
        return sources;
    }

    List<RagFragment> discoverSqlFragments(String quarkusVersion) {
        Path m2Repo = Path.of(System.getProperty("user.home"), ".m2", "repository");
        if (!Files.isDirectory(m2Repo)) {
            return List.of();
        }

        List<RagFragment> fragments = new ArrayList<>();

        if (quarkusVersion != null) {
            RagFragment aggregated = readFragmentFromAggregatedArtifact(m2Repo, quarkusVersion);
            if (aggregated != null) {
                fragments.add(aggregated);
                Log.infof("Found aggregated RAG SQL for Quarkus %s", quarkusVersion);
            } else {
                fragments.addAll(scanCoreExtensionJars(m2Repo, quarkusVersion));
            }
        }

        Log.infof("Discovered %d RAG SQL fragment(s)", fragments.size());
        return fragments;
    }

    private RagFragment readFragmentFromAggregatedArtifact(Path m2Repo, String version) {
        Path aggregatedJar = m2Repo.resolve("io/quarkus/quarkus-documentation-core-rag")
                .resolve(version)
                .resolve("quarkus-documentation-core-rag-" + version + ".jar");

        return readFragmentFromJar(aggregatedJar, "quarkus-documentation");
    }

    private List<RagFragment> scanCoreExtensionJars(Path m2Repo, String version) {
        Path quarkusDir = m2Repo.resolve("io/quarkus");
        if (!Files.isDirectory(quarkusDir)) {
            return List.of();
        }

        List<RagFragment> fragments = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(quarkusDir,
                entry -> Files.isDirectory(entry)
                        && entry.getFileName().toString().startsWith("quarkus-")
                        && entry.getFileName().toString().endsWith(DEPLOYMENT_SUFFIX))) {
            for (Path extDir : stream) {
                String deploymentArtifactId = extDir.getFileName().toString();
                String artifactId = deploymentArtifactId.substring(0,
                        deploymentArtifactId.length() - DEPLOYMENT_SUFFIX.length());
                Path deploymentJar = extDir.resolve(version)
                        .resolve(deploymentArtifactId + "-" + version + ".jar");
                if (!Files.isRegularFile(deploymentJar)) {
                    continue;
                }
                RagFragment fragment = readFragmentFromJar(deploymentJar, artifactId);
                if (fragment != null) {
                    fragments.add(fragment);
                }
            }
        } catch (IOException e) {
            Log.debugf("Failed to scan extension JARs: %s", e.getMessage());
        }
        return fragments;
    }

    private RagFragment readFragmentFromJar(Path jarPath, String fallbackSource) {
        if (!Files.isRegularFile(jarPath)) {
            return null;
        }
        try (JarFile jar = new JarFile(jarPath.toFile())) {
            JarEntry entry = jar.getJarEntry(RAG_DATA_SQL_PATH);
            if (entry == null) {
                entry = jar.getJarEntry(RAG_SQL_PATH);
            }
            if (entry == null) {
                return null;
            }
            String sql;
            try (InputStream is = jar.getInputStream(entry)) {
                sql = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            }
            String source = extractSource(sql, fallbackSource);
            return new RagFragment(source, sql);
        } catch (IOException e) {
            return null;
        }
    }

    static String extractSource(String sql, String fallbackSource) {
        Matcher m = SOURCE_PATTERN.matcher(sql);
        if (m.find()) {
            return m.group(1);
        }
        return fallbackSource;
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
