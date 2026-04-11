# kotlin-acyclic Architecture

This document explains how the `kotlin-acyclic` repository is structured, how configuration flows through it, and what semantic boundaries the current implementation enforces.

## Repository Topology

The repository is split into four main modules:

| Module | Purpose |
| --- | --- |
| `library` | source-retained annotations and enums used from Kotlin code |
| `gradle-plugin` | typed Gradle integration that wires the compiler plugin into Kotlin compilations |
| `compiler-plugin` | K2/FIR implementation that builds dependency graphs and reports diagnostics |
| `ij-plugin` | IntelliJ helper plugin that enables external K2 compiler-plugin loading for trusted projects |

In normal consumer builds:

1. application code depends on `one.wabbit:kotlin-acyclic`
2. the build applies `one.wabbit.acyclic`
3. the Gradle plugin resolves the Kotlin-matched compiler plugin artifact
4. the compiler plugin enforces the rules during Kotlin compilation

## Module Responsibilities

### `library`

Owns the source-level control surface:

- `@Acyclic`
- `@AllowCompilationUnitCycles`
- `@AllowSelfRecursion`
- `@AllowMutualRecursion`
- `AcyclicOrder`

This module contains no compiler internals.

### `gradle-plugin`

Owns build integration:

- exposes the `acyclic {}` DSL
- maps Gradle values to raw compiler-plugin options
- resolves `kotlin-acyclic-plugin:<baseVersion>-kotlin-<kotlinVersion>`

This is the normal consumer entry point.

### `compiler-plugin`

Owns enforcement:

- parses compiler-plugin options
- registers the FIR checker extension
- performs file-level and declaration-level dependency analysis
- evaluates SCCs and declaration-order rules
- reports compiler diagnostics

### `ij-plugin`

Owns IDE bridge behavior:

- detects `kotlin-acyclic` usage from compiler classpaths and Gradle files
- enables external K2 compiler-plugin loading for the current trusted project session
- re-checks after trust/import changes

It does not yet add IDE-native inspections or quick fixes.

## Configuration Flow

The effective policy is resolved from broadest scope to narrowest scope:

1. Gradle defaults in `acyclic {}` or direct compiler-plugin options
2. file annotations
3. declaration annotations
4. declaration-level order overrides

For declaration order specifically:

- build config defines the module default
- `@file:Acyclic(order = ...)` overrides that default inside one file
- `@Acyclic(order = DEFAULT)` resets one declaration back to the module default

## Rule Families

The compiler plugin enforces three rule families.

### Compilation-unit acyclicity

Detects semantic cycles between Kotlin source files.

Key properties:

- whole-compilation scope
- semantic dependency edges, not import-string heuristics
- file-level opt-in/opt-out model
- a file-level cycle is exempt only when every participating file opts out

### Declaration acyclicity

Detects recursive dependency structure between tracked declarations.

Key properties:

- file-local today
- tracked declaration nodes include top-level declarations and declarations nested inside classes
- local declarations are not separate nodes
- dependencies discovered inside local declarations are attributed to the enclosing tracked declaration

### Declaration order

Adds an optional directional source-order rule on top of declaration acyclicity.

Supported policies:

- `NONE`
- `TOP_DOWN`
- `BOTTOM_UP`

Current reporting policy:

- if an edge is already part of a reported declaration cycle, the cycle diagnostic takes precedence
- redundant order diagnostics for that same cyclic edge are suppressed
- non-cyclic wrong-direction edges still report declaration-order violations normally

## Current Semantic Boundaries

These are current product boundaries, not accidents:

- declaration analysis is same-file only
- cross-file declaration edges are ignored by the declaration graph
- cross-file recursive structure is therefore enforced by compilation-unit analysis, not by a module-wide declaration graph
- lexical containment is distinguished from semantic dependency for cases like self return types, enclosing-type constructor calls, and nested-type containment references

## Publishing Model

The repository publishes:

- a plain-version annotations library
- a plain-version Gradle plugin
- a Kotlin-line-specific compiler plugin

Compiler-plugin coordinates use the form:

- `one.wabbit:kotlin-acyclic-plugin:<baseVersion>-kotlin-<kotlinVersion>`

This exists because FIR/compiler-plugin binaries are tied to the Kotlin compiler APIs they were built against.

## Test Strategy

The repository uses three complementary layers:

- graph-level tests for SCC behavior and order-direction logic
- compiler integration tests for real FIR-driven semantics
- real-project proving against sibling repositories

The project direction is:

- regressions first
- semantic changes second
- product decisions made explicitly, then documented

## Suggested Reading Order

If you are reviewing or extending the repo, use this order:

1. [README.md](../README.md)
2. [user-guide.md](./user-guide.md)
3. [library/README.md](../library/README.md)
4. [gradle-plugin/README.md](../gradle-plugin/README.md)
5. [compiler-plugin/README.md](../compiler-plugin/README.md)
6. [compiler-plugin/GOAL.md](../compiler-plugin/GOAL.md)
7. [compiler-plugin/WALKTHROUGH.md](../compiler-plugin/WALKTHROUGH.md)

That path moves from public surface to internal implementation.
