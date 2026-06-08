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

3. **Put your key in** `local.properties` (in the `assistant/` folder — Android
   Studio creates this file automatically on first sync). Add these lines:

   ```properties
   ANTHROPIC_API_KEY=sk-ant-api03-...your key...
   ANTHROPIC_MODEL=claude-haiku-4-5
   ```

   - Get a key at https://console.anthropic.com → API keys.
   - `local.properties` is **gitignored**, so your key never leaves your machine.
   - The project **builds without it** too — you'll just get a "configure your
     key" message in the app until you add the line and rebuild.
   - Gradle bakes the value into `BuildConfig.ANTHROPIC_API_KEY` at compile time.

4. **Plug in your phone** (USB debugging on), pick it in the device dropdown,
   and press **Run ▶**. Android Studio builds, installs, and launches Aria.

   Prefer the command line? `./gradlew installDebug` (or `gradlew.bat
   installDebug` on Windows) with the phone connected.

## Charging users — Google Play Billing

Billing is wired up but **off by default** so the app just works while you
develop. Turn it on when you're ready to sell:

1. Create a Google Play **developer account** (one-time $25) and a new app.
2. In Play Console → **Monetize → Subscriptions**, create a subscription and
   note its **product ID** (e.g. `aria_premium`).
3. In `local.properties` add:
   ```properties
   BILLING_ENABLED=true
   SUB_PRODUCT_ID=aria_premium
   ```
4. Upload the app to a Play **test track** and test the purchase.

When on, the app shows a **Subscribe** paywall until the user subscribes. The
subscription is tied to their **Google account**, so if they delete and
reinstall Aria, **their plan is restored automatically** (the "Restore" button
re-checks too). You'll see **revenue and subscriber numbers right in the Play
Console** — no server, login, or admin panel needed.

> Note: the model API key is still hardcoded in this build (see above). Because
> a determined user can extract it, the most robust setup for a public release
> is to also move the key behind a tiny server — but that's optional and can come
> later.

## Notes

- **Model:** defaults to `claude-haiku-4-5` (fast + cheap, ideal for a phone
  assistant). Set `ANTHROPIC_MODEL=claude-sonnet-4-6` for harder requests.
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
      res/                            # icons, colors, styles
  build.gradle, settings.gradle       # Gradle project
  gradlew, gradlew.bat, gradle/       # Gradle wrapper
  local.properties                    # YOUR KEY (gitignored, you create/edit)
  build.sh                            # offline CI build (Debian Android tools)
```
