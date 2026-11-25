#!/usr/bin/env python3
from pathlib import Path
import re
root=Path(__file__).resolve().parents[1]
src=root/'src/main/java/com/chattranslator'
plugin=(src/'ChatTranslatorPlugin.java').read_text()
config=(src/'ChatTranslatorConfig.java').read_text()
sel=(src/'ChatMessageSelection.java').read_text()
http=(src/'MyMemoryClient.java').read_text()
req=(src/'TranslationRequest.java').read_text()
source='\n'.join(p.read_text() for p in src.glob('*.java'))
checks={
 'no development command': 'CommandExecuted' not in plugin and 'fctsmoketest' not in source,
 'no temporary settings toggle': 'runControlledSample' not in source and 'Run French sample test' not in source,
 'no hardcoded French sample': 'fixedFrenchDevelopmentSample' not in source and 'Bonjour, je cherche' not in source,
 'no DEV result routing': 'controlledTest' not in source and 'showControlledGameMessage' not in source,
 'single translation path': plugin.count('private void translateSelected(')==1,
 'right-click menu retained': 'void onMenuOpened(MenuOpened event)' in plugin and 'selectMessageFromHoveredRow' in plugin,
 'event provenance cache retained': 'matchRenderedLine' in sel and 'matchRenderedParts' in sel,
 'main opt-in defaults OFF': bool(re.search(r'default boolean allowThirdPartyTranslation\(\)\s*\{\s*return false;',config)),
 'sensitive opt-in defaults OFF': bool(re.search(r'default boolean acknowledgePrivateChatSharing\(\)\s*\{\s*return false;',config)),
 'third party standard warning retained twice': config.count('This feature submits your IP address to a 3rd-party server not controlled or verified by RuneLite developers')==2,
 'revocation cancellation preserved': 'consentRevocations.incrementAndGet()' in plugin and 'privateChatRevocations.incrementAndGet()' in plugin and 'cancelActiveTranslation()' in plugin,
 'request only on explicit menu click': '.onClick(menuEntry ->' in plugin and 'translateSelected(verified)' in plugin,
 'http response size bounded': 'BoundedResponseReader.readUtf8' in http,
 'service uses injected OkHttp': '@Inject' in http and 'OkHttpClient' in http and '.get()' in http,
 'no auto-transmission on chat event': 'translationClient.translate' not in plugin.split('public void onChatMessage(ChatMessage event)')[1].split('private static ChatMessageSelection.Channel channelFor')[0],
 'no known forbidden network/process API': not re.search(r'HttpURLConnection|ProcessBuilder|java\.net\.http|Runtime\.getRuntime|java\.awt\.Robot',source),
 'plugin metadata correct': 'displayName=Free Chat Translator' in (root/'runelite-plugin.properties').read_text(),
 'build set standard': 'build=standard' in (root/'runelite-plugin.properties').read_text(),
 'no generated classes': not list(root.rglob('*.class')),
}
for name,ok in checks.items(): print(('PASS' if ok else 'FAIL')+': '+name)
print(f'{sum(checks.values())}/{len(checks)} release-surface assertions PASS')
if not all(checks.values()): raise SystemExit(1)
