package org.chappiebot.exception;

/**
 * Contains the suggested fix from AI
 *
 * @author Phillip Kruger (phillip.kruger@gmail.com)
 */
public record SuggestedFix(String response, String explanation, String diff, String suggestedSource) {
}
