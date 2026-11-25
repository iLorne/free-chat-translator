package com.chattranslator;

/** Pure decision rule shared by runtime consent handling and regression tests. */
public final class TranslationConsent
{
    private TranslationConsent() { }

    public static boolean shouldCancelAfterPrivateRevocation(ChatMessageSelection.Channel channel)
    {
        return channel != null && channel.requiresPrivacyAcknowledgment();
    }
}
