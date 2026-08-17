# Changelog

Notable changes per release. Versions follow [semantic versioning](https://semver.org),
with the caveat stated in the README: the API is expected to move before 1.0.

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
