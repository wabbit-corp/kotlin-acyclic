# kotlin-acyclic-ij-plugin

IntelliJ IDEA support for `one.wabbit:kotlin-acyclic-plugin`.

This plugin exists so projects that already use `kotlin-acyclic` in Gradle can get IDE-side loading of the external compiler plugin without manual registry spelunking. For the broader rule model, start with the [root README](../README.md), [user guide](../docs/user-guide.md), and [API reference](../docs/api-reference.md).

## Status

This is pre-1.0, phase-1 IDE support. It focuses on compiler-plugin activation rather than custom inspections or quick fixes.

## What It Does

This plugin does not replace Kotlin analysis inside the IDE. Instead, it bridges into the Kotlin IDE plugin's existing external compiler plugin loading path:

- it scans imported Kotlin compiler arguments for `kotlin-acyclic-plugin`
- it also scans Gradle build files and version catalogs for `one.wabbit.acyclic`
- if found, it temporarily enables all non-bundled K2 compiler plugins for the opened project
- it re-checks support after project trust changes and after Gradle/import-driven project model updates
- it exposes a manual refresh action under `Tools | Refresh Acyclic IDE Support`

That gives the Kotlin IDE plugin a chance to load the external compiler plugin registrar from the compiler plugin classpath already configured by the build.

IntelliJ only exposes a coarse registry switch here, so enabling support for `kotlin-acyclic` enables all non-bundled K2 compiler plugins for the current trusted project session, not only this one.

## Current Scope

This is phase 1 IDE support:

- detect acyclic plugin usage
- enable external K2 compiler plugins for the current trusted project session
- re-activate automatically when trust or imported project model state changes
- provide a refresh action and notifications

It does not yet add custom IntelliJ-native inspections, quick fixes, or graph visualizations on its own.

## What It Requires

- IntelliJ IDEA with the bundled Kotlin plugin
- a trusted project
- a build that already applies `one.wabbit.acyclic` or otherwise configures `kotlin-acyclic-plugin`

This plugin does not synthesize Gradle or Maven compiler plugin configuration by itself.

## Installation

```bash
./gradlew :kotlin-acyclic-ij-plugin:buildPlugin
```

The build writes an installable ZIP under [`build/distributions`](./build/distributions). In IntelliJ IDEA, use `Settings | Plugins | Install Plugin from Disk...` and select that ZIP.

## Usage

1. Build or install the IntelliJ plugin.
2. Open a project that already applies `kotlin-acyclic-plugin`.
   Applying `one.wabbit.acyclic` through Gradle is enough.
3. Trust the project when IntelliJ asks.
4. If needed, run `Tools | Refresh Acyclic IDE Support`.

When the plugin detects the compiler plugin classpath or Gradle plugin declaration, it enables external K2 compiler plugins for that project session and notifies you.

The easiest verification path is: open a trusted project that already applies `one.wabbit.acyclic`, wait for the notification, then confirm Kotlin analysis reflects the compiler plugin without running the manual refresh action.

Release notes live in [`../CHANGELOG.md`](../CHANGELOG.md). If activation fails, start with [`../docs/troubleshooting.md`](../docs/troubleshooting.md) and the contribution/support guidance in the [root README](../README.md).
