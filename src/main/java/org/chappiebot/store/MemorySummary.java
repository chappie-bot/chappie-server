package org.chappiebot.store;

public record MemorySummary(
    String memoryId,
    String niceName,
    java.time.OffsetDateTime lastActivity,
    int messageCount
){}
