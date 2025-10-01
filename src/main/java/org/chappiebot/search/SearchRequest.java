package org.chappiebot.search;

public record SearchRequest(String queryMessage, Integer maxResults, String extension) {
}
