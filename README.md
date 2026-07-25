# Staff Tracker

A lightweight, client side counter for staff members. Press a key each time you help a player. That is the whole mod.

By Cosmic Player. All Rights Reserved. See LICENSE.

## Requirements

- Minecraft 1.21.11
- Fabric Loader 0.19.3+
- Fabric API

## Usage

- Press the count keybind (default `H`) each time you help a player.
- The counter shows on a small HUD panel. It flashes on each count.
- Counts are stored per calendar day and roll over at midnight in the client's time zone.
- Open the pause menu and click **Staff Tracker** in the top right to change settings.

## The window

Everything lives in one window with a sidebar on the left: Settings, History, and Adjust HUD.

- **Settings**: HUD on or off, label on or off, counter view (day, week, or month), and the count keybind.
  The keybind supports mouse buttons and modifier combos like Ctrl Shift H, and is stored in the mod's own config.
- **History**: totals by day, week, or month in a bordered list. Weeks run Sunday to Saturday.
  Click a week or month to see its days. Click the back chevron to return.
- **Adjust HUD**: drag the counter anywhere on screen. Scroll to resize it in one percent steps.
- **Undo count**: removes one count from today, for accidental presses.

## Data

Everything is stored locally, nothing is sent anywhere.

- `config/stafftracker/settings.json` holds the UI settings.
- `config/stafftracker/data.json` holds the per day history.

## Building

```
./gradlew build
```

The jar lands in `build/libs/`. Java 21 is required and is resolved automatically by the Gradle toolchain.

## Project layout

- `StaffTrackerClient` wires everything up on client init.
- `StaffTrackerConfig` and `HelpData` handle settings and counts, both saved as JSON.
- `hud/CounterHud` draws the on screen panel.
- `ui/` holds the custom widgets and screens. `Theme` has the shared colors and drawing helpers.
- `mixin/GameMenuScreenMixin` adds the pause menu button.

The UI uses the bundled Inter font (SIL OFL 1.1, license included in the jar) so text is smooth instead of pixelated.
