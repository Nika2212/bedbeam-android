# bedbeam-android

**BedBeam**: a lightweight, remote-first web browser for Android TV projectors (built for a Wanbo Cube 2 Pro: Android TV 11, 1 GB RAM, 720p, 32-bit ARM).

## Build & install

Requirements: JDK 17 (`%USERPROFILE%\.jdks\jdk-17*`), Android SDK (`%LOCALAPPDATA%\Android\Sdk`, platform 35 + build-tools 35.0.0), adb.

```powershell
.\scripts\deploy.ps1            # release build → install on 192.168.1.100:5555 → launch
.\scripts\deploy.ps1 -Debug     # debug build (installs side by side as com.bedbeam.browser.debug)
```

## Signing

Release builds are signed with `keystore/bedbeam-release.jks`, configured in `keystore.properties`.
Both are git-ignored. **Back them up**: without them, updates can't be installed over the existing app.
