package org.chappiebot.store;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageSerializer;
import dev.langchain4j.data.message.ChatMessageType;
import dev.langchain4j.data.message.Content;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.UserMessage;
import jakarta.inject.Inject;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.List;
import java.util.Map;

/**
 * The Endpoint for the message store
 * @author Phillip Kruger (phillip.kruger@gmail.com)
 */
@Path("/api/store")
public class StoreEndpoint {
    
    @Inject
    StoreManager storeManager;
    
    @GET
    @Path("/most-recent")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getMostRecentChat() {
        return storeManager.getJdbcChatMemoryStore()
            .map(store -> {
                Map<MemorySummary, List<ChatMessage>> map = store.getMostRecentChat();
                if (map.isEmpty()) {
                    return Response.noContent().build();
                }

                Map.Entry<MemorySummary, List<ChatMessage>> entry = map.entrySet().iterator().next();
                MemorySummary s = entry.getKey();

                List<ChatMessage> transformed = entry.getValue().stream()
                    .filter(m -> m != null && m.type() != ChatMessageType.SYSTEM)
                    .map(this::normalizeUserMessage)
                    .toList();

                String messagesJson = ChatMessageSerializer.messagesToJson(transformed);
                String json = buildMostRecentPayload(s, messagesJson);
                return Response.ok(json).build();
            })
            .orElseGet(() -> Response.noContent().build());
    }

    @GET
    @Path("/messages/{memoryId}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getMessages(@PathParam("memoryId") String memoryId) {
        return storeManager.getJdbcChatMemoryStore()
            .map(store -> {
                Map<MemorySummary, List<ChatMessage>> map = store.getChat(memoryId);
                if (map.isEmpty()) {
                    return Response.noContent().build();
                }

                Map.Entry<MemorySummary, List<ChatMessage>> entry = map.entrySet().iterator().next();
                MemorySummary summary = entry.getKey();

                List<ChatMessage> transformed = entry.getValue().stream()
                    .filter(m -> m != null && m.type() != ChatMessageType.SYSTEM)
                    .map(this::normalizeUserMessage)
                    .toList();

                String messagesJson = ChatMessageSerializer.messagesToJson(transformed);
                String json = buildMostRecentPayload(summary, messagesJson); // same structure as /most-recent
                return Response.ok(json).build();
            })
            .orElseGet(() -> Response.noContent().build());
    }
    
    @DELETE
    @Path("/messages/{memoryId}")
    public Response deleteMessages(@PathParam("memoryId") String memoryId) {
        return storeManager.getJdbcChatMemoryStore()
            .map(store -> {
                store.deleteConversation(memoryId);
                return Response.noContent().build();
            })
            .orElseGet(() -> Response.noContent().build());
    }
    
    @GET
    @Path("/chats")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getChats(@QueryParam("filter") String filter,
                            @QueryParam("limit") @DefaultValue("100")int limit, 
                            @QueryParam("offset") @DefaultValue("0") int offset) {
        return storeManager.getJdbcChatMemoryStore()
            .map(store -> {
                List<MemorySummary> listSummaries = store.listSummaries(filter, limit, offset);
                return Response.ok(listSummaries).build();
            })
            .orElseGet(() -> Response.noContent().build());
    }
    
    
    
    @GET
    @Path("/memoryIds")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getAllMemoryIds(){
        if(storeManager.getJdbcChatMemoryStore().isPresent()){
            JdbcChatMemoryStore chatMemoryStore = storeManager.getJdbcChatMemoryStore().get();
            return Response.ok(chatMemoryStore.getAllMemoryIds()).build();
        }
        return Response.noContent().build();
    }
    
    private ChatMessage normalizeUserMessage(ChatMessage m) {
        if (m.type() != ChatMessageType.USER || !(m instanceof UserMessage um)) {
            return m;
        }

        String text = safeText(um);
        if (text != null) {
            String cleaned = cleanUserText(text);
            return userMessageFromText(cleaned);
        }

        if (um.contents() != null && !um.contents().isEmpty()) {
            List<Content> newContents = um.contents().stream()
                .map(c -> (c instanceof TextContent tc)
                    ? new TextContent(cleanUserText(tc.text()))
                    : c)
                .toList();
            return userMessageFromContents(newContents);
        }

        return m;
    }

    private static String safeText(UserMessage um) {
        try {
            String t = um.singleText();
            return t;
        } catch (Throwable ignore) {
            return null;
        }
    }

    private static UserMessage userMessageFromText(String text) {
        try {
            return UserMessage.from(text);
        } catch (Throwable ignore) {
            try {
                return UserMessage.userMessage(text);
            } catch (Throwable ignore2) {
                return UserMessage.from(List.of(new TextContent(text)));
            }
        }
    }

    private static UserMessage userMessageFromContents(List<Content> contents) {
        try {
            return UserMessage.from(contents);
        } catch (Throwable ignore) {
            String joined = contents.stream()
                .map(c -> c instanceof TextContent tc ? tc.text() : "")
                .reduce("", (a, b) -> a.isEmpty() ? b : a + "\n" + b);
            return userMessageFromText(joined);
        }
    }

    private static String cleanUserText(String text) {
        if (text == null) return null;

        // Remove the RAG part
        int ragStartIndex = text.indexOf("[RAG CONTEXT]");
        if(ragStartIndex >= 0){
            text = text.substring(0, ragStartIndex);
        }
        // Remove the User tags
        text = text.replace("[USER PROMPT]", "");
        text = text.replace("[/USER PROMPT]", "");
        return text.trim();
    }
    
    private static String buildMostRecentPayload(MemorySummary s, String messagesJson) {
        String memoryId = jsonString(s.memoryId());
        String niceName = jsonString(s.niceName() == null ? "" : s.niceName());
        String lastActivity = jsonString(s.lastActivity() == null ? "" : s.lastActivity().toString());
        int messageCount = s.messageCount();

        return "{"
            + "\"summary\":{"
                + "\"memoryId\":" + memoryId + ","
                + "\"niceName\":" + niceName + ","
                + "\"lastActivity\":" + lastActivity + ","
                + "\"messageCount\":" + messageCount
            + "},"
            + "\"messages\":" + messagesJson
        + "}";
    }

    private static String jsonString(String s) {
        if (s == null) return "null";
        StringBuilder sb = new StringBuilder(s.length() + 16).append('"');
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            switch (ch) {
                case '"'  -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (ch < 0x20) {
                        sb.append(String.format("\\u%04x", (int) ch));
                    } else {
                        sb.append(ch);
                    }
                }
            }
        }
        return sb.append('"').toString();
    }

}