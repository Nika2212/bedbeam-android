# BedBeam UI guide

How the interface is put together, and where to change things when redesigning it.
Design reference: the **Projector Browser Design** canvas (https://claude.ai/artifact/RN5WqphqTLay4KtLo9AyHq).

## Principles

- **Built for a remote**: everything is reachable with the D-pad, OK, hold OK and Back. No touch, no mouse.
- **No UI on a web page**: the page is full screen; menus appear only on hold OK.
- **Low memory**: screens are plain Android views built in Kotlin, no AppCompat/Compose and no XML inflation,
  so a 1 GB device opens them instantly. Keep it that way: avoid heavy libraries and image-based UI.

## Where to change what

| To change… | Edit | Notes |
|---|---|---|
| **Colours** | `app/src/main/res/values/colors.xml` | Single source. Read at start-up by `ui/Ui.kt` → `Palette`. Never write colour literals in screens; add a token instead (both files). |
| **Texts** | `res/values/strings.xml` (English), `res/values-ru/strings.xml` (Russian) | Every visible text is a string resource. Brand names and key symbols are `translatable="false"`. |
| **A new language** | copy `values/strings.xml` to `values-<code>/strings.xml`, translate, add `"<code>" to R.string.lang_<code>` in `Settings.LANGUAGES` | The Settings › Language row picks it up automatically. |
| **Sizes, spacing, radii** | the screen's file (table below) | Written in **design pixels** of the 1280×720 canvas via `Dims.px()` / `Dims.f()`, so numbers match the design canvas 1:1 and scale on other screens. |
| **Text styles** | `textStyle(d, size, color, weight)` in `ui/Ui.kt` | Sizes in design px; weight 400–700. Font: system sans (Roboto). |
| **Focus look** | each screen's `style(has: Boolean)` + `focusScale()` in `ui/Ui.kt` | House style: focused = light background (`text`), dark text, slight grow. Avoid shadows/elevation (slow on the Mali-G31 GPU). |
| **Icons** | `res/drawable/ic_*.xml` | 24×24 vector line icons, white; tinted in code. |
| **Logo / banner** | `branding/` (masters) → `res/mipmap-*`, `res/drawable-xxxhdpi/banner.png` | See `branding/README.md`. |

## Screens

| Screen | File | Design artboard | Opens from |
|---|---|---|---|
| Start screen | `ui/StartScreen.kt` | Start screen | App launch, Back from first page, cross menu ▲▲ |
| Address entry | `ui/AddressScreen.kt` | Typing an address | ↑ / search pill, cross menu ▲ |
| Bookmark actions | `ui/BookmarkActions.kt` | Bookmark actions (hold OK) | Hold OK on a bookmark tile |
| Add / rename bookmark | `ui/EditSheet.kt` | – | Add tile, Rename |
| Settings | `ui/SettingsScreen.kt` (rows defined in `MainActivity.openSettings`) | – | Gear on the start screen |
| Cross menu | `ui/CrossMenu.kt` (actions defined in `MainActivity.openCrossMenu`) | Cross menu (hold OK) | Hold OK on a page / in video |
| Pointer | `web/Pointer.kt` (`PointerView`) | Web page: pointer mode | Default page mode |
| Link mode ring | `assets/page_bridge.js` (`.__bb_focus` style) | Web page: link mode | Cross menu ▼ |
| Video seek bubble + timeline | `ui/VideoOsd.kt` | Full-screen video | ◀ ▶ ▲ ▼ OK in full-screen video |

`MainActivity` owns the layering (page → start screen → full-screen video → one overlay) and key routing;
screens only build views and report actions through callbacks, so a screen can be redesigned in isolation.

## Adding a setting

1. Add the value to `data/Settings.kt` (stored in SharedPreferences).
2. Add its label/hint/value strings to both `strings.xml` files.
3. Add a `SettingsScreen.Row(...)` in `MainActivity.openSettings`: `value` returns the text shown on the
   right, `onOk` changes it (cycle through choices) or runs an action.

## Testing a UI change on the projector

```powershell
.\scripts\deploy.ps1        # build, install, launch
```
Screenshot: `adb -s 192.168.1.100:5555 exec-out screencap -p > shot.png`. Switch language in Settings to check both.
