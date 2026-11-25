package com.chattranslator;

import java.util.Locale;

/**
 * Small, predictable labels for the classic OSRS chat font.
 * Use plain ASCII in chat: the game's font can render some Unicode arrows as '?'.
 */
public final class ChatDisplayFormat
{
    private ChatDisplayFormat()
    {
    }

    /** Confirmed English input is handled locally, with no HTTP request. */
    public static String alreadyEnglish()
    {
        return "The selected message is already in English. No translation is needed.";
    }

    /** Unknown means insufficient local evidence, not necessarily a foreign language. */
    public static String unidentifiedLanguage()
    {
        return "Couldn't identify the selected message's language. It may already be English.";
    }

    public static String translation(SourceLanguage source, String translatedText)
    {
        if (source == null || translatedText == null)
        {
            throw new IllegalArgumentException("Source language and translation are required");
        }
        return "[" + source.code().toUpperCase(Locale.ROOT) + " to EN] " + translatedText;
    }
}
