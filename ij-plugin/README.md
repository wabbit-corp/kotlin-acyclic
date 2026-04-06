# kotlin-acyclic-ij-plugin

IntelliJ IDEA support for `one.wabbit:kotlin-acyclic-plugin`.

## What It Does

This plugin does not replace Kotlin analysis inside the IDE. Instead, it bridges into the Kotlin IDE plugin's existing external compiler plugin loading path:

- it scans imported Kotlin compiler arguments for `kotlin-acyclic-plugin`
- it also scans Gradle build files and version catalogs for `one.wabbit.acyclic`
- if found, it temporarily enables all non-bundled K2 compiler plugins for the opened project
- it re-checks support after project trust changes and after Gradle/import-driven project model updates
- it exposes a manual refresh action under `Tools | Refresh Acyclic IDE Support`

That gives the Kotlin IDE plugin a chance to load the external compiler plugin registrar from the compiler plugin classpath already configured by the build.

Important detail: IntelliJ only exposes a coarse registry switch here. Enabling support for `kotlin-acyclic` enables all non-bundled K2 compiler plugins for the current trusted project session, not only this one.

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

## Build

```bash
./gradlew :ij-plugin:buildPlugin
```

## Usage

1. Build or install the IntelliJ plugin.
2. Open a project that already applies `kotlin-acyclic-plugin`.
   Applying `one.wabbit.acyclic` through Gradle is enough.
3. Trust the project when IntelliJ asks.
4. If needed, run `Tools | Refresh Acyclic IDE Support`.

When the plugin detects the compiler plugin classpath or Gradle plugin declaration, it enables external K2 compiler plugins for that project session and notifies you.

## Related Docs

- [`../README.md`](../README.md)
- [`../docs/user-guide.md`](../docs/user-guide.md)
- [`../docs/development.md`](../docs/development.md)
- [`../docs/ARCHITECTURE.md`](../docs/ARCHITECTURE.md)
- [`../gradle-plugin/README.md`](../gradle-plugin/README.md)
- [`../compiler-plugin/README.md`](../compiler-plugin/README.md)
