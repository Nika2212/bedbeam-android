# bedbeam-android

**BedBeam**: a lightweight, remote-first web browser for Android TV projectors.
Built for a Wanbo Cube 2 Pro (Android TV 11, 1 GB RAM, 1280×720, 32-bit ARM, a remote with only D-pad / OK / Back).

- **One tab, no on-page UI.** Pages are full screen; everything else is behind **hold OK**.
- **Pointer mode** (default): the D-pad moves a pointer that speeds up while held; OK clicks; pushing against an edge scrolls.
  Clicks, hovers and scrolls are real touch/mouse events, so they work inside embedded players from other sites.
- **Link mode**: arrows jump a focus ring between links and buttons; OK opens.
- **Cross menu** (hold OK): ◀ Back / Forward · ▶ Reload / Bookmark · ▲ Address / Start screen · ▼ Pointer↔Links / Ad-block for this site.
- **Video**: players' full screen with remote control: OK play/pause, ◀ ▶ seek 10 s (held: faster), ▲ ▼ show time,
  Back leaves. Hardware decoding (H.264, HEVC, VP9, AV1), DRM (Widevine), screen kept on while playing.
- **Built-in ad blocker**: AdGuard DNS, Base and Russian lists (~190k domains) as a 1.5 MB hash array, updated weekly;
  blocks ad requests, ad redirects, app-link hijacks and popunders; hides common ad boxes.
- **Start screen**: bookmarks (hold OK to rename / move / remove), address bar with bookmark, history and search suggestions.
- **Settings**: search engine (Google, Yandex, DuckDuckGo, Bing), pointer speed, auto full-screen video, desktop sites,
  ad-list update, clear history and site data.
- **Low memory**: no AppCompat/Compose (~400 KB APK); the page's renderer process is ended when you leave a page.

Engine: the device's **Android System WebView** (Chromium). It is not bundled, so it updates through the Play Store.

## Build & install

Requirements: JDK 17 (`%USERPROFILE%\.jdks\jdk-17*`), Android SDK (`%LOCALAPPDATA%\Android\Sdk`, platform 35 + build-tools 35.0.0), adb.

```powershell
.\scripts\deploy.ps1            # release build → install on 192.168.1.100:5555 → launch
.\scripts\deploy.ps1 -Debug     # debug build (installs side by side as com.bedbeam.browser.debug)
```

## Signing

Release builds are signed with `keystore/bedbeam-release.jks`, configured in `keystore.properties`.
Both are git-ignored. **Back them up**: without them, updates can't be installed over the existing app.

## Branding

Logo masters and how the app icons and TV banner were made from them: [`branding/`](branding/README.md).
