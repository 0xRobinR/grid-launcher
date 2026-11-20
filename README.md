<div align="center">

# Grid Launcher

### Your whole phone on one calm screen.

**No search bar. No app drawer. Every app in reach, with the thing you open it for right under its name.**

A minimalist Android home screen built around a quiet grid, free-form widgets and screens you
shape yourself. Save as many setups as you like, and hand your whole launcher to a friend with
**one live code they scan once**.

![Android 10+](https://img.shields.io/badge/Android-10%2B-9DB8FF?style=flat-square&labelColor=17191C)
![Target SDK 36](https://img.shields.io/badge/target%20SDK-36-9DB8FF?style=flat-square&labelColor=17191C)
![Kotlin 2.2](https://img.shields.io/badge/Kotlin-2.2-9DB8FF?style=flat-square&labelColor=17191C)
![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-Material%203-9DB8FF?style=flat-square&labelColor=17191C)
![Dark only](https://img.shields.io/badge/theme-dark%20only-9DB8FF?style=flat-square&labelColor=17191C)
![No ads, no tracking](https://img.shields.io/badge/ads%20%26%20tracking-none-9DB8FF?style=flat-square&labelColor=17191C)

<br>

<img src="docs/designs/01-home.png" width="240" alt="Home screen: free-form widgets above a 3-column app grid with quick actions under each app">
&nbsp;
<img src="docs/designs/26-pinboard.png" width="240" alt="A pinboard home screen mixing a clover-shaped photo, apps with quick actions and widgets above the app grid">
&nbsp;
<img src="docs/designs/38-scan-to-import.png" width="240" alt="The Scan to import sheet with a live styled QR code and Save image and Share image buttons">

<sub>Calm home · Apps and widgets, mixed · One live code to share it all</sub>

<br><br>

[The idea](#the-idea) ·
[Features](#features) ·
[Saved setups](#saved-setups) ·
[Live codes](#share-with-a-live-code) ·
[Edge effects](#edge-effects) ·
[Gallery](#gallery) ·
[Privacy](#privacy-and-permissions) ·
[Get started](#get-started) ·
[Under the hood](#under-the-hood)

</div>

---

## The idea

Most launchers hide your apps behind a drawer, then add a search bar so you can find them again.
Grid Launcher goes the other way: **everything sits on the screen**, in an order your thumb learns.

Then it removes the second step. Want a selfie? There's a **Selfie** button right under Camera.
Directions home? **Home** sits under Maps. These are each app's own shortcuts, and you choose which
show up.

Everything else follows the same rule: **fewer things on screen, each one doing more.**

---

## Features

### 🏠 Home, your way

- **Up to 8 screens**, each with its own widgets and its own apps: every app, a hand-picked set, or
  none at all for a widgets-only screen.
- **A grid your size:** XS (5 columns) to XL (2 columns with big icons). Sort A–Z, by most used, or
  in your own order.
- **Each screen its own way:** give one screen its own grid size, quick actions, notification dots,
  labels and themed icons.
- **Screens from app groups:** one tap suggests screens by type (Finance, Games, Social, News,
  Shopping, Travel and more) or by publisher (Google, Microsoft…).
- **Screens that show themselves:** tie a screen to set hours or a Wi-Fi network. Work shows 9 to 5,
  Home shows at home, and each is hidden the rest of the time.
- **A switcher you design:** every screen gets a name and one of 40 icons. Choose how the bar looks:
  Pill, Icons, Labels, Dots, Snake, Line, or Off.

### 🧩 Widgets and the board

- **Free-form widgets:** put them anywhere, drag to move, pull a corner to resize. Every widget
  resizes both ways, even one that asks for a fixed size.
- **Alignment that helps:** widgets snap to edges, the center and each other, with guide lines.
  Hold one over another for 2 seconds and the one below glides down to make room.
- **Undo, step by step:** the arrow next to Done steps back up to 30 moves, resizes and aligns.
- **A pinboard:** mix apps in with your widgets, complete with their labels and quick actions.
- **Widgets between your apps:** stack a screen any way you like: widgets, apps, more widgets,
  more apps.
- **A photo widget:** any picture, cut to Rounded, Oval, Arch, Scallop or Clover.
- **Smart widgets, built in:** clock, battery, quick controls, up next, quick timer with a live
  countdown, quick note, day progress and text labels in any Google Font.

### 📱 Apps, one tap closer

- **Quick actions under every app:** none, one or two of its shortcuts as buttons, and you pin the
  ones you like.
- **Folders, notification dots and a recents row**, all optional.
- **App lock:** open chosen apps only after your fingerprint or screen lock.
- **Hidden apps, a favorites row, icon packs, themed icons** and **work profile** apps, badged, in
  the same grid.

### 🗂️ Safe to experiment

- **Saved setups:** keep whole launchers and single screens on the phone and switch in a tap.
  [More below.](#saved-setups)
- **Backups:** save everything to a file, and restore it on this phone or a new one.
- **Tutorials:** every gesture and feature on one page, each with a small animated drawing.

---

## Saved setups

> Build a Work week layout, a Weekend layout and a Travel screen, and switch between them whenever
> you like.

<table>
  <tr>
    <td width="55%" valign="top">

**Save:**
- The whole launcher: **Settings › More › Saved setups › Save this launcher**, then name it.
- One screen: open the screen's options › **Save this screen**.

**Use each saved setup to:**
- **Switch to it:** replaces your screens and settings with the saved ones.
- **Only add its screens:** puts its screens next to yours.
- **Rename** it, **share it as a code**, or **delete** it.

**What makes them different from sharing:**
- They're complete: hidden and locked apps, photos and other apps' widgets all come back.
- They never leave the phone.
- There's no limit; each one is a few kilobytes.

</td>
    <td width="45%" align="center" valign="top"><img src="docs/designs/31-saved-setups.png" width="260" alt="Saved setups: a Save this launcher button and a list of saved launchers and screens, each with apply, rename, share and delete"></td>
  </tr>
</table>

---

## Share with a live code

> Your whole launcher in **one code, read in one scan**.

Most apps share a layout as a file or a long line of text. Grid Launcher shows a **styled QR code**:
round dots in a deep blue-to-violet gradient, rounded corners and its own mark in the middle.

When a launcher is too big for one code, it becomes a **live code**: a single code that plays its
frames on a loop, a few times a second. The other phone holds its camera on it once, and a ring
fills as it reads every frame, usually in a second or two.

<table>
  <tr>
    <td width="50%" align="center"><img src="docs/designs/39-share-and-scan-steps.png" width="300" alt="Five numbered steps from sharing on one phone to scanning and importing on another"></td>
    <td width="50%" valign="top">

**Share** (phone A)
1. **Settings › Share your launcher**, or **Share this screen** in a screen's options. Saved setups
   share the same way.
2. The code appears. A big launcher plays as a live code.
3. Or tap **Save image** or **Share image** to send it as one picture.

**Import** (phone B)
1. **Settings › Import a shared code › Scan a code.**
2. Hold the camera on the code until the ring fills.
3. Choose **Replace my launcher** or **Only add its screens**.

Got a picture instead? **From a screenshot** reads every code in it at once.

</td>
  </tr>
</table>

**Built to travel safely**

| | |
| --- | --- |
| **Nothing personal** | Codes leave out hidden and locked apps, usage, recent apps, photos and Wi-Fi names. |
| **Widgets arrive as placeholders** | Other apps' widgets come in the same spot and size. Tap one to add the real widget. |
| **Apps are matched** | Apps the other phone doesn't have are skipped; the rest land in place. |
| **Camera only while scanning** | The scanner asks for the camera once and uses it only while it's open. Say no and Google's scanner, which needs no permission, reads one frame at a time. |
| **Dense by design** | Codes are packed in Base45, QR's compact alphabet, so each frame holds about 1.6 times as much as plain text. |

---

## Edge effects

Subtle animations along the edges of home. They're part of home only: they never cover another
app, and touches pass straight through.

| Effect | What it does |
| --- | --- |
| **Charging** | 12 styles over the charging port: Glow, Fire with sparks, Wave, Rings, Bubbles, Electric, Aurora, Neon, Orb, Fountain, Heartbeat and Rainbow. Turn the phone and it moves to the edge the port is on. |
| **Full charge** | Confetti at 100%, once per charge, then a calm green glow. |
| **Low battery** | A slow amber glow under 30%, and a quicker red pulse under 15%. |
| **Music** | Bars, Pulse or Waveform while anything plays: to a steady beat, or to the actual sound if you allow the audio visualizer. |
| **Notification flash** | The edges glow for a moment when a notification arrives. |
| **Timer** | A line drains along the bottom as a Quick timer runs, then pulses in the last ten seconds. |
| **Time of day** | A sky along the top: dawn pink, daylight blue, sunset orange, and night with stars. |

---

## Phone monitor

Swipe left of your first screen for a live dashboard: battery with a 24-hour chart, network speed,
data used per app, screen time, memory, storage and processor. It can alert you when the phone runs
hot or storage runs low. It only runs while you're looking at it.

---

## Gallery

<sub>Design mockups the app was built from. App icons are generic glyphs and every number is an example.</sub>

### Home and settings

<table>
  <tr>
    <td align="center" width="25%"><img src="docs/designs/01-home.png" alt="Home screen with widgets and the app grid"><br><b>Home</b><br><sub>Widgets on top, every app below</sub></td>
    <td align="center" width="25%"><img src="docs/designs/02-home-favorites.png" alt="Home screen with the favorites row above the app grid"><br><b>Favorites row</b><br><sub>Shown only when turned on</sub></td>
    <td align="center" width="25%"><img src="docs/designs/03-home-full-scroll.png" alt="The whole home screen scrolled"><br><b>The whole home</b><br><sub>One scroll, no drawer</sub></td>
    <td align="center" width="25%"><img src="docs/designs/06-app-menu.png" alt="App long-press menu"><br><b>Long-press an app</b><br><sub>Shortcuts, pins, info, hide, uninstall</sub></td>
  </tr>
  <tr>
    <td align="center"><img src="docs/designs/07-settings.png" alt="Launcher settings screen"><br><b>Settings</b><br><sub>Everything on one page</sub></td>
    <td align="center"><img src="docs/designs/08-font-chooser.png" alt="Font chooser with Google Fonts families"><br><b>Font chooser</b><br><sub>Any Google Fonts family</sub></td>
    <td align="center"><img src="docs/designs/21-folders-dots.png" alt="A Work screen with a recents row, folders, notification counts and a locked app"><br><b>Folders and dots</b><br><sub>Recents, counts, app lock</sub></td>
    <td align="center"><img src="docs/designs/40-tutorials-live-code.png" alt="The Tutorials page with the live code lesson drawn as two phones"><br><b>Tutorials</b><br><sub>Every feature, animated</sub></td>
  </tr>
</table>

### Screens

<table>
  <tr>
    <td align="center" width="25%"><img src="docs/designs/09-custom-screen.png" alt="A Work screen with a text label, a calendar widget and six chosen apps"><br><b>A screen of its own</b><br><sub>Label, widgets, a custom set</sub></td>
    <td align="center" width="25%"><img src="docs/designs/10-screen-options.png" alt="Screen options sheet with apps mode, move, add and delete"><br><b>Screen options</b><br><sub>All apps, custom or none</sub></td>
    <td align="center" width="25%"><img src="docs/designs/11-clear-apps.png" alt="Confirmation dialog for clearing a screen's apps"><br><b>Clear apps</b><br><sub>Asks first, uninstalls nothing</sub></td>
    <td align="center" width="25%"><img src="docs/designs/12-empty-screen.png" alt="A widgets-only screen with a Focus label and a large clock"><br><b>Widgets only</b><br><sub>No apps, no grid</sub></td>
  </tr>
  <tr>
    <td align="center"><img src="docs/designs/13-choose-apps.png" alt="App picker with checkboxes and copy-from chips"><br><b>Choose apps</b><br><sub>Tick, or copy another set</sub></td>
    <td align="center"><img src="docs/designs/18-add-screen.png" alt="The page after the last screen, with a large plus button"><br><b>Add a screen</b><br><sub>Swipe past the last one</sub></td>
    <td align="center"><img src="docs/designs/19-screen-look.png" alt="Screen options with a name field and a grid of icons"><br><b>Name and icon</b><br><sub>Your own switcher</sub></td>
    <td align="center"><img src="docs/designs/29-app-groups.png" alt="Screens from app groups by type and by publisher"><br><b>Screens from groups</b><br><sub>By type or publisher</sub></td>
  </tr>
  <tr>
    <td align="center"><img src="docs/designs/23-switcher-styles.png" alt="Seven screen switcher styles"><br><b>Switcher styles</b><br><sub>Seven looks</sub></td>
    <td align="center"><img src="docs/designs/24-switcher-motion.png" alt="The Snake and Line switchers through one swipe"><br><b>Switcher motion</b><br><sub>Snake and Line follow your swipe</sub></td>
    <td align="center"></td>
    <td align="center"></td>
  </tr>
</table>

### Widgets and the board

<table>
  <tr>
    <td align="center" width="25%"><img src="docs/designs/04-edit-widgets.png" alt="Edit mode with resize handles on the selected widget"><br><b>Edit widgets</b><br><sub>Move, resize, snap, lock</sub></td>
    <td align="center" width="25%"><img src="docs/designs/05-add-widget.png" alt="Add a widget sheet with widgets grouped by app"><br><b>Add a widget</b><br><sub>Every widget on your phone</sub></td>
    <td align="center" width="25%"><img src="docs/designs/16-alignment.png" alt="A widget held over another, which moves down to make room"><br><b>Hold to make room</b><br><sub>Guides, snap, 2-second hold</sub></td>
    <td align="center" width="25%"><img src="docs/designs/20-widget-toolbar.png" alt="A selected widget with its toolbar just below it"><br><b>Widget toolbar</b><br><sub>Right under the widget</sub></td>
  </tr>
  <tr>
    <td align="center"><img src="docs/designs/25-resize-undo.png" alt="Edit mode with Undo beside Done and a stretched widget"><br><b>Resize and undo</b><br><sub>Any widget, any size</sub></td>
    <td align="center"><img src="docs/designs/17-smart-widgets.png" alt="A screen of built-in smart widgets"><br><b>Smart widgets</b><br><sub>Glance and act in one tap</sub></td>
    <td align="center"><img src="docs/designs/14-text-label.png" alt="Text label editor with font, weight, alignment and color"><br><b>Text labels</b><br><sub>Any Google Font, any size</sub></td>
    <td align="center"><img src="docs/designs/26-pinboard.png" alt="Apps, a photo and widgets mixed on the board"><br><b>Pinboard</b><br><sub>Apps and widgets, mixed</sub></td>
  </tr>
  <tr>
    <td align="center"><img src="docs/designs/27-photo-widget.png" alt="A scallop-shaped photo widget and its five outlines"><br><b>Photo widget</b><br><sub>Five outlines</sub></td>
    <td align="center"><img src="docs/designs/28-widget-areas.png" alt="Widget areas between rows of apps in edit mode"><br><b>Widget areas</b><br><sub>Widgets, apps, widgets, apps</sub></td>
    <td align="center"></td>
    <td align="center"></td>
  </tr>
</table>

### Setups and sharing

<table>
  <tr>
    <td align="center" width="25%"><img src="docs/designs/31-saved-setups.png" alt="Saved launchers and screens with apply, rename, share and delete"><br><b>Saved setups</b><br><sub>Switch layouts in a tap</sub></td>
    <td align="center" width="25%"><img src="docs/designs/38-scan-to-import.png" alt="A live styled QR code on the share sheet"><br><b>Scan to import</b><br><sub>One live code, one scan</sub></td>
    <td align="center" width="25%"><img src="docs/designs/39-share-and-scan-steps.png" alt="Sharing and scanning in five steps"><br><b>Share and scan</b><br><sub>Step by step</sub></td>
    <td align="center" width="25%"><img src="docs/designs/32-widget-placeholders.png" alt="Placeholders for other apps' widgets after an import"><br><b>Widget placeholders</b><br><sub>Tap to add the real widget</sub></td>
  </tr>
  <tr>
    <td align="center"><img src="docs/designs/30-share-codes.png" alt="Importing a pasted text code"><br><b>Text codes</b><br><sub>Still there, for pasting</sub></td>
    <td align="center"></td>
    <td align="center"></td>
    <td align="center"></td>
  </tr>
</table>

### Effects and the monitor

<table>
  <tr>
    <td align="center" width="25%"><img src="docs/designs/33-charging-effects.png" alt="Fire and sparks over the charging port"><br><b>Charging effects</b><br><sub>Over the port, any rotation</sub></td>
    <td align="center" width="25%"><img src="docs/designs/34-more-charging-effects.png" alt="Aurora, Neon, Orb, Fountain, Heartbeat and Rainbow"><br><b>More charging effects</b><br><sub>Twelve in all</sub></td>
    <td align="center" width="25%"><img src="docs/designs/35-edge-effects.png" alt="Music, notification, low battery and full charge effects"><br><b>Edge effects</b><br><sub>Music, notifications, battery</sub></td>
    <td align="center" width="25%"><img src="docs/designs/36-timer-time-of-day.png" alt="A timer line and four time-of-day skies"><br><b>Timer and sky</b><br><sub>Countdown line, daylight tint</sub></td>
  </tr>
  <tr>
    <td align="center"><img src="docs/designs/37-music-live.png" alt="Music effect following the actual sound, with a revoke option"><br><b>Music, live</b><br><sub>Opt-in, one-tap revoke</sub></td>
    <td align="center"><img src="docs/designs/15-phone-monitor.png" alt="The phone monitor dashboard"><br><b>Phone monitor</b><br><sub>The whole phone, live</sub></td>
    <td align="center"><img src="docs/designs/22-monitor-history.png" alt="24-hour battery history and data per app"><br><b>Monitor history</b><br><sub>24 h battery, data, alerts</sub></td>
    <td align="center"></td>
  </tr>
</table>

---

## Gestures

| Do this | Where | And this happens |
| --- | --- | --- |
| Tap | An app or a quick-action button | Opens it, or straight into that shortcut |
| Long-press | An app | All its shortcuts, pins, folders, place on the board, app info, hide, uninstall |
| Long-press | Empty space or a widget | Edit mode |
| Drag | A widget, in edit mode | Moves it; it snaps to edges, the center and other widgets |
| Drag | A corner handle | Resizes the widget |
| Hold 2 seconds | Over another widget | The one underneath glides down to make room |
| Tap **Undo** | Next to Done | Steps back the last move, resize or align |
| Swipe left or right | Anywhere on home | Moves between screens; past the last one, adds a new one |
| Tap | The screen switcher | Jumps to that screen |
| Swipe down | At the top of a screen | Notifications or quick settings |
| Swipe up / double-tap | Past the apps / empty space | Locks the screen, if turned on |
| Press Home, or swipe or press Back | On home | Back to the first screen and the top, or leaves edit mode |

---

## Privacy and permissions

Grid Launcher has no ads, no analytics and no account. It only asks for a permission the first time
you use the feature that needs it.

| Permission | Asked when | Used for |
| --- | --- | --- |
| Default home app | Setup | Quick actions: Android shares other apps' shortcuts only with the default launcher |
| Notification access | Dots, music effect or notification flash | Counting notifications and seeing that media is playing. Notification content is never read |
| Usage access | The phone monitor's screen time | Screen time, read on the phone |
| Calendar | The Up next widget | Your next events, read on the phone |
| Location | A screen shown on a Wi-Fi network | Android's rule for reading the network's name |
| Camera | Scanning a shared code | Only while the scanner is open. Optional: Google's scanner works without it |
| Microphone | *Follow the actual sound*, if you turn it on | Android's audio visualizer reads the sound playing as levels. Nothing is recorded. **Revoke microphone permission** hands it back |
| Accessibility service | Gestures | Only opening notifications and locking the screen |
| Fingerprint or screen lock | App lock | Opening locked apps |

---

## Get started

### Install on your phone

You'll need **Android Studio** (it includes Java, the Android SDK and `adb`) and a phone running
Android 10 or newer.

1. **Open the project.** Clone this repository, then in Android Studio choose **Open** and select
   the folder. The first sync downloads Gradle and the libraries.
2. **Prepare your phone.** Go to **Settings › About phone**, tap **Build number** seven times, then
   turn on **Settings › System › Developer options › USB debugging**. Connect by USB and allow
   debugging when asked.
3. **Run it.** Pick your phone and press **Run ▶**, or run `./gradlew :app:installDebug`.
4. **Make it home.** Open **Settings › Apps › Default apps › Home app**, choose **Grid Launcher**,
   then press Home.

> [!NOTE]
> Quick actions appear only while Grid Launcher is your default home app. Until then, home offers a
> **Set as home** button. To go back, choose your previous launcher on the same screen.

### Build from the command line

Requires JDK 17 or newer and the Android SDK with platform 36.

```sh
./gradlew :app:assembleDebug        # APK in app/build/outputs/apk/debug/
./gradlew :app:testDebugUnitTest    # JVM unit tests
./gradlew :app:lintDebug            # Android lint
```

The release build is minified but unsigned. Add your own signing config before publishing.

---

## Under the hood

```mermaid
flowchart LR
    subgraph Android
        LA["LauncherApps<br/>apps and shortcuts"]
        AWH["AppWidgetHost<br/>other apps' widgets"]
        SYS["Battery, network, usage,<br/>media sessions, display"]
        CAM["CameraX<br/>live-code scanner"]
    end
    subgraph Data["data/"]
        AR["AppRepository"]
        SR["SettingsRepository<br/>screens and settings"]
        SS["SetupStore<br/>saved setups"]
        SC["ShareCode + ShareQr<br/>codes, Base45, frames"]
    end
    subgraph Domain["domain/ + PageOps (pure Kotlin)"]
        RULES["Ordering, quick actions,<br/>widget geometry, screen edits"]
    end
    VM["LauncherViewModel"]
    UI["Compose UI<br/>home, edge effects, settings, share"]

    LA --> AR --> VM
    SR <--> VM
    SS <--> VM
    SC <--> VM
    RULES --> VM
    AWH --> UI
    SYS --> UI
    CAM --> UI
    VM <--> UI
```

**Built with:** Kotlin and Jetpack Compose (Material 3) in a single activity · DataStore ·
`LauncherApps` · `AppWidgetHost` · Google Fonts through Play services · ZXing to draw and read QR
codes · CameraX for the live scanner · Google's code scanner as the no-permission fallback ·
WorkManager for monitor alerts.

**A few decisions worth knowing:**
- **Pure rules are separate and tested.** App ordering, quick actions, widget snapping and every edit
  to the list of screens live in `domain/` and `PageOps`, with unit tests.
- **Every setting is written in one transaction,** so quick taps never overwrite each other.
- **Share codes are compressed JSON.** Text codes use Base64 (`GL1:`). QR codes use Base45 (`GL2:`)
  so they fit QR's compact alphanumeric mode, and big ones split into live frames
  (`GLP2:<id>:<n>/<count>:…`) read in any order. Tests draw each frame as a real QR code and read
  it back.
- **Edge effects are drawn by home itself.** Nothing floats over other apps, and no overlay
  permission is needed.

<details>
<summary><b>Project layout</b></summary>

```text
app/src/main/java/app/gridlauncher/
├── MainActivity.kt          Home activity, widget binding, Home button, app lock
├── LauncherApplication.kt   App-wide repositories and the widget host
├── domain/                  Ordering, quick actions, widget geometry (unit tested)
├── data/                    Apps, icons, settings, screens, backups, saved setups,
│                            share codes, Base45, QR frames (unit tested)
├── widgets/                 Widget host and controller
├── notifications/           Notification counts, media play state, audio levels
├── gestures/                Accessibility service for gestures
├── timer/                   Quick timers: alarms and notifications
├── monitor/                 Live phone statistics and alerts
└── ui/
    ├── home/                Screens, grid, widgets, board, edge effects, screen options
    ├── share/               Styled QR codes, the share sheet, the live scanner
    ├── settings/            Settings, saved setups, app groups, tutorials, pickers
    ├── monitor/             The phone monitor dashboard
    ├── components/          Shared controls
    └── theme/               Colors, fonts, icons

docs/designs/                Mockup images, their HTML source, and the render script
```

</details>

### Designs

The mockups live in [`docs/designs/`](docs/designs/), each with an HTML source in
[`docs/designs/source/`](docs/designs/source/). To render them again after editing:

```sh
npm install playwright && npx playwright install chromium   # once
node docs/designs/render.js docs/designs/source docs/designs
```

The design system is small on purpose:

| Token | Value | Used for |
| --- | --- | --- |
| Ground | `#0F1012` | Background |
| Surface | `#17191C` | Widgets, cards, sheets |
| Tile | `#1F2226` | App icon tiles |
| Line | `#2A2E33` | Outlines and dividers |
| Text | `#ECEDEF` | Labels and headings |
| Muted | `#9CA1A9` | Hints and captions |
| Accent | `#9DB8FF` | The one color on screen (you can pick another) |

Type is **Geist** by default. Pick from 26 curated families or type the name of any Google Font.

---

## Good to know

- **Quick actions need the default home role.** That's an Android rule, not a setting.
- **Fonts need Google Play services.** Without it, the system font is used.
- **The monitor only runs while you look at it.** It stops the moment you swipe away.
- **Quick timers run in the launcher.** They ring with your alarm sound and show a countdown
  notification with Cancel.
- **App lock covers opening apps from this launcher.** Apps opened another way, such as from a
  notification, aren't locked.
- **Backups and saved setups keep other apps' widgets** on the same phone. Share codes carry them as
  placeholders, because a widget can only be set up on the phone that shows it.
- **Photos stay on the phone.** They're copied into the launcher, kept by saved setups, and left out
  of share codes.
- **Landscape and tablets work.** Widget layouts keep their proportions, and the grid adds columns.
- **Some numbers depend on the phone.** Rows a phone can't report are hidden rather than shown as zero.
- **Some effects depend on the phone.** Wallpaper blur appears only where Android supports it, and a
  few phones don't offer the audio visualizer; the music effect then keeps a steady beat.

---

## Contributing

Issues and pull requests are welcome. Before opening one, please run:

```sh
./gradlew :app:testDebugUnitTest :app:lintDebug
```

Keep changes in the spirit of the project: **fewer things on screen, each one doing more.**
