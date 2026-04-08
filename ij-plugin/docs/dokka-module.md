# Module kotlin-acyclic-ij-plugin

IntelliJ IDEA helper plugin for `one.wabbit.acyclic` projects.

This module does not implement separate IDE-native acyclicity inspections. Its current job is to
help the bundled Kotlin IDE plugin load the external `kotlin-acyclic` compiler plugin registrar for
trusted imported projects that already apply `one.wabbit.acyclic` in Gradle.

## Current Scope

- detect whether imported compiler arguments reference the `kotlin-acyclic` compiler plugin
- surface IDE-side enablement controls for trusted projects
- coordinate refresh and rescan behavior when project configuration changes

## Important Boundary

The IntelliJ platform currently exposes only coarse-grained support for external K2 compiler
plugins in this integration path. Enabling support for `kotlin-acyclic` allows the IDE session to
load non-bundled K2 compiler plugins for the current trusted project; it is not a dedicated
per-plugin sandbox.

## Relationship To The Other Modules

Most end users interact with:

- `one.wabbit:kotlin-acyclic` for source annotations
- `one.wabbit:kotlin-acyclic-gradle-plugin` for build integration
- Kotlin-line-specific `one.wabbit:kotlin-acyclic-plugin` artifacts resolved by the Gradle plugin

This IDEA plugin is a companion integration layer for local IDE behavior, not a replacement for the
compiler plugin itself.
