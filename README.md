# Free Chat Translator

**Development candidate v0.7.0 — not yet approved for the RuneLite Plugin Hub.**

Free Chat Translator adds **Translate to English** to an eligible incoming player chat message's right-click menu. It uses local, conservative source-language detection and MyMemory's free HTTPS translation service. Translation is **manual**, not automatic, and has **no paid API key**, no profanity filter, and no background translation.

## Using the plugin

1. Launch RuneLite with the plugin in a development environment and enable **Free Chat Translator**.
2. Right-click an incoming player's chat message and choose **Translate to English**. The plugin checks the selected message locally first. Messages recognized as English or of uncertain language get local feedback without contacting MyMemory.
3. To send an eligible foreign-language message for translation, enable **Allow MyMemory translation** in the plugin settings. It is **OFF by default**. Merely enabling it **does not send any chat**.
4. Incoming private/group messages require the **additional, independent** setting **I understand: share private/group chats**, also **OFF by default**. When it is disabled, the menu explains that private/group translation is off. Both permissions and a deliberate message click are needed for sensitive-chat translation.

Eligible incoming player chat can include public chat, private DMs, Friends Chat, Clan, Guest Clan and Group Ironman Clan. It does not translate your outgoing messages, system notifications, or every incoming message automatically. Output is displayed **locally** in RuneLite as an informational chat line, not sent as a game chat message.

The detector is intentionally cautious with short, ambiguous chat, slang and typos, so a message can return an uncertain-language notice rather than a translation. No message is sent in that case.

## Privacy and service limits

**Your selected message text is shared with a third party when a translation request is made.** After a permitted right-click, the plugin submits the body of the selected message to `https://api.mymemory.translated.net/get` using an HTTPS `GET` query parameter named `q`; the request also contains the detected source-to-English language pair. Because the text is in a URL parameter, it may be recorded in service-side logs. MyMemory can observe network information including your IP address. A message that was private between players is **not private from MyMemory** once submitted. Do not translate messages you are unwilling to share.

The plugin does not intentionally attach your RuneScape account credentials or the sender's username, and does not upload chat automatically. It keeps a bounded, temporary in-memory cache for selected-chat verification. Revoking consent prevents new requests and attempts to cancel pending requests, but cannot retract content the third party already received. See [PRIVACY.md](PRIVACY.md) for details.

The free anonymous MyMemory allowance is documented as **5,000 characters/day** and **500 UTF-8 bytes/request**. This plugin does not set a contact email, require a paid key, or bypass service limits. Translation failures can occur if MyMemory is unavailable or has reached its quota.

## Development and local build

The project targets **Java 11** and uses RuneLite's `latest.release` dependency in `build.gradle`. Import the Gradle project into IntelliJ IDEA or another compatible Java/Gradle environment. Use the Gradle **`run`** task to start the development client. If invoking from a terminal with Gradle installed, run `gradle run`. **This source tree does not include a Gradle wrapper or the separate Windows `.bat` launchers.**

The `src/test` folder contains the development entry point. `offline-tests/` contains independently runnable source and synthetic-behavior checks used during development; **those are not equivalent to native RuneLite gameplay tests**.

Development evidence includes an owner-reported v0.7.0 Windows launch and working plugin. In a **controlled manual two-account in-game test**, another player's incoming French public message was translated through the right-click flow into English and displayed locally in RuneLite at 19:34; the owner supplied a screenshot. The owner separately confirmed that a correctly spelled Spanish message translated successfully. A screenshot of a misspelled Spanish word showed MyMemory preserving the unknown word; this is a translation-quality limitation, not proof of automatic typo correction. Earlier accepted menu, consent, and translation tests are retained separately in project archives and are not represented here as new tests. An independent private GitHub Actions Java 11 build compiled the production and test Java sources against real RuneLite dependencies; neither compilation nor the gameplay evidence constitutes Plugin Hub approval.

## Review status

This plugin has **not** been submitted or approved by RuneLite. RuneLite's [rejected-features list](https://github.com/runelite/runelite/wiki/Rejected-or-Rolled-Back-Features) warns against plugins that expose player information over HTTP. Although the plugin uses HTTPS and explicit consent, selected player-chat text still leaves the client. **Those safeguards do not establish RuneLite policy approval.** See [POLICY_REVIEW_REQUIRED.md](POLICY_REVIEW_REQUIRED.md).

The public-facing plugin maintainer name is **Just Saiyan** (GitHub: **iLorne**). The project incorporates material subject to the existing BSD-2-Clause [LICENSE](LICENSE), preserving its original attribution.
