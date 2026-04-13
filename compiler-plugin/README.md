# kotlin-acyclic-plugin

`kotlin-acyclic-plugin` is the K2/FIR compiler plugin that enforces the `one.wabbit.acyclic` rule set.

Most projects should apply `one.wabbit.acyclic` through the companion Gradle plugin, but this module is the actual compiler-side implementation and is the right entry point for direct compiler integration, build-tool adapters, and Dokka API documentation.

Use this module when you need direct compiler wiring or want to inspect the compiler-side behavior. For normal build setup, start with the [Gradle plugin README](../gradle-plugin/README.md), the [user guide](../docs/user-guide.md), and the [API reference](../docs/api-reference.md).

## Status

This module is pre-1.0 and publishes Kotlin-line-specific variants for the repository's supported Kotlin matrix.

## Artifact

The compiler plugin is published as a Kotlin-line-specific artifact:

- `one.wabbit:kotlin-acyclic-plugin:0.1.0-kotlin-2.3.10`

The `-kotlin-<kotlinVersion>` suffix matters. FIR compiler-plugin binaries are coupled to the Kotlin compiler APIs they were built against.

The current release train publishes Kotlin-specific compiler-plugin variants for:

- `2.3.10`
- `2.4.0-Beta1`

If you use Gradle, the companion plugin resolves the matching variant automatically.

## What The Compiler Plugin Enforces

The compiler plugin evaluates three rule families:

- compilation-unit acyclicity
- declaration acyclicity
- declaration order

Compilation-unit acyclicity reports cycles between Kotlin source files.

Declaration acyclicity reports recursive dependency structure between tracked declarations in a file. The declaration graph is file-local today.

Declaration order adds an optional directional rule on top of declaration acyclicity and checks whether declaration dependencies respect `top-down` or `bottom-up` source order.

When an edge is already part of a reported declaration cycle, the cycle diagnostic takes precedence and the redundant declaration-order diagnostic for that same edge is suppressed.

## Compiler Options

The plugin accepts three module-level options:

- `compilationUnits=disabled|opt-in|enabled`
- `declarations=disabled|opt-in|enabled`
- `declarationOrder=none|top-down|bottom-up`

Raw CLI form:

```text
-P plugin:one.wabbit.acyclic:compilationUnits=disabled|opt-in|enabled
-P plugin:one.wabbit.acyclic:declarations=disabled|opt-in|enabled
-P plugin:one.wabbit.acyclic:declarationOrder=none|top-down|bottom-up
```

These options define the build-level defaults for the compilation. Source annotations from `one.wabbit:kotlin-acyclic` can then opt specific files or declarations in, override declaration-order policy, or declare narrowly scoped recursion exemptions.

## Control Precedence

The effective policy is resolved in this order:

1. compiler-plugin options establish the build-level defaults for the compilation
2. file annotations can opt whole files in, allow whole-file compilation-unit cycles, and set a file-local declaration-order default
3. declaration annotations can opt individual tracked declarations in and grant narrow recursion exceptions
4. declaration-level `@Acyclic(order = DEFAULT|NONE|TOP_DOWN|BOTTOM_UP)` can replace the file-level order rule or reset back to the build-level default

For declaration order specifically:

- module default comes from `declarationOrder`
- `@file:Acyclic(order = ...)` overrides that default for tracked declarations in the file
- `@Acyclic(order = DEFAULT)` on a declaration resets that declaration back to the module default

## Installation And Direct Usage

If you are wiring the plugin into the Kotlin compiler directly:

```text
-Xplugin=/path/to/kotlin-acyclic-plugin.jar
-P plugin:one.wabbit.acyclic:compilationUnits=enabled
-P plugin:one.wabbit.acyclic:declarations=enabled
-P plugin:one.wabbit.acyclic:declarationOrder=top-down
```

If source code uses `one.wabbit.acyclic.*`, the annotations library still needs to be present on the compilation classpath.

To verify the plugin is active, compile a small source set with `declarations=enabled` and a same-file mutual recursion pair such as `fun a() = b(); fun b() = a()`. The compilation should fail with a declaration-cycle diagnostic.

## Analysis Model

The compiler-plugin pipeline is:

1. `AcyclicCommandLineProcessor` parses raw compiler-plugin options.
2. `AcyclicCompilerPluginRegistrar` registers the FIR checker extension.
3. `AcyclicFileAnalysis` walks resolved FIR and records dependency evidence.
4. `AcyclicDependencyGraph` evaluates file-level strongly connected components.
5. `AcyclicDeclarationGraph` evaluates declaration cycles and order violations.
6. `AcyclicDiagnostics` reports compiler errors.

The critical design choice is semantic analysis. Dependencies come from resolved FIR symbols and resolved types rather than from imports or syntax-only heuristics.

## Scope

Declaration analysis distinguishes lexical containment from dependency.

Examples that remain legal:

- `sealed interface Foo { class Boo : Foo }`
- `class Foo { fun self(): Foo = this }`

Declaration analysis currently covers top-level declarations and declarations nested inside classes, and it only evaluates declaration dependencies within the current file. Local declarations inside function bodies, accessors, and other local scopes are not tracked as separate declaration nodes.

Local declarations still matter semantically: their resolved dependencies are attributed to the enclosing tracked declaration instead of becoming separate graph nodes.

## Worked Examples

### Legal scoping

These shapes stay legal because they express containment or self-typing, not sibling recursion:

```kotlin
package sample

sealed interface Foo {
    class Boo : Foo
}

class Box {
    fun self(): Box = this
}
```

### Illegal declaration recursion

With declaration analysis enabled, same-file mutual recursion is rejected:

```kotlin
package sample

fun parseA(): Node = parseB()

fun parseB(): Node = parseA()
```

### Illegal file cycles

With compilation-unit analysis enabled, cross-file semantic cycles are rejected:

```kotlin
// sample/A.kt
package sample

class A(val b: B)
```

```kotlin
// sample/B.kt
package sample

class B(val a: A)
```

### Order violations

With `-P plugin:one.wabbit.acyclic:declarationOrder=bottom-up`, the following file is rejected because `use()` appears earlier but depends on `helper()`:

```kotlin
package sample

fun use(): Int = helper()

fun helper(): Int = 1
```

Under `top-down`, the same file is valid.

### Explicit opt-outs

Escape hatches are all-or-nothing at the cycle level:

```kotlin
package sample

import one.wabbit.acyclic.AllowMutualRecursion

@AllowMutualRecursion
fun even(n: Int): Boolean =
    if (n == 0) true else odd(n - 1)

@AllowMutualRecursion
fun odd(n: Int): Boolean =
    if (n == 0) false else even(n - 1)
```

If only one participant opts out, the cycle is still reported.

## When To Use This Module Directly

Use this artifact directly when:

- integrating with a non-Gradle build pipeline
- debugging compiler-plugin behavior
- testing Kotlin-version-specific compiler-plugin variants
- reading the Dokka API surface for compiler-side internals

If you are using Gradle, prefer [`../gradle-plugin/README.md`](../gradle-plugin/README.md).

Release notes live in [`../CHANGELOG.md`](../CHANGELOG.md). For diagnostics and setup issues, start with [`../docs/troubleshooting.md`](../docs/troubleshooting.md) and the contribution/support guidance in the [root README](../README.md).
