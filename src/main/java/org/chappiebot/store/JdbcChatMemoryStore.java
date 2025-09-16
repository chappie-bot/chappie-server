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
import javax.sql.DataSource;

/**
 * Implements ChatMemoryStore to use the already existing DB
 * @author Phillip Kruger (phillip.kruger@gmail.com)
 */
public class JdbcChatMemoryStore implements ChatMemoryStore {
    
    private final DataSource ds;
    private final String table;
    
    public JdbcChatMemoryStore(DataSource ds, String table) {
        this.ds = ds;
        this.table = table;
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
    
    @Override
    public void updateMessages(Object memoryId, List<ChatMessage> messages) {
        // We rewrite the full set; simpler and correct for windowed memory.
        String deleteSql = "DELETE FROM " + table + " WHERE memory_id = ?";
        String insertSql = "INSERT INTO " + table + " (memory_id, msg_index, message_json) VALUES (?, ?, ?::jsonb)";

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
