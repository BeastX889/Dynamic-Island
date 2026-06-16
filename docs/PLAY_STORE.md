# Play Store submission notes

Practical checklist and justification text for publishing Dynamic Island. The app uses two
permissions Google flags as **sensitive/high-risk**, so the listing must address them head-on.

## Sensitive permissions & required declarations

### 1. Display over other apps (`SYSTEM_ALERT_WINDOW`)
- **Core functionality:** the entire product *is* a floating overlay; without it the app cannot
  function.
- In the Play Console **App content → Permissions declaration**, state that the overlay is the core
  feature and is user-initiated (the user explicitly grants it and taps "Start island").

### 2. Notification access (`BIND_NOTIFICATION_LISTENER_SERVICE`)
- **Why:** read active media sessions (now playing) and ongoing progress notifications to mirror
  them on the island. This is the same data Android's own media controls use.
- This requires the **Notification access / sensitive permission declaration** and a link to the
  privacy policy (`PRIVACY.md`, hosted at a public URL).
- We deliberately avoid `MEDIA_CONTENT_CONTROL` (system-only) by using the notification-listener
  component to read sessions.

### 3. Foreground service — `specialUse`
- Declared in the manifest with `PROPERTY_SPECIAL_USE_FGS_SUBTYPE`.
- In the Console you must **justify the `specialUse` type** (other FGS types don't fit a persistent
  overlay). Justification: _"Persistent floating overlay that mirrors media, calls, timers and live
  activities on top of other apps; requires a long-lived foreground service tied to a user-visible
  ongoing notification."_

### Permissions we intentionally DON'T request
- No `READ_CALL_LOG`, `READ_CONTACTS`, `READ_PHONE_NUMBERS`, SMS, or location — keeping out of the
  most heavily-restricted permission groups simplifies review.

## Data safety form
- Data collected: **none**. Data shared: **none**.
- No analytics/ads SDKs. Settings stored locally only.

## Pre-launch checklist
- [ ] Release signing config (upload key) + `release` build with R8 (already enabled).
- [ ] App icon replacing the placeholder vector.
- [ ] Privacy policy hosted at a public URL; linked in the listing.
- [ ] Sensitive-permission declarations + demo video showing the overlay in use (Google often asks
      for a short video for overlay/notification-listener apps).
- [ ] Store listing assets: screenshots, feature graphic, short/long description.
- [ ] Target API level meets the current Play requirement (project targets SDK 35).

## Note on sideloaded test builds
Debug APKs that request notification access are blocked by **Google Play Protect** on install
(generic "sensitive data" warning). This is expected for sideloading and does not occur for builds
installed **through the Play Store**.
