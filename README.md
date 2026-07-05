# Voice Todo — offline voice-driven todo list (Kotlin / Android)

A **100% offline** Android app. Tap the mic, **speak a task**, and it parses the
date/time and saves it. It reminds you with a **notification** at the right time
(including **daily** reminders), and shows everything on a **calendar**.

Everything runs on-device with open-source tools — no internet, no accounts.

## Features
- 🎤 **Voice input** with [Vosk](https://alphacephei.com/vosk/) — offline, open-source speech-to-text.
- 🧠 **Natural language** parsing: *"call mom tomorrow at 6 pm"*, *"daily standup at 9 am"*, *"pay rent on 31"*.
- 🔔 **Notifications** that fire on time (exact alarms, survive reboot), one-shot or recurring.
- 🗓️ **Calendar** view — days with tasks are marked; tap a day to see its tasks.
- 💾 **Offline storage** with Room (SQLite). No network permission at all.

## Tech stack (all open source)
Kotlin · Jetpack Compose · Room · AlarmManager · Vosk (Apache-2.0)

---

## 1. Install the toolchain (one time)

You said you'll use **VS Code**. VS Code is just the editor — Android still needs
a JDK, the Android SDK, and Gradle. Install:

1. **JDK 17** — https://adoptium.net/ (Temurin 17). Confirm: `java -version` → 17.
2. **Android SDK command-line tools** — https://developer.android.com/studio#command-line-tools-only
   - Unzip, then install platform + build tools:
     ```
     sdkmanager "platform-tools" "platforms;android-34" "build-tools;34.0.0"
     ```
   - Set env vars: `ANDROID_HOME` = your SDK path, and add `platform-tools` to `PATH`.
3. Create **`local.properties`** in this folder pointing to the SDK:
   ```
   sdk.dir=C\:\\Users\\awant\\AppData\\Local\\Android\\Sdk
   ```
   (use your real SDK path; on Windows escape backslashes as `\\`)
4. (Optional) VS Code extensions: *Kotlin Language*, *Gradle for Java*.

> The Gradle **wrapper** is already included, so you do **not** need to install
> Gradle separately — use `gradlew` / `gradlew.bat`.

## 2. Add a Vosk speech model (required for the mic)

The app ships without a model (they're 40 MB+). Download one and drop it in:

```
app/src/main/assets/model-en-us/
```

Steps:
1. Get **`vosk-model-small-en-us-0.15`** from https://alphacephei.com/vosk/models
2. Unzip it. Inside you'll see folders like `am/`, `conf/`, `graph/`, `ivector/`.
3. Copy **those contents** into `app/src/main/assets/model-en-us/` so you end up with:
   ```
   app/src/main/assets/model-en-us/am/...
   app/src/main/assets/model-en-us/conf/...
   app/src/main/assets/model-en-us/graph/...
   ```

**Other languages:** download that language's small model, put it in e.g.
`app/src/main/assets/model-hi/`, and change `modelAssetDir` in
`MainActivity.kt` (the `VoskManager(...)` line). Vosk has models for Hindi,
Spanish, French, German, Chinese, Russian, and 20+ more.

> Note: the voice **recognition** works in any language you load a model for.
> The **date/keyword parser** (`TodoParser.kt`) currently understands English
> words ("tomorrow", "daily", "on 31"). Extend the keyword maps there for other languages.

## 3. Build & run

Plug in an Android phone with **USB debugging** on (or start an emulator), then in
the VS Code terminal:

```bash
# Windows PowerShell
./gradlew.bat installDebug

# or build just the APK
./gradlew.bat assembleDebug
# -> app/build/outputs/apk/debug/app-debug.apk
```

First build downloads dependencies (needs internet **once**); after that the app
itself runs fully offline.

## 4. Using it
1. Launch **Voice Todo**. Grant **microphone** and **notification** permissions.
2. Tap the **mic** button and speak, e.g. *"submit report tomorrow at 5 pm"*.
3. The task appears in **Tasks**; check the **Calendar** tab to see it on its date.
4. A **notification** fires at the due time. Recurring ("daily"/"weekly") tasks
   re-arm automatically, and all reminders are restored after a reboot.

### Example phrases
| You say | Result |
|---|---|
| `buy milk on 31` | task on the 31st at 09:00 (default time) |
| `call mom tomorrow at 6 pm` | tomorrow 18:00 |
| `daily standup at 9 am` | every day 09:00 |
| `gym every day at 7:30 am` | every day 07:30 |
| `submit report on 15 july at 5 pm` | 15 Jul 17:00 |
| `meeting on monday at 10` | next Monday 10:00 |

## Project layout
```
app/src/main/java/com/example/voicetodo/
├─ MainActivity.kt         # UI shell, mic button, permissions
├─ VoiceTodoApp.kt         # creates the notification channel
├─ data/                   # Room entity, DAO, database, repository
├─ stt/VoskManager.kt      # offline speech-to-text
├─ nlp/TodoParser.kt       # "spoken sentence" -> date/time/recurrence
├─ notify/                 # AlarmManager scheduling + notifications + boot restore
└─ ui/                     # Compose screens (list + calendar) and ViewModel
```

## Troubleshooting
- **"Speech model not found"** → you skipped step 2, or files are nested one folder too deep.
- **No notification** → on Android 12+ allow "Alarms & reminders" for the app in system settings.
- **Build can't find SDK** → fix `local.properties` `sdk.dir`.
