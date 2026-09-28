# SapotLokal — Prerequisites & Setup Guide (A to Z)

**Purpose of this file:** This is the checklist to complete **before** giving the AI the Phase 1 prompt in `SAPOTLOKAL_BUILD_GUIDE.md`. It covers installing and checking tools, creating the project folder, setting up Git and GitHub, connecting Antigravity in VS Code, preparing a test device (emulator first), and understanding how to code from **VS Code and Android Studio at the same time**. Work through it top to bottom. An AI assistant can help if a step fails — tell it: *"Read SAPOTLOKAL_PREREQUISITES.md. I'm stuck on Step [N]."*

---

## 0. Setup Snapshot

- **Goal:** One folder on your computer that is (a) a Git repo connected to GitHub, (b) open in both VS Code (with the Antigravity extension) and Android Studio, and (c) able to build and run on an Android emulator (real phone later)
- **Already done:** Latest stable Android Studio is installed. **No separate JDK is needed** — Android Studio includes its own JDK, which it uses to build the app by default
- **Still to do:** Verify the Android SDK, install Git, create a GitHub account and repo, create the project folder, install VS Code and the Antigravity extension, create an emulator
- **Not done here:** The Android Studio project itself and the Firebase project. Both are created in **Phase 1** of the build guide, with the AI explaining each step. Do not create them now
- **Operating system:** Not yet confirmed. Where commands differ, both **Windows (PowerShell)** and **macOS/Linux (Terminal)** versions are shown. Use the one that matches your machine
- **Final folder layout after Phase 1:**
  ```
  sapotlokal/
  ├── app/            # Android Studio module (Kotlin + XML) — created in Phase 1
  ├── docs/           # Flowchart, ERD, Figma exports, notes
  ├── .gitignore
  └── SAPOTLOKAL_BUILD_GUIDE.md
  ```

---

## 1. Rules for the AI Assistant (if you ask it for help)

1. **One step at a time.** Do not skip ahead. Wait for the developer to confirm each step worked before giving the next.
2. **Explain what and why** in 2–5 plain sentences before any command, so the developer understands what it does.
3. **Label the environment** of every command block (PowerShell, Terminal, Android Studio, VS Code, a website). Ask the developer's OS before giving OS-specific commands.
4. **Never ask the developer to paste secrets** (passwords, tokens, private SSH keys) into the chat.
5. **Never install anything not listed in Section 2** without proposing it and getting approval first.
6. **Do not create the Android Studio project or Firebase project** in this file's steps. That belongs to Phase 1.
7. **If a command fails, ask for the exact error text** and diagnose from it. Do not guess.

---

## 2. Tools & Accounts Checklist

| Item | Status | Notes |
|---|---|---|
| Android Studio (latest stable) | ✅ Installed | Includes the Android SDK manager, emulator, and Logcat |
| JDK | ✅ Not needed separately | Android Studio bundles its own JDK and uses it by default. Do not install another JDK unless the AI tells you a specific reason |
| Android SDK Platform + Platform-Tools | ⬜ Verify in Step 1 | Comes through Android Studio's SDK Manager. Platform-Tools provides `adb` |
| Git | ⬜ Install in Step 2 | Latest stable, from git-scm.com |
| GitHub account | ⬜ Step 3 | Free account is enough |
| VS Code | ⬜ Step 6 | **Version 1.90 or later** (Antigravity extension requirement) |
| Google Antigravity extension for VS Code | ⬜ Step 6 | Published by **Google** on the VS Code Marketplace. Needs a Google account with any Antigravity plan, including the free tier |
| Android Emulator with a **Google Play** system image | ⬜ Step 7 | Your main test device for now. Needs virtualization enabled on your computer |
| Physical Android phone (Android 8.0+) | ⬜ Later | Not needed to start. Borrow one to test real camera scanning before finishing |
| Figma access | ⬜ Optional now | Export screens to `docs/figma/` when ready |
| Google account for Firebase | ⬜ Needed in Phase 1 | Just make sure you can sign in. The Firebase project is created in Phase 1 |

---

## 3. Step-by-Step Setup

### Step 1 — Verify Android Studio, the SDK, and the bundled JDK

**Where:** Android Studio and a terminal

1. Open Android Studio. If it shows the Welcome screen, that's fine — you don't need a project open yet.
2. Open **More Actions → SDK Manager** (or **Tools → SDK Manager** if a project is open).
3. On the **SDK Platforms** tab, make sure the newest stable Android SDK Platform is ticked and installed.
4. On the **SDK Tools** tab, make sure these are ticked and installed: **Android SDK Build-Tools**, **Android SDK Platform-Tools**, **Android Emulator**.
5. Note the **Android SDK Location** shown at the top of the SDK Manager. You'll need it in Step 7.
6. Look at which JDK Android Studio uses (view only — don't change anything): **Settings → Build, Execution, Deployment → Build Tools → Gradle**, then read the **Gradle JDK** field. It should show a JDK bundled with Android Studio (often labelled with "jbr" or "embedded"). If a project isn't open, this setting may only appear once a project exists — that's fine, Phase 1 checks it.

   You do **not** need `java -version` to work in your terminal. Building the app happens inside Android Studio, which brings its own JDK. If a terminal command later needs Java, the AI will tell you.

**Why this matters:** Android Studio builds the app using the SDK and a JDK. The SDK you verify here; the JDK comes with Android Studio. The exact "Gradle JDK" setting is confirmed in Phase 1, so don't change it now.

---

### Step 2 — Install and configure Git

**Where:** A website, then a terminal

1. Download and install Git from **git-scm.com** using the defaults. (On macOS it may already be present or be offered via Xcode command line tools.)
2. Close and reopen your terminal, then check it installed:
   ```
   git --version
   ```
3. Tell Git who you are (use the same email as your GitHub account):
   ```
   git config --global user.name "Your Name"
   git config --global user.email "you@example.com"
   git config --global init.defaultBranch main
   ```
4. **Windows only** — avoid line-ending problems between VS Code and Android Studio:
   ```
   git config --global core.autocrlf true
   ```
   **macOS/Linux:**
   ```
   git config --global core.autocrlf input
   ```

**Why this matters:** Git records every change, so you can roll back if the AI edits something badly. The line-ending setting stops Windows and Unix line endings from making files look "changed" when you switch between the two IDEs.

---

### Step 3 — Create your GitHub account and repository

**Where:** github.com in your browser

1. Sign up or log in at **github.com**.
2. Click **New repository**.
3. Set:
   - **Repository name:** `sapotlokal`
   - **Visibility:** **Private** (recommended while building)
   - **Do NOT** tick "Add a README", "Add .gitignore", or "Choose a license". Leave the repo completely empty so your first push has no conflicts
4. Click **Create repository**.
5. Copy the repo URL from the next page. It looks like `https://github.com/YOUR-USERNAME/sapotlokal.git`.

**Sign-in method for pushing:** The simplest is HTTPS. The first time you push, Git opens a browser window to sign in to GitHub — just approve it. (On Windows, Git for Windows includes the sign-in helper. On macOS/Linux, if you're asked for a password, GitHub no longer accepts your account password there; use a personal access token or set up SSH. Ask the AI to walk you through whichever you prefer.)

---

### Step 4 — Create the project folder

**Where:** File Explorer / Finder plus a terminal

**Choose a location carefully:**
- Use a **short path with no spaces** (e.g., `C:\dev\sapotlokal` or `~/dev/sapotlokal`)
- **Avoid** folders synced by OneDrive, Google Drive, iCloud, or Dropbox. Sync tools lock and rewrite files while Gradle is building, which causes strange errors

**Windows (PowerShell):**
```
mkdir C:\dev\sapotlokal
mkdir C:\dev\sapotlokal\docs
cd C:\dev\sapotlokal
```

**macOS/Linux (Terminal):**
```
mkdir -p ~/dev/sapotlokal/docs
cd ~/dev/sapotlokal
```

Now copy these files in:
- `SAPOTLOKAL_BUILD_GUIDE.md` → into `sapotlokal/` (the root)
- `SAPOTLOKAL_PREREQUISITES.md` → into `sapotlokal/docs/`
- Your Flowchart PDF, ERD PDF, and Simple App Guide `.md` → into `sapotlokal/docs/`

**Do not create the Android Studio project yet.** Phase 1 does that, with the project location set to this same folder.

---

### Step 5 — Create `.gitignore`, make the first commit, and push

**Where:** Terminal inside `sapotlokal/`

1. Create a file named `.gitignore` in the root of `sapotlokal/` with this content. It keeps build junk, personal settings, and secrets out of GitHub:
   ```
   # Gradle / build output
   .gradle/
   build/
   */build/

   # Android Studio / IDE
   .idea/
   *.iml
   local.properties
   captures/
   .externalNativeBuild/
   .cxx/

   # Secrets and signing (never commit these)
   google-services.json
   *.jks
   *.keystore
   *.env

   # OS junk
   .DS_Store
   Thumbs.db
   ```
   Android Studio generates its own `.gitignore` during Phase 1. When that happens, the AI should **merge** the two, keeping every line above.

2. Initialize Git, make the first commit, and connect to GitHub (replace the URL with yours from Step 3):
   ```
   git init
   git add .
   git commit -m "Initial commit: build guide and project docs"
   git branch -M main
   git remote add origin https://github.com/YOUR-USERNAME/sapotlokal.git
   git push -u origin main
   ```
3. Refresh the GitHub page. You should see your files.

**Why this matters:** `google-services.json` is ignored on purpose, following Rule 5 of the build guide. In Phase 1 the AI creates a placeholder example file and tells you how to get the real one from Firebase.

---

### Step 6 — Install VS Code and the Antigravity extension

**Where:** A website, then VS Code

1. Download **VS Code** from code.visualstudio.com (**version 1.90 or later**) and install it.
2. Open VS Code and open the Extensions sidebar: **Ctrl+Shift+X** (Windows/Linux) or **Cmd+Shift+X** (macOS).
3. Search for **Google Antigravity** and pick the one published by **Google**. Click **Install**.
4. Click the **Antigravity icon** in VS Code's left Activity Bar and sign in with your Google account (a free plan is fine). On first launch, the extension installs a small local background service called `agy` automatically — let it finish.
5. Open your project: **File → Open Folder…** → choose `sapotlokal`. Click **Yes, I trust the authors** if asked.
6. **Smoke test.** In the Antigravity panel, type:
   > *Read SAPOTLOKAL_BUILD_GUIDE.md and list the 7 phases in one line each. Do not write any code.*

   If it lists the phases correctly, the agent can see your project. **Do not start Phase 1 yet.**

**Note on models:** Which AI models the extension uses depends on your Antigravity plan. Check the plan and model options shown in the extension after signing in.

---

### Step 7 — Set up your test device (emulator first, real phone later)

**Where:** Android Studio

You don't have a physical phone yet, so development starts on the **Android Emulator** (a virtual phone on your computer). That is fine for building almost everything. The one part that needs extra care is **QR scanning**, covered below. Plan to test on a real phone before you call the app finished.

**7A — Check your computer can run the emulator**

- **Virtualization must be on.** On Windows, this means hardware virtualization enabled in BIOS/UEFI and a Windows feature such as Windows Hypervisor Platform. If the emulator refuses to start, tell the AI the exact message. macOS and Linux normally work without extra setup.
- **Free RAM and disk.** The emulator needs several GB of RAM and disk space. Close heavy apps while it runs.

**7B — Create a virtual phone**

1. In Android Studio, open **Tools → Device Manager** (or **More Actions → Virtual Device Manager**).
2. Click **Create Device** and pick a phone model (any recent Pixel is a safe choice).
3. **System image — important:** choose an Android image that says **Google Play** (or at least **Google APIs**), **not** a plain "AOSP" image. Firebase and notifications depend on Google Play services, which only the Google images include.
4. Pick an Android version at or above **8.0 (API 26)**. A recent version is best for your main test device.
5. Finish, then press **Play ▶** on the device to start it. The first boot is slow.
6. Check it works: the emulator should show the Android home screen, and Android Studio's device dropdown should list it.

**7C — Emulator camera (needed for QR scanning)**

1. In Device Manager, click the **pencil/edit** icon on your virtual device → **Show Advanced Settings**.
2. Under **Camera**, set **Front** and **Back** to **Webcam0** if your computer has a webcam, or leave **Back** on **VirtualScene** to use the emulator's built-in 3D room.
3. **Ways to test a QR scan in the emulator:**
   - **Webcam route:** point your computer's webcam at a QR code shown on another screen (or printed).
   - **VirtualScene route:** the emulator's virtual room lets you place an image on a wall. Ask the AI to explain the current steps in the emulator's camera settings for showing a QR image there.
   - **Debug helper route (recommended for early phases):** the build guide's Testing caveat has the AI add a temporary debug screen where you can paste a shop or voucher code instead of scanning. It must be removed before Phase 7.

**7D — Two roles, one computer**

The app needs a Customer and a Shop Owner. Choose one:
- **Two emulators** running at once (two virtual devices in Device Manager) — one for each role. Needs more RAM.
- **One emulator, switching accounts** — log out and log in as the other role. Simpler, slower.

**7E — What the emulator can't tell you**

The emulator is good for screens, logic, database rules, and most flows. It is **not** a reliable test of: real camera focus and lighting on a QR code, real-world performance on a low-end phone, and how push notifications behave on a real device. Before you consider the app finished, test on at least one real Android phone (8.0+) — a friend's or a borrowed one is enough. When you get a real phone, turn on **Developer options** (tap **Build number** 7 times in About phone), enable **USB debugging**, connect it with a data cable, and accept the "Allow USB debugging?" prompt. Then check it shows as `device` with `adb devices` (Platform-Tools folder inside your SDK location from Step 1).

---

### Step 8 — Final pre-Phase-1 checklist

Tick everything before starting:

- [ ] Android Studio opens; SDK Platform, Build-Tools, Platform-Tools, and Emulator are installed
- [ ] You've looked at the Gradle JDK setting (or noted it's unavailable until a project exists) — no separate JDK installed
- [ ] `git --version` works and your name/email are configured
- [ ] GitHub repo `sapotlokal` exists and your first push succeeded
- [ ] Project folder is on a short path, not inside a cloud-synced folder
- [ ] `SAPOTLOKAL_BUILD_GUIDE.md` is in the repo root; flowchart, ERD, and guide are in `docs/`
- [ ] `.gitignore` is in place and committed
- [ ] VS Code (1.90+) has the Google Antigravity extension, signed in, with the `sapotlokal` folder open
- [ ] The smoke test in Step 6 correctly listed the 7 phases
- [ ] An emulator with a Google Play system image (Android 8.0+) starts and appears in Android Studio's device dropdown
- [ ] You've chosen how to test QR scanning for now (webcam, VirtualScene, or the debug paste helper)
- [ ] You can sign in to your Google account for Firebase (project is created in Phase 1)

When everything is ticked, go to Section 5.

---

## 4. How Two-Way Coding Works (VS Code ⇄ Android Studio)

You are not choosing one tool. Both open the **same folder** on your disk. Files are the shared truth: when either tool saves a file, the other sees it. Each tool does what it is best at.

### 4.1 Who does what

| Task | Use | Why |
|---|---|---|
| Ask the AI to write or change Kotlin, XML, Gradle files, Security Rules | **VS Code + Antigravity** | The agent works across the whole project, plans, and shows diffs |
| Review the AI's changes (diffs) before accepting | **VS Code + Antigravity** | Inline diff review is built in |
| **Sync Gradle**, build, and run on the emulator | **Android Studio** | It owns the Android build system |
| Emulator, Logcat (runtime logs), crash stack traces | **Android Studio** | VS Code has no equivalent |
| XML layout **Preview** and the visual design editor | **Android Studio** | Shows screens as they'll look |
| Kotlin refactoring, "find usages", debugger breakpoints | **Android Studio** | Deep Kotlin/Android understanding |
| Editing `.md` docs (build guide, notes) | **Either** (VS Code is more comfortable) | Plain text |
| Git commits and pushes | **One tool only** — pick whichever you find easier and stay consistent | Avoids confusion about what's committed |

### 4.2 The loop

```
 ┌──────────────────────────┐        saves files        ┌──────────────────────────┐
 │  VS Code + Antigravity   │ ────────────────────────► │      Project folder      │
 │  (AI writes the code)    │                           │   (one Git repo)         │
 └──────────────────────────┘                           └────────────┬─────────────┘
            ▲                                                        │ Android Studio
            │  copy build errors / Logcat back                       │ reloads files
            │                                                        ▼
            │                                           ┌──────────────────────────┐
            └────────────────────────────────────────── │      Android Studio      │
                                                        │  Sync → Build → Run      │
                                                        │  on emulator, read Logcat   │
                                                        └──────────────────────────┘
```

**A normal round trip:**
1. **VS Code:** Tell the agent what to do (e.g., "Phase 2, sign-up screen"). Review the diff and accept.
2. **Android Studio:** Switch to its window. It normally detects the changed files by itself. If it doesn't, use **File → Sync Project with File System**. If Gradle files changed, click **Sync Now** in the banner that appears.
3. **Android Studio:** Press **Run** ▶ with your emulator selected. Test the feature.
4. **If it fails:** Copy the red text from Android Studio's **Build** tab (compile errors) or the **Logcat** tab (crashes) and paste it to the agent in VS Code. Repeat.
5. **When the phase's Definition of Done is met:** Commit and push (see 4.3).

### 4.3 Ground rules that prevent pain

1. **One tool edits a given file at a time.** If you edit a file in Android Studio while the agent is editing it in VS Code, one of you will overwrite the other. Save and finish in one place before switching.
2. **Save everything before switching.** Unsaved changes live only inside the tool you typed in. The other tool cannot see them.
3. **Android Studio owns build and run.** Don't run Gradle build commands in VS Code's terminal while Android Studio is syncing or building. Two Gradle processes on the same project fight over locks and cause confusing errors.
4. **Never commit the `.idea/` folder, `local.properties`, `build/` folders, or `google-services.json`.** The `.gitignore` from Step 5 already covers these.
5. **Commit at the end of every phase at minimum**, and ideally before asking the agent for a big change. Then a bad change is one `git restore`/`git revert` away.
6. **Let the agent edit Gradle files, but you press Sync.** The agent can change dependencies. Only Android Studio can sync them. If the agent adds a library, switch to Android Studio, click **Sync Now**, and report any error back.
7. **Read the diff before accepting.** The point of "vibe coding" here is speed, not blind trust. Especially check Firebase Security Rules and anything touching points or vouchers, since mistakes there let users cheat.
8. **Pick one place for Git.** Either use Android Studio's Git tools or VS Code's Source Control panel or the terminal — not a mix — so you always know the state.

### 4.4 What each tool can't do

- **VS Code can't build or run the Android app.** It has no Android build system, no emulator, and no Logcat. Everything about "does it actually work" happens in Android Studio.
- **Android Studio doesn't run the Antigravity agent.** The agent lives in VS Code (through the extension). If you find yourself wanting to ask the AI something while in Android Studio, switch windows and paste in the error or code.

---

## 5. Troubleshooting

| Problem | Likely cause | Fix |
|---|---|---|
| `git` "not recognized" | Terminal opened before install, or PATH not set | Close and reopen the terminal; if it persists, restart the computer |
| `git push` asks for a password and rejects it | GitHub doesn't accept account passwords over HTTPS | Sign in through the browser prompt (Git for Windows), or use a personal access token / SSH — ask the AI to walk you through it |
| Emulator won't start / "virtualization" error | Hardware virtualization is off or unsupported | Enable virtualization in BIOS/UEFI and the matching Windows feature; send the AI the exact message |
| Emulator is very slow or freezes | Not enough RAM, or too many apps open | Close other apps, use a lighter device profile, run one emulator instead of two |
| Emulator camera shows a blank/black screen | Camera not set to Webcam/VirtualScene, or webcam in use by another app | Edit the virtual device → Camera settings; close other apps using the webcam |
| Firebase/Google features fail on the emulator | Emulator uses a plain AOSP image without Google Play services | Create a new virtual device with a **Google Play** system image |
| (Later, real phone) `adb devices` shows nothing or `unauthorized` | Charge-only cable, USB debugging off, or prompt not accepted | Use a data cable, enable USB debugging, accept the prompt on the phone |
| Antigravity icon missing in VS Code | Extension not installed/enabled, or VS Code older than 1.90 | Check Extensions list; update VS Code; restart it |
| Antigravity sign-in fails | Google account has no Antigravity entitlement, or browser blocked the sign-in tab | Sign in again; make sure the account has an Antigravity plan (free tier counts) |
| Android Studio doesn't see the AI's file changes | Auto-detect delayed | **File → Sync Project with File System**, or switch windows again |
| Strange Gradle "file is locked" errors | Project is inside OneDrive/Drive/iCloud, or two Gradle processes are running | Move the project to a non-synced folder; close extra terminals running Gradle |
| Every file shows as modified in Git | Line-ending mismatch | Re-check the `core.autocrlf` setting from Step 2 |

---

## 6. Quick Reference — You're Ready for Phase 1

When Step 8's checklist is fully ticked:

1. Open the `sapotlokal` folder in VS Code (Antigravity signed in) **and** have Android Studio open and ready.
2. Give the agent this prompt:

> "Read SAPOTLOKAL_BUILD_GUIDE.md. We're on Phase 1. My OS is [Windows/macOS/Linux]. Android Studio (latest) is already installed using its bundled JDK (no separate JDK), and the GitHub repo and this folder are already set up."

3. In Phase 1, the AI will guide you through creating the Android Studio project **inside this existing folder**. If Android Studio complains that the folder isn't empty, tell the AI — the fallback is to create the project in a fresh empty folder and move its contents into the repo.

If a setup decision changes (for example, you move the project folder, or choose SSH instead of HTTPS), ask the AI to update this file so it stays accurate.
