# kotlin-acyclic-gradle-plugin

`kotlin-acyclic-gradle-plugin` is a Gradle plugin for applying the `one.wabbit.acyclic` Kotlin compiler plugin to Kotlin JVM and Kotlin Multiplatform projects.

It exists to make acyclicity rules a normal part of project configuration instead of requiring raw `-P plugin:...` compiler arguments or manual `-Xplugin` wiring.

The plugin configures the compiler plugin, exposes a typed Gradle DSL, and lets projects choose how strict they want acyclicity enforcement to be.

## What It Enforces

The compiler plugin currently exposes three separate controls:

- compilation-unit acyclicity
- declaration acyclicity
- declaration order

Compilation-unit acyclicity is about `.kt` files depending on each other without cycles.

Declaration acyclicity is about declarations inside a file not depending on each other recursively.

Declaration order is a stricter rule on top of declaration acyclicity. It says the dependency graph must also follow a chosen source-order direction.

## Why Use The Gradle Plugin

You can always pass compiler plugin options manually, but the Gradle plugin gives you a better integration surface:

- the compiler plugin artifact is added automatically
- the configuration lives in normal Gradle DSL
- Kotlin JVM and KMP compilations are handled uniformly
- the options are typed instead of stringly-typed build-script flags

## Related Repositories

- `../kotlin-acyclic/README.md`
- `../kotlin-acyclic-plugin/README.md`
- `../kotlin-acyclic-plugin/WALKTHROUGH.md`

## Current Defaults

The Gradle extension defaults are:

- `compilationUnits = OPT_IN`
- `declarations = DISABLED`
- `declarationOrder = NONE`

That means:

- file-level cycle checking is available, but files must opt in with `@Acyclic`
- declaration-level checking is off unless you enable it in Gradle
- declaration-order checking is off unless you enable it in Gradle

## Kotlin Version

This repository uses `defaultKotlinVersion` in `gradle.properties` for local builds, and `supportedKotlinVersions` for the publish matrix. You can still override the active compiler line with `-PkotlinVersion=...`.

The Gradle plugin resolves the compiler plugin artifact version as `<baseVersion>-kotlin-<kotlinVersion>`, where `<kotlinVersion>` is the Kotlin Gradle plugin version applied in the consuming build.

## Installation

This plugin is currently best consumed either:

- as a composite build, or
- via your own internal publication flow

The repository is not set up here as a Gradle Plugin Portal project.

## Composite Build Installation

For local development, include the Gradle plugin build in `pluginManagement`, and include the compiler plugin build as a normal included build with dependency substitution.

If you also want the annotation library locally, include that build too.

```kotlin
pluginManagement {
    includeBuild("../kotlin-acyclic-gradle-plugin")

    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

includeBuild("../kotlin-acyclic-plugin") {
    dependencySubstitution {
        substitute(module("one.wabbit:kotlin-acyclic-plugin")).using(project(":"))
    }
}

includeBuild("../kotlin-acyclic") {
    dependencySubstitution {
        substitute(module("one.wabbit:kotlin-acyclic")).using(project(":"))
    }
}
```

Then apply the plugin in `build.gradle.kts`:

```kotlin
import one.wabbit.acyclic.gradle.AcyclicDeclarationOrderMode
import one.wabbit.acyclic.gradle.AcyclicEnforcementMode

plugins {
    kotlin("jvm") version "2.3.10"
    id("one.wabbit.acyclic")
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

## Published Artifact Consumption

If you publish the Gradle plugin and related artifacts yourself, the moving parts are:

- Gradle plugin id: `one.wabbit.acyclic`
- compiler plugin artifact: `one.wabbit:kotlin-acyclic-plugin:0.0.1-kotlin-2.3.10`
- annotations library: `one.wabbit:kotlin-acyclic:0.0.1`

The Gradle plugin adds the compiler plugin artifact automatically and picks the Kotlin-suffixed variant that matches the consuming build.

The annotations library is still a normal project dependency and should be added explicitly by the consumer.

## JVM Example

```kotlin
import one.wabbit.acyclic.gradle.AcyclicDeclarationOrderMode
import one.wabbit.acyclic.gradle.AcyclicEnforcementMode

plugins {
    kotlin("jvm") version "2.3.10"
    id("one.wabbit.acyclic")
}

dependencies {
    implementation("one.wabbit:kotlin-acyclic:0.0.1")
}

acyclic {
    compilationUnits.set(AcyclicEnforcementMode.ENABLED)
    declarations.set(AcyclicEnforcementMode.ENABLED)
    declarationOrder.set(AcyclicDeclarationOrderMode.TOP_DOWN)
}
```

## KMP Example

```kotlin
import one.wabbit.acyclic.gradle.AcyclicDeclarationOrderMode
import one.wabbit.acyclic.gradle.AcyclicEnforcementMode

plugins {
    kotlin("multiplatform") version "2.3.10"
    id("one.wabbit.acyclic")
}

kotlin {
    jvm()
    iosArm64()

    sourceSets {
        commonMain.dependencies {
            implementation("one.wabbit:kotlin-acyclic:0.0.1")
        }
    }
}

acyclic {
    compilationUnits.set(AcyclicEnforcementMode.OPT_IN)
    declarations.set(AcyclicEnforcementMode.OPT_IN)
    declarationOrder.set(AcyclicDeclarationOrderMode.BOTTOM_UP)
}
```

## Gradle DSL

The plugin creates an `acyclic {}` extension with three properties.

```kotlin
acyclic {
    compilationUnits.set(AcyclicEnforcementMode.OPT_IN)
    declarations.set(AcyclicEnforcementMode.DISABLED)
    declarationOrder.set(AcyclicDeclarationOrderMode.NONE)
}
```

### `compilationUnits`

Controls file-level cycle checking.

Available values:

- `DISABLED`
- `OPT_IN`
- `ENABLED`

Meaning:

- `DISABLED`: file-level checking is off
- `OPT_IN`: only files annotated with `@Acyclic` participate
- `ENABLED`: all files participate unless opted out

### `declarations`

Controls declaration-level cycle checking.

Available values:

- `DISABLED`
- `OPT_IN`
- `ENABLED`

Meaning:

- `DISABLED`: declaration-level checking is off
- `OPT_IN`: only declarations or files annotated with `@Acyclic` participate
- `ENABLED`: all tracked declarations participate unless opted out

### `declarationOrder`

Controls source-order enforcement for declaration dependencies.

Available values:

- `NONE`
- `TOP_DOWN`
- `BOTTOM_UP`

Meaning:

- `NONE`: only declaration cycles are checked
- `TOP_DOWN`: earlier declarations may depend only on later declarations
- `BOTTOM_UP`: later declarations may depend only on earlier declarations

Declaration order is layered on top of declaration checking. If `declarations = DISABLED`, order diagnostics are effectively off as well.

## Compiler Option Mapping

The Gradle plugin is a thin typed layer over compiler plugin options.

It forwards:

- `compilationUnits` to `plugin:one.wabbit.acyclic:compilationUnits`
- `declarations` to `plugin:one.wabbit.acyclic:declarations`
- `declarationOrder` to `plugin:one.wabbit.acyclic:declarationOrder`

The raw compiler forms are:

```text
-P plugin:one.wabbit.acyclic:compilationUnits=disabled|opt-in|enabled
-P plugin:one.wabbit.acyclic:declarations=disabled|opt-in|enabled
-P plugin:one.wabbit.acyclic:declarationOrder=none|top-down|bottom-up
```

Most consumers should prefer the Gradle DSL instead of setting these directly.

## Annotation Library

The associated annotations live in `one.wabbit:kotlin-acyclic`.

The main annotations are:

- `@Acyclic`
- `@AllowCompilationUnitCycles`
- `@AllowSelfRecursion`
- `@AllowMutualRecursion`
- `AcyclicOrder`

`@Acyclic` can be used on:

- files
- classes
- functions
- properties
- type aliases

Declaration analysis currently applies to top-level declarations and declarations nested inside classes.
Local declarations inside functions, accessors, and initializer bodies are not tracked.

`@AllowCompilationUnitCycles` is file-only.

`@AllowSelfRecursion` and `@AllowMutualRecursion` can be used on:

- files
- classes
- functions
- properties
- type aliases

`AcyclicOrder` supports:

- `DEFAULT`
- `NONE`
- `TOP_DOWN`
- `BOTTOM_UP`

## Precedence And Overrides

The intended control flow is:

- Gradle sets the default compiler behavior for the module
- file annotations can opt whole files in or out
- declaration annotations can opt individual declarations in or out

In practice:

- `@file:Acyclic` matters for compilation-unit checks when `compilationUnits = OPT_IN`
- `@file:AllowCompilationUnitCycles` opts a file out of compilation-unit checks even when `compilationUnits = ENABLED`
- `@Acyclic` on a declaration matters for declaration checks when `declarations = OPT_IN`
- `@AllowSelfRecursion` permits a 1-node declaration SCC
- `@AllowMutualRecursion` permits a multi-node declaration SCC only when every declaration in that SCC opts out
- file-level `@Acyclic(order = ...)` sets the default declaration-order policy for tracked declarations in that file
- declaration-level `@Acyclic(order = DEFAULT)` resets that declaration back to the module default order policy

## File-Level Opt-In Example

With `compilationUnits = OPT_IN`, a file must opt in explicitly:

```kotlin
@file:one.wabbit.acyclic.Acyclic

package sample
```

At that point, a same-package cycle like `sample/A.kt <-> sample/B.kt` is still a cycle and is rejected.

Package membership does not weaken compilation-unit acyclicity.

## Declaration-Level Opt-In Example

With `declarations = OPT_IN`, you can opt in a single declaration:

```kotlin
import one.wabbit.acyclic.Acyclic

@Acyclic
fun parse(): Node = parse()
```

That declaration can now trigger declaration-cycle diagnostics even if the rest of the file remains outside declaration-level checking.

## Explicit Recursion Escape Hatches

Self recursion:

```kotlin
import one.wabbit.acyclic.AllowSelfRecursion

@AllowSelfRecursion
fun loop(n: Int): Int =
    if (n <= 0) 0 else loop(n - 1)
```

Mutual recursion:

```kotlin
import one.wabbit.acyclic.AllowMutualRecursion

@AllowMutualRecursion
fun even(n: Int): Boolean =
    if (n == 0) true else odd(n - 1)

@AllowMutualRecursion
fun odd(n: Int): Boolean =
    if (n == 0) false else even(n - 1)
```

## File-Level Order Override Example

You can override the default order policy for a file:

```kotlin
@file:one.wabbit.acyclic.Acyclic(
    order = one.wabbit.acyclic.AcyclicOrder.TOP_DOWN,
)

package sample
```

This is useful when a project-wide default exists, but one file should read in the opposite direction.

## Scoping Exemptions

The compiler plugin intentionally does not treat lexical containment by itself as a dependency edge.

These patterns are allowed:

```kotlin
sealed interface Foo {
    class Boo : Foo
}
```

```kotlin
class Bar {
    fun self(): Bar = this
}
```

The goal is to reject semantic recursion, not nominal self-reference or simple nesting.

## Diagnostics

The current diagnostics are:

- `Circular dependency detected between Kotlin files: ...`
- `Circular dependency detected between Kotlin declarations: ...`
- `Kotlin declaration order violation: ...`

When declaration order is enabled, a recursive declaration pattern can produce both:

- a cycle diagnostic
- an order diagnostic

That is expected. One diagnostic tells you that the graph is recursive. The other tells you how it also violates the chosen source-order policy.

## What Counts As A Dependency

The current implementation tracks resolved dependencies for declarations of these kinds:

- classes
- functions
- properties
- type aliases

Declaration-level analysis currently covers top-level declarations and class members. Local
declarations inside function bodies, accessors, and initializer blocks are intentionally out of
scope.

It uses resolved FIR references rather than raw text matching.

That means:

- cross-file references are semantic, not import-string based
- same-file declaration cycles can be detected through resolved references
- explicit source-order rules can be checked against the resulting declaration graph

## Current Status

Compilation-unit acyclicity is the more mature part of the system.

Declaration-level checking is now implemented and working, but it is newer and still evolving. In particular:

- declaration checks are intentionally default-off
- file-level and declaration-level order overrides are implemented
- declaration-level opt-in and opt-out are implemented
- some edge cases involving implicit or generated structure may continue to be refined

If you are adopting this across an existing codebase, the safest rollout is usually:

- enable compilation-unit checks first
- keep them `OPT_IN` initially
- then enable declaration checks in selected files or modules
- finally enable declaration order once the declaration graph is stable

## Relationship To The Compiler Plugin

This project is only the Gradle integration layer.

The actual compiler analysis lives in:

- `kotlin-acyclic-plugin`

The annotation definitions live in:

- `kotlin-acyclic`

Consumers usually need all three pieces:

- the Gradle plugin
- the compiler plugin artifact
- the annotations library

## Licensing

This project is licensed under the GNU Affero General Public License v3.0 (AGPL-3.0) for open source use.

For commercial use, contact Wabbit Consulting Corporation at `wabbit@wabbit.one`.

## Contributing

Before contributions can be merged, contributors need to agree to the repository CLA.
