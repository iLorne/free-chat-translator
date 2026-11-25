package com.chattranslator;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

/** Development launcher: testing in-game must be done by the user. */
public final class ChatTranslatorPluginTest
{
    public static void main(String[] args) throws Exception
    {
        ExternalPluginManager.loadBuiltin(ChatTranslatorPlugin.class);
        RuneLite.main(args);
    }
}
