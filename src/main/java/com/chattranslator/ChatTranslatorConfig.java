package com.chattranslator;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

/** Replaces the legacy Google API-key config with free, explicitly opted-in translation. */
@ConfigGroup("chattranslator-free")
public interface ChatTranslatorConfig extends Config
{
    @ConfigItem(
        keyName = "allowThirdPartyTranslation",
        name = "Allow MyMemory translation",
        description = "Send only a chat message you manually choose to translate to MyMemory. The service receives the text and network information including your IP address.",
        warning = "This feature submits your IP address to a 3rd-party server not controlled or verified by RuneLite developers",
        position = 0
    )
    default boolean allowThirdPartyTranslation()
    {
        return false;
    }

    @ConfigItem(
        keyName = "acknowledgePrivateChatSharing",
        name = "I understand: share private/group chats",
        description = "OFF by default: Private DMs, Friends Chat, Clan, Guest Clan and Group Ironman Clan messages will NOT translate until this box is checked. By checking, you understand that ONLY a message you right-click to translate is sent to the MyMemory third-party service. That message is no longer private from MyMemory. The service also receives network information such as your IP address. Nothing is sent automatically.",
        warning = "This feature submits your IP address to a 3rd-party server not controlled or verified by RuneLite developers",
        position = 1
    )
    default boolean acknowledgePrivateChatSharing()
    {
        return false;
    }

}
