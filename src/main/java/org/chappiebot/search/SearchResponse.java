package org.chappiebot.search;

import java.util.List;

public record SearchResponse(List<SearchMatch> results) {
}
