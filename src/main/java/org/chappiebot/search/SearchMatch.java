package org.chappiebot.search;

import java.util.Map;

public record SearchMatch(String text, String source, double score, Map<String, Object> metadata) {
}
