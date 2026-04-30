# OHM Time Filter

A JOSM plugin for [OpenHistoricalMap](https://www.openhistoricalmap.org/)
that highlights features extant at a chosen date and fades or hides the rest.

## What it does

Given a "filter date" and an optional ± window, the plugin classifies every
primitive in your data layer based on its `start_date` / `end_date` tags:

- **On this day** — fully visible and selectable.
- **Inside the window** — visible but visually faded; still selectable.
- **Filtered out** — hidden from view (geometry, label, icon, way-corner
  nodes); still in the dataset, selection is cleared.

A row of date-shift buttons (`<C` `<X` `<Y` `<M` `<D` … `D>` `M>` `Y>` `X>` `C>`)
lets you scrub backward and forward by century / decade / year / month / day,
re-applying the filter as you go. A separate "Filter to Selection" button
picks a focus date by averaging the selected primitives' dates and applies it.

The plugin composes with JOSM's built-in Filter dialog: the time filter only
ever *escalates* primitive visibility (an item already hidden by a JOSM tag
filter stays hidden), it never reveals.

## Requirements

- JOSM **r19439** or later.
- Java **17** or later.

## Install

Build the jar (see below) and copy `dist/ohm-time-filter.jar` into your JOSM
plugins directory:

- macOS: `~/Library/JOSM/plugins/`
- Linux: `~/.config/JOSM/plugins/` or `~/.local/share/JOSM/plugins/`
- Windows: `%APPDATA%\JOSM\plugins\`

Then enable the plugin in **Preferences → Plugins**.

## Build

The plugin compiles directly against a JOSM core jar — no Ivy / Maven needed.

```sh
# 1. Check out JOSM trunk into core/ (gitignored).
svn checkout https://josm.openstreetmap.de/svn/trunk core

# 2. Build the jar.
ant dist
# → dist/ohm-time-filter.jar

# 3. (optional) Run unit tests.
ant test

# 4. (optional) Build, copy to your JOSM plugins dir, and launch JOSM.
ant runjosm
```

The build pins to the revision of `core/` you have checked out; bumping
`core/` requires bumping `plugin.main.version` in `build.xml`.

## Project layout

```
src/                    Plugin source (org.openhistoricalmap.josm.plugins.timefilter)
test/unit/              JUnit 4 unit tests (pure-data; no JOSM static init)
resources/images/       Plugin icons (svg)
build.xml               Self-contained Ant build
core/                   JOSM trunk svn checkout (gitignored)
lib/                    Test deps — junit / hamcrest (gitignored)
```

## Contributing

Pull requests and issues welcome. Two one-time setup steps before your first
commit:

```sh
# Use the project's commit-message template (preloads the Co-Authored-By
# trailer when you run `git commit` interactively).
git config commit.template .gitmessage

# Activate the prepare-commit-msg hook so the trailer is appended even on
# `git commit -m "…"` invocations.
git config core.hooksPath .githooks
```

## AI-assisted development

This project was developed with substantial assistance from Anthropic's
Claude (web interface and Claude Code). See
[ATTRIBUTION.md](ATTRIBUTION.md) for the full disclosure and rationale.
Commits that involved AI assistance carry a `Co-Authored-By: Claude`
trailer; the hook above keeps that consistent across `git commit` and
`git commit -m`.

## License

GPL — see [LICENSE](LICENSE).
