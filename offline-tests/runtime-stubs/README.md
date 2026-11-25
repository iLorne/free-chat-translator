# Offline API stand-ins — not RuneLite

These small, deliberately minimal Java classes emulate **only the APIs called by this plugin** to let `RuntimeBehaviorChecks` invoke production event handlers and MyMemory callbacks locally. They are not vendor code and are not proof of compatibility with the real RuneLite, OkHttp or Gson APIs. No network calls are made. Run the real Windows `compileJava` and `compileTestJava` acceptance checks before release.
