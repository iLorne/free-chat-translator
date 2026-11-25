package com.chattranslator;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.HashSet;
import java.util.Set;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Small, deliberately conservative, offline language guesser for short player chat.
 * Only runs after a user explicitly chooses Translate. It never transmits text.
 *
 * This is a rule-based prototype, NOT a general-purpose statistical detector.
 * It abstains on unclear short text instead of inventing a source language.
 */
public final class LocalLanguageDetector
{
    private static final Pattern WORD = Pattern.compile("\\p{L}+(?:['’]\\p{L}+)?");
    private static final Map<String, Map<String, Integer>> CUES = new HashMap<>();
    private static final String ENGLISH = "en";
    // Only checked on explicit translation requests when an exact cue is absent.
    // Strong-cue typos must be short enough to avoid guessing on arbitrary text.
    private static final Map<String, Map<String, Integer>> ASCII_FOLDED_CUES = new HashMap<>();
    // Indexed once at class initialization. Unknown words only compare against
    // strong cues of similar length, never the entire language dictionary.
    private static final Map<Integer, List<FuzzyCue>> STRONG_CUES_BY_LENGTH = new HashMap<>();
    private static final Pattern COMBINING_MARKS = Pattern.compile("\\p{M}+");

    static
    {
        // Higher-weight words are distinctive in short chat; lower-weight words
        // can occur across related languages. This is not a trained model.
        strong(ENGLISH, "stuff drops hello hey thanks thank you sorry anyone everyone where what why who when please kill killing looking need help party together going want wants does should would could there their this that with from your you're have can't don't okay let's sure tonight tomorrow attack fight fighting meet ready speaking english right left how mine yours friend friends follow give trade buy sell already finished task just about which before after world message chat translate good morning evening night");
        weak(ENGLISH, "the and are was were for can you its not yes no here we they them our all do is to of on in at go my me it he she up down one two a an so if but now then will well");
        // Common everyday English/chat words previously missed by the
        // conservative detector. A message still needs several compatible
        // lexical cues; an unfamiliar or mixed-language message may abstain.
        // Avoid cross-language gamer jargon (gg, afk, boss, raid, etc.) as
        // English-specific strong evidence.
        strong(ENGLISH, "hi howdy what's whats i'm im i've ive i'll ill he's she's we're they're isn't isnt wasn't wasnt weren't werent haven't havent hasn't hasnt didn't didnt doesn't doesnt won't wont wouldn't wouldnt couldn't couldnt shouldn't shouldnt dont gonna wanna gotta yall yeah yep nope nah thanks cheers appreciated nice awesome amazing great excellent brilliant wonderful fantastic funny hilarious kidding joking jokes joke laugh laughing lovely beautiful cool boring bored interesting exciting easy easier easiest difficult harder hardest bad worse worst better best new newer newest old older oldest first last next later earlier soon today yesterday weekday weekend week weeks month months year years day days hours hour minutes minute second seconds home house houses school teacher teachers college work working worked walking walked walk walks outside inside weather sunny rainy raining snow snowing hot cold warm freezing cloud clouds people person persons somebody anybody nobody someone anyone everybody something anything nothing thing things guy guys girl girls mate mates family friends buddy buddies friend friendship cat cats dog dogs animal animals got getting get gotten found find finding seeing seen saw look looks looked looking think thinking thought know knew knowing guess guessing mean meant means understand understood explain explained saying said tell told talking talked talk speaks speak speaking words word language languages game games play playing played player players really definitely probably perhaps maybe seriously actually literally honestly true false correct wrong okay sure wait waiting waited done finished finish trying try tried tries started start starting stop stopped stopping keep keeping kept lose losing lost win won winning fun silly weird random whatever anyway anyways right now together still again always never sometimes usually often rarely quickly slowly happy sad angry tired hungry sleepy good luck job one stuff just enough plenty more less same different morning afternoon evening night bye goodbye see ya you a lot");
        weak(ENGLISH, "am has had did does done being been be us as by than though while because since until although however around through under over above below out off onto into before after these those each every either neither both some any much many few little other another maybe mostly pretty quite very too such only even still yet also just got having here there then where own mine yours ourselves themselves yourself myself herself himself which whom whose who all how why when what if whether would could should might must may shall will can able about up down along across near far really whatever rather");

        // Real gameplay exposed ordinary English replies returning UNKNOWN.
        // Add recognizably English verbs, contractions, everyday phrases and
        // OSRS-chat terms without treating ASCII or shared gamer slang as English.
        // Intentional unknowns include: gg, lol, brb, afk, ty (Polish for
        // 'you'), "no idea" (also valid Spanish), and other ambiguous fragments.
        strong(ENGLISH, "yes yea idk tysm thx thnx yw alright yup that's thats it's its there's theres they're theyre you'll youll you'd youd i'd wasn't wasnt didn't didnt wouldn't wouldnt couldn't couldnt can't cant hasn't hasnt don't dont join joining invite invited inviting remember remembered remembers inventory full worries worry swear agreed agree agreeing dunno exactly absolutely certainly basically obviously fair crazy ridiculous wild hilarious cute sweet truly clear clearly wow wot seriously legit reckon definitely instead willing welcome anymore eventually appreciate glad lovely classmate classmates schoolmate schoolmates grade grades exam exams homework gear equipment armour armor sword swords shield shields boots potions potion backpack slots slot click clicking clicked checked unchecked reset enable enabled disable disabled settings checkbox checkboxes chatbox username usernames rightclick rightclicking worldhop hopped hopping loading loaded lag laggy crash crashed bug bugs broken fixed fixes working testing tested launch launched update updated release errors reason reasons scammed scamming scammer scammers tradeable untradeable teleport teleported teleing");
        weak(ENGLISH, "i u ur oh okay okie coz cause cuz prolly tho rn pls plz sry ah uh kinda sorta");


        strong("es", "biblioteca");
        strong("es", "hola buenas gracias alguien quiere quiero quieres dónde donde cómo como quién quien cuándo cuando necesito necesitas ayudame ayúdame ayuda vamos venga vale conmigo contigo mañana mañana noche tengo tienes tenemos estoy estás estas está están ellos nosotros vosotros puedes puedo podemos matar matamos jefe mazmorra equipo intercambiar vender comprar buscando busca grupo venir venid déjame porque ahora mejor otra otra vez");
        weak("es", "el la los las un una unos unas que qué por para pero con sin yo tú tu usted ustedes ellos ellas hay muy si sí de del al es soy somos ya no me te se lo le les más mas");

        strong("pt", "biblioteca");
        strong("pt", "olá ola obrigado obrigada alguém alguem quero queres queremos gostaria preciso precisamos ajuda ajudar vocês voce você vamos comigo contigo amanhã amanha hoje agora noite bom dia obrigado procurando grupo matar chefe podemos chegar venham posso consigo vocês tudo fazer falar português portugues obrigado onde como estou está estamos tem temos tenho também tambem brigado beleza bora valeu");
        weak("pt", "não nao sim você voce vocês voces da do dos das uma um uns umas em para por com sem que quem qual quando onde sou meu minha teu sua suas são sao mais mas pois");

        strong("fr", "bonjour salut merci beaucoup quelqu'un quelqu’un cherche chercher recherche pourquoi comment voulez voulez-vous veux besoin aider aide pouvez pourrais pouvez-vous vous êtes etes jouer ensemble ce soir demain aujourd'hui rejoindre allons on va je suis suis prêt pret où ou quel quelle groupe tuer équipe equipe acheter vendre avec moi personne quelqu'un arrive bientôt bientot");
        weak("fr", "le la les des du une un de est et sont mais dans pour avec sans pas ne oui non je tu il elle nous vous ils elles mon ma mes ton ta tes ce cette ces au aux que qui quoi très tres plus sur");

        strong("de", "hallo danke vielen dank bitte jemand möchte moechte will willst brauchst brauche hilfe helfen zusammen heute morgen abend nacht gruppe suchen suche sucht suche nach können koennen kannst warum wo wohin wie was deutsch verstehen töten toeten kaufen verkaufen kämpfen kaempfen bereits fertig komme komm mit mir spieler spielern keine ahnung");
        weak("de", "boss ich du er sie wir ihr ihnen das die der den dem des ein eine einen einer und oder aber nicht ja nein zu zum mit ohne für fuer von ist sind habe hast haben auf hier dort bei mir dir uns euch");

        strong("it", "biblioteca");
        strong("it", "ciao grazie buongiorno buonasera qualcuno qualcuno vuole voglio vuoi bisogno aiuto aiutami aiutare insieme stasera domani oggi domani gruppo cerco cercando possiamo potete venire vieni dove come perché perche uccidere comprare vendere squadra pronto pronta andiamo andate sei sono capisco parlano parla italiano");
        weak("it", "il lo la gli le un una uno della dello delle dei degli che chi cosa per con senza non sì si noi voi loro mio mia miei tue suo sua sono sei è e di da del al nel");

        strong("nl", "hallo bedankt dankjewel alsjeblieft iemand iemand wil willen ik wil ik zoek zoeken gezocht nodig helpen hulp samen vanavond morgen vandaag groep komen kom mee waar waarom hoe wie wanneer nederlands begrijpen vechten doden kopen verkopen klaar klaar voor misschien gezellig toch even jongens meisje bedankt maat");
        weak("nl", "de het een en of maar niet geen wel ja nee zijn is ben jij wij zij jullie ik je jij hij ze met zonder voor van op hier daar naar deze dat dit die wat als nog");

        strong("pl", "cześć czesc witaj dzięki dzieki dziękuję dziekuje proszę prosze ktoś ktos chcę chce chcesz potrzebuję potrzebuje pomoc pomocy pomóż pomoz razem dziś dzis dzisiaj jutro wieczorem grupa szukam szuka szukać gdzie dlaczego jak kiedy polski polsku zabić zabic walczyć walczyc sprzedam kupię kupie chodź chodz idziemy gotowy gotowa");
        weak("pl", "jest jestem jesteś jestes mamy mają maja nie tak może moze ktoś ktos czy co kto z ze na do od dla pod bez się sie ja ty oni wy my ale i albo że ze");

        strong("tr", "merhaba selam teşekkür teşekkürler tesekkurler teşekkür ederim lutfen lütfen birisi biri isteyen istiyorum istiyor yardım yardim yardımcı yardimci gel gelin birlikte bugün bugun yarın yarin akşam aksam gece grup arıyorum ariyorum nerede neden nasıl nasil kim ne zaman türkçe turkce anlıyor musun anlamıyorum anlamiyorum öldürmek oldurmek satın almak satmak hazırım hazirim");
        weak("tr", "ve veya ama değil degil evet hayır hayir var yok için icin ile bir bu şu su ben sen biz siz onlar burada orada daha çok cok ne kim nerede nasıl nasil");

        strong("da", "hej tak takker mange tak undskyld nogen ønsker oensker vil gerne behøver behoever hjælp hjaelp hjælpe hjaelpe sammen i aften imorgen i morgen gruppe leder efter hvor hvorfor hvordan hvornår hvoernaar dansk forstå forstaa dræbe draebe købe koebe sælge saelge klar venner kom med");
        weak("da", "og eller men ikke ja nej jeg du han hun vi i er det den de en et med uden for til fra her der på paa som hvad hvem kan skal vil har");

        strong("sv", "hej tack tackar någon nagon vill behöver behover hjälp hjalp hjälpa hjalpa tillsammans ikväll ikvall imorgon idag grupp letar söker soker efter var varför varfor hur när nar svenska förstår forstar döda doda köpa kopa sälja salja färdig fardig följa folja kom hit kan ni");
        weak("sv", "och eller men inte ja nej jag du han hon vi ni de det den en ett med utan för for från fran här har där dar på pa som vad vem kan ska vill har");

        strong("ru", "привет здравствуйте спасибо пожалуйста кто-нибудь кто нибудь хочу хочешь нужно нужна помогите помощь помоги вместе сегодня завтра вечером группа ищу ищем пойдём пойдем давай можно можешь где почему как когда русский русски понимаю понимаешь убить купить продать готов готова отлично давайте");
        weak("ru", "и или но не да нет это есть тут там там же мы вы они он она ты я мне тебя нам для из на по как что кто от с со до уже ещё еще");

        strong("uk", "привіт вітаю дякую будь ласка хтось хочу хочеш потрібно потрібна допоможіть допомога допоможи разом сьогодні завтра ввечері група шукаю шукаємо ходімо можна можеш де чому як коли українською розумію розумієш вбити купити продати готовий готова добре давайте");
        weak("uk", "і або але не так ні це є тут там ми ви вони він вона ти я мені тебе нам для на по що хто з зі до вже ще");

        strong("ja", "こんにちは ありがとう ありがとうございます だれか 誰か 助けて 助ける 一緒 今日 明日 行こう 行きましょう どこ なぜ どうして 日本語 わかる 分かる 募集 ボス グループ");
        strong("ko", "안녕하세요 감사합니다 고마워요 누구 도움이 도와주세요 같이 오늘 내일 어디 왜 어떻게 한국어 알아요 그룹 보스 가자 갑시다 필요해요");
        strong("zh-CN", "你好 谢谢 需要 帮助 一起 今天 明天 哪里 为什么 怎么 谁 组队 你好啊 中文 汉语 简体 准备好了 打怪 买 卖");
        strong("ar", "مرحبا أهلا شكرا شكراً من أحد أحتاج احتاج أريد اريد ساعدني مساعدة نذهب معا معاً اليوم غدا غداً أين اين لماذا كيف متى العربية عربي مجموعة شراء بيع مستعد");
        // Chat shorthand researched for the sixteen currently recognized languages.
        // Only local detection uses these hints. The original text is sent unchanged
        // to MyMemory after user confirmation; shared slang is not a language label.
        // Longer distinctive words carry more confidence. Short/shared expressions
        // need other surrounding language evidence.
        strong("es", "tqm tkm ntp jajaja xfa xfavor");
        weak("es", "xq xk pq tmb bn jaja");
        strong("pt", "blz vlw tmj obg pfv sdds rsrsrs huehue");
        weak("pt", "vc tbm rs kkkk pq");
        strong("fr", "mdr tkt jsp dsl bjr bcp ptdr");
        weak("fr", "slt stp svp pk pq cc");
        strong("de", "vllt digga krass hdl hdgdl gn8");
        weak("de", "kp ka jz eig nich lg");
        strong("it", "cmq tvb tvtb anke grz xke xké");
        weak("it", "qnd nn qst tt ke");
        strong("nl", "idd sws gwn wss iig wrs zsm");
        weak("nl", "ff mss wrm egt vgm");
        strong("pl", "nwm pzdr spoko siema pozdro spk nara dzk");
        weak("pl", "zw cb elo cze");
        strong("tr", "nbr knk tmm eyw kanka harbi aynen tesekkur");
        weak("tr", "slm mrb tsk kib");
        strong("sv", "asså grym keff chilla sjukt najs");
        weak("sv", "iaf ngn venne fett typ");
        strong("da", "grineren hygge hygger");
        weak("da", "ikk hva hep ses lige");
        // Shared Cyrillic chat words are intentionally weak in BOTH languages.
        // Existing Ukrainian-only і/ї/є and Russian-only ы/э/ё cues remain stronger.
        strong("ru", "имхо нзч");
        weak("ru", "спс пж прив норм рофл кринж го жиза");
        strong("uk", "імхо дякс");
        weak("uk", "спс пж прив норм рофл крінж го жиза");
        // Japanese and Korean slang can also be recognized from their scripts;
        // do not infer Japanese from single kanji that might be Chinese.
        strong("ja", "おつ りょ よろ よろしく 草生える すごい やばい 乙");
        strong("ko", "ㅋㅋㅋ ㅎㅎㅎ ㅇㅋ ㄱㄱ ㄱㅅ ㅊㅋ ㄴㄴ 대박 헐");
        strong("zh-CN", "哈哈哈哈 牛逼 躺平 内卷 破防 绝绝子 哈哈哈");
        strong("ar", "يلا تمام معلش ههههه والله شباب طيب");
        // Cross-language tokens (gg, lol, brb, afk, omg, xd, 666, etc.)
        // are deliberately absent; they cannot identify a source language.
        // Index fallback cues once, rather than building a model or using a network API.
        for (Map.Entry<String, Map<String, Integer>> entry : CUES.entrySet())
        {
            String folded = foldAccents(entry.getKey());
            if (!folded.equals(entry.getKey()))
            {
                Map<String, Integer> foldedScores = ASCII_FOLDED_CUES.computeIfAbsent(folded,
                    ignored -> new HashMap<>());
                for (Map.Entry<String, Integer> cue : entry.getValue().entrySet())
                {
                    foldedScores.merge(cue.getKey(), cue.getValue(), Math::max);
                }
            }
            if (entry.getKey().codePointCount(0, entry.getKey().length()) >= 5)
            {
                Map<String, Integer> strongLanguages = new HashMap<>();
                for (Map.Entry<String, Integer> cue : entry.getValue().entrySet())
                {
                    if (cue.getValue() >= 4)
                    {
                        strongLanguages.put(cue.getKey(), 4);
                    }
                }
                if (!strongLanguages.isEmpty())
                {
                    FuzzyCue candidate = new FuzzyCue(folded, strongLanguages);
                    STRONG_CUES_BY_LENGTH.computeIfAbsent(folded.length(), ignored -> new ArrayList<>())
                        .add(candidate);
                }
            }
        }
    }

    private LocalLanguageDetector() { }

    private static void strong(String language, String words)
    {
        add(language, words, 4);
    }

    private static void weak(String language, String words)
    {
        add(language, words, 1);
    }

    private static void add(String language, String words, int weight)
    {
        for (String word : words.toLowerCase(Locale.ROOT).split("\\s+"))
        {
            if (!word.isEmpty())
            {
                CUES.computeIfAbsent(word, ignored -> new HashMap<>()).merge(language, weight, Math::max);
            }
        }
    }

    public static Result detect(String text)
    {
        if (text == null || text.trim().isEmpty())
        {
            return Result.unknown();
        }
        String normalized = Normalizer.normalize(text, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
        int kana = 0, hangul = 0, han = 0, arabic = 0, cyrillic = 0;
        for (int pos = 0; pos < normalized.length(); )
        {
            int ch = normalized.codePointAt(pos);
            pos += Character.charCount(ch);
            Character.UnicodeScript script = Character.UnicodeScript.of(ch);
            if (script == Character.UnicodeScript.HIRAGANA || script == Character.UnicodeScript.KATAKANA)
            {
                kana++;
            }
            else if (script == Character.UnicodeScript.HANGUL)
            {
                hangul++;
            }
            else if (script == Character.UnicodeScript.HAN)
            {
                han++;
            }
            else if (script == Character.UnicodeScript.ARABIC)
            {
                arabic++;
            }
            else if (script == Character.UnicodeScript.CYRILLIC)
            {
                cyrillic++;
            }
        }
        // Kana plus Han is normal Japanese, but distinct *incompatible*
        // writing systems in the same message do not establish one language.
        // Avoid first-matching-script guesses (e.g. Japanese + Korean).
        int incompatibleScriptGroups = (kana >= 2 ? 1 : 0) + (hangul >= 2 ? 1 : 0)
            + (arabic >= 2 ? 1 : 0) + (cyrillic >= 2 ? 1 : 0);
        if (incompatibleScriptGroups >= 2
            || (han >= 2 && (hangul >= 2 || arabic >= 2 || cyrillic >= 2)))
        {
            return Result.unknown();
        }
        if (kana > 0)
        {
            // A single character is too little evidence for a confident guess.
            return kana >= 2 ? Result.source(SourceLanguage.JAPANESE) : Result.unknown();
        }
        if (hangul > 0)
        {
            return hangul >= 2 ? Result.source(SourceLanguage.KOREAN) : Result.unknown();
        }
        // Arabic script is also used for languages outside our detector,
        // including Persian and Urdu. Script alone does not establish Arabic.
        if (han > 0)
        {
            // A few simplified-only characters and familiar Chinese expressions
            // are reasonably distinctive. All-Kanji Japanese stays unknown.
            if (han >= 2 && (containsAny(normalized, "帮吗们谢汉这买卖为对从让见个觉说话们哈逼躺")
                || normalized.contains("你好") || normalized.contains("谢谢")))
            {
                return Result.source(SourceLanguage.CHINESE_SIMPLIFIED);
            }
            return Result.unknown();
        }
        // Russian and Ukrainian share Cyrillic letters and many words.
        // Their distinctive letters are evidence, not an unconditional verdict;
        // compare them with all other matching words below.
        Map<String, Evidence> scores = new HashMap<>();
        Set<String> countedTokens = new HashSet<>();
        List<String> wordTokens = new ArrayList<>();
        List<Set<String>> supportedWords = new ArrayList<>();
        Matcher matcher = WORD.matcher(normalized);
        int foundWords = 0;
        while (matcher.find())
        {
            foundWords++;
            String token = matcher.group();
            // Treat stretched/accented repeats as the same evidence, too.
            // For example, 'hola', 'holaaaa' and 'holá' are not three votes.
            String folded = foldAccents(token);
            String stretched = collapseLongRuns(folded);
            wordTokens.add(stretched);
            supportedWords.add(new HashSet<>());
            if (!countedTokens.add(stretched))
            {
                continue;
            }
            Map<String, Integer> cues = CUES.get(token);
            if (cues != null)
            {
                addScores(scores, cues, false);
                supportedWords.get(supportedWords.size() - 1).addAll(cues.keySet());
                continue;
            }
            // Normalize accents and stretched letters ONLY for local detection.
            // The original player message is still sent unchanged to MyMemory.
            Map<String, Integer> normalizedCues = CUES.get(stretched);
            if (normalizedCues == null)
            {
                normalizedCues = ASCII_FOLDED_CUES.get(stretched);
            }
            if (normalizedCues != null)
            {
                addScores(scores, normalizedCues, false);
                supportedWords.get(supportedWords.size() - 1).addAll(normalizedCues.keySet());
            }
            else if (token.length() >= 5 && token.length() <= 18)
            {
                // Approximate typo matches count less than actual known words.
                supportedWords.get(supportedWords.size() - 1).addAll(addSingleEditScores(scores, folded));
            }
        }
        // Players sometimes split a word while typing ("grac ias", "biblio techa").
        // Check only adjacent pairs whose combined length resembles a real
        // strong cue. Never repair or modify the original chat message.
        // Bound the work to ordinary chat lengths; long messages keep the
        // regular single-token detection path only.
        if (wordTokens.size() <= 30)
        {
            for (int i = 0; i + 1 < wordTokens.size(); i++)
            {
                String left = wordTokens.get(i), right = wordTokens.get(i + 1);
                if (left.length() < 2 || right.length() < 2 ||
                    (CUES.containsKey(left) && CUES.containsKey(right)))
                {
                    continue;
                }
                String joined = left + right;
                if (joined.length() < 8 || joined.length() > 18)
                {
                    continue;
                }
                Map<String, Integer> joinedCues = CUES.get(joined);
                if (joinedCues == null) joinedCues = ASCII_FOLDED_CUES.get(joined);
                if (joinedCues != null)
                {
                    // An exact rejoined word carries regular evidence.
                    addScores(scores, joinedCues, false);
                    supportedWords.get(i).addAll(joinedCues.keySet());
                    supportedWords.get(i + 1).addAll(joinedCues.keySet());
                }
                else
                {
                    // A split word can also contain one typo. This remains
                    // weak evidence and cannot decide the language on its own.
                    Set<String> joinedLanguages = addSingleEditScores(scores, joined);
                    supportedWords.get(i).addAll(joinedLanguages);
                    supportedWords.get(i + 1).addAll(joinedLanguages);
                }
            }
        }
        // Distinctive writing-system characters are supporting evidence.
        if (normalized.contains("¿") || normalized.contains("¡") || normalized.contains("ñ")) boost(scores, "es", 4);
        if (containsAny(normalized, "ãõ")) boost(scores, "pt", 4);
        if (normalized.contains("ß")) boost(scores, "de", 4);
        if (containsAny(normalized, "łćęąśżźń")) boost(scores, "pl", 4);
        if (containsAny(normalized, "ğış")) boost(scores, "tr", 4);
        if (cyrillic > 0 && containsAny(normalized, "іїєґ")) boost(scores, "uk", 5);
        if (cyrillic > 0 && containsAny(normalized, "ыэёъ")) boost(scores, "ru", 5);
        if (arabic > 0 && scores.isEmpty()) return Result.unknown();
        if (cyrillic > 0 && foundWords < 2 && scores.isEmpty()) return Result.unknown();

        String best = null;
        Evidence bestEvidence = null;
        Evidence secondEvidence = null;
        // All candidates are compared; neither a single high-weight word nor
        // a shared abbreviation should override a larger set of language cues.
        for (Map.Entry<String, Evidence> candidate : scores.entrySet())
        {
            Evidence evidence = candidate.getValue();
            if (bestEvidence == null || evidence.score() > bestEvidence.score())
            {
                secondEvidence = bestEvidence;
                bestEvidence = evidence;
                best = candidate.getKey();
            }
            else if (secondEvidence == null || evidence.score() > secondEvidence.score())
            {
                secondEvidence = evidence;
            }
        }
        // A single strongly language-specific typo may be enough when at
        // least two other recognized words provide context and the winner
        // has a clear lead over related languages. For example:
        // "Donda esta la biblio techa" should not be rejected merely
        // because "donda" is the only distinctive Spanish typo clue.
        boolean contextualSingleTypo = bestEvidence != null
            && bestEvidence.uniqueExact == 0 && bestEvidence.uniqueFuzzy == 1
            && bestEvidence.sharedExact >= 2 && bestEvidence.score() >= 9
            && foundWords >= 4 && secondEvidence != null
            && bestEvidence.score() - secondEvidence.score() >= 4;
        // A single recognizable word buried in junk cannot label an entire
        // message. Count how much of the message has real lexical evidence,
        // including words recovered from a single reasonable split. Repeating
        // one keyword does not provide additional evidence.
        int supportedCount = 0;
        for (Set<String> supported : supportedWords)
        {
            if (supported.contains(best)) supportedCount++;
        }
        if ((foundWords >= 3 && supportedCount <= 1)
            || (foundWords >= 4 && supportedCount * 2 < foundWords))
        {
            return Result.unknown();
        }
        if (bestEvidence == null || bestEvidence.score() < 6
            || bestEvidence.uniqueExact + bestEvidence.uniqueFuzzy == 0
            || (bestEvidence.uniqueExact == 0 && bestEvidence.uniqueFuzzy < 2
                && !contextualSingleTypo))
        {
            return Result.unknown();
        }
        if (secondEvidence != null)
        {
            if (bestEvidence.score() - secondEvidence.score() < 3)
            {
                return Result.unknown();
            }
            // Mixed chat with several different-language words should not
            // be resolved by an arbitrary strong cue or by a hash-map tie.
            if (secondEvidence.uniqueExact >= 2
                && bestEvidence.uniqueExact < secondEvidence.uniqueExact * 2
                && bestEvidence.uniqueExact < 4)
            {
                return Result.unknown();
            }
        }
        if (ENGLISH.equals(best))
        {
            return Result.english();
        }
        for (SourceLanguage language : SourceLanguage.values())
        {
            if (language.code().equals(best))
            {
                return Result.source(language);
            }
        }
        return Result.unknown();
    }

    /**
     * Count matching words, not just raw points. A language-specific matched
     * word adds evidence on top of its strong/weak weight. Words recognized
     * in multiple languages score for both, but cannot establish dominance.
     * Approximate typo matches have less influence than exact words.
     */
    private static void addScores(Map<String, Evidence> scores, Map<String, Integer> cues, boolean fuzzy)
    {
        boolean unique = cues.size() == 1;
        for (Map.Entry<String, Integer> cue : cues.entrySet())
        {
            Evidence evidence = scores.computeIfAbsent(cue.getKey(), ignored -> new Evidence());
            if (fuzzy)
            {
                evidence.rawScore += 2;
                if (unique) evidence.uniqueFuzzy++;
            }
            else
            {
                evidence.rawScore += cue.getValue();
                if (unique) evidence.uniqueExact++;
                else evidence.sharedExact++;
            }
        }
    }

    private static final class Evidence
    {
        int rawScore;
        int uniqueExact;
        int uniqueFuzzy;
        int sharedExact;
        int scriptScore;

        int score()
        {
            // Ten distinctive weak-word matches outweigh two unrelated
            // strong-word matches (10*(1+2) > 2*(4+2)).
            return rawScore + 2 * uniqueExact + uniqueFuzzy + scriptScore;
        }
    }

    private static String foldAccents(String text)
    {
        return COMBINING_MARKS.matcher(Normalizer.normalize(text, Normalizer.Form.NFD))
            .replaceAll("");
    }

    // 'holaaaa' -> 'hola' and 'bueeenas' -> 'buenas'; real double letters remain.
    private static String collapseLongRuns(String token)
    {
        StringBuilder builder = new StringBuilder(token.length());
        for (int i = 0; i < token.length(); )
        {
            char ch = token.charAt(i);
            int end = i + 1;
            while (end < token.length() && token.charAt(end) == ch)
            {
                end++;
            }
            int copies = end - i;
            for (int count = 0; count < (copies >= 3 ? 1 : copies); count++)
            {
                builder.append(ch);
            }
            i = end;
        }
        return builder.toString();
    }

    private static Set<String> addSingleEditScores(Map<String, Evidence> scores, String word)
    {
        if (word.length() < 5 || word.length() > 18)
        {
            return java.util.Collections.emptySet();
        }
        Map<String, Integer> guesses = new HashMap<>();
        for (int length = word.length() - 1; length <= word.length() + 1; length++)
        {
            List<FuzzyCue> possible = STRONG_CUES_BY_LENGTH.get(length);
            if (possible == null) continue;
            for (FuzzyCue candidate : possible)
            {
                if (!oneTypoApart(word, candidate.folded)) continue;
                for (String lang : candidate.languages.keySet())
                {
                    guesses.put(lang, 4);
                }
            }
        }
        if (!guesses.isEmpty())
        {
            addScores(scores, guesses, true);
        }
        return guesses.keySet();
    }

    private static final class FuzzyCue
    {
        final String folded;
        final Map<String, Integer> languages;

        FuzzyCue(String folded, Map<String, Integer> languages)
        {
            this.folded = folded;
            this.languages = languages;
        }
    }

    /** At most one insertion, deletion, substitution or adjacent swap. */
    private static boolean oneTypoApart(String typed, String known)
    {
        int a = typed.length(), b = known.length();
        if (Math.abs(a - b) > 1 || typed.equals(known))
        {
            return false;
        }
        if (a == b)
        {
            int first = -1, second = -1;
            for (int i = 0; i < a; i++)
            {
                if (typed.charAt(i) != known.charAt(i))
                {
                    if (first < 0) first = i;
                    else if (second < 0) second = i;
                    else return false;
                }
            }
            return first >= 0 && (second < 0 || (second == first + 1
                && typed.charAt(first) == known.charAt(second)
                && typed.charAt(second) == known.charAt(first)));
        }
        String shorter = a < b ? typed : known;
        String longer = a < b ? known : typed;
        int i = 0, j = 0, skips = 0;
        while (i < shorter.length() && j < longer.length())
        {
            if (shorter.charAt(i) == longer.charAt(j))
            {
                i++; j++;
            }
            else
            {
                skips++; j++;
                if (skips > 1) return false;
            }
        }
        return true;
    }

    private static boolean containsAny(String text, String chars)
    {
        for (int i = 0; i < chars.length(); i++)
        {
            if (text.indexOf(chars.charAt(i)) >= 0) return true;
        }
        return false;
    }

    private static void boost(Map<String, Evidence> scores, String language, int weight)
    {
        scores.computeIfAbsent(language, ignored -> new Evidence()).scriptScore += weight;
    }

    public static final class Result
    {
        public enum Kind { SOURCE, ENGLISH, UNKNOWN }
        private final Kind kind;
        private final SourceLanguage language;

        private Result(Kind kind, SourceLanguage language)
        {
            this.kind = kind;
            this.language = language;
        }

        static Result source(SourceLanguage language) { return new Result(Kind.SOURCE, language); }
        static Result english() { return new Result(Kind.ENGLISH, null); }
        static Result unknown() { return new Result(Kind.UNKNOWN, null); }

        public Kind kind() { return kind; }
        public SourceLanguage language() { return language; }
    }
}
