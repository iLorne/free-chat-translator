package com.chattranslator;

import com.google.inject.Provides;
import java.awt.Rectangle;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import javax.inject.Inject;
import net.runelite.api.ChatMessageType;
import net.runelite.api.GameState;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.Client;
import net.runelite.api.MenuAction;
import net.runelite.api.Player;
import net.runelite.api.MenuEntry;
import net.runelite.api.Point;
import net.runelite.api.events.MenuOpened;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetUtil;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.util.Text;
import okhttp3.Call;

/**
 * Opt-in, right-click incoming player chat translation using MyMemory.
 *
 * Adapted from the chat-widget selection approach of the BSD-2-Clause
 * Chat Translator originally by Spencer Imbleau (nuzzles/chat-translator).
 * The release-prep source still requires native acceptance and RuneLite review.
 */
@PluginDescriptor(
    name = "Free Chat Translator",
    description = "Right-click player chat to translate into English with opt-in MyMemory",
    tags = {"chat", "translator", "translate", "language"}
)
public class ChatTranslatorPlugin extends Plugin
{
    @Inject private Client client;
    @Inject private ClientThread clientThread;
    @Inject private ChatTranslatorConfig config;
    @Inject private MyMemoryClient translationClient;

    // Accessed on RuneLite client thread. HTTP callbacks always marshal back to that thread.
    private boolean translating;
    private Call activeCall;
    private ChatMessageSelection.Channel activeChannel;
    private volatile boolean running;
    private long requestGeneration;
    // A consent OFF event invalidates pending callbacks immediately, even if
    // the setting is switched back ON before the queued client-thread task runs.
    private final AtomicLong consentRevocations = new AtomicLong();
    private final AtomicLong privateChatRevocations = new AtomicLong();
    private final ChatMessageSelection recentChat = new ChatMessageSelection();

    @Provides
    ChatTranslatorConfig provideConfig(ConfigManager configManager)
    {
        return configManager.getConfig(ChatTranslatorConfig.class);
    }

    @Override
    protected void startUp()
    {
        running = true;
        requestGeneration++;
        recentChat.clear();
    }

    @Override
    protected void shutDown()
    {
        running = false;
        cancelActiveTranslation();
        recentChat.clear();
    }

    /** Do not preserve public-chat selections or pending results across logout/hops. */
    @Subscribe
    public void onGameStateChanged(GameStateChanged event)
    {
        GameState state = event.getGameState();
        if (state == GameState.LOGIN_SCREEN || state == GameState.HOPPING
            || state == GameState.CONNECTION_LOST)
        {
            cancelActiveTranslation();
            recentChat.clear();
        }
    }

    /**
     * Withdrawing consent cancels a pending MyMemory request and invalidates its
     * callback. RuneLite config events can originate outside the client thread,
     * so the state change is marshalled onto the same thread as requests.
     */
    @Subscribe
    public void onConfigChanged(ConfigChanged event)
    {
        if (!"chattranslator-free".equals(event.getGroup()))
        {
            return;
        }
        if ("allowThirdPartyTranslation".equals(event.getKey())
            && !Boolean.parseBoolean(event.getNewValue()))
        {
            consentRevocations.incrementAndGet();
            clientThread.invokeLater(this::cancelActiveTranslation);
        }
        if ("acknowledgePrivateChatSharing".equals(event.getKey())
            && !Boolean.parseBoolean(event.getNewValue()))
        {
            // Clearing this checkbox revokes sensitive-chat consent immediately,
            // even if it is re-enabled before queued work executes.
            privateChatRevocations.incrementAndGet();
            clientThread.invokeLater(this::cancelSensitiveTranslation);
        }
    }

    /** Revoking sensitive permission cannot cancel a public-chat request. */
    private void cancelSensitiveTranslation()
    {
        if (TranslationConsent.shouldCancelAfterPrivateRevocation(activeChannel))
        {
            cancelActiveTranslation();
        }
    }

    /** Run on the RuneLite client thread. Never emit a result from an old request. */
    private void cancelActiveTranslation()
    {
        ++requestGeneration;
        Call call = activeCall;
        activeCall = null;
        activeChannel = null;
        translating = false;
        if (call != null)
        {
            call.cancel();
        }
    }

    /** Keep only incoming player chat in a short-lived, in-memory allow-list. */
    @Subscribe
    public void onChatMessage(ChatMessage event)
    {
        ChatMessageSelection.Channel channel = channelFor(event.getType());
        if (channel == null)
        {
            return;
        }
        String name = event.getName();
        String message = event.getMessage();
        Player localPlayer = client.getLocalPlayer();
        if (name == null || message == null || localPlayer == null || localPlayer.getName() == null)
        {
            return;
        }
        recentChat.record(channel, Text.removeTags(name), Text.removeTags(message),
            Text.removeTags(localPlayer.getName()), System.currentTimeMillis());
    }

    private static ChatMessageSelection.Channel channelFor(ChatMessageType type)
    {
        if (type == null)
        {
            return null;
        }
        switch (type)
        {
            case PUBLICCHAT:
            case MODCHAT:
                return ChatMessageSelection.Channel.PUBLIC;
            case PRIVATECHAT:
            case MODPRIVATECHAT:
                return ChatMessageSelection.Channel.PRIVATE;
            case FRIENDSCHAT:
                return ChatMessageSelection.Channel.FRIENDS_CHAT;
            case CLAN_CHAT:
                return ChatMessageSelection.Channel.CLAN;
            case CLAN_GUEST_CHAT:
                return ChatMessageSelection.Channel.GUEST_CLAN;
            case CLAN_GIM_CHAT:
                return ChatMessageSelection.Channel.GIM_CLAN;
            default:
                // Outgoing DMs, own messages, notifications, trade & system
                // messages cannot be sent for translation.
                return null;
        }
    }

    /**
     * Use the final right-click menu instead of a single specific "Report"
     * action. RuneLite supports editing menu entries in MenuOpened; other
     * chat plugins may customize Report, but a real chat row still has its
     * own CHATBOX sender widget and message-content child.
     *
     * Fail closed if the inspected menu identifies conflicting messages.
     * No network request occurs until a user clicks a verified row entry.
     */
    @Subscribe
    public void onMenuOpened(MenuOpened event)
    {
        if (!running || client.getGameState() != GameState.LOGGED_IN)
        {
            return;
        }
        // Some chat layouts expose no ordinary actions in a newly opened menu.
        // The hovered-row path must still be allowed to run in that case.
        final MenuEntry[] entries = event.getMenuEntries() == null
            ? new MenuEntry[0] : event.getMenuEntries();
        ChatMessageSelection.Selection selected = null;
        boolean ambiguity = false;
        for (MenuEntry entry : entries)
        {
            if (entry == null || WidgetUtil.componentToInterface(entry.getParam1()) != InterfaceID.CHATBOX
                || WidgetUtil.componentToId(entry.getParam1()) < WidgetUtil.componentToId(InterfaceID.Chatbox.LINE0))
            {
                continue;
            }
            final ChatMessageSelection.Selection found = selectMessageFromChatRowEntry(entry);
            if (found == null)
            {
                continue;
            }
            if (selected != null && (selected.channel != found.channel || !selected.text.equals(found.text)))
            {
                ambiguity = true;
                break;
            }
            selected = found;
        }
        // Restore the hover/Y-row scan that actually worked in the v0.6.4
        // in-game self-chat test. The newer Report-row mapping is useful but
        // not guaranteed to exist on every menu/layout. The fallback can only
        // select a sender + entire message that matches our typed event cache.
        // If distinct selections are identified, fail closed rather than guess.
        final ChatMessageSelection.Selection hover = selectMessageFromHoveredRow();
        if (hover != null)
        {
            if (selected != null && (selected.channel != hover.channel || !selected.text.equals(hover.text)))
            {
                ambiguity = true;
            }
            else
            {
                selected = hover;
            }
        }
        if (ambiguity || selected == null)
        {
            return;
        }

        final ChatMessageSelection.Selection verified = selected;
        final long menuGeneration = requestGeneration;
        final long menuConsentEpoch = consentRevocations.get();
        final long menuPrivateEpoch = privateChatRevocations.get();
        final boolean locked = verified.channel.requiresPrivacyAcknowledgment()
            && !config.acknowledgePrivateChatSharing();
        client.getMenu().createMenuEntry(-1)
            .setType(MenuAction.RUNELITE)
            .setOption(locked ? "Private/group translation OFF (see settings)" : "Translate to English")
            .setTarget("")
            .onClick(menuEntry ->
            {
                if (running && requestGeneration == menuGeneration
                    && consentRevocations.get() == menuConsentEpoch
                    && privateChatRevocations.get() == menuPrivateEpoch
                    && client.getGameState() == GameState.LOGGED_IN)
                {
                    translateSelected(verified);
                }
            });
    }

    /**
     * Conservative fallback: use the actual chat widget row at the cursor,
     * exactly as the v0.6.4 successful developer build did. The UI may render
     * a complete line or separate sender/message pieces. No arbitrary widget
     * text is eligible unless an incoming typed chat event matches exactly.
     */
    private ChatMessageSelection.Selection selectMessageFromHoveredRow()
    {
        final Widget scroll = client.getWidget(InterfaceID.Chatbox.SCROLLAREA);
        final Point mouse = client.getMouseCanvasPosition();
        if (scroll == null || scroll.isHidden() || mouse == null)
        {
            return null;
        }
        final Rectangle area = scroll.getBounds();
        if (area == null || !area.contains(mouse.getX(), mouse.getY()))
        {
            return null;
        }
        final Set<Widget> seen = Collections.newSetFromMap(new IdentityHashMap<Widget, Boolean>());
        final StringBuilder combined = new StringBuilder();
        ChatMessageSelection.Selection match = null;
        boolean hoveringTextWidget = false;
        final Widget[][] groups = {
            scroll.getChildren(), scroll.getDynamicChildren(),
            scroll.getStaticChildren(), scroll.getNestedChildren()
        };
        for (Widget[] children : groups)
        {
            if (children == null)
            {
                continue;
            }
            for (Widget child : children)
            {
                if (child == null || !seen.add(child) || child.isHidden())
                {
                    continue;
                }
                final Rectangle bounds = child.getBounds();
                if (bounds == null || mouse.getY() < bounds.getMinY()
                    || mouse.getY() >= bounds.getMaxY())
                {
                    continue;
                }
                final String raw = child.getText();
                if (raw == null || raw.isEmpty())
                {
                    continue;
                }
                final String text = Text.removeTags(raw).trim();
                if (!text.isEmpty() && ChatRowHitTest.containsHorizontal(bounds, mouse.getX()))
                {
                    hoveringTextWidget = true;
                }
                if (text.isEmpty())
                {
                    continue;
                }
                if (combined.length() != 0)
                {
                    combined.append(' ');
                }
                combined.append(text);
                final ChatMessageSelection.Selection fromPart = recentChat.matchRenderedLine(
                    text, System.currentTimeMillis());
                if (fromPart != null)
                {
                    if (match != null && (match.channel != fromPart.channel || !match.text.equals(fromPart.text)))
                    {
                        return null;
                    }
                    match = fromPart;
                }
            }
        }
        // The row must be clicked over a real text widget, not blank space
        // elsewhere in the chat scroll area on the same Y coordinate.
        if (!hoveringTextWidget)
        {
            return null;
        }
        final ChatMessageSelection.Selection fromCombined = recentChat.matchRenderedLine(
            combined.toString(), System.currentTimeMillis());
        if (fromCombined != null)
        {
            if (match != null && (match.channel != fromCombined.channel || !match.text.equals(fromCombined.text)))
            {
                return null;
            }
            match = fromCombined;
        }
        return match;
    }

    /**
     * Map a chat-row menu entry's static sender widget to its dynamic
     * message text widget, as RuneLite Chat History does. Exact text and
     * sender must match a recently observed supported incoming event.
     */
    private ChatMessageSelection.Selection selectMessageFromChatRowEntry(MenuEntry entry)
    {
        final int component = entry.getParam1();
        final int childId = WidgetUtil.componentToId(component);
        final int firstRowId = WidgetUtil.componentToId(InterfaceID.Chatbox.LINE0);
        final Widget senderWidget = client.getWidget(InterfaceID.CHATBOX, childId);
        if (senderWidget == null)
        {
            return null;
        }
        final Widget parent = senderWidget.getParent();
        if (parent == null || parent.getId() != InterfaceID.Chatbox.SCROLLAREA)
        {
            return null;
        }
        final long messageChildIndex = ((long) childId - firstRowId) * 4L + 1L;
        if (messageChildIndex < 0 || messageChildIndex > Integer.MAX_VALUE)
        {
            return null;
        }
        final Widget messageWidget = parent.getChild((int) messageChildIndex);
        if (messageWidget == null || messageWidget.getText() == null)
        {
            return null;
        }
        final String message = Text.removeTags(messageWidget.getText());
        final long now = System.currentTimeMillis();
        ChatMessageSelection.Selection selected = recentChat.matchRenderedParts(
            Text.removeTags(senderWidget.getText()), message, now);
        // In some layouts the static widget's text may differ from the menu
        // target. Target is still verified against the same incoming cache.
        if (selected == null && entry.getTarget() != null)
        {
            selected = recentChat.matchRenderedParts(Text.removeTags(entry.getTarget()), message, now);
        }
        return selected;
    }

    private void translateSelected(ChatMessageSelection.Selection selected)
    {
        if (!running || client.getGameState() != GameState.LOGGED_IN)
        {
            return;
        }
        final boolean sensitive = selected.channel.requiresPrivacyAcknowledgment();
        if (sensitive && !config.acknowledgePrivateChatSharing())
        {
            showMessage(selected.channel, "Private/group translations are OFF. In Free Chat Translator settings, tick "
                + "'I understand: share private/group chats'. Selected text goes to MyMemory; "
                + "nothing is sent automatically.");
            return;
        }
        final String selectedMessage = selected.text;
        // Detection is fully local and only runs after an explicit menu click.
        LocalLanguageDetector.Result guess = LocalLanguageDetector.detect(selectedMessage);
        if (guess.kind() == LocalLanguageDetector.Result.Kind.ENGLISH)
        {
            showMessage(selected.channel, ChatDisplayFormat.alreadyEnglish());
            return;
        }
        if (guess.kind() == LocalLanguageDetector.Result.Kind.UNKNOWN)
        {
            showMessage(selected.channel, ChatDisplayFormat.unidentifiedLanguage());
            return;
        }
        if (!config.allowThirdPartyTranslation())
        {
            showMessage(selected.channel, "Enable 'Allow MyMemory translation' in the plugin settings first.");
            return;
        }
        final SourceLanguage source = guess.language();
        if (translating)
        {
            showMessage(selected.channel, "A translation is already in progress. Try again shortly.");
            return;
        }
        final long consentEpoch = consentRevocations.get();
        final long privateEpoch = privateChatRevocations.get();
        translating = true;
        final long requestId = ++requestGeneration;
        activeChannel = selected.channel;

        try
        {
            // Re-check immediately before dispatch: consent may have changed
            // between the first check and local language detection.
            if (!config.allowThirdPartyTranslation() || consentEpoch != consentRevocations.get()
                || (sensitive && (!config.acknowledgePrivateChatSharing()
                    || privateEpoch != privateChatRevocations.get())))
            {
                cancelActiveTranslation();
                showMessage(selected.channel, "MyMemory permission was disabled. Nothing was sent.");
                return;
            }
            activeCall = translationClient.translate(selectedMessage, source, new MyMemoryClient.TranslationCallback()
            {
                @Override
                public void onSuccess(String translation)
                {
                    finish(requestId, consentEpoch, privateEpoch, selected.channel,
                        ChatDisplayFormat.translation(source, translation));
                }

                @Override
                public void onError(String reason)
                {
                    finish(requestId, consentEpoch, privateEpoch, selected.channel,
                        "Translation failed: " + reason);
                }
            });
        }
        catch (IllegalArgumentException e)
        {
            requestGeneration++;
            activeChannel = null;
            translating = false;
            activeCall = null;
            showMessage(selected.channel, "Translation not sent: " + e.getMessage());
        }
        catch (RuntimeException e)
        {
            requestGeneration++;
            activeChannel = null;
            translating = false;
            activeCall = null;
            showMessage(selected.channel, "Translation could not be started.");
        }
    }

    /** Finished HTTP callbacks must pass both current consent epochs. */
    private void finish(long requestId, long consentEpoch, long privateEpoch,
                        ChatMessageSelection.Channel channel, String message)
    {
        clientThread.invokeLater(() ->
        {
            if (!running || requestGeneration != requestId
                || consentEpoch != consentRevocations.get()
                || !config.allowThirdPartyTranslation()
                || (channel.requiresPrivacyAcknowledgment()
                    && (!config.acknowledgePrivateChatSharing()
                        || privateEpoch != privateChatRevocations.get())))
            {
                return;
            }
            activeCall = null;
            activeChannel = null;
            translating = false;
            // Local-only chat insertion; never a Jagex chat packet.
            client.addChatMessage(outputType(channel),
                ChatMessageSelection.LOCAL_TRANSLATION_SENDER, message, null);
        });
    }

    private static ChatMessageType outputType(ChatMessageSelection.Channel channel)
    {
        switch (channel)
        {
            case PRIVATE:
                return ChatMessageType.PRIVATECHAT;
            case FRIENDS_CHAT:
                return ChatMessageType.FRIENDSCHAT;
            case CLAN:
                return ChatMessageType.CLAN_CHAT;
            case GUEST_CLAN:
                return ChatMessageType.CLAN_GUEST_CHAT;
            case GIM_CLAN:
                return ChatMessageType.CLAN_GIM_CHAT;
            default:
                return ChatMessageType.PUBLICCHAT;
        }
    }

    private void showMessage(ChatMessageSelection.Channel channel, String message)
    {
        final long statusGeneration = requestGeneration;
        clientThread.invokeLater(() ->
        {
            // A queued local status must not reappear after a logout,
            // world-hop, plugin shutdown, or newer translation request.
            if (running && client.getGameState() == GameState.LOGGED_IN
                && requestGeneration == statusGeneration)
            {
                // Route local status to the selected chat channel so users
                // on Private/Clan tabs can actually see why translation is off.
                // This only inserts a local chat line; no Jagex packet is sent.
                client.addChatMessage(outputType(channel),
                    ChatMessageSelection.LOCAL_TRANSLATION_SENDER, message, null);
            }
        });
    }

}
