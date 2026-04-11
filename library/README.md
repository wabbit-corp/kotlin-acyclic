# kotlin-acyclic

`kotlin-acyclic` is the annotation library for the `one.wabbit.acyclic` Kotlin compiler plugin family.

It contains only source-retained annotations and enums. There are no compiler internals here. The library is intended to be added as a normal dependency by projects that want to opt into, override, or explicitly exempt acyclicity rules in source.

Start with the [root README](../README.md) for the overview, [user guide](../docs/user-guide.md) for setup and rule semantics, and [API reference](../docs/api-reference.md) for the public surface.

## Status

This module is pre-1.0 and follows the repository Kotlin compatibility matrix in [`../gradle.properties`](../gradle.properties).

## Repository Role

The acyclic project family is split into three parts:

- `kotlin-acyclic`: source annotations used by application and library code
- `kotlin-acyclic-plugin`: the Kotlin compiler plugin that enforces the rules
- `kotlin-acyclic-gradle-plugin`: the Gradle bridge that wires the compiler plugin into Kotlin compilations

Most consumers need this repository together with the Gradle plugin.

The annotations library keeps the base project version, while the compiler plugin is published as a Kotlin-specific variant such as `one.wabbit:kotlin-acyclic-plugin:0.0.1-kotlin-2.3.10`.

## Artifact

- coordinates: `one.wabbit:kotlin-acyclic:0.0.1`

## Installation

Most users add this library alongside the Gradle plugin:

```kotlin
plugins {
    kotlin("jvm") version "2.3.10"
    id("one.wabbit.acyclic") version "0.0.1"
}

dependencies {
    implementation("one.wabbit:kotlin-acyclic:0.0.1")
}
```

Run `./gradlew compileKotlin` after adding the dependency to confirm the annotations resolve in source.

## Annotations

### `@Acyclic`

`@Acyclic` opts a file or declaration into checking and can optionally override declaration order.

Targets:

- file
- class
- function
- property
- typealias

Declaration analysis currently applies to top-level declarations and declarations nested inside
classes, and declaration dependencies are evaluated only within the current file. Local declarations
inside functions, accessors, and initializer bodies are not tracked by the compiler plugin as
separate declaration nodes. Their resolved dependencies still contribute to the enclosing tracked
declaration.

Example:

```kotlin
@file:one.wabbit.acyclic.Acyclic(
    order = one.wabbit.acyclic.AcyclicOrder.TOP_DOWN,
)

package sample
```

### `AcyclicOrder`

`AcyclicOrder` controls declaration-order policy for `@Acyclic`.

Values:

- `DEFAULT`
  Uses the module-level default order policy, even when a file-level `@Acyclic(order = ...)`
  override is present.
- `NONE`
- `TOP_DOWN`
- `BOTTOM_UP`

When `@file:Acyclic(order = ...)` is present, that file-level order becomes the default for
tracked declarations in the file unless a declaration-level `@Acyclic(order = ...)` replaces it or
resets it with `DEFAULT`.

## Precedence Within One Compilation

The annotations in this module sit on top of build-level defaults from the Gradle plugin or direct
compiler-plugin options.

The effective policy is resolved in this order:

1. build-level defaults from `acyclic {}` or `plugin:one.wabbit.acyclic:*` options
2. file annotations such as `@file:Acyclic` and `@file:AllowCompilationUnitCycles`
3. declaration annotations such as `@Acyclic`, `@AllowSelfRecursion`, and `@AllowMutualRecursion`

For declaration order specifically:

- the module-level default comes from the build configuration
- `@file:Acyclic(order = ...)` overrides that default for tracked declarations in the file
- `@Acyclic(order = DEFAULT)` on a declaration resets that declaration back to the module-level default

### `@AllowCompilationUnitCycles`

`@AllowCompilationUnitCycles` explicitly permits a file to participate in a file-level cycle.

A compilation-unit cycle is exempt only when every file in the reported cycle opts out with this annotation.

Example:

```kotlin
@file:one.wabbit.acyclic.AllowCompilationUnitCycles

package sample
```

### `@AllowSelfRecursion`

`@AllowSelfRecursion` explicitly permits direct self-recursion for the annotated declaration or file.

Targets:

- file
- class
- function
- property
- typealias

Example:

```kotlin
import one.wabbit.acyclic.AllowSelfRecursion

@AllowSelfRecursion
fun loop(n: Int): Int =
    if (n <= 0) 0 else loop(n - 1)
```

### `@AllowMutualRecursion`

`@AllowMutualRecursion` explicitly permits mutual recursion for the annotated declaration or file.

A mutual-recursion component is exempt only when every declaration in the cycle opts out with this annotation.

Targets:

- file
- class
- function
- property
- typealias

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

## Source Retention

All annotations in this module use `AnnotationRetention.SOURCE`.

These annotations are source-retained because they are consumed during compilation, do not need to remain in runtime metadata, and are part of the static structure policy rather than runtime behavior.

## Typical Usage

With the companion Gradle plugin:

```kotlin
plugins {
    kotlin("jvm") version "2.3.10"
    id("one.wabbit.acyclic")
}

dependencies {
    implementation("one.wabbit:kotlin-acyclic:0.0.1")
}
```

Then use annotations in source only where you want to:

- opt files or declarations into checking
- override the default declaration-order policy
- carve out narrow, explicit recursion exceptions

Release notes live in [`../CHANGELOG.md`](../CHANGELOG.md). If you run into unexpected diagnostics, start with [`../docs/troubleshooting.md`](../docs/troubleshooting.md) and the contribution/support guidance in the [root README](../README.md).
