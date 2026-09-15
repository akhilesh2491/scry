# Changelog

Notable changes per release. Versions follow [semantic versioning](https://semver.org),
with the caveat stated in the README: the API is expected to move before 1.0.

## Unreleased

### Added

- **Launchers — Scry is now visible without being told about.** Shake was the only way in on
  Android, and it is undiscoverable: it needs the emulator's extended controls, it is unreliable
  through a case, and it does not exist on iOS or desktop. After `Scry.install(...)` you now get a
  draggable **bubble** over the app (Android and iOS), an ongoing **notification** (Android) or
  **tray icon** (desktop), and an app-icon **shortcut** (Android and iOS) — no extra code.
- `launchers { }` in the install DSL, with `bubble`, `notification`, `appShortcut`, `launcherIcon`,
  `shake` and `bubbleCorner`; matching `.bubble(…)` / `.shake(…)` setters on the Java builder.
  Everything is on except `launcherIcon` and `shake`.
- The bubble is drawn into the app's own window — no `SYSTEM_ALERT_WINDOW`. It snaps to the nearer
  edge, stays clear of the system bars, remembers its position across launches, hides on long-press
  and never draws over the Scry UI itself. On iOS it is its own button-sized `UIWindow`, so touches
  anywhere else still reach the app.
- `ScryBubble`, `ScryNotification`, `ScryAppShortcut` and `ScryLauncherIcon` (Android),
  `ScryBubble` and `ScryQuickAction` (iOS), `ScryTray` (desktop), for hosts that want to drive a
  surface directly.
- An optional launcher-drawer icon, off by default: it requires an `exported="true"` component, so
  it enables a small trampoline that re-checks the build is debuggable, and the activity that shows
  request bodies, preferences and database rows stays unexported.
- **`scry-analytics` — analytics event verification.** Every event the app reports through
  `ScryAnalytics.track(name, params)` is shown with its parameters and the screen it fired
  on. Declare what a screen should emit (`expect("Checkout") { event("begin_checkout") { … } }`)
  and Scry turns the feed into a per-screen pass/fail: missing events, missing or misspelled
  parameters, a number sent as a string, a value outside its allowed set, a duplicate or a
  forbidden event. `onIssue { }` hands each failure to the app, so a QA run or an instrumented
  test can fail on it.
- A verdict belongs to one *visit* to a screen, judged when you navigate away or `settleMillis`
  after you arrive — an event that has not fired yet is not yet missing. Screens with no
  expectation are marked `NO SPEC`, never `PASS`.
- `ScreenChangedEvent` on the core event bus. `PerfPlugin` publishes it from the activity,
  fragment and composable tracking it already does, and `scry-analytics` consumes it, so events
  are attributed to screens automatically when both are installed and neither module depends on
  the other.
- `Redactor.shouldRedactBodyKey` / `redactBodyValue`, for captured data that arrives as a map
  rather than as a JSON document. Analytics parameters are redacted on capture with the same
  rules as request bodies.
- `ScryModule.ANALYTICS` in the Gradle plugin.

### Changed

- `ScryDesktopWindow` is now an extension on `ApplicationScope`, which is where a Compose tray icon
  has to be declared. Existing call sites are already inside `application { }`, so they keep
  compiling unchanged.

## 0.3.0

### Fixed

- **Sharing did nothing on iOS.** `shareScryFile` presented the share sheet from the key
  window's `rootViewController`, but Scry's own UI is already presented modally on that
  same root — UIKit silently refuses a second presentation on a controller that is already
  presenting. Every export on every screen was a no-op. It now walks the
  `presentedViewController` chain and presents from the topmost controller.
- **Sharing threw on Android.** The `FileProvider` was declared without its
  `android.support.FILE_PROVIDER_PATHS` meta-data. `FileProvider.getUriForFile` reads the
  path strategy from that manifest entry and never instantiates the provider subclass, so
  passing the paths resource to `ScryFileProvider`'s constructor did not reach it — every
  export failed with `Missing android.support.FILE_PROVIDER_PATHS`.
- **Sharing frequently did nothing on desktop.** `Desktop.open` throws for file types with
  no registered handler, which is the common case for `.har` and `.json`. It now falls back
  to revealing the file, then to opening its folder.
- **Every share failure was silent.** Screens called `shareScryFile` and discarded the
  `Boolean` it returns, while each platform swallowed its exception — a refused share was
  indistinguishable from an unwired button. Failures are now reported in the UI and logged
  under the `Scry` tag.
- iOS: the share sheet no longer crashes on iPad (no popover anchor was set) and no longer
  depends on the deprecated `UIApplication.keyWindow`, which returns nil in scene-based apps.

### Added

- **Clear/delete on individual plugin screens.** Previously only the plugin *list* offered a
  clear, and it wiped everything. Network, Logs, Crashes and Performance each clear their own
  captured data; Preferences clears a single store; Database deletes a row or a table's rows.
  All behind a confirmation naming what is about to go.
- **Export from every screen.** Crash reports (all records), preference stores (JSON),
  database tables and query results (CSV), and Performance as CSV as well as JSON.
- `scry-ui` gains shared building blocks, usable by third-party plugins: `ScryScreenBar`,
  `ScryShareAction`, `ScryDestructiveAction`, `ScryCard`, `ScryStat`, `ScryStatGrid`, and
  `LocalScryFeedback` for reporting an action's outcome.
- Android exports now declare a MIME type derived from the file extension, so a JSON viewer
  or spreadsheet app appears in the share sheet instead of only text editors.

### Changed

- **The Performance screen was rebuilt.** Its single dense scroll is now sub-sections
  (Overview / Startup / Screens / Frames / Spans, omitting empty ones), each measurement in a
  card. Statistics put the label above the value instead of trailing it inline, and wrap
  rather than crowding six onto one row. The startup waterfall has a phase legend, budget bars
  state the value and the limit, and the frame sparkline is taller.
- Building an export payload moved off the main thread. Only handing it to the platform stays
  on the main thread, as UIKit requires.

### Compatibility

No published API was removed or changed; `shareScryFile` keeps its signature, and `scry-no-op`
still mirrors it. Apps upgrading from 0.2.0 need no source changes.

## 0.2.0

`scry-perf`: on-device startup, screen-load and frame timing with budgets, session history and
JSON/CSV export.

## 0.1.0

First release: network capture (Ktor + OkHttp), preferences, database, crashes and ANRs, logs,
the Compose UI shell, the no-op release artifact with its parity gate, and the Gradle plugin.
