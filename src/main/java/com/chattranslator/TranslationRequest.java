package com.chattranslator;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pure-Java input checks and URL construction so important limits can be tested offline.
 * No external request is made by this class.
 */
public final class TranslationRequest
{
    static final int MAX_INPUT_BYTES = 500;
    private static final Pattern HTML_ENTITY = Pattern.compile(
        "&(#(?:[xX][0-9a-fA-F]{1,6}|[0-9]{1,7})|[A-Za-z]{2,8});");

    private TranslationRequest() { }

    public static String buildUrl(String message, SourceLanguage source)
    {
        if (source == null)
        {
            throw new IllegalArgumentException("Source language was not automatically identified");
        }
        if (message == null || message.trim().isEmpty())
        {
            throw new IllegalArgumentException("Message is empty");
        }
        if (message.getBytes(StandardCharsets.UTF_8).length > MAX_INPUT_BYTES)
        {
            throw new IllegalArgumentException("Message exceeds MyMemory's 500-byte per-request limit");
        }
        try
        {
            return "https://api.mymemory.translated.net/get?q="
                + URLEncoder.encode(message, StandardCharsets.UTF_8.name())
                + "&langpair=" + URLEncoder.encode(source.code() + "|en", StandardCharsets.UTF_8.name());
        }
        catch (UnsupportedEncodingException e)
        {
            throw new IllegalStateException("Java UTF-8 support missing", e);
        }
    }

    /** Decode one layer only: &amp;lt; stays &lt;, not a second-stage tag. */
    static String decodeHtmlEntitiesOnce(String text)
    {
        Matcher matcher = HTML_ENTITY.matcher(text);
        StringBuffer result = new StringBuffer();
        while (matcher.find())
        {
            String entity = matcher.group(1);
            String replacement = decodeEntity(entity);
            matcher.appendReplacement(result, Matcher.quoteReplacement(
                replacement == null ? matcher.group() : replacement));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private static String decodeEntity(String entity)
    {
        switch (entity)
        {
            case "quot": return "\"";
            case "apos": return "'";
            case "amp": return "&";
            case "lt": return "<";
            case "gt": return ">";
            case "nbsp": return " ";
            default:
                break;
        }
        if (!entity.startsWith("#"))
        {
            return null;
        }
        try
        {
            boolean hex = entity.length() > 2 && (entity.charAt(1) == 'x' || entity.charAt(1) == 'X');
            int codepoint = Integer.parseInt(entity.substring(hex ? 2 : 1), hex ? 16 : 10);
            if (!Character.isValidCodePoint(codepoint)
                || codepoint < 32 || (codepoint >= 0x7F && codepoint <= 0x9F)
                || (codepoint >= 0xD800 && codepoint <= 0xDFFF))
            {
                return null;
            }
            return new String(Character.toChars(codepoint));
        }
        catch (NumberFormatException e)
        {
            return null;
        }
    }

    /** Render API text as plain chat text, never interpret API-provided markup. */
    public static String forChatDisplay(String input)
    {
        if (input == null)
        {
            return "";
        }
        // Decode once, then neutralize any RuneLite-style markup.
        String clean = decodeHtmlEntitiesOnce(input);
        return clean.replace('<', '(').replace('>', ')').replace('\n', ' ').replace('\r', ' ');
    }
}
