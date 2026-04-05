# Changelog

## Unreleased

- Added the initial `kotlin-acyclic-ij-plugin` IntelliJ plugin module.
- Added project startup and manual refresh support that enable external K2 compiler plugins when `kotlin-acyclic-plugin` is detected in imported Kotlin compiler arguments.
- Added detection of `one.wabbit.acyclic` in Gradle build files and version catalogs so Gradle-loaded projects can enable IDE support earlier.
- Added tests for compiler plugin classpath and Gradle plugin detection.

