# User Guide

This guide covers the user-facing model of `kotlin-acyclic`: how to wire it into a build, what each rule family checks, and how the source-level controls refine the module defaults.

## Why This Model Exists

The project treats structural recursion and source-order policy as things teams should be able to make explicit.

What the plugin adds on top of plain Kotlin is:

- semantic file-cycle checking
- semantic same-file declaration-cycle checking
- optional declaration source-order enforcement
- narrow, explicit escape hatches for the cases that are genuinely intentional

Without that extra structure, the language still permits these shapes, but nothing makes the architectural dependency policy visible or enforceable.

## Setup

### Gradle

The normal integration path is the Gradle plugin:

```kotlin
// settings.gradle.kts
pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}
```

```kotlin
// build.gradle.kts
import one.wabbit.acyclic.gradle.AcyclicDeclarationOrderMode
import one.wabbit.acyclic.gradle.AcyclicEnforcementMode

plugins {
    kotlin("jvm") version "2.3.10"
    id("one.wabbit.acyclic") version "0.0.1"
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("one.wabbit:kotlin-acyclic:0.0.1")
}

acyclic {
    compilationUnits.set(AcyclicEnforcementMode.OPT_IN)
    declarations.set(AcyclicEnforcementMode.ENABLED)
    declarationOrder.set(AcyclicDeclarationOrderMode.TOP_DOWN)
}
```

The Gradle plugin selects the compiler-plugin artifact variant that matches the applied Kotlin Gradle plugin version.

### Manual compiler wiring

If you are not using the Gradle plugin, you need:

- the annotations dependency `one.wabbit:kotlin-acyclic:0.0.1`
- the compiler plugin artifact `one.wabbit:kotlin-acyclic-plugin:<baseVersion>-kotlin-<kotlinVersion>`
- compiler options in the standard plugin format

```text
-Xplugin=/path/to/kotlin-acyclic-plugin.jar
-P plugin:one.wabbit.acyclic:compilationUnits=disabled|opt-in|enabled
-P plugin:one.wabbit.acyclic:declarations=disabled|opt-in|enabled
-P plugin:one.wabbit.acyclic:declarationOrder=none|top-down|bottom-up
```

## Module-Level Defaults

Three build-level controls exist:

- `compilationUnits`
- `declarations`
- `declarationOrder`

Meanings:

- `compilationUnits` controls file-cycle checking
- `declarations` controls declaration-cycle checking
- `declarationOrder` controls optional top-down/bottom-up source-order enforcement

The Gradle defaults are:

- `compilationUnits = OPT_IN`
- `declarations = DISABLED`
- `declarationOrder = NONE`

## Design Intent

The rule model is constrained on purpose:

- semantic rather than syntax-only
- explicit rather than inferred from naming or imports
- narrow escape hatches instead of broad suppression
- predictable enough that users can usually tell from source why a dependency is legal or illegal

## Source-Level Controls

The public annotation surface is:

- `@Acyclic`
- `@AllowCompilationUnitCycles`
- `@AllowSelfRecursion`
- `@AllowMutualRecursion`
- `AcyclicOrder`

### Effective precedence

The final policy is resolved in this order:

1. Gradle defaults or direct compiler-plugin options
2. file annotations
3. declaration annotations
4. declaration-level order overrides

For declaration order:

- module default comes from `declarationOrder`
- `@file:Acyclic(order = ...)` overrides that default for tracked declarations in the file
- `@Acyclic(order = DEFAULT)` resets one declaration back to the module default

## Rule Semantics

### Compilation-unit acyclicity

Compilation-unit analysis reports semantic cycles between Kotlin source files.

With `compilationUnits = OPT_IN`, a file opts in with:

```kotlin
@file:one.wabbit.acyclic.Acyclic
```

A file-level cycle is exempt only when every participating file uses `@file:AllowCompilationUnitCycles`.

### Declaration acyclicity

Declaration analysis reports recursive dependency structure between tracked declarations.

Current tracked declaration nodes are:

- top-level classes
- top-level functions
- top-level properties
- top-level typealiases
- declarations nested inside classes using the same tracked kinds

Current boundary:

- declaration analysis is file-local today
- cross-file declaration edges are ignored by the declaration graph
- cross-file recursion is therefore enforced by compilation-unit analysis, not a module-wide declaration graph

Local declarations are not separate declaration nodes. Their resolved dependencies are attributed to the enclosing tracked declaration instead.

### Declaration order

Declaration order adds an optional directional source-order rule.

- `TOP_DOWN`: earlier declarations may depend on later declarations
- `BOTTOM_UP`: later declarations may depend on earlier declarations
- `NONE`: no source-order rule

If an edge is already part of a reported declaration cycle, the cycle diagnostic takes precedence and the redundant order diagnostic for that same edge is suppressed.

## Legal And Illegal Shapes

### Legal scoping

These are legal:

```kotlin
sealed interface Token {
    class Word(val text: String) : Token
}

class Box {
    fun self(): Box = this
}
```

The reason is simple: lexical containment and nominal self-typing are not treated as sibling recursion.

### Illegal same-file recursion

```kotlin
fun a(): Int = b()
fun b(): Int = a()
```

With declaration checking enabled, that is rejected as a declaration cycle.

### Illegal cross-file cycle

```kotlin
// A.kt
class A(val b: B)
```

```kotlin
// B.kt
class B(val a: A)
```

With compilation-unit checking enabled, that is rejected as a file cycle.

### Illegal order violation

```kotlin
fun use(): Int = helper()
fun helper(): Int = 1
```

That file is valid under `TOP_DOWN` and rejected under `BOTTOM_UP`.

## Escape Hatches

Escape hatches are narrow.

- `@AllowSelfRecursion` permits direct self-recursion
- `@AllowMutualRecursion` permits a declaration cycle only when every declaration in the cycle opts out
- `@AllowCompilationUnitCycles` permits a file cycle only when every participating file opts out

Example:

```kotlin
import one.wabbit.acyclic.AllowMutualRecursion

@AllowMutualRecursion
fun even(n: Int): Boolean =
    if (n == 0) true else odd(n - 1)

@AllowMutualRecursion
fun odd(n: Int): Boolean =
    if (n == 0) false else even(n - 1)
```

If only one participant opts out, the cycle is still reported.

## Current Non-Goals

The current implementation does not try to:

- build a module-wide declaration graph
- treat every form of lexical nesting as a dependency edge
- infer hidden recursion from arbitrary runtime behavior

The project direction is to keep the rule set semantic and explicit, but still understandable enough that users can predict what the compiler will do.

## Where To Look Next

- [README.md](../README.md)
- [ARCHITECTURE.md](./ARCHITECTURE.md)
- [api-reference.md](./api-reference.md)
- [migration.md](./migration.md)
- [troubleshooting.md](./troubleshooting.md)
- [library/README.md](../library/README.md)
- [gradle-plugin/README.md](../gradle-plugin/README.md)
- [compiler-plugin/README.md](../compiler-plugin/README.md)
