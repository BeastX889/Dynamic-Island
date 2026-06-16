# Privacy Policy — Dynamic Island

_Last updated: 2026-06-16_

Dynamic Island ("the app") is designed to display information that is already on your device on a
small floating overlay. **The app does not collect, transmit, or share any personal data.**

## What the app accesses, and why

| Capability | Why it's used | Leaves your device? |
|---|---|---|
| **Display over other apps** (`SYSTEM_ALERT_WINDOW`) | Draw the floating island on top of other apps. | No |
| **Notification access** | Read currently-playing media (title/artist/artwork) and ongoing progress notifications (e.g. deliveries) to mirror them on the island. | No |
| **Phone state** (`READ_PHONE_STATE`) | Show an indicator when a call is ringing or active. The app does **not** read your call log, contacts, or phone numbers. | No |
| **Post notifications** | Show the persistent "island is active" notification required for the background service. | No |

## Data handling

- All processing happens **locally on the device**. Media metadata, notification content, and call
  state are read in memory to render the overlay and are **not stored, logged, or transmitted**.
- The app contains **no analytics, advertising, or third-party tracking SDKs**.
- The only data persisted on the device is your own **app settings** (island position, size, and
  which features are enabled), stored locally via Android DataStore.

## Your control

- Every permission is optional and individually revocable from Android Settings.
- Disabling a permission simply disables the corresponding feature.

## Contact

Questions about this policy: ayush.thakur.at74@gmail.com
