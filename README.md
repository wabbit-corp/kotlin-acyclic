# kotlin-acyclic

`kotlin-acyclic` is a Kotlin compiler-plugin family for enforcing structural acyclicity rules in source code.

It is built for teams that want compile-time guardrails around declaration recursion, file-to-file dependency cycles, and source-order conventions without relying on lint-only heuristics or import-string analysis.

## Status

This repository is pre-1.0 and K2-only.

- Kotlin publish matrix is driven by `supportedKotlinVersions` in [`gradle.properties`](./gradle.properties). The current matrix is `2.3.10` and `2.4.0-Beta1`.
- The annotations library is Kotlin Multiplatform and uses JDK 21 for JVM compilation.
- The compiler plugin and Gradle plugin target JDK 21.
- The IntelliJ plugin targets JVM 17 and IntelliJ IDEA `2025.3`.

## Design Intent

This project is trying to be strict without becoming magical.

- dependency edges come from resolved semantics, not import-string heuristics
- declaration analysis is intentionally scoped so users can predict what the rule means
- escape hatches are explicit and all-participants, not partial suppression tricks
- current limits are documented as product boundaries rather than treated as accidental quirks

## Why This Exists

Kotlin makes it easy to write elegant recursive code and to spread definitions across files. That is usually a strength. In larger codebases, it can also hide structural problems:

- mutually recursive declarations that make a file harder to reason about
- cross-file cycles that blur compilation-unit boundaries
- source-order conventions that drift because nothing enforces them

`kotlin-acyclic` makes those rules explicit and compile-time enforced.

## Modules

| Module | Gradle project | Purpose |
| --- | --- | --- |
| [`library/`](./library/) | `:kotlin-acyclic` | Public annotation API: `@Acyclic`, recursion opt-outs, and `AcyclicOrder` |
| [`gradle-plugin/`](./gradle-plugin/) | `:kotlin-acyclic-gradle-plugin` | Gradle integration for `one.wabbit.acyclic` |
| [`compiler-plugin/`](./compiler-plugin/) | `:kotlin-acyclic-plugin` | K2/FIR compiler plugin: semantic dependency collection, cycle detection, and order enforcement |
| [`ij-plugin/`](./ij-plugin/) | `:kotlin-acyclic-ij-plugin` | IntelliJ IDEA helper plugin for external compiler-plugin loading |

## What It Checks

The project enforces three related rule families.

| Rule family | What it reports | Current scope |
| --- | --- | --- |
| Compilation-unit acyclicity | semantic cycles between Kotlin source files | whole compilation |
| Declaration acyclicity | recursive dependency structure between tracked declarations | same file |
| Declaration order | wrong-direction declaration dependencies under `TOP_DOWN` or `BOTTOM_UP` | same file |

Important current boundary:

- declaration analysis is intentionally file-local today
- top-level declarations and declarations nested inside classes become declaration nodes
- local declarations are not separate nodes, but their resolved dependencies are attributed to the enclosing tracked declaration

## Current Boundaries

These are intentional current boundaries, not hidden surprises:

- declaration analysis is same-file only
- cross-file declaration recursion is enforced by compilation-unit analysis, not by a module-wide declaration graph
- lexical containment is distinguished from semantic dependency for shapes like self return types, nested type containment, and enclosing-type references
- the rule set is structural and semantic, not arbitrary runtime recursion analysis

## Quick Start

Assuming Maven Central publication:

```kotlin
// settings.gradle.kts
pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
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

In most builds, that is all you need. The Gradle plugin resolves the Kotlin-matched compiler-plugin artifact automatically.

## Default Behavior

The Gradle plugin defaults are conservative:

- `compilationUnits = OPT_IN`
- `declarations = DISABLED`
- `declarationOrder = NONE`

That means:

- file-level checks are available but not forced
- declaration-level checks stay off until explicitly enabled
- no source-order policy is imposed by default

## Control Model

The effective policy is resolved from broadest scope to narrowest scope:

1. Gradle defaults in `acyclic {}` or direct compiler-plugin options
2. file annotations such as `@file:Acyclic` and `@file:AllowCompilationUnitCycles`
3. declaration annotations such as `@Acyclic`, `@AllowSelfRecursion`, and `@AllowMutualRecursion`
4. declaration-level `@Acyclic(order = DEFAULT|NONE|TOP_DOWN|BOTTOM_UP)` for per-declaration order policy

Practical reading:

- build configuration establishes the default policy for the compilation
- file annotations can opt an entire file into checks, opt an entire file out of file-cycle checks, and set the default declaration-order policy for that file
- declaration annotations can opt individual tracked declarations in and carve out narrow, explicit exceptions
- `@Acyclic(order = DEFAULT)` resets one declaration back to the build-level order policy, not the file-level override

## Worked Examples

### Legal scoping

Lexical containment and nominal self-reference are not treated as declaration cycles:

```kotlin
package sample

sealed interface Token {
    class Word(val text: String) : Token
}

class Box {
    fun self(): Box = this
}
```

### Illegal declaration recursion

With declaration checking enabled, same-file mutual recursion is rejected:

```kotlin
package sample

fun a(): Int = b()

fun b(): Int = a()
```

### Illegal file cycles

With compilation-unit checking enabled, semantic cross-file cycles are rejected:

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

With `declarationOrder = BOTTOM_UP`, later declarations may depend on earlier ones, but not the reverse:

```kotlin
package sample

fun use(): Int = helper()

fun helper(): Int = 1
```

That file is valid under `TOP_DOWN` and rejected under `BOTTOM_UP`.

If an edge is already part of a reported declaration cycle, the cycle diagnostic wins and the redundant declaration-order diagnostic for that same edge is suppressed.

### Explicit opt-outs

Opt-outs are intentionally narrow. A cycle is exempt only when every participant opts out:

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

The same all-participants rule applies at file scope with `@file:AllowCompilationUnitCycles`.

## Published Modules

Most consumers only need the annotations library and the Gradle plugin.

| Module | Gradle project | Coordinates | Role |
| --- | --- | --- | --- |
| Annotations library | `:kotlin-acyclic` | `one.wabbit:kotlin-acyclic` | source-retained annotations and enums used from normal Kotlin code |
| Gradle plugin | `:kotlin-acyclic-gradle-plugin` | `one.wabbit:kotlin-acyclic-gradle-plugin` | typed Gradle DSL and compiler-plugin wiring |
| Compiler plugin | `:kotlin-acyclic-plugin` | `one.wabbit:kotlin-acyclic-plugin:<baseVersion>-kotlin-<kotlinVersion>` | Kotlin-line-specific K2/FIR implementation |
| IntelliJ plugin | `:kotlin-acyclic-ij-plugin` | local/plugin distribution | enables IDE-side loading of the external compiler plugin for trusted projects |

## Kotlin Compatibility And Versioning

The compiler plugin is published per Kotlin compiler line, using a version suffix of the form:

- `one.wabbit:kotlin-acyclic-plugin:<baseVersion>-kotlin-<kotlinVersion>`

For the current release train, the repository is configured to publish compiler-plugin variants for:

- `2.3.10`
- `2.4.0-Beta1`

The Gradle plugin chooses the matching compiler-plugin artifact automatically. If you integrate the compiler plugin directly, choose the artifact whose `-kotlin-<kotlinVersion>` suffix matches your compiler.

## Direct Compiler Usage

If you are not using Gradle, wire the compiler plugin directly:

```text
-Xplugin=/path/to/kotlin-acyclic-plugin.jar
-P plugin:one.wabbit.acyclic:compilationUnits=disabled|opt-in|enabled
-P plugin:one.wabbit.acyclic:declarations=disabled|opt-in|enabled
-P plugin:one.wabbit.acyclic:declarationOrder=none|top-down|bottom-up
```

If source code uses `one.wabbit.acyclic.*`, the annotations library still needs to be present on the compilation classpath.

## Local Composite Builds

Before publication, or when testing locally across repositories, consumers should use both forms of composite-build wiring:

```kotlin
pluginManagement {
    includeBuild("../kotlin-acyclic")
}

includeBuild("../kotlin-acyclic")
```

The first resolves the Gradle plugin ID. The second lets Gradle substitute the annotations and compiler-plugin artifacts.

## IntelliJ Support

The IntelliJ plugin in this repository does not implement separate IDE-native inspections yet. Its current job is to help the bundled Kotlin IDE plugin load the external compiler plugin registrar for trusted projects that already apply `kotlin-acyclic`.

Important detail:

- IntelliJ only exposes a coarse registry switch here
- enabling support for `kotlin-acyclic` enables all non-bundled K2 compiler plugins for the current trusted project session, not just this one

## Build And Test

Common commands from the repo root:

```bash
./gradlew build
./gradlew projects
./gradlew :kotlin-acyclic:compileKotlinMetadata
./gradlew :kotlin-acyclic-plugin:test
./gradlew :kotlin-acyclic-gradle-plugin:test
./gradlew :kotlin-acyclic-ij-plugin:test
```

To run the compiler plugin against a specific supported Kotlin line:

```bash
./gradlew -PkotlinVersion=2.3.10 :kotlin-acyclic-plugin:test
./gradlew -PkotlinVersion=2.4.0-Beta1 :kotlin-acyclic-plugin:test
```

## Documentation Map

- [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md): repo-wide architecture, configuration flow, and analysis boundaries
- [docs/user-guide.md](docs/user-guide.md): installation, configuration, rule semantics, and source-level control model
- [docs/development.md](docs/development.md): local build, test, versioning, publishing, and composite-build notes
- [library/README.md](library/README.md): source-level annotations and precedence details
- [gradle-plugin/README.md](gradle-plugin/README.md): Gradle DSL, installation, and Kotlin version negotiation
- [compiler-plugin/README.md](compiler-plugin/README.md): direct compiler integration and compiler-side behavior
- [ij-plugin/README.md](ij-plugin/README.md): IntelliJ plugin scope and lifecycle
- [compiler-plugin/GOAL.md](compiler-plugin/GOAL.md): design goals and semantics
- [compiler-plugin/WALKTHROUGH.md](compiler-plugin/WALKTHROUGH.md): guided code-review path through the implementation

## Suggested Reading Order

If you are new to the repository, this order usually works well:

1. [README.md](./README.md)
2. [docs/user-guide.md](./docs/user-guide.md)
3. [docs/ARCHITECTURE.md](./docs/ARCHITECTURE.md)
4. [library/README.md](./library/README.md)
5. [gradle-plugin/README.md](./gradle-plugin/README.md)
6. [compiler-plugin/README.md](./compiler-plugin/README.md)
7. [compiler-plugin/PLAN.md](./compiler-plugin/PLAN.md)

## Licensing

This project is licensed under the GNU Affero General Public License v3.0 (AGPL-3.0) for open source use.

For commercial use, contact Wabbit Consulting Corporation at `wabbit@wabbit.one`.

## Contributing

Before contributions can be merged, contributors need to agree to the repository CLA.
