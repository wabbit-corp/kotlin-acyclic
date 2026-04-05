# kotlin-acyclic-plugin

`kotlin-acyclic-plugin` is the Kotlin compiler plugin that enforces the `one.wabbit.acyclic` rules.

It is built against the Kotlin version selected by `-PkotlinVersion` in this repository, defaulting to `defaultKotlinVersion` from `gradle.properties`, and runs as a K2/FIR plugin. Published compiler-plugin artifacts use the version format `<baseVersion>-kotlin-<kotlinVersion>`, and the repository publish workflows fan out across `supportedKotlinVersions`. In normal use, consumers should apply it through `kotlin-acyclic-gradle-plugin` rather than wiring `-Xplugin` and raw `-P plugin:...` flags by hand.

## Repository Role

The acyclic project family is split into three parts:

- `kotlin-acyclic`: annotations used in source code
- `kotlin-acyclic-plugin`: compiler enforcement
- `kotlin-acyclic-gradle-plugin`: Gradle integration

This repository is the enforcement engine.

## What It Checks

The plugin currently models three related rule families:

- compilation-unit acyclicity
- declaration acyclicity
- declaration order

Compilation-unit acyclicity is about `.kt` files forming a DAG.

Declaration acyclicity is about declarations within a file forming a DAG.

Declaration order is a stricter rule layered on top of declaration acyclicity. It requires the declaration DAG to align with either top-down or bottom-up source order.

## Architecture

The main flow is:

1. `AcyclicCommandLineProcessor` parses compiler options.
2. `AcyclicCompilerPluginRegistrar` registers a FIR checker extension.
3. `AcyclicFileAnalysis` analyzes each FIR file using resolved references and resolved types.
4. `AcyclicDependencyGraph` evaluates file-level SCCs.
5. `AcyclicDeclarationGraph` evaluates declaration SCCs and source-order violations.
6. `AcyclicDiagnostics` reports the resulting errors.

The important design choice is that the analysis is semantic rather than text-based. Dependencies are derived from resolved FIR symbols instead of source regexes or import-string heuristics.

## Scoping Rules

The plugin intentionally distinguishes scoping from dependency.

Examples that should remain legal:

- `sealed interface Foo { class Boo : Foo }`
- `class Foo { fun self(): Foo }`

The goal is to ban recursive definition structure, not to ban lexical containment or nominal self-reference.

## Configuration

The compiler plugin accepts three options:

- `compilationUnits=disabled|opt-in|enabled`
- `declarations=disabled|opt-in|enabled`
- `declarationOrder=none|top-down|bottom-up`

Raw CLI form:

```text
-P plugin:one.wabbit.acyclic:compilationUnits=disabled|opt-in|enabled
-P plugin:one.wabbit.acyclic:declarations=disabled|opt-in|enabled
-P plugin:one.wabbit.acyclic:declarationOrder=none|top-down|bottom-up
```

Direct compiler invocation is supported, but the companion Gradle plugin is the intended surface.

## Direct Usage

If you are debugging the compiler plugin itself, the direct form looks like:

```text
-Xplugin=/path/to/kotlin-acyclic-plugin.jar
-P plugin:one.wabbit.acyclic:compilationUnits=enabled
-P plugin:one.wabbit.acyclic:declarations=enabled
-P plugin:one.wabbit.acyclic:declarationOrder=top-down
```

If you are resolving the jar from Maven coordinates directly, use the Kotlin-matched variant such as `one.wabbit:kotlin-acyclic-plugin:0.0.1-kotlin-2.3.10`.

The annotation library still needs to be present on the compilation classpath if source uses `one.wabbit.acyclic.*`.

## Source Layout

- `src/main/kotlin/one/wabbit/acyclic/AcyclicCommandLineProcessor.kt`
  Parses CLI options into `CompilerConfiguration`.
- `src/main/kotlin/one/wabbit/acyclic/AcyclicCompilerPluginRegistrar.kt`
  Registers the FIR extension.
- `src/main/kotlin/one/wabbit/acyclic/AcyclicFileAnalysis.kt`
  Builds per-file analysis state from resolved FIR structures.
- `src/main/kotlin/one/wabbit/acyclic/AcyclicDependencyGraph.kt`
  Detects compilation-unit cycles.
- `src/main/kotlin/one/wabbit/acyclic/AcyclicDeclarationGraph.kt`
  Detects declaration cycles and order violations.
- `src/main/kotlin/one/wabbit/acyclic/AcyclicControls.kt`
  Reads annotations and source-level overrides.
- `src/main/kotlin/one/wabbit/acyclic/AcyclicDiagnostics.kt`
  Defines FIR diagnostics.

## Tests

The test suite is split into two layers:

- graph-level tests
  These validate SCC detection and order-violation rules in isolation.
- compiler integration tests
  These compile real snippets through the plugin and assert on diagnostics.

The current integration suite covers:

- scoping exemptions
- self recursion
- mutual recursion
- file cycles
- opt-in behavior
- order-direction behavior
- file-level order overrides
- explicit recursion escape hatches
- local declarations staying out of declaration analysis

Declaration analysis currently covers top-level declarations and class members. Local declarations
inside function bodies, accessors, and initializer blocks are intentionally ignored.

Run:

```bash
./gradlew test
```

from the repository root.

## Related Docs

- `GOAL.md` for the project-level design targets
- `WALKTHROUGH.md` for a manual review order and architecture diagrams
- `../kotlin-acyclic/README.md` for annotations
- `../kotlin-acyclic-gradle-plugin/README.md` for Gradle integration
