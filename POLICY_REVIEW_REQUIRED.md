# RuneLite policy review required

Free Chat Translator sends the **selected text of another player's incoming chat message** to the free MyMemory HTTPS service when a user explicitly requests translation and enables the appropriate permissions. It does not intentionally send sender names, credentials, or batch-chat archives. Private/group messages require a second permission, both permissions default OFF, and local English/unknown feedback makes no request.

**Potential conflict:** RuneLite's [Rejected or Rolled Back Features](https://github.com/runelite/runelite/wiki/Rejected-or-Rolled-Back-Features) lists plugins exposing player information over HTTP among the features not currently considered. Selected chat text can itself include personal information, and transmitting it over HTTPS is still an external HTTP request. Existing translators are not assurance of a grandfathering exception for this plugin.

This is an explicit disclosure for future RuneLite reviewers, **not** a claim of compliance or approval. Source publication, any Plugin Hub marker file and the Plugin Hub pull request remain separate future actions.
