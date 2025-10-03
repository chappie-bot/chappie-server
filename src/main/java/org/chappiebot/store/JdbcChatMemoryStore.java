package org.chappiebot.store;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageDeserializer;
import dev.langchain4j.data.message.ChatMessageSerializer;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;

/**
 * Implements ChatMemoryStore to use the already existing DB
 * @author Phillip Kruger (phillip.kruger@gmail.com)
 */
public class JdbcChatMemoryStore implements ChatMemoryStore {
    
    private final DataSource ds;
    private final String table;
    private final String nameTable;
            
    public JdbcChatMemoryStore(DataSource ds, String table, String nameTable) {
        this.ds = ds;
        this.table = table;
        this.nameTable = nameTable;
    }
    
    public void setNiceName(String memoryId, String niceName) {
        if (niceName == null || niceName.isBlank()) return;
        String clean = niceName.strip();
        if (clean.length() > 200) clean = clean.substring(0, 200);

        String sql = "INSERT INTO " + nameTable + " (memory_id, nice_name) " +
                     "VALUES (?, ?) " +
                     "ON CONFLICT (memory_id) DO UPDATE SET nice_name = EXCLUDED.nice_name";
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, memoryId);
            ps.setString(2, clean);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to set nice name for " + memoryId, e);
        }
    }
    
    public String getNiceName(String memoryId) {
        String sql = "SELECT nice_name FROM " + nameTable + " WHERE memory_id = ?";
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, memoryId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString(1);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to get nice name for " + memoryId, e);
        }
        return null;
    }
    
    public void deleteNiceName(String memoryId) {
        String sql = "DELETE FROM " + nameTable + " WHERE memory_id = ?";
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, memoryId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to delete nice name for " + memoryId, e);
        }
    }

    public List<MemorySummary> listSummaries(String nameFilterILike, int limit, int offset) {
        String base = """
            SELECT m.memory_id,
                   COALESCE(n.nice_name, '') AS nice_name,
                   MAX(GREATEST(m.last_modified, m.created_at)) AS last_activity,
                   COUNT(*) AS message_count
            FROM %s m
            LEFT JOIN %s n ON n.memory_id = m.memory_id
            WHERE m.memory_id IS NOT NULL
            """.formatted(table, nameTable);

        String filter = (nameFilterILike != null && !nameFilterILike.isBlank())
                ? " AND (n.nice_name ILIKE ? OR m.memory_id ILIKE ?) "
                : "";

        String tail = """
            GROUP BY m.memory_id, n.nice_name
            ORDER BY last_activity DESC, m.memory_id ASC
            LIMIT ? OFFSET ?
            """;

        String sql = base + filter + tail;

        List<MemorySummary> out = new ArrayList<>();
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {

            int i = 1;
            if (!filter.isEmpty()) {
                String like = "%" + nameFilterILike.strip() + "%";
                ps.setString(i++, like);
                ps.setString(i++, like);
            }
            ps.setInt(i++, limit <= 0 ? 50 : limit);
            ps.setInt(i, Math.max(offset, 0));

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new MemorySummary(
                        rs.getString("memory_id"),
                        rs.getString("nice_name"),
                        rs.getObject("last_activity", java.time.OffsetDateTime.class),
                        rs.getInt("message_count")
                    ));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to list memory summaries", e);
        }
        return out;
    }
    
    public List<String> getAllMemoryIds() {
        String sql =
            "SELECT memory_id " +
            "FROM " + table + " " +
            "WHERE memory_id IS NOT NULL " +
            "GROUP BY memory_id " +
            "ORDER BY MAX(GREATEST(last_modified, created_at)) DESC, memory_id ASC";

        List<String> ids = new ArrayList<>();
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) ids.add(rs.getString(1));
        } catch (SQLException e) {
            throw new RuntimeException("Failed to load memory IDs", e);
        }
        return ids;
    }
    
    public Map<MemorySummary, List<ChatMessage>> getMostRecentChat(){
        String topSql = """
            SELECT m.memory_id,
                   COALESCE(n.nice_name, '') AS nice_name,
                   MAX(GREATEST(m.last_modified, m.created_at)) AS last_activity,
                   COUNT(*) AS message_count
            FROM %s m
            LEFT JOIN %s n ON n.memory_id = m.memory_id
            WHERE m.memory_id IS NOT NULL
            GROUP BY m.memory_id, n.nice_name
            ORDER BY last_activity DESC, m.memory_id ASC
            LIMIT 1
            """.formatted(table, nameTable);

        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(topSql);
             ResultSet rs = ps.executeQuery()) {

            if (!rs.next()) {
                return java.util.Collections.emptyMap();
            }

            String memoryId = rs.getString("memory_id");
            String niceName = rs.getString("nice_name");
            java.time.OffsetDateTime lastActivity =
                    rs.getObject("last_activity", java.time.OffsetDateTime.class);
            int messageCount = rs.getInt("message_count");

            MemorySummary summary = new MemorySummary(memoryId, niceName, lastActivity, messageCount);

            String msgSql = "SELECT message_json FROM " + table +
                            " WHERE memory_id = ? ORDER BY msg_index ASC";

            List<ChatMessage> messages = new ArrayList<>();
            try (PreparedStatement pm = c.prepareStatement(msgSql)) {
                pm.setString(1, memoryId);
                try (ResultSet rm = pm.executeQuery()) {
                    while (rm.next()) {
                        messages.add(ChatMessageDeserializer.messageFromJson(rm.getString(1)));
                    }
                }
            }

            return java.util.Collections.singletonMap(summary, messages);

        } catch (SQLException e) {
            throw new RuntimeException("Failed to load most recent chat", e);
        }
    }
    
    public Map<MemorySummary, List<ChatMessage>> getChat(String memoryId) {
        String summarySql = """
            SELECT m.memory_id,
                   COALESCE(n.nice_name, '') AS nice_name,
                   MAX(GREATEST(m.last_modified, m.created_at)) AS last_activity,
                   COUNT(*) AS message_count
            FROM %s m
            LEFT JOIN %s n ON n.memory_id = m.memory_id
            WHERE m.memory_id = ?
            GROUP BY m.memory_id, n.nice_name
            """.formatted(table, nameTable);

        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(summarySql)) {

            ps.setString(1, memoryId);

            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return java.util.Collections.emptyMap();
                }

                String id = rs.getString("memory_id");
                String niceName = rs.getString("nice_name");
                java.time.OffsetDateTime lastActivity =
                        rs.getObject("last_activity", java.time.OffsetDateTime.class);
                int messageCount = rs.getInt("message_count");

                MemorySummary summary = new MemorySummary(id, niceName, lastActivity, messageCount);

                // Load the full message list for that memory, in order
                String msgSql = "SELECT message_json FROM " + table +
                                " WHERE memory_id = ? ORDER BY msg_index ASC";

                List<ChatMessage> messages = new ArrayList<>();
                try (PreparedStatement pm = c.prepareStatement(msgSql)) {
                    pm.setString(1, id);
                    try (ResultSet rm = pm.executeQuery()) {
                        while (rm.next()) {
                            messages.add(ChatMessageDeserializer.messageFromJson(rm.getString(1)));
                        }
                    }
                }

                return java.util.Collections.singletonMap(summary, messages);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to load chat for " + memoryId, e);
        }
    }
    
    @Override
    public List<ChatMessage> getMessages(Object memoryId) {
        String sql = "SELECT message_json FROM " + table + " WHERE memory_id = ? ORDER BY msg_index ASC";
        List<ChatMessage> out = new ArrayList<>();
        try (Connection c = ds.getConnection();
            PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, String.valueOf(memoryId));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String json = rs.getString(1);
                    out.add(ChatMessageDeserializer.messageFromJson(json));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to load chat memory for " + memoryId, e);
        }
        return out;
    }
    
    public void deleteConversation(String memoryId) {
        String delMsgs = "DELETE FROM " + table + " WHERE memory_id = ?";
        String delName = "DELETE FROM " + nameTable + " WHERE memory_id = ?";

        try (Connection c = ds.getConnection()) {
            c.setAutoCommit(false);

            try (PreparedStatement pm = c.prepareStatement(delMsgs)) {
                pm.setString(1, memoryId);
                pm.executeUpdate();
            }

            try (PreparedStatement pn = c.prepareStatement(delName)) {
                pn.setString(1, memoryId);
                pn.executeUpdate();
            }

            c.commit();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to delete conversation for " + memoryId, e);
        }
    }
    
    @Override
    public void updateMessages(Object memoryId, List<ChatMessage> messages) {
        // We rewrite the full set; simpler and correct for windowed memory.
        String deleteSql = "DELETE FROM " + table + " WHERE memory_id = ?";
        String insertSql = "INSERT INTO " + table + " (memory_id, msg_index, message_json, last_modified) VALUES (?, ?, ?::jsonb, now())";
        
        try (Connection c = ds.getConnection()) {
            c.setAutoCommit(false);
            try (PreparedStatement del = c.prepareStatement(deleteSql)) {
                del.setString(1, String.valueOf(memoryId));
                del.executeUpdate();
            }
            try (PreparedStatement ins = c.prepareStatement(insertSql)) {
                for (int i = 0; i < messages.size(); i++) {
                    ins.setString(1, String.valueOf(memoryId));
                    ins.setInt(2, i);
                    ins.setString(3, ChatMessageSerializer.messageToJson(messages.get(i)));
                    ins.addBatch();
                }
                ins.executeBatch();
            }
            c.commit();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to update chat memory for " + memoryId, e);
        }
    }

    @Override
    public void deleteMessages(Object memoryId) {
        String sql = "DELETE FROM " + table + " WHERE memory_id = ?";
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, String.valueOf(memoryId));
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to delete chat memory for " + memoryId, e);
        }
    }
}
