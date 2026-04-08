# Changelog

All notable changes to this project will be documented in this file.

The format is intentionally simple and release-oriented for now.

## 0.0.1 - 2026-04-07

Initial public release.

Included in this release:

- `kotlin-acyclic`: Kotlin Multiplatform annotation library for file-level and declaration-level acyclicity controls
- `kotlin-acyclic-gradle-plugin`: typed Gradle plugin for `one.wabbit.acyclic`
- `kotlin-acyclic-plugin`: Kotlin-line-specific K2/FIR compiler plugin artifacts
- `kotlin-acyclic-ij-plugin`: IntelliJ IDEA helper plugin for external compiler-plugin loading

Highlights:

- compilation-unit cycle checking from resolved semantic dependencies
- same-file declaration-cycle checking for tracked declarations
- declaration-order enforcement with `TOP_DOWN` and `BOTTOM_UP`
- explicit source-level escape hatches such as `@AllowSelfRecursion`, `@AllowMutualRecursion`, and `@AllowCompilationUnitCycles`
- Kotlin publish matrix driven by `supportedKotlinVersions`
