package com.chattranslator;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.regex.Pattern;

/**
 * Transient, bounded allow-list of incoming player chat, tagged by channel.
 * Event filtering occurs in ChatTranslatorPlugin. Only supported player-chat
 * types are recorded; notifications, outbound DMs and own chat are ignored.
 * Nothing is sent to MyMemory here, and no file or telemetry is written.
 * The exact rendered row must match an observed event. Ambiguous duplicates
 * across channels are rejected, especially on the mixed All tab.
 */
public final class ChatMessageSelection
{
    /** This synthetic label is not a valid OSRS username and is never transmitted to Jagex. */
    public static final String LOCAL_TRANSLATION_SENDER = "Translator (local)";
    /** Also reject locally inserted output from earlier development versions. */
    private static final String LEGACY_LOCAL_TRANSLATION_SENDER = "Chat Translator (local)";
    // RuneLite timestamps sometimes appear before the visible sender name.
    private static final Pattern CHAT_TIMESTAMP = Pattern.compile(
        "^(?:\\[\\d{1,2}:\\d{2}(?::\\d{2})?\\]|\\d{1,2}:\\d{2}(?::\\d{2})?)\\s+");
    private static final int MAX_RECORDS = 120;
    private static final long MAX_AGE_MS = 10 * 60 * 1000L;
    private final Deque<Line> recent = new ArrayDeque<>();

    public enum Channel
    {
        PUBLIC, PRIVATE, FRIENDS_CHAT, CLAN, GUEST_CLAN, GIM_CLAN;

        public boolean requiresPrivacyAcknowledgment()
        {
            return this != PUBLIC;
        }
    }

    public static final class Selection
    {
        public final String text;
        public final Channel channel;

        private Selection(String text, Channel channel)
        {
            this.text = text;
            this.channel = channel;
        }
    }

    /** Record only selected incoming channel types, after event filtering. */
    public void record(Channel channel, String sender, String text, String localPlayerName, long nowMs)
    {
        String safeSender = normalize(sender);
        String safeText = normalize(text);
        String safeLocalPlayer = normalize(localPlayerName);
        if (channel == null || safeSender.isEmpty() || safeText.isEmpty() || safeLocalPlayer.isEmpty()
            || safeSender.equalsIgnoreCase(safeLocalPlayer)
            || safeSender.equalsIgnoreCase(LOCAL_TRANSLATION_SENDER)
            || safeSender.equalsIgnoreCase(LEGACY_LOCAL_TRANSLATION_SENDER)
            || safeSender.contains(":"))
        {
            return;
        }
        prune(nowMs);
        recent.addLast(new Line(safeSender, safeText, channel, nowMs));
        while (recent.size() > MAX_RECORDS)
        {
            recent.removeFirst();
        }
    }

    /**
     * Match an observed message against the sender and contents of a specific
     * RuneLite Report menu chat row. If the same name/text occurs in different
     * channels, the tab alone cannot prove provenance: fail closed.
     */
    public Selection matchRenderedParts(String renderedSender, String renderedMessage, long nowMs)
    {
        prune(nowMs);
        String sender = normalize(renderedSender);
        if (sender.endsWith(":"))
        {
            sender = sender.substring(0, sender.length() - 1).trim();
        }
        String message = normalize(renderedMessage);
        if (sender.isEmpty() || message.isEmpty())
        {
            return null;
        }
        Selection selected = null;
        for (Line candidate : recent)
        {
            boolean exactSender = candidate.sender.equalsIgnoreCase(sender);
            // Incoming private chat may render the sender as "From Name".
            boolean fromSender = candidate.channel == Channel.PRIVATE
                && sender.length() > 5 && sender.regionMatches(true, 0, "From ", 0, 5)
                && candidate.sender.equalsIgnoreCase(sender.substring(5));
            if ((exactSender || fromSender) && candidate.text.equals(message))
            {
                if (selected != null && selected.channel != candidate.channel)
                {
                    return null;
                }
                selected = new Selection(candidate.text, candidate.channel);
            }
        }
        return selected;
    }

    /**
     * Match a completely rendered chat line from the hovered widget row.
     * Only names/texts already seen in an allowed typed chat event can match;
     * never use the rendered line itself as the network request payload.
     */
    public Selection matchRenderedLine(String renderedLine, long nowMs)
    {
        String line = CHAT_TIMESTAMP.matcher(normalize(renderedLine)).replaceFirst("");
        int separator = line.indexOf(':');
        if (separator <= 0 || separator >= line.length() - 1)
        {
            return null;
        }
        return matchRenderedParts(line.substring(0, separator), line.substring(separator + 1), nowMs);
    }

    int size()
    {
        return recent.size();
    }

    public void clear()
    {
        recent.clear();
    }

    private void prune(long nowMs)
    {
        while (!recent.isEmpty()
            && (nowMs < recent.peekFirst().timestampMs
                || nowMs - recent.peekFirst().timestampMs > MAX_AGE_MS))
        {
            recent.removeFirst();
        }
    }

    private static String normalize(String text)
    {
        return text == null ? "" : text.replace('\u00a0', ' ').trim().replaceAll("\\s+", " ");
    }

    private static final class Line
    {
        private final String sender;
        private final String text;
        private final Channel channel;
        private final long timestampMs;

        private Line(String sender, String text, Channel channel, long timestampMs)
        {
            this.sender = sender;
            this.text = text;
            this.channel = channel;
            this.timestampMs = timestampMs;
        }
    }
}
