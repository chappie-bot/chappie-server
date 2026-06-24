package org.chappiebot.rag;

import io.quarkus.logging.Log;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.sql.DataSource;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * Downloads and loads pre-generated RAG SQL fragments from the
 * {@code io.quarkus:quarkus-documentation-core-rag} Maven artifact
 * into a pgvector database. The SQL contains vector embeddings for
 * all Quarkus documentation guides.
 */
public class RagSqlLoader {

    private static final String RAG_SQL_PATH = "META-INF/quarkus-rag.sql";
    private static final String RAG_DATA_SQL_PATH = "META-INF/quarkus-rag-data.sql";
    private static final String RAG_ARTIFACT_POINTER_PATH = "META-INF/quarkus-rag-artifact.properties";
    private static final String DEPLOYMENT_SUFFIX = "-deployment";
    private static final String CORE_GROUP_ID = "io.quarkus";
    private static final String AGGREGATED_ARTIFACT_ID = "quarkus-documentation-core-rag";
    private static final String AGGREGATED_GROUP_PATH = "io/quarkus";
    private static final String MAVEN_CENTRAL_BASE = "https://repo1.maven.org/maven2";
    private static String cachedProjectDir;

    static boolean supportsRagSql(String version) {
        if (version == null || version.isBlank() || version.contains("SNAPSHOT")) {
            return true;
        }
        try {
            String[] parts = version.split("[.\\-]");
            int major = Integer.parseInt(parts[0]);
            int minor = parts.length > 1 ? Integer.parseInt(parts[1]) : 0;
            if (major > 3 || (major == 3 && minor > 36)) {
                return true;
            }
            if (major == 3 && minor == 36) {
                int patch = parts.length > 2 ? Integer.parseInt(parts[2]) : 0;
                return patch >= 1;
            }
            return false;
        } catch (NumberFormatException e) {
            return false;
        }
    }

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

    private static final String CORE_SOURCE = "quarkus-documentation";

    public static void ensureLoaded(DataSource dataSource, String quarkusVersion) {
        ensureLoaded(dataSource, quarkusVersion, null);
    }

    public static void ensureLoaded(DataSource dataSource, String quarkusVersion, String projectDir) {
        if (quarkusVersion == null || quarkusVersion.isBlank()) {
            quarkusVersion = detectLatestInstalledVersion();
        }
        if (quarkusVersion == null) {
            Log.warn("Could not determine Quarkus version for RAG loading");
            return;
        }

        if (!supportsRagSql(quarkusVersion)) {
            Log.debugf("Quarkus %s predates RAG SQL support — skipping (using pre-built image)", quarkusVersion);
            return;
        }

        cachedProjectDir = projectDir;

        try (Connection conn = dataSource.getConnection()) {
            ensureSchema(conn);
        } catch (SQLException e) {
            Log.warn("Failed to create RAG schema: " + e.getMessage());
            return;
        }

        var loadedSources = queryLoadedSources(dataSource);

        List<String> newSql = new ArrayList<>();

        if (!loadedSources.contains(CORE_SOURCE)) {
            String coreSql = loadCoreSqlContent(quarkusVersion);
            if (coreSql != null) {
                newSql.add(coreSql);
            } else {
                Log.warnf("No core RAG SQL found for Quarkus %s", quarkusVersion);
            }
        }

        for (var fragment : discoverNonCoreFragments(projectDir)) {
            if (!loadedSources.contains(fragment.source)) {
                newSql.add(fragment.sql);
                Log.infof("Found new RAG SQL for extension %s", fragment.source);
            }
        }

        if (newSql.isEmpty()) {
            if (loadedSources.isEmpty()) {
                Log.warn("No RAG SQL fragments found — documentation search will be limited");
            } else {
                Log.debugf("All RAG sources already loaded (%d source(s))", loadedSources.size());
            }
            return;
        }

        executeSql(dataSource, newSql, quarkusVersion);
    }

    private static void ensureSchema(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            stmt.execute(CREATE_EXTENSION_DDL);
            stmt.execute(CREATE_TABLE_DDL);
        }
    }

    private static Set<String> queryLoadedSources(DataSource dataSource) {
        var sources = new HashSet<String>();
        try (Connection conn = dataSource.getConnection();
                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(
                        "SELECT DISTINCT metadata->>'source' FROM rag_documents")) {
            while (rs.next()) {
                String source = rs.getString(1);
                if (source != null) {
                    sources.add(source);
                }
            }
        } catch (SQLException e) {
            Log.debugf("Failed to query loaded RAG sources: %s", e.getMessage());
        }
        return sources;
    }

    private static String loadCoreSqlContent(String version) {
        Path m2Repo = resolveLocalMavenRepo();
        Path jarPath = m2Repo.resolve(AGGREGATED_GROUP_PATH)
                .resolve(AGGREGATED_ARTIFACT_ID)
                .resolve(version)
                .resolve(AGGREGATED_ARTIFACT_ID + "-" + version + ".jar");

        String sql = readSqlFromJar(jarPath);
        if (sql != null) {
            Log.infof("Found RAG SQL artifact locally for Quarkus %s", version);
            return injectExtensionFromSource(sql);
        }

        if (version.endsWith("-SNAPSHOT")) {
            Log.debugf("Skipping remote download for SNAPSHOT version %s", version);
            return null;
        }

        Path downloaded = downloadFromMavenCentral(version, jarPath);
        if (downloaded != null) {
            sql = readSqlFromJar(downloaded);
            if (sql != null) {
                Log.infof("Downloaded RAG SQL artifact for Quarkus %s", version);
                return injectExtensionFromSource(sql);
            }
        }

        return null;
    }

    private static String readSqlFromJar(Path jarPath) {
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
            try (InputStream is = jar.getInputStream(entry)) {
                return new String(is.readAllBytes(), StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            Log.debugf("Failed to read RAG SQL from %s: %s", jarPath, e.getMessage());
            return null;
        }
    }

    private static Path downloadFromMavenCentral(String version, Path targetPath) {
        String baseUrl = resolveMavenRepoBaseUrl();
        String url = baseUrl + "/" + AGGREGATED_GROUP_PATH + "/" + AGGREGATED_ARTIFACT_ID
                + "/" + version + "/" + AGGREGATED_ARTIFACT_ID + "-" + version + ".jar";

        Log.infof("RAG SQL not found locally, downloading from %s...", url);

        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(30))
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .build();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(60))
                    .GET()
                    .build();

            HttpResponse<InputStream> response = client.send(request,
                    HttpResponse.BodyHandlers.ofInputStream());

            if (response.statusCode() == 200) {
                Files.createDirectories(targetPath.getParent());
                try (InputStream body = response.body()) {
                    Files.copy(body, targetPath, StandardCopyOption.REPLACE_EXISTING);
                }
                Log.infof("Downloaded RAG SQL artifact to %s", targetPath);
                return targetPath;
            } else {
                Log.warnf("RAG SQL artifact not available at %s (HTTP %d)", url, response.statusCode());
                return null;
            }
        } catch (IOException | InterruptedException e) {
            Log.warnf("Failed to download RAG SQL: %s", e.getMessage());
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return null;
        }
    }

    private static void executeSql(DataSource dataSource, List<String> sqlFragments, String version) {
        Log.infof("Loading %d RAG SQL fragment(s) for Quarkus %s...", sqlFragments.size(), version);

        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);

            try (Statement stmt = conn.createStatement()) {
                stmt.execute(CREATE_EXTENSION_DDL);
                stmt.execute(CREATE_TABLE_DDL);

                for (String sql : sqlFragments) {
                    for (String statement : splitSqlStatements(sql)) {
                        if (!statement.isBlank()) {
                            stmt.execute(statement);
                        }
                    }
                }

                stmt.execute(CREATE_INDEX_DDL);
            }

            conn.commit();

            try (Statement stmt = conn.createStatement();
                    ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM rag_documents")) {
                if (rs.next()) {
                    Log.infof("RAG data loaded: %d documents for Quarkus %s", rs.getLong(1), version);
                }
            }
        } catch (SQLException e) {
            Log.errorf(e, "Failed to load RAG SQL for Quarkus %s", version);
        }
    }

    private record RagFragment(String source, String sql) {
    }

    private static final Pattern SOURCE_PATTERN = Pattern.compile(
            "metadata\\s*->>\\s*'source'\\s*=\\s*'([^']+)'");
    private static final Pattern PROPERTY_PATTERN = Pattern.compile("\\$\\{([^}]+)}");

    private static List<RagFragment> discoverNonCoreFragments(String projectDir) {
        if (projectDir == null || projectDir.isBlank()) {
            return List.of();
        }

        Path dir = Path.of(projectDir);
        List<Dependency> deps;
        if (Files.isRegularFile(dir.resolve("pom.xml"))) {
            deps = parsePomDependencies(dir.resolve("pom.xml"));
        } else if (Files.isRegularFile(dir.resolve("build.gradle")) || Files.isRegularFile(dir.resolve("build.gradle.kts"))) {
            deps = resolveGradleDependencies(dir);
        } else {
            return List.of();
        }

        if (deps.isEmpty()) {
            return List.of();
        }

        Path m2Repo = resolveLocalMavenRepo();
        List<RagFragment> fragments = new ArrayList<>();

        for (Dependency dep : deps) {
            if (CORE_GROUP_ID.equals(dep.groupId) || dep.version == null) {
                continue;
            }
            String groupPath = dep.groupId.replace('.', '/');
            Path deploymentJar = m2Repo.resolve(groupPath)
                    .resolve(dep.artifactId + DEPLOYMENT_SUFFIX)
                    .resolve(dep.version)
                    .resolve(dep.artifactId + DEPLOYMENT_SUFFIX + "-" + dep.version + ".jar");

            if (!Files.isRegularFile(deploymentJar)) {
                continue;
            }

            // Check for a pointer to a separate RAG artifact
            RagArtifactPointer pointer = readRagArtifactPointer(deploymentJar);
            if (pointer != null) {
                String ragGroupPath = pointer.groupId.replace('.', '/');
                Path ragJarPath = m2Repo.resolve(ragGroupPath)
                        .resolve(pointer.artifactId)
                        .resolve(dep.version)
                        .resolve(pointer.artifactId + "-" + dep.version + ".jar");

                String sql = readSqlFromJar(ragJarPath);
                if (sql != null) {
                    String source = extractSource(sql, pointer.artifactId);
                    fragments.add(new RagFragment(source, injectExtensionMetadata(sql, dep.artifactId)));
                    continue;
                }
            }

            // Fallback: read RAG SQL directly from the deployment JAR
            String sql = readSqlFromJar(deploymentJar);
            if (sql != null) {
                String source = extractSource(sql, dep.artifactId);
                fragments.add(new RagFragment(source, injectExtensionMetadata(sql, dep.artifactId)));
            }
        }

        return fragments;
    }

    private static String extractSource(String sql, String fallback) {
        Matcher m = SOURCE_PATTERN.matcher(sql);
        return m.find() ? m.group(1) : fallback;
    }

    private static String injectExtensionMetadata(String sql, String extensionName) {
        return sql.replace("'{\"source\":",
                "'{\"extension\":\"" + extensionName + "\",\"source\":");
    }

    private static String injectExtensionFromSource(String sql) {
        return sql.replaceAll(
                "'\\{\"source\":\"([^\"]+)\"",
                "'{\"extension\":\"$1\",\"source\":\"$1\"");
    }

    private record RagArtifactPointer(String groupId, String artifactId) {
    }

    private static RagArtifactPointer readRagArtifactPointer(Path jarPath) {
        try (JarFile jar = new JarFile(jarPath.toFile())) {
            JarEntry entry = jar.getJarEntry(RAG_ARTIFACT_POINTER_PATH);
            if (entry == null) {
                return null;
            }
            Properties props = new Properties();
            try (InputStream is = jar.getInputStream(entry)) {
                props.load(is);
            }
            String groupId = props.getProperty("groupId");
            String artifactId = props.getProperty("artifactId");
            if (groupId == null || artifactId == null) {
                Log.warnf("Invalid RAG artifact pointer in %s: groupId=%s, artifactId=%s",
                        jarPath, groupId, artifactId);
                return null;
            }
            return new RagArtifactPointer(groupId.trim(), artifactId.trim());
        } catch (IOException e) {
            Log.debugf("Failed to read RAG artifact pointer from %s: %s",
                    jarPath, e.getMessage());
            return null;
        }
    }

    private record Dependency(String groupId, String artifactId, String version) {
    }

    private static List<Dependency> parsePomDependencies(Path pomFile) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            Document doc = factory.newDocumentBuilder().parse(pomFile.toFile());

            Map<String, String> properties = new HashMap<>();
            NodeList propsNodes = doc.getElementsByTagName("properties");
            if (propsNodes.getLength() > 0) {
                Element propsEl = (Element) propsNodes.item(0);
                NodeList children = propsEl.getChildNodes();
                for (int i = 0; i < children.getLength(); i++) {
                    if (children.item(i) instanceof Element el) {
                        properties.put(el.getTagName(), el.getTextContent().trim());
                    }
                }
            }

            List<Dependency> deps = new ArrayList<>();
            NodeList depNodes = doc.getElementsByTagName("dependency");
            for (int i = 0; i < depNodes.getLength(); i++) {
                Element depEl = (Element) depNodes.item(i);
                if (isInPluginOrManagement(depEl)) {
                    continue;
                }
                String groupId = childText(depEl, "groupId");
                String artifactId = childText(depEl, "artifactId");
                String version = childText(depEl, "version");

                if (groupId == null || artifactId == null) {
                    continue;
                }

                groupId = resolveProperty(groupId, properties);
                artifactId = resolveProperty(artifactId, properties);
                if (version != null) {
                    version = resolveProperty(version, properties);
                    if (version.contains("${")) {
                        version = null;
                    }
                }

                deps.add(new Dependency(groupId, artifactId, version));
            }
            return deps;
        } catch (Exception e) {
            Log.debugf("Failed to parse pom.xml: %s", e.getMessage());
            return List.of();
        }
    }

    private static final Pattern GRADLE_DEP_LINE = Pattern.compile(
            "^[|\\s]*[+\\\\]---\\s+(\\S+):(\\S+):(\\S+?)(?:\\s+->\\s+(\\S+))?(?:\\s+\\(.*\\))?\\s*$");

    private static List<Dependency> resolveGradleDependencies(Path projectDir) {
        String gradleCmd = Files.isRegularFile(projectDir.resolve("gradlew"))
                ? "./gradlew" : "gradle";
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    gradleCmd, "dependencies",
                    "--configuration", "runtimeClasspath",
                    "-q", "--console=plain")
                    .directory(projectDir.toFile())
                    .redirectErrorStream(true);
            Process process = pb.start();
            String output;
            try (var reader = new java.io.BufferedReader(new java.io.InputStreamReader(process.getInputStream()))) {
                output = reader.lines().collect(java.util.stream.Collectors.joining("\n"));
            }
            if (!process.waitFor(60, java.util.concurrent.TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return List.of();
            }
            if (process.exitValue() != 0) {
                return List.of();
            }
            return parseGradleDependencyOutput(output);
        } catch (Exception e) {
            Log.debugf("Failed Gradle dependency resolution: %s", e.getMessage());
            return List.of();
        }
    }

    private static List<Dependency> parseGradleDependencyOutput(String output) {
        if (output == null || output.isBlank()) {
            return List.of();
        }
        List<Dependency> deps = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (String line : output.split("\n")) {
            if (!line.startsWith("+---") && !line.startsWith("\\---")) {
                continue;
            }
            Matcher m = GRADLE_DEP_LINE.matcher(line);
            if (m.matches()) {
                String groupId = m.group(1);
                String artifactId = m.group(2);
                String version = m.group(4) != null ? m.group(4) : m.group(3);
                if (seen.add(groupId + ":" + artifactId)) {
                    deps.add(new Dependency(groupId, artifactId, version));
                }
            }
        }
        return deps;
    }

    private static String resolveProperty(String value, Map<String, String> properties) {
        if (value == null || !value.contains("${")) {
            return value;
        }
        Matcher m = PROPERTY_PATTERN.matcher(value);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String replacement = properties.getOrDefault(m.group(1), m.group(0));
            m.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private static boolean isInPluginOrManagement(Element el) {
        org.w3c.dom.Node parent = el.getParentNode();
        while (parent instanceof Element parentEl) {
            String tag = parentEl.getTagName();
            if ("plugin".equals(tag) || "exclusions".equals(tag) || "dependencyManagement".equals(tag)) {
                return true;
            }
            parent = parentEl.getParentNode();
        }
        return false;
    }

    private static String childText(Element parent, String tagName) {
        NodeList children = parent.getElementsByTagName(tagName);
        if (children.getLength() > 0) {
            String text = children.item(0).getTextContent();
            return text != null ? text.trim() : null;
        }
        return null;
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

            if (c == '\'') {
                if (inSingleQuote && i + 1 < sql.length() && sql.charAt(i + 1) == '\'') {
                    current.append('\'');
                    current.append('\'');
                    i++;
                    continue;
                }
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

    private static String detectLatestInstalledVersion() {
        Path quarkusDir = resolveLocalMavenRepo().resolve("io/quarkus/quarkus-core");
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

    // ── Maven settings resolution ───────────────────────────────────────────

    private static String resolveMavenRepoBaseUrl() {
        String url = findInSettingsFiles(RagSqlLoader::parseMirrorUrl);
        return url != null ? url : MAVEN_CENTRAL_BASE;
    }

    private static Path resolveLocalMavenRepo() {
        Path repo = findInSettingsFiles(RagSqlLoader::parseLocalRepository);
        return repo != null ? repo : Path.of(System.getProperty("user.home"), ".m2", "repository");
    }

    private static <T> T findInSettingsFiles(java.util.function.Function<Path, T> extractor) {
        if (cachedProjectDir != null) {
            Path customSettings = parseSettingsFromMvnConfig(Path.of(cachedProjectDir));
            if (customSettings != null && Files.isRegularFile(customSettings)) {
                T result = extractor.apply(customSettings);
                if (result != null) {
                    return result;
                }
            }
        }

        Path userSettings = Path.of(System.getProperty("user.home"), ".m2", "settings.xml");
        if (Files.isRegularFile(userSettings)) {
            T result = extractor.apply(userSettings);
            if (result != null) {
                return result;
            }
        }

        String mavenHome = System.getenv("MAVEN_HOME");
        if (mavenHome == null) {
            mavenHome = System.getenv("M2_HOME");
        }
        if (mavenHome == null) {
            mavenHome = System.getProperty("maven.home");
        }
        if (mavenHome != null) {
            Path globalSettings = Path.of(mavenHome, "conf", "settings.xml");
            if (Files.isRegularFile(globalSettings)) {
                T result = extractor.apply(globalSettings);
                if (result != null) {
                    return result;
                }
            }
        }

        return null;
    }

    private static Path parseSettingsFromMvnConfig(Path projectDir) {
        Path configFile = projectDir.resolve(".mvn/maven.config");
        if (!Files.isRegularFile(configFile)) {
            return null;
        }
        try {
            String content = Files.readString(configFile, StandardCharsets.UTF_8);
            String[] tokens = content.trim().split("\\s+");
            for (int i = 0; i < tokens.length; i++) {
                String token = tokens[i];
                String settingsValue = null;
                if ((token.equals("-s") || token.equals("--settings")) && i + 1 < tokens.length) {
                    settingsValue = tokens[i + 1];
                } else if (token.startsWith("-s=")) {
                    settingsValue = token.substring(3);
                } else if (token.startsWith("--settings=")) {
                    settingsValue = token.substring(11);
                }
                if (settingsValue != null) {
                    Path settingsPath = Path.of(settingsValue);
                    if (!settingsPath.isAbsolute()) {
                        settingsPath = projectDir.resolve(settingsPath);
                    }
                    return settingsPath.normalize();
                }
            }
        } catch (IOException e) {
            Log.debugf("Failed to read .mvn/maven.config: %s", e.getMessage());
        }
        return null;
    }

    private static String parseMirrorUrl(Path settingsFile) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            Document doc = factory.newDocumentBuilder().parse(settingsFile.toFile());

            NodeList mirrors = doc.getElementsByTagName("mirror");
            for (int i = 0; i < mirrors.getLength(); i++) {
                Element mirror = (Element) mirrors.item(i);
                String mirrorOf = childText(mirror, "mirrorOf");
                String url = childText(mirror, "url");

                if (mirrorOf != null && url != null && mirrorOfMatchesCentral(mirrorOf)) {
                    return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
                }
            }
        } catch (Exception e) {
            Log.debugf("Failed to parse settings.xml at %s: %s", settingsFile, e.getMessage());
        }
        return null;
    }

    private static boolean mirrorOfMatchesCentral(String mirrorOf) {
        String trimmed = mirrorOf.trim();
        if (trimmed.equals("*") || trimmed.equals("central") || trimmed.equals("external:*")) {
            return true;
        }
        if (trimmed.contains(",")) {
            String[] parts = trimmed.split(",");
            boolean matched = false;
            for (String part : parts) {
                String p = part.trim();
                if (p.equals("!central")) {
                    return false;
                }
                if (p.equals("*") || p.equals("central") || p.equals("external:*")) {
                    matched = true;
                }
            }
            return matched;
        }
        return false;
    }

    private static Path parseLocalRepository(Path settingsFile) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            Document doc = factory.newDocumentBuilder().parse(settingsFile.toFile());

            NodeList nodes = doc.getElementsByTagName("localRepository");
            if (nodes.getLength() > 0) {
                String text = nodes.item(0).getTextContent();
                if (text != null && !text.trim().isEmpty()) {
                    String path = text.trim().replace("${user.home}", System.getProperty("user.home"));
                    return Path.of(path);
                }
            }
        } catch (Exception e) {
            Log.debugf("Failed to parse settings.xml at %s: %s", settingsFile, e.getMessage());
        }
        return null;
    }
}
