# BedBeam UI guide

How the interface is put together, and where to change things when redesigning it.
Design reference: the **Projector Browser Design** canvas (https://claude.ai/artifact/RN5WqphqTLay4KtLo9AyHq).

## Principles

- **Built for a remote**: everything is reachable with the D-pad, OK, hold OK and Back. No touch, no mouse.
- **No UI on a web page**: the page is full screen; menus appear only on hold OK.
- **Light**: plain Android views from XML layouts (no AppCompat, no Compose), so a 1 GB device opens screens instantly.
  Avoid heavy libraries, shadows/elevation (slow on the Mali-G31 GPU) and big bitmaps.

## How a screen is built

Every screen is **markup + a thin Kotlin class**:

- `res/layout/*.xml`: what it looks like: structure, sizes, spacing, colours, text styles, focus behaviour.
- `ui/*.kt`: inflates the layout and binds data and actions (texts, click handlers). No styling in Kotlin.

Focus looks are declared in XML, not code:

| Look | Where |
|---|---|
| Background on focus (light fill, outlines) | `res/drawable/bg_*.xml` (selectors on `state_focused`; `state_selected` for the cross menu; `state_activated` for a tile being moved) |
| Text/icon colour on focus | `res/color/on_focus_*.xml`, `on_select_text.xml`; children use `android:duplicateParentState="true"` to follow their row |
| Grow on focus | `res/animator/focus_grow*.xml`, `select_grow.xml`, set with `android:stateListAnimator` |

## Where to change what

| To change… | Edit |
|---|---|
| **Colours** | `res/values/colors.xml`: single source (layouts use `@color/…`; code-drawn parts read it through `ui/Ui.kt` → `Palette`). |
| **Shared sizes, radii, text sizes** | `res/values/dimens.xml`. Units: **dp = design px × 0.75** (the TV screen is 960×540 dp = the 1280×720 canvas). |
| **Text styles** | `res/values/styles.xml` (`BB.Text`, `.Medium`, `.Bold`, `.Muted`, `.ScreenTitle`, `.Section`, `.KeyChip`…). |
| **One screen's layout** | its `res/layout` file (table below). |
| **Texts** | `res/values/strings.xml` (English), `res/values-ru/strings.xml` (Russian). |
| **A new language** | copy `values/strings.xml` to `values-<code>/strings.xml`, translate, add `"<code>" to R.string.lang_<code>` in `Settings.LANGUAGES`. |
| **Icons** | `res/drawable/ic_*.xml` (24×24 white vectors, tinted by the layout). |
| **Pointer** | drawn in code: `web/Pointer.kt` → `PointerView` (`RADIUS`, `OUTLINE`, colours from `Palette`). |
| **Link-mode ring** | `assets/page_bridge.js` → the `.__bb_focus` CSS rule. |
| **Logo / banner** | `branding/` masters → `res/mipmap-*`, `res/drawable-xxxhdpi/banner.png` (see `branding/README.md`). |

## Screens

| Screen | Layout(s) | Kotlin | Design artboard |
|---|---|---|---|
| Start screen | `screen_start`, `item_bookmark_tile`, `item_add_tile`, `item_key_hint` | `ui/StartScreen.kt` | Start screen |
| Address entry | `screen_address`, `item_suggestion` | `ui/AddressScreen.kt` | Typing an address |
| Bookmark actions | `sheet_bookmark_actions`, `item_sheet_action`, `item_sheet_action_danger` | `ui/BookmarkActions.kt` | Bookmark actions |
| Add / rename bookmark | `sheet_edit`, `item_edit_field` | `ui/EditSheet.kt` | – |
| Settings | `screen_settings`, `item_setting_row` (rows defined in `MainActivity.openSettings`) | `ui/SettingsScreen.kt` | – |
| Cross menu | `overlay_cross_menu`, `item_cross_pill_inner/outer`, `item_key_hint_dim` (actions in `MainActivity.openCrossMenu`) | `ui/CrossMenu.kt` | Cross menu |
| Full-screen video feedback | `overlay_video_osd` | `ui/VideoOsd.kt` | Full-screen video |

`MainActivity` owns the layering (page → start screen → full-screen video → one overlay) and key routing;
screens only show views and report actions through callbacks, so each can be redesigned on its own.
Keep the view **ids** a Kotlin class looks up (`R.id.…`) when you restyle a layout.

## Adding a setting

1. Add the value to `data/Settings.kt` (stored in SharedPreferences).
2. Add its label/hint/value strings to both `strings.xml` files.
3. Add a `SettingsScreen.Row(...)` in `MainActivity.openSettings`: `value` returns the text shown on the right,
   `onOk` changes it (cycle through choices) or runs an action. The row's look is `item_setting_row.xml`.

## Testing a UI change on the projector

```powershell
.\scripts\deploy.ps1        # build, install, launch
```
Screenshot: `adb -s 192.168.1.100:5555 exec-out screencap -p > shot.png`. Switch the language in Settings to check both.
