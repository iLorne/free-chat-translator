# Free Chat Translator — privacy notice

**The translation service is external.** Selected message text is shared with MyMemory only following an explicit right-click translation action and the required permissions. The service can log the URL query, message text and network data including IP address. The developer does not control MyMemory's retention or processing.

## Permissions

- **Allow MyMemory translation**: OFF by default. Required before submitting any eligible, locally detected non-English message to MyMemory. Enabling the option alone does not send anything.
- **I understand: share private/group chats**: OFF by default. Separately required for selected incoming private DMs, Friends Chat, Clan, Guest Clan and Group Ironman Clan messages. This does not override the main permission.

Neither English nor uncertain-language messages are sent to MyMemory. They receive local feedback. The plugin does not automatically translate, send other chat or insert text into RuneScape's chat input.

## Data flow

1. Incoming eligible player messages are temporarily cached in memory (maximum **120 records**, maximum **10 minutes**) to verify the line selected in the menu. The plugin does not save this cache to a file.
2. On explicit selection, it validates the message, applies the sensitive-chat permission gate, and detects language locally. A request is made only for supported non-English messages when the main consent is on.
3. An HTTPS `GET` request goes to `https://api.mymemory.translated.net/get` with the selected message body in the **`q` URL parameter** and the source-to-English language pair in **`langpair`**. Neither OSRS login credentials nor sender names are intentionally sent as separate fields, but the chat text itself may contain identifying data.
4. MyMemory receives the text and request metadata (including your network address). It may retain this according to its own policies. Turning permissions OFF cannot recall messages already received.
5. The result displays only as an informational chat line in the local RuneLite client and is **not** transmitted as game chat to another player.

The plugin attempts to cancel outstanding requests if the main permission is revoked. Disabling the additional sensitive-chat permission cancels relevant sensitive requests without canceling a public translation. Stale responses are prevented from displaying after consent revocation.

The anonymous MyMemory service documents **5,000 characters/day** and a maximum **500 UTF-8 bytes per request**. This implementation uses neither contact email nor paid API key, and it does not bypass quotas. For terms and limits see [MyMemory usage limits](https://mymemory.translated.net/doc/usagelimits.php).

## RuneLite policy

RuneLite's [Rejected or Rolled Back Features](https://github.com/runelite/runelite/wiki/Rejected-or-Rolled-Back-Features) currently flags plugins exposing player information over HTTP. External chat translation remains **unapproved for this plugin**; user consent and HTTPS do not guarantee acceptance.
