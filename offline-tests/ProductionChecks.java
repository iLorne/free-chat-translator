package com.chattranslator;

import java.nio.charset.StandardCharsets;

/** Tests input limits and encoding without Gradle, RuneLite, network or a game account. */
public final class ProductionChecks
{
    private static int checks;

    private static void check(boolean condition, String reason)
    {
        checks++;
        if (!condition)
        {
            throw new AssertionError(reason);
        }
    }

    private static void mustReject(String text, SourceLanguage lang)
    {
        boolean rejected = false;
        try
        {
            TranslationRequest.buildUrl(text, lang);
        }
        catch (IllegalArgumentException expected)
        {
            rejected = true;
        }
        check(rejected, "Expected rejection");
    }

    private static void expectLanguage(String text, SourceLanguage language)
    {
        LocalLanguageDetector.Result result = LocalLanguageDetector.detect(text);
        check(result.kind() == LocalLanguageDetector.Result.Kind.SOURCE && result.language() == language,
            "Expected " + language + " for: " + text + "; got " + result.kind() + " " + result.language());
    }

    private static void expectKind(String text, LocalLanguageDetector.Result.Kind kind)
    {
        LocalLanguageDetector.Result result = LocalLanguageDetector.detect(text);
        check(result.kind() == kind, "Expected " + kind + " for: " + text + "; got " + result.kind());
    }

    public static void main(String[] args)
    {
        String spanish = TranslationRequest.buildUrl("¿Alguien quiere matar a Vorkath?", SourceLanguage.SPANISH);
        check(spanish.startsWith("https://api.mymemory.translated.net/get?q="), "HTTPS endpoint");
        check(spanish.contains("langpair=es%7Cen"), "Spanish-to-English language pair");
        check(spanish.contains("%C2%BF"), "UTF-8 encoding");
        check(!spanish.contains("key="), "No API credentials");
        mustReject("", SourceLanguage.SPANISH);
        mustReject("   ", SourceLanguage.SPANISH);
        mustReject(null, SourceLanguage.SPANISH);
        mustReject("hello", null);
        mustReject("x".repeat(501), SourceLanguage.SPANISH);
        check(TranslationRequest.buildUrl("x".repeat(500), SourceLanguage.SPANISH).contains("q="), "500 bytes accepted");
        mustReject("😀".repeat(126), SourceLanguage.SPANISH);
        check(TranslationRequest.buildUrl("😀".repeat(125), SourceLanguage.SPANISH).contains("q="), "500 UTF-8 bytes accepted");
        check("It's fine".equals(TranslationRequest.forChatDisplay("It&#39;s fine")), "HTML apostrophe");
        check("(col=red)test(/col)".equals(TranslationRequest.forChatDisplay("&lt;col=red&gt;test&lt;/col&gt;")), "Markup neutralization");
        check(TranslationRequest.buildUrl("hello", SourceLanguage.FRENCH).contains("langpair=fr%7Cen"), "Other language pairs");
        check(TranslationRequest.buildUrl("hello", SourceLanguage.CHINESE_SIMPLIFIED).contains("langpair=zh-CN%7Cen"), "Region code");
        // Chat font cosmetics: ASCII direction markers and a clearly local synthetic sender.
        check("[ES to EN] Hi, does anyone want to do raids with me?".equals(
            ChatDisplayFormat.translation(SourceLanguage.SPANISH,
                "Hi, does anyone want to do raids with me?")), "Readable Spanish chat label");
        check("[ZH-CN to EN] Hello".equals(
            ChatDisplayFormat.translation(SourceLanguage.CHINESE_SIMPLIFIED, "Hello")),
            "Regional source labels remain readable");
        check(ChatDisplayFormat.translation(SourceLanguage.FRENCH, "Merci").indexOf('\u2192') < 0,
            "Do not emit OSRS-incompatible arrow glyph in translated chat");
        check(ChatMessageSelection.LOCAL_TRANSLATION_SENDER.length() > 12,
            "Local sender is distinguishable from an OSRS player's username");
        // Common RuneLite player-chat snippets. Not a validated multilingual corpus.
        expectLanguage("¿Alguien quiere matar a Vorkath?", SourceLanguage.SPANISH);
        expectLanguage("Hola, como estas?", SourceLanguage.SPANISH);
        expectLanguage("Gracias amigo", SourceLanguage.SPANISH);
        expectLanguage("Quieres ir conmigo al jefe?", SourceLanguage.SPANISH);
        expectLanguage("Bonjour, je cherche un groupe pour combattre le boss", SourceLanguage.FRENCH);
        expectLanguage("Merci beaucoup", SourceLanguage.FRENCH);
        expectLanguage("Salut, qui veut jouer avec moi?", SourceLanguage.FRENCH);
        expectLanguage("Hallo, ich suche eine Gruppe", SourceLanguage.GERMAN);
        expectLanguage("Ich brauche hilfe", SourceLanguage.GERMAN);
        expectLanguage("Danke!", SourceLanguage.GERMAN);
        expectLanguage("Olá, alguém quer lutar hoje?", SourceLanguage.PORTUGUESE);
        expectLanguage("Preciso de ajuda, obrigado", SourceLanguage.PORTUGUESE);
        expectLanguage("eu quero ajuda", SourceLanguage.PORTUGUESE);
        expectLanguage("Ciao, qualcuno vuole giocare?", SourceLanguage.ITALIAN);
        expectLanguage("Grazie, ho bisogno di aiuto", SourceLanguage.ITALIAN);
        expectLanguage("Hallo ik zoek een groep", SourceLanguage.DUTCH);
        expectLanguage("Heb je hulp nodig?", SourceLanguage.DUTCH);
        expectLanguage("Cześć, potrzebuję pomocy", SourceLanguage.POLISH);
        expectLanguage("Merhaba, yardıma ihtiyacım var", SourceLanguage.TURKISH);
        expectLanguage("Hej, behöver hjälp idag", SourceLanguage.SWEDISH);
        expectLanguage("Hej, jeg har brug for hjælp", SourceLanguage.DANISH);
        expectLanguage("Привет, кто хочет убить босса?", SourceLanguage.RUSSIAN);
        expectLanguage("Привіт, хто хоче допомогти?", SourceLanguage.UKRAINIAN);
        expectLanguage("ありがとう、助けてください", SourceLanguage.JAPANESE);
        expectLanguage("안녕하세요, 도움이 필요해요", SourceLanguage.KOREAN);
        expectLanguage("مرحبا، أريد مساعدة", SourceLanguage.ARABIC);
        expectLanguage("你好，我需要帮助", SourceLanguage.CHINESE_SIMPLIFIED);
        expectLanguage("你好", SourceLanguage.CHINESE_SIMPLIFIED);
        // v0.6.9.16: actual RuneLite report: English frequently returned UNKNOWN.
        // Small, realistic chat lines and complete sentences now provide a
        // consistent English answer when there is recognizable evidence.
        String[] recognizedEnglish = {
            "yes", "yes pls", "ok yes", "yea", "alright", "that's fine",
            "thats crazy", "nice", "oh really", "wow", "i got 3",
            "I remember", "I dont remember", "can i join?",
            "can u invite me", "no worries", "thats fair", "theyre back",
            "i swear", "i agree", "My inventory is full",
            "u here", "how's it going?", "i just logged on",
            "my gear is in the bank", "i think they're still playing",
            "I need some food for my inventory", "I already finished the quest",
            "they can't find the entrance", "Did you see that guy?",
            "my friend went to school abroad", "the boss is near the cave",
            "my chatbox is broken", "the plugin is disabled",
            "I'm testing the new build", "did you update the plugin?",
            "we can try that tomorrow", "it doesnt matter to me",
            "good morning everyone", "just a second please", "what are you doing",
            "i would love to join", "i will help you", "that guy is funny",
            "thanks a lot mate", "I lost my armor", "you should check settings",
            "tysm", "idk", "idk lol", "thx", "thnx", "yw"
        };
        for (String englishLine : recognizedEnglish)
        {
            expectKind(englishLine, LocalLanguageDetector.Result.Kind.ENGLISH);
        }
        // Intentionally ambiguous fragments must not become falsely English.
        String[] ambiguousChat = {
            "lol", "gg", "afk", "brb", "gg wp", "ty", "np", "bet",
            "haha", "omg", "no idea", "Vorkath", "hello xxx yyy zzz",
            "bonjour merci danke hilfe", "hola hola hola hola danke"
        };
        for (String ambiguous : ambiguousChat)
        {
            expectKind(ambiguous, LocalLanguageDetector.Result.Kind.UNKNOWN);
        }
        // Regression: ordinary English chat must say it is already English.
        // These phrases had gaps in the older English vocabulary/coverage.
        expectKind("I was walking around today", LocalLanguageDetector.Result.Kind.ENGLISH);
        expectKind("nice weather outside", LocalLanguageDetector.Result.Kind.ENGLISH);
        expectKind("I am bored at home", LocalLanguageDetector.Result.Kind.ENGLISH);
        expectKind("im just trying to find my gear", LocalLanguageDetector.Result.Kind.ENGLISH);
        expectKind("dont know what u mean lol", LocalLanguageDetector.Result.Kind.ENGLISH);
        expectKind("yall wanna do something later", LocalLanguageDetector.Result.Kind.ENGLISH);
        expectKind("I just got back from school", LocalLanguageDetector.Result.Kind.ENGLISH);
        expectKind("I don't think he was talking to me", LocalLanguageDetector.Result.Kind.ENGLISH);
        expectKind("How is everyone doing today?", LocalLanguageDetector.Result.Kind.ENGLISH);
        expectKind("This is an awesome game", LocalLanguageDetector.Result.Kind.ENGLISH);
        expectKind("That player has a friend from another city", LocalLanguageDetector.Result.Kind.ENGLISH);
        expectKind("you are so funny", LocalLanguageDetector.Result.Kind.ENGLISH);
        expectKind("I need to find someone to help me", LocalLanguageDetector.Result.Kind.ENGLISH);
        expectKind("we should get going soon", LocalLanguageDetector.Result.Kind.ENGLISH);
        expectKind("did you see that", LocalLanguageDetector.Result.Kind.ENGLISH);
        expectKind("hello everyone", LocalLanguageDetector.Result.Kind.ENGLISH);
        expectKind("hi everyone", LocalLanguageDetector.Result.Kind.ENGLISH);
        // Full-audit regressions: incompatible scripts must not be assigned to
        // whichever script rule happens to run first.
        expectKind("こんにちは 안녕하세요", LocalLanguageDetector.Result.Kind.UNKNOWN);
        expectKind("안녕하세요 こんにちは", LocalLanguageDetector.Result.Kind.UNKNOWN);
        expectKind("안녕하세요 你好", LocalLanguageDetector.Result.Kind.UNKNOWN);
        expectKind("你好 안녕하세요", LocalLanguageDetector.Result.Kind.UNKNOWN);
        expectKind("مرحبا 안녕하세요", LocalLanguageDetector.Result.Kind.UNKNOWN);
        expectKind("Привет こんにちは", LocalLanguageDetector.Result.Kind.UNKNOWN);
        expectLanguage("こんにちは世界", SourceLanguage.JAPANESE);
        expectLanguage("안녕하세요, 도움이 필요해요", SourceLanguage.KOREAN);
        expectLanguage("你好，我需要帮助", SourceLanguage.CHINESE_SIMPLIFIED);
        check("It’s time".equals(TranslationRequest.forChatDisplay("It&#8217;s time")), "Numerical apostrophe");
        check("A B".equals(TranslationRequest.forChatDisplay("A&nbsp;B")), "Nonbreaking space");
        check("&lt;tag&gt;".equals(TranslationRequest.forChatDisplay("&amp;lt;tag&amp;gt;")), "One-pass entities");
        check("(col=red)X(/col)".equals(TranslationRequest.forChatDisplay("&#60;col=red&#62;X&#60;/col&#62;")), "Safe numeric tag display");
        // Foreign and ambiguous messages must not be falsely declared English.
        expectKind("gg", LocalLanguageDetector.Result.Kind.UNKNOWN);
        expectKind("hello xxx yyy zzz", LocalLanguageDetector.Result.Kind.UNKNOWN);
        expectKind("bonjour merci danke hilfe", LocalLanguageDetector.Result.Kind.UNKNOWN);
        expectLanguage("hola gracias today", SourceLanguage.SPANISH);

        expectKind("Anyone want to kill Vorkath?", LocalLanguageDetector.Result.Kind.ENGLISH);
        expectKind("Can someone help me?", LocalLanguageDetector.Result.Kind.ENGLISH);
        expectKind("si", LocalLanguageDetector.Result.Kind.UNKNOWN);
        expectKind("gg", LocalLanguageDetector.Result.Kind.UNKNOWN);
        expectKind("ok", LocalLanguageDetector.Result.Kind.UNKNOWN);
        expectKind("Vorkath", LocalLanguageDetector.Result.Kind.UNKNOWN);
        expectKind("Vorkath 2026", LocalLanguageDetector.Result.Kind.UNKNOWN);
        expectKind("да", LocalLanguageDetector.Result.Kind.UNKNOWN);
        expectKind("", LocalLanguageDetector.Result.Kind.UNKNOWN);
        expectKind(" ", LocalLanguageDetector.Result.Kind.UNKNOWN);
        expectKind(null, LocalLanguageDetector.Result.Kind.UNKNOWN);
        expectKind("日本", LocalLanguageDetector.Result.Kind.UNKNOWN);
        // One isolated kana, Hangul syllable or Han character is ambiguous.
        expectKind("あ", LocalLanguageDetector.Result.Kind.UNKNOWN);
        expectKind("네", LocalLanguageDetector.Result.Kind.UNKNOWN);
        expectKind("帮", LocalLanguageDetector.Result.Kind.UNKNOWN);
        // Longer ordinary messages and slang keep their normal recognition.
        expectLanguage("ありがとう", SourceLanguage.JAPANESE);
        expectLanguage("안녕하세요", SourceLanguage.KOREAN);
        expectLanguage("你好", SourceLanguage.CHINESE_SIMPLIFIED);
        // Typo tolerance: infer from nearby clear words and common single edits.
        expectLanguage("holaaaa xq no quieres venir conmigo", SourceLanguage.SPANISH);
        expectLanguage("gracais algien qiere hacer raids", SourceLanguage.SPANISH);
        expectLanguage("Bonjor cherch un grupe pour raids", SourceLanguage.FRENCH);
        expectLanguage("mercie beacoup", SourceLanguage.FRENCH);
        expectLanguage("dankee ich suche gruppe", SourceLanguage.GERMAN);
        expectLanguage("Olaa precisso de ajuda", SourceLanguage.PORTUGUESE);
        expectLanguage("grazzie qualcuno vuole venire", SourceLanguage.ITALIAN);
        expectLanguage("merhba yardim istiyorum", SourceLanguage.TURKISH);
        // Real player report: short Spanish sentence with a typo and a word
        // accidentally split at a space. This used to return UNKNOWN.
        expectLanguage("Donda esta la biblio techa", SourceLanguage.SPANISH);
        expectLanguage("Donda esta la biblioteca", SourceLanguage.SPANISH);
        expectLanguage("Donde esta la biblio techa", SourceLanguage.SPANISH);
        expectLanguage("Donde esta la biblioteca", SourceLanguage.SPANISH);
        expectLanguage("Donda esta la biblioteca por favor", SourceLanguage.SPANISH);
        // Accept rejoined distinctive words while keeping original message
        // untouched for MyMemory, with no remote detector or new user control.
        expectLanguage("obri gado quero ajuda", SourceLanguage.PORTUGUESE);
        expectLanguage("mer haba yardim istiyorum", SourceLanguage.TURKISH);
        expectLanguage("dank e ich suche gruppe", SourceLanguage.GERMAN);
        // Shared Romance words alone still must not force a guess.
        expectKind("esta la biblioteca", LocalLanguageDetector.Result.Kind.UNKNOWN);
        expectKind("esta la biblio techa", LocalLanguageDetector.Result.Kind.UNKNOWN);
        expectKind("Donda esta la", LocalLanguageDetector.Result.Kind.UNKNOWN);
        expectKind("Donda esta la techa", LocalLanguageDetector.Result.Kind.UNKNOWN);
        expectKind("biblio techa", LocalLanguageDetector.Result.Kind.UNKNOWN);
        // False-positive guard: one recognizable greeting buried in garbage
        // should not turn the entire chat message into a language guess.
        // Only evidence in the *winning language* counts toward coverage.
        expectKind("hola xxxxx xxxxx gg", LocalLanguageDetector.Result.Kind.UNKNOWN);
        expectKind("hola x x x x x", LocalLanguageDetector.Result.Kind.UNKNOWN);
        expectKind("bonjour xxx yyy zzz", LocalLanguageDetector.Result.Kind.UNKNOWN);
        expectKind("hallo qqqq zzzz wwwww", LocalLanguageDetector.Result.Kind.UNKNOWN);
        expectKind("gracias xxx yyy zz", LocalLanguageDetector.Result.Kind.UNKNOWN);
        expectKind("Donda esta la tkrra blop", LocalLanguageDetector.Result.Kind.UNKNOWN);
        expectKind("hola gracias xx yy xx yy", LocalLanguageDetector.Result.Kind.UNKNOWN);
        expectKind("gg and hola x y z", LocalLanguageDetector.Result.Kind.UNKNOWN);
        expectKind("xxx abcde uvwxy zzzzz", LocalLanguageDetector.Result.Kind.UNKNOWN);
        expectKind("hallo zzzzzzzz qqqqq asdfgh", LocalLanguageDetector.Result.Kind.UNKNOWN);
        // Deliberate typo + split-word case remains recognized.
        expectLanguage("Donda esta la biblio techa", SourceLanguage.SPANISH);
        expectKind("gg", LocalLanguageDetector.Result.Kind.UNKNOWN);
        expectKind("I want a trade for the boss", LocalLanguageDetector.Result.Kind.ENGLISH);
        expectKind("hello everyone can we meet", LocalLanguageDetector.Result.Kind.ENGLISH);
        expectLanguage("hola alguien quiere hacer raids conmigo", SourceLanguage.SPANISH);
        // Source-language clues from everyday internet shorthand across all 16 languages.
        expectLanguage("tqm amigo gracias", SourceLanguage.SPANISH);
        expectLanguage("blz bora jogar comigo", SourceLanguage.PORTUGUESE);
        expectLanguage("mdr merci beaucoup", SourceLanguage.FRENCH);
        expectLanguage("vllt danke ich komme", SourceLanguage.GERMAN);
        expectLanguage("cmq ciao voglio venire", SourceLanguage.ITALIAN);
        expectLanguage("idd dankjewel ik zoek hulp", SourceLanguage.DUTCH);
        expectLanguage("spoko siema pomocy", SourceLanguage.POLISH);
        expectLanguage("nbr merhaba kanka", SourceLanguage.TURKISH);
        expectLanguage("asså tack behöver hjälp", SourceLanguage.SWEDISH);
        expectLanguage("grineren tak for hjælpen", SourceLanguage.DANISH);
        expectLanguage("спс спасибо помоги", SourceLanguage.RUSSIAN);
        expectLanguage("імхо дякую допоможіть", SourceLanguage.UKRAINIAN);
        expectLanguage("おつ ありがとう", SourceLanguage.JAPANESE);
        expectLanguage("ㅋㅋㅋ 감사합니다", SourceLanguage.KOREAN);
        expectLanguage("哈哈哈哈 你好", SourceLanguage.CHINESE_SIMPLIFIED);
        expectLanguage("يلا مرحبا أريد مساعدة", SourceLanguage.ARABIC);
        // Shared slang or gamer jargon must not force a foreign language guess.
        expectKind("lol", LocalLanguageDetector.Result.Kind.UNKNOWN);
        expectKind("brb", LocalLanguageDetector.Result.Kind.UNKNOWN);
        expectKind("afk", LocalLanguageDetector.Result.Kind.UNKNOWN);
        expectKind("xq", LocalLanguageDetector.Result.Kind.UNKNOWN);
        expectKind("kkkk", LocalLanguageDetector.Result.Kind.UNKNOWN);
        expectKind("my boss drops stuff", LocalLanguageDetector.Result.Kind.ENGLISH);
        expectKind("thank you boss", LocalLanguageDetector.Result.Kind.ENGLISH);
        // Mixed-language conflict checks: compare MULTIPLE DISTINCT word
        // matches before deciding, rather than assigning a language from one
        // distinctive word or a shared gaming/slang abbreviation.
        expectLanguage("ich du er sie wir ihr ihnen das die der gracias hola",
            SourceLanguage.GERMAN); // ten German weak cues vs two Spanish strong cues
        expectLanguage("hallo danke bitte jemand heute morgen abend nacht gruppe suche hola gracias",
            SourceLanguage.GERMAN);
        expectLanguage("hola gracias alguien quiero vamos conmigo mañana tengo puedes necesito ich danke",
            SourceLanguage.SPANISH);
        expectLanguage("bonjour merci beaucoup je cherche besoin aider demain allons danke hallo",
            SourceLanguage.FRENCH);
        expectLanguage("bora valeu obrigado preciso ajuda comigo hoje amanhã vamos vocês hola gracias",
            SourceLanguage.PORTUGUESE);
        expectLanguage("cześć dzięki potrzebuję szukam grupa dziś pomoc razem jutro hola gracias",
            SourceLanguage.POLISH);
        expectKind("gracias hola danke gruppe", LocalLanguageDetector.Result.Kind.UNKNOWN);
        expectKind("bonjour merci danke hilfe", LocalLanguageDetector.Result.Kind.UNKNOWN);
        expectKind("como que hola obrigado", LocalLanguageDetector.Result.Kind.UNKNOWN);
        expectKind("merci hola", LocalLanguageDetector.Result.Kind.UNKNOWN);
        expectKind("hello danke", LocalLanguageDetector.Result.Kind.UNKNOWN);
        expectKind("hola hola hola hola danke", LocalLanguageDetector.Result.Kind.UNKNOWN);
        expectKind("hola holaaaa hola danke", LocalLanguageDetector.Result.Kind.UNKNOWN);
        expectKind("hallo", LocalLanguageDetector.Result.Kind.UNKNOWN); // German/Dutch
        expectKind("hej", LocalLanguageDetector.Result.Kind.UNKNOWN); // Danish/Swedish
        expectKind("como", LocalLanguageDetector.Result.Kind.UNKNOWN); // Spanish/Portuguese
        expectKind("ja", LocalLanguageDetector.Result.Kind.UNKNOWN);
        expectKind("boss", LocalLanguageDetector.Result.Kind.UNKNOWN);
        expectKind("lol gg afk", LocalLanguageDetector.Result.Kind.UNKNOWN);
        expectKind("xq gg xd", LocalLanguageDetector.Result.Kind.UNKNOWN);
        expectKind("my boss drops stuff", LocalLanguageDetector.Result.Kind.ENGLISH);
        expectKind("thank you boss", LocalLanguageDetector.Result.Kind.ENGLISH);
        expectKind("hello everyone I need help with raids", LocalLanguageDetector.Result.Kind.ENGLISH);
        // Related-language overlaps: evidence from the dominant language
        // should beat a couple of conflicting phrases, but balanced cues
        // cannot justify a confident choice.
        expectLanguage("ik zoek hulp vanavond bedankt dankjewel alsjeblieft iemand danke gruppe",
            SourceLanguage.DUTCH);
        expectLanguage("ich brauche hilfe heute bitte jemand zusammen danke ik bedankt",
            SourceLanguage.GERMAN);
        expectLanguage("tack hjälp tillsammans ikväll behöver söker idag tack tak undskyld",
            SourceLanguage.SWEDISH);
        expectLanguage("tak undskyld nogen hjælp sammen imorgen venner hjaelp tack behöver",
            SourceLanguage.DANISH);
        expectLanguage("привіт дякую допоможіть сьогодні шукаю добре завтра спасибо привет",
            SourceLanguage.UKRAINIAN);
        expectLanguage("привет спасибо помогите сегодня вечером группа готов давай дякую привіт",
            SourceLanguage.RUSSIAN);
        expectLanguage("hola gracias alguien quiero conmigo mañana tengo puedes obrigado ajuda",
            SourceLanguage.SPANISH);
        expectLanguage("obrigado preciso ajuda comigo amanhã hoje vamos bora hola gracias",
            SourceLanguage.PORTUGUESE);
        expectLanguage("ciao grazie qualcuno voglio bisogno insieme domani gruppo hola gracias",
            SourceLanguage.ITALIAN);
        expectKind("need help with raids thanks hello boss ich danke",
            LocalLanguageDetector.Result.Kind.ENGLISH);
        expectKind("hej tack tak", LocalLanguageDetector.Result.Kind.UNKNOWN);
        // Do not mistake every Arabic-script chat line for Arabic: Urdu and
        // Persian use the same writing system, but are not in this prototype.
        expectKind("مجھے مدد چاہیے", LocalLanguageDetector.Result.Kind.UNKNOWN);
        expectKind("سلام خوبی", LocalLanguageDetector.Result.Kind.UNKNOWN);
        expectLanguage("مرحبا أريد مساعدة", SourceLanguage.ARABIC);
        // Earlier typo/slang behaviour and untranslated profanity are
        // covered by the assertions above; no filters or new requests added.
        // Neither input nor output uses a profanity/insult blacklist or redaction.
        // URL encoding preserves original text byte-for-byte after decoding.
        String uncensored = "hola mierda carajo putain fuck";
        try
        {
            String url = TranslationRequest.buildUrl(uncensored, SourceLanguage.SPANISH);
            String encoded = url.substring(url.indexOf("?q=") + 3, url.indexOf("&langpair="));
            check(uncensored.equals(java.net.URLDecoder.decode(encoded, "UTF-8")),
                "Original message, including profanity, is transmitted without filtering");
        }
        catch (java.io.UnsupportedEncodingException impossible)
        {
            throw new AssertionError("UTF-8 must be available", impossible);
        }
        check("Damn, mierda, merde!".equals(
            TranslationRequest.forChatDisplay("Damn, mierda, merde!")),
            "Returned translated words are not censored");
        // v0.6.9.8: selected incoming player chat can be public or sensitive;
        // only exact, unambiguous sender+text matches are eligible.
        ChatMessageSelection selection = new ChatMessageSelection();
        long now = 1_000_000L;
        selection.record(ChatMessageSelection.Channel.PUBLIC, "Jugador123",
            "¿Alguien quiere matar a Vorkath?", "MyAccount", now);
        check(selection.matchRenderedParts("Jugador123:", "¿Alguien quiere matar a Vorkath?", now)
            .channel == ChatMessageSelection.Channel.PUBLIC, "Public message selection");
        for (ChatMessageSelection.Channel c : ChatMessageSelection.Channel.values())
        {
            check(c.requiresPrivacyAcknowledgment() == (c != ChatMessageSelection.Channel.PUBLIC),
                "Sensitive-channel opt-in classification: " + c);
        }
        selection.record(ChatMessageSelection.Channel.PRIVATE, "OtherPlayer",
            "Bonjour tout le monde", "MyAccount", now);
        check(selection.matchRenderedParts("OtherPlayer:", "Bonjour tout le monde", now)
            .channel == ChatMessageSelection.Channel.PRIVATE, "Incoming DM matched");
        check(selection.matchRenderedParts("From OtherPlayer:", "Bonjour tout le monde", now)
            .channel == ChatMessageSelection.Channel.PRIVATE, "Incoming DM with From prefix matched");
        selection.record(ChatMessageSelection.Channel.FRIENDS_CHAT, "Guildy",
            "Hallo zusammen", "MyAccount", now);
        check(selection.matchRenderedParts("Guildy", "Hallo zusammen", now)
            .channel == ChatMessageSelection.Channel.FRIENDS_CHAT, "Friends Chat matched");
        selection.record(ChatMessageSelection.Channel.CLAN, "ClanMate", "Hola amigos", "MyAccount", now);
        check(selection.matchRenderedParts("ClanMate", "Hola amigos", now)
            .channel == ChatMessageSelection.Channel.CLAN, "Clan matched");
        selection.record(ChatMessageSelection.Channel.GUEST_CLAN, "Guest", "Merci beaucoup", "MyAccount", now);
        check(selection.matchRenderedParts("Guest", "Merci beaucoup", now)
            .channel == ChatMessageSelection.Channel.GUEST_CLAN, "Guest Clan matched");
        selection.record(ChatMessageSelection.Channel.GIM_CLAN, "IronFriend", "Grazie mille", "MyAccount", now);
        check(selection.matchRenderedParts("IronFriend", "Grazie mille", now)
            .channel == ChatMessageSelection.Channel.GIM_CLAN, "GIM clan matched");
        check(selection.matchRenderedParts("ClanMate", "Hola amigos and a secret", now) == null,
            "No concatenation of adjacent text");
        check(selection.matchRenderedParts("PlayerWhoDidNotSpeak", "Hola amigos", now) == null,
            "Sender must match");
        check(selection.matchRenderedParts("From ClanMate:", "Hola amigos", now) == null,
            "From prefix may only match incoming DM");
        selection.record(ChatMessageSelection.Channel.PRIVATE, "ClanMate", "Hola amigos", "MyAccount", now);
        check(selection.matchRenderedParts("ClanMate:", "Hola amigos", now) == null,
            "Identical public/private/clan-looking text cannot be disambiguated on All tab");
        selection.clear();
        check(selection.matchRenderedParts("ClanMate", "Hola amigos", now) == null,
            "Logout or disable clears record cache");
        selection.record(ChatMessageSelection.Channel.PRIVATE, "MyAccount", "Don't send my text", "MyAccount", now);
        check(selection.matchRenderedParts("MyAccount", "Don't send my text", now) == null,
            "Self messages are excluded on private channel too");
        selection.record(ChatMessageSelection.Channel.CLAN,
            ChatMessageSelection.LOCAL_TRANSLATION_SENDER, "Translated output", "MyAccount", now);
        check(selection.matchRenderedParts(ChatMessageSelection.LOCAL_TRANSLATION_SENDER,
            "Translated output", now) == null, "Locally emitted clan translation never re-ingested");
        selection.record(ChatMessageSelection.Channel.PUBLIC, "MyAccount", "Some words", "MyAccount", now);
        check(selection.matchRenderedParts("MyAccount", "Some words", now) == null,
            "Own public chat excluded");
        selection.record(ChatMessageSelection.Channel.PRIVATE, "Bob", "Hola", null, now);
        check(selection.matchRenderedParts("Bob", "Hola", now) == null,
            "Missing local identity fails closed");
        selection.record(ChatMessageSelection.Channel.PUBLIC, "Alice", "Hola amiga", "MyAccount", now);
        check(selection.matchRenderedParts("Alice", "Hola amiga", now+600001L) == null,
            "Expired message not selectable");
        selection.clear();
        for (int i = 0; i < 140; i++)
        {
            selection.record(ChatMessageSelection.Channel.PRIVATE, "P" + i,
                "mensaje " + i, "MyAccount", now);
        }
        check(selection.matchRenderedParts("P0", "mensaje 0", now) == null,
            "Bounded sensitive text cache evicts oldest");
        check("mensaje 139".equals(selection.matchRenderedParts("P139", "mensaje 139", now).text),
            "Bounded sensitive text cache keeps newest");
        check(LocalLanguageDetector.detect("gg").kind() == LocalLanguageDetector.Result.Kind.UNKNOWN,
            "Ambiguous very short chat stays unknown");
        check(ChatDisplayFormat.alreadyEnglish().equals(
            "The selected message is already in English. No translation is needed."),
            "English feedback is clear and local-only");
        check(ChatDisplayFormat.unidentifiedLanguage().equals(
            "Couldn't identify the selected message's language. It may already be English."),
            "Unknown feedback does not pretend the text must be foreign");
        check(ChatDisplayFormat.unidentifiedLanguage().contains("selected message"),
            "Unknown language feedback clearly identifies selected message");
        check(!ChatDisplayFormat.unidentifiedLanguage().contains("MyMemory does not support"),
            "Do not falsely claim MyMemory itself lacks that language");
        System.out.println("PASS: " + checks + " offline Java checks; no network used.");
    }
}
