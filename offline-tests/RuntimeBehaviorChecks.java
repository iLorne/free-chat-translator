package com.chattranslator;

import com.google.gson.Gson;
import java.awt.Rectangle;
import java.lang.reflect.Field;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.MenuEntry;
import net.runelite.api.Point;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.MenuOpened;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.events.ConfigChanged;
import okhttp3.Call;
import okhttp3.OkHttpClient;

/** Behavioral offline harness that compiles and invokes the actual production plugin and HTTP client.
 * All RuneLite, OkHttp, and Gson classes in this harness are clearly identified stand-ins.
 * It is NOT native RuneLite acceptance or a real network test.
 */
public final class RuntimeBehaviorChecks
{
    private static int checks;
    static void check(boolean ok, String label)
    {
        checks++;
        if (!ok) throw new AssertionError(label);
    }
    static void field(Object object,String key,Object value)
    {
        try
        {
            Field f=ChatTranslatorPlugin.class.getDeclaredField(key);
            f.setAccessible(true);f.set(object,value);
        }
        catch(ReflectiveOperationException e){throw new AssertionError(e);}
    }
    static final class TestConfig implements ChatTranslatorConfig
    {
        boolean master,sensitive;
        @Override public boolean allowThirdPartyTranslation(){return master;}
        @Override public boolean acknowledgePrivateChatSharing(){return sensitive;}
    }
    static final class Fixture
    {
        final ChatTranslatorPlugin plugin=new ChatTranslatorPlugin();
        final Client client=new Client();
        final ClientThread thread=new ClientThread();
        final TestConfig config=new TestConfig();
        final OkHttpClient http=new OkHttpClient();
        Fixture()
        {
            field(plugin,"client",client);
            field(plugin,"clientThread",thread);
            field(plugin,"config",config);
            field(plugin,"translationClient",new MyMemoryClient(http,new Gson()));
            plugin.startUp();
        }
        MenuEntry select(ChatMessageType type,String text,int x)
        {
            client.menu.clear();
            client.mouse=new Point(x,15);
            Widget scroll=new Widget();
            scroll.bounds=new Rectangle(0,0,340,120);
            Widget row=new Widget();
            row.bounds=new Rectangle(0,4,230,30);
            row.text="Bob: "+text;
            scroll.children=new Widget[]{row};
            client.scroll=scroll;
            plugin.onChatMessage(new ChatMessage(type,"Bob",text));
            plugin.onMenuOpened(new MenuOpened());
            return client.menu.entries.isEmpty()?null:client.menu.entries.get(0);
        }
        void revokePrivate()
        {
            config.sensitive=false;
            plugin.onConfigChanged(new ConfigChanged("chattranslator-free","acknowledgePrivateChatSharing","false"));
            thread.flush();
        }
        void revokeMaster()
        {
            config.master=false;
            plugin.onConfigChanged(new ConfigChanged("chattranslator-free","allowThirdPartyTranslation","false"));
            thread.flush();
        }
    }
    public static void main(String[] args)
    {
        check(!TranslationConsent.shouldCancelAfterPrivateRevocation(null),"idle request not canceled");
        check(!TranslationConsent.shouldCancelAfterPrivateRevocation(ChatMessageSelection.Channel.PUBLIC),"public does not need sensitive consent");
        for(ChatMessageSelection.Channel ch:ChatMessageSelection.Channel.values())
        {
            check(TranslationConsent.shouldCancelAfterPrivateRevocation(ch)==ch.requiresPrivacyAcknowledgment(),"channel rule "+ch);
        }
        check(ChatRowHitTest.containsHorizontal(new Rectangle(40,1,100,20),50),"inside horizontal span");
        check(!ChatRowHitTest.containsHorizontal(new Rectangle(40,1,100,20),151),"right of horizontal span");
        check(!ChatRowHitTest.containsHorizontal(new Rectangle(40,1,100,20),39),"left of horizontal span");
        check(!ChatRowHitTest.containsHorizontal(new Rectangle(40,1,0,20),40),"empty span");
        check(!ChatRowHitTest.containsHorizontal(null,50),"null span");
        String french="Bonjour, je cherche un groupe pour combattre le boss";
        Fixture f=new Fixture();
        f.config.master=true;f.config.sensitive=true;
        MenuEntry publicMenu=f.select(ChatMessageType.PUBLICCHAT,french,60);
        check(publicMenu!=null && publicMenu.option.equals("Translate to English"),"hovered public menu");
        publicMenu.click();
        Call publicCall=f.http.latest;
        check(publicCall!=null && publicCall.callback!=null,"public HTTP queued");
        check(publicCall.request.url.contains("langpair=fr%7Cen"),"correct language pair dispatched");
        f.revokePrivate();
        check(!publicCall.canceled,"sensitive OFF must not cancel public call");
        publicCall.succeed("{\"responseStatus\":200,\"responseData\":{\"translatedText\":\"Hello, I need a team.\"}}");
        f.thread.flush();
        check(f.client.messages.stream().anyMatch(x->x.contains("[FR to EN] Hello, I need a team.")),"public result survives private OFF");
        Fixture dm=new Fixture();dm.config.master=true;dm.config.sensitive=true;
        MenuEntry dmMenu=dm.select(ChatMessageType.PRIVATECHAT,french,60);
        check(dmMenu!=null,"private right-click menu");dmMenu.click();Call dmCall=dm.http.latest;
        check(dmCall!=null,"private HTTP queued");dm.revokePrivate();
        check(dmCall.canceled,"private OFF cancels active private request");
        dmCall.succeed("{\"responseStatus\":200,\"responseData\":{\"translatedText\":\"Hello\"}}");dm.thread.flush();
        check(dm.client.messages.stream().noneMatch(x->x.contains("[FR to EN]")),"revoked private never displays old result");
        Fixture master=new Fixture(); master.config.master=true;
        MenuEntry masterMenu=master.select(ChatMessageType.PUBLICCHAT,french,60);
        check(masterMenu!=null,"public selection available");masterMenu.click();Call masterCall=master.http.latest;
        master.revokeMaster();check(masterCall.canceled,"master consent OFF cancels any request");
        Fixture english=new Fixture();
        MenuEntry enMenu=english.select(ChatMessageType.PUBLICCHAT,"I am looking for the boss",60);
        check(enMenu!=null,"English chat can be selected with master consent OFF");enMenu.click();english.thread.flush();
        check(english.client.messages.stream().anyMatch(x->x.contains("No translation is needed")),"local English status without HTTP permission");
        check(english.http.latest==null,"English produces no HTTP request");
        Fixture unknown=new Fixture();MenuEntry uMenu=unknown.select(ChatMessageType.PUBLICCHAT,"xqzr zyqw zxx",60);
        check(uMenu!=null,"unknown chat selected");uMenu.click();unknown.thread.flush();
        check(unknown.client.messages.stream().anyMatch(x->x.contains("Couldn't identify")),"uncertain local status without HTTP permission");
        check(unknown.http.latest==null,"unknown produces no HTTP request");
        Fixture locked=new Fixture(); locked.config.master=true;
        MenuEntry lockedMenu=locked.select(ChatMessageType.PRIVATECHAT,french,60);
        check(lockedMenu!=null && lockedMenu.option.contains("Private/group translation OFF"),"private warning menu text");
        lockedMenu.click();locked.thread.flush();
        check(locked.client.messages.stream().anyMatch(x->x.contains("Private/group translations are OFF")),"private warning response");
        check(locked.http.latest==null,"private denied never contacts HTTP");
        Fixture hover=new Fixture();
        check(hover.select(ChatMessageType.PUBLICCHAT,french,280)==null,"no hover translation in blank row to right");
        hover.client.mouse=new Point(60,15); hover.plugin.onMenuOpened(new MenuOpened());
        check(!hover.client.menu.entries.isEmpty(),"hover works on real text widget");
        hover.client.menu.clear();hover.plugin.onGameStateChanged(new GameStateChanged(GameState.HOPPING));
        hover.plugin.onMenuOpened(new MenuOpened());
        check(hover.client.menu.entries.isEmpty(),"world hop clears cached messages");
        // Genuine MyMemoryClient flow with controllable fake OkHttp/Gson objects.
        final StringBuilder output=new StringBuilder();
        MyMemoryClient client=new MyMemoryClient(new OkHttpClient(),new Gson());
        OkHttpClient net=new OkHttpClient();client=new MyMemoryClient(net,new Gson());
        MyMemoryClient.TranslationCallback callback=new MyMemoryClient.TranslationCallback()
        {public void onSuccess(String s){output.append("OK:").append(s);}public void onError(String s){output.append("ERR:").append(s);}};
        Call call=client.translate(french,SourceLanguage.FRENCH,callback);
        call.succeed("{\"responseStatus\":200,\"responseData\":{\"translatedText\":\"It&#8217;s fine &amp; safe\"}}");
        check(output.toString().equals("OK:It’s fine & safe"),"response parser success + safe HTML entities");
        output.setLength(0);call=client.translate(french,SourceLanguage.FRENCH,callback);
        call.respond(429,"{}");check(output.toString().contains("HTTP 429"),"HTTP error reporting");
        output.setLength(0);call=client.translate(french,SourceLanguage.FRENCH,callback);
        call.fail();check(output.toString().contains("couldn't be reached"),"I/O error reporting");
        output.setLength(0);call=client.translate(french,SourceLanguage.FRENCH,callback);
        call.succeed("{\"quotaFinished\":true,\"responseStatus\":200}");
        check(output.toString().contains("quota"),"quota error reporting");
        output.setLength(0);call=client.translate(french,SourceLanguage.FRENCH,callback);
        call.succeed("not-json");check(output.toString().contains("response could not be read"),"invalid JSON error reporting");
        output.setLength(0);call=client.translate(french,SourceLanguage.FRENCH,callback);
        call.succeed("x".repeat(70000));check(output.toString().contains("unexpectedly large"),"bounded HTTP response");
        check("It’s time".equals(TranslationRequest.forChatDisplay("It&#8217;s time")),"decimal entity");
        check("A B".equals(TranslationRequest.forChatDisplay("A&nbsp;B")),"nonbreaking space entity");
        check("(col=red)hi(/col)".equals(TranslationRequest.forChatDisplay("&#60;col=red&#62;hi&#60;/col&#62;")),"numeric markup neutralization");
        check("&lt;col=red&gt;".equals(TranslationRequest.forChatDisplay("&amp;lt;col=red&amp;gt;")),"one entity decoding pass only");
        check("&#0;".equals(TranslationRequest.forChatDisplay("&#0;")),"do not decode NUL");
        check(LocalLanguageDetector.detect("こんにちは 안녕하세요").kind()==LocalLanguageDetector.Result.Kind.UNKNOWN,"mixed Japanese/Korean uncertain");
        check(LocalLanguageDetector.detect("안녕하세요 你好").kind()==LocalLanguageDetector.Result.Kind.UNKNOWN,"mixed Korean/Chinese uncertain");
        check(LocalLanguageDetector.detect("こんにちは世界").language()==SourceLanguage.JAPANESE,"Japanese kana+han remains valid");
        check(LocalLanguageDetector.detect("안녕하세요, 도움이 필요해요").language()==SourceLanguage.KOREAN,"Korean remains valid");
        check(LocalLanguageDetector.detect("你好，我需要帮助").language()==SourceLanguage.CHINESE_SIMPLIFIED,"Chinese remains valid");
        System.out.println("RuntimeBehaviorChecks: "+checks+"/"+checks+" PASS (synthetic RuneLite/OkHttp/Gson, NO NETWORK)");
    }
}
