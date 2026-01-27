package org.chappiebot.rag;

import java.util.List;

public class RagEvalCase {
    public String id;
    public String query;
    public Integer maxResults;
    public String restrictToExtension; // nullable
    public Assertions assertions;

    public static class Assertions {
        // Any returned repo_path ends with one of these
        public List<String> anyRepoPathEndsWith;

        // Any returned repo_path contains one of these substrings (case-insensitive)
        public List<String> anyRepoPathContains;

        // Require there exists a match within <= rank with score >= score
        // Example: { "rank": 10, "score": 0.82 }
        public MinScoreAtRankLe minScoreAtRankLe;

        // Optional: require at least N matches total
        public Integer minMatches;
    }

    public static class MinScoreAtRankLe {
        public Integer rank;
        public Double score;
    }
}
