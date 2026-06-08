# Aria — AI assistant (build it yourself with your hardcoded key)

Aria is an agentic phone assistant: you type or speak, Claude decides which
tool to run, and the app does it on your phone (open any app, send SMS,
WhatsApp, call, set alarms/timers, web search) then tells you what it did.

This is an **Android Studio project**. You build it on your own machine so your
API key stays on your computer and is hardcoded into the APK — it is never
shared and never committed (the key file is gitignored).

## Build it (key stays on your machine)

1. **Install Android Studio** (free): https://developer.android.com/studio
   On first launch it downloads the Android SDK automatically — accept the
   defaults.

2. **Open the project:** Android Studio → *Open* → select this `assistant`
   folder. Let it finish "Gradle sync" (it downloads Gradle/AGP the first time).

3. **Put your key in** `app/src/main/res/values/secrets.xml`:

   ```xml
   <resources>
       <string name="anthropic_api_key">sk-ant-api03-...your key...</string>
       <string name="anthropic_model">claude-haiku-4-5</string>
       <string name="backend_url"></string>
   </resources>
   ```

   - Get a key at https://console.anthropic.com → API keys.
   - This file is **gitignored**, so your key never leaves your machine.
   - If the file isn't there yet, copy `secrets.example.xml` to that path.

4. **Plug in your phone** (USB debugging on), pick it in the device dropdown,
   and press **Run ▶**. Android Studio builds, installs, and launches Aria.

   Prefer the command line? `./gradlew installDebug` (or `gradlew.bat
   installDebug` on Windows) with the phone connected.

## Notes

- **Keep this build private.** A hardcoded key can be extracted from the APK,
  so don't distribute it. For a public/paid release, leave `anthropic_api_key`
  empty and set `backend_url` to a server that holds the key and checks the
  subscription — then the app ships with no key.
- **Model:** defaults to `claude-haiku-4-5` (fast + cheap, ideal for a phone
  assistant). Change `anthropic_model` to `claude-sonnet-4-6` for harder
  requests.
- **Permissions:** on first run, grant SMS / phone / contacts / mic so the
  assistant can text, call, and listen.

## Project layout

```
assistant/
  app/
    build.gradle                      # module config (namespace, SDKs)
    src/main/
      AndroidManifest.xml
      java/com/aiassistant/           # Agent, Tools, AnthropicClient, ChatActivity…
      res/values/secrets.xml          # YOUR KEY (gitignored)
  build.gradle, settings.gradle       # Gradle project
  gradlew, gradlew.bat, gradle/       # Gradle wrapper
  secrets.example.xml                 # template to copy
  build.sh                            # offline CI build (Debian Android tools)
```
