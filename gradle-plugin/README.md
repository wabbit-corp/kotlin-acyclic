# kotlin-acyclic-gradle-plugin

`kotlin-acyclic-gradle-plugin` is the typed Gradle integration for the `one.wabbit.acyclic` compiler plugin.

It applies the compiler plugin to Kotlin JVM and Kotlin Multiplatform compilations, exposes a normal `acyclic {}` DSL, and resolves the Kotlin-matched compiler-plugin artifact automatically.

If you want compile-time enforcement of file cycles, same-file declaration cycles, or declaration-order rules in a normal Gradle build, this is the entry point to use. The [root README](../README.md), [user guide](../docs/user-guide.md), and [API reference](../docs/api-reference.md) cover the broader model.

## Status

This module is pre-1.0 and tracks the repository Kotlin compatibility matrix.

## Plugin Coordinates

- plugin id: `one.wabbit.acyclic`
- artifact: `one.wabbit:kotlin-acyclic-gradle-plugin:0.1.0`
- extension: `acyclic {}`

The annotations library remains a normal dependency:

- `one.wabbit:kotlin-acyclic:0.1.0`

The Gradle plugin does not add that annotations dependency automatically.

## Installation

Add the plugin and the annotation library to the consuming build:

```kotlin
// settings.gradle.kts
pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
    }
}
```

```kotlin
// build.gradle.kts
plugins {
    kotlin("jvm") version "2.3.10"
    id("one.wabbit.acyclic") version "0.1.0"
}

dependencies {
    implementation("one.wabbit:kotlin-acyclic:0.1.0")
}
```

That is the minimum supported setup for a normal consumer build. The plugin resolves the Kotlin-line-specific compiler-plugin artifact automatically.

## Quick Start

Use the normal Gradle plugin and dependency repositories:

```kotlin
// settings.gradle.kts
pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}
```

To verify the plugin is active, enable `declarations.set(AcyclicEnforcementMode.ENABLED)`, add a same-file mutual recursion pair such as `fun a() = b(); fun b() = a()`, and run `./gradlew compileKotlin`. The build should fail with a declaration-cycle diagnostic.

```kotlin
// build.gradle.kts
import one.wabbit.acyclic.gradle.AcyclicDeclarationOrderMode
import one.wabbit.acyclic.gradle.AcyclicEnforcementMode

plugins {
    kotlin("jvm") version "2.3.10"
    id("one.wabbit.acyclic") version "0.1.0"
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("one.wabbit:kotlin-acyclic:0.1.0")
}

acyclic {
    compilationUnits.set(AcyclicEnforcementMode.OPT_IN)
    declarations.set(AcyclicEnforcementMode.ENABLED)
    declarationOrder.set(AcyclicDeclarationOrderMode.TOP_DOWN)
}
```

## Kotlin Multiplatform

The Gradle plugin also applies cleanly to Kotlin Multiplatform builds:

```kotlin
import one.wabbit.acyclic.gradle.AcyclicDeclarationOrderMode
import one.wabbit.acyclic.gradle.AcyclicEnforcementMode

plugins {
    kotlin("multiplatform") version "2.3.10"
    id("one.wabbit.acyclic") version "0.1.0"
}

kotlin {
    jvm()
    iosArm64()

    sourceSets {
        commonMain.dependencies {
            implementation("one.wabbit:kotlin-acyclic:0.1.0")
        }
    }
}

acyclic {
    compilationUnits.set(AcyclicEnforcementMode.OPT_IN)
    declarations.set(AcyclicEnforcementMode.OPT_IN)
    declarationOrder.set(AcyclicDeclarationOrderMode.BOTTOM_UP)
}
```

## Defaults

The Gradle extension defaults are:

- `compilationUnits = OPT_IN`
- `declarations = DISABLED`
- `declarationOrder = NONE`

Those defaults keep file-level checks opt-in, leave declaration-level checks off until requested, and avoid enforcing source order unless configured.

## Kotlin Version Negotiation

The Gradle plugin resolves the compiler plugin artifact as:

- `one.wabbit:kotlin-acyclic-plugin:<baseVersion>-kotlin-<kotlinVersion>`

`<kotlinVersion>` comes from the Kotlin Gradle plugin applied in the consumer build.

That keeps the compiler plugin aligned with the Kotlin compiler API used by the build without forcing consumers to manage the Kotlin-suffixed artifact directly.

For the current release train, compiler-plugin variants are published for:

- `2.3.10`
- `2.4.0-Beta1`

If a consumer build uses a Kotlin version without a published compiler-plugin variant, resolution fails fast instead of silently guessing a compatibility shim.

## Gradle DSL

```kotlin
acyclic {
    compilationUnits.set(AcyclicEnforcementMode.OPT_IN)
    declarations.set(AcyclicEnforcementMode.DISABLED)
    declarationOrder.set(AcyclicDeclarationOrderMode.NONE)
}
```

### `compilationUnits`

Controls file-level cycle checking.

- `DISABLED`
- `OPT_IN`
- `ENABLED`

### `declarations`

Controls declaration-level cycle checking.

- `DISABLED`
- `OPT_IN`
- `ENABLED`

### `declarationOrder`

Controls source-order enforcement for declaration dependencies.

- `NONE`
- `TOP_DOWN`
- `BOTTOM_UP`

`TOP_DOWN` permits earlier declarations to depend on later declarations.

`BOTTOM_UP` permits later declarations to depend on earlier declarations.

## Source-Level Controls

The Gradle plugin defines module-level defaults. Source annotations from `one.wabbit:kotlin-acyclic` refine those defaults inside code.

In practice:

- `@file:Acyclic` matters for compilation-unit checks when `compilationUnits = OPT_IN`
- `@file:AllowCompilationUnitCycles` opts a file out of compilation-unit checks even when `compilationUnits = ENABLED`
- `@Acyclic` on a declaration matters for declaration checks when `declarations = OPT_IN`
- `@AllowSelfRecursion` permits direct self-recursion for the annotated declaration or file
- `@AllowMutualRecursion` permits a declaration cycle only when every declaration in that cycle opts out
- file-level `@Acyclic(order = ...)` sets the default declaration-order policy for tracked declarations in that file
- declaration-level `@Acyclic(order = DEFAULT)` resets that declaration back to the module default order policy

Declaration analysis currently applies to top-level declarations and declarations nested inside classes, and declaration dependencies are evaluated only within the current file. Local declarations inside functions, accessors, and initializer bodies are not tracked as separate declaration nodes.

Local declarations still affect analysis: their resolved dependencies are attributed to the enclosing tracked declaration rather than becoming separate declaration nodes.

## Effective Precedence

When the Gradle plugin is in use, the effective policy is resolved like this:

1. `acyclic {}` defines the module-level defaults
2. those values are forwarded as compiler-plugin options for each Kotlin compilation
3. file annotations refine the defaults for one file
4. declaration annotations refine the policy again for one tracked declaration

For declaration order specifically:

- `acyclic.declarationOrder` sets the module default
- `@file:Acyclic(order = ...)` overrides that default for tracked declarations in the file
- `@Acyclic(order = DEFAULT)` resets one declaration back to the module default

## Compiler Option Mapping

The Gradle plugin is a typed layer over raw compiler-plugin options. It forwards:

- `compilationUnits` to `plugin:one.wabbit.acyclic:compilationUnits`
- `declarations` to `plugin:one.wabbit.acyclic:declarations`
- `declarationOrder` to `plugin:one.wabbit.acyclic:declarationOrder`

If you need direct compiler wiring details, see [`../compiler-plugin/README.md`](../compiler-plugin/README.md).

## Licensing

This project is licensed under the GNU Affero General Public License v3.0 (AGPL-3.0) for open source use.

## Contributing

Before contributions can be merged, contributors need to agree to the repository CLA.

Release notes live in [`../CHANGELOG.md`](../CHANGELOG.md). For support, troubleshooting, and contribution guidance, start with [`../docs/troubleshooting.md`](../docs/troubleshooting.md) and the [root README](../README.md).
