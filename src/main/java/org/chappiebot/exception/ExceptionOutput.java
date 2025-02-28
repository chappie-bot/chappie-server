package org.chappiebot.exception;

/**
 * Contains the suggested fix from AI
 *
 * @author Phillip Kruger (phillip.kruger@gmail.com)
 */
public record ExceptionOutput(String response, String explanation, String diff, String manipulatedContent) {
}
