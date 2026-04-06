# Development

This guide covers local development, testing, versioning, and publishing for `kotlin-acyclic`.

## Prerequisites

- JDK 21 for the annotations library, compiler plugin, and Gradle plugin
- JDK 17-compatible environment for the IntelliJ plugin target
- the checked-in Gradle wrapper

The main build settings are pinned in:

- [`gradle.properties`](../gradle.properties)
- [`settings.gradle.kts`](../settings.gradle.kts)

## Gradle Project Names

Gradle project names:

- `:kotlin-acyclic`
- `:kotlin-acyclic-plugin`
- `:kotlin-acyclic-gradle-plugin`
- `:kotlin-acyclic-ij-plugin`

The annotations library lives under `library/`, but its Gradle project name is `:kotlin-acyclic`.

## Common Commands

From the repository root:

```bash
./gradlew build
./gradlew projects
./gradlew :kotlin-acyclic:compileKotlinMetadata
./gradlew :kotlin-acyclic-plugin:test
./gradlew :kotlin-acyclic-gradle-plugin:test
./gradlew :kotlin-acyclic-ij-plugin:test
```

Targeted examples:

```bash
./gradlew :kotlin-acyclic-plugin:test --tests 'one.wabbit.acyclic.CompilerIntegrationTest'
./gradlew :kotlin-acyclic-gradle-plugin:test --tests 'one.wabbit.acyclic.gradle.AcyclicGradlePluginTest'
./gradlew :kotlin-acyclic-ij-plugin:test --tests 'one.wabbit.acyclic.idea.AcyclicCompilerPluginDetectorTest'
```

## Testing Against Different Kotlin Versions

The Kotlin publish/test matrix is driven by:

- `defaultKotlinVersion`
- `supportedKotlinVersions`

Both are defined in [`gradle.properties`](../gradle.properties).

To run the compiler plugin against a specific supported Kotlin line:

```bash
./gradlew -PkotlinVersion=2.3.10 :kotlin-acyclic-plugin:test
./gradlew -PkotlinVersion=2.4.0-Beta1 :kotlin-acyclic-plugin:test
```

The same override works for the Gradle and IntelliJ modules when you need Kotlin-line-specific verification.

## Local Publishing

For local downstream testing, publish the pieces you need:

```bash
./gradlew :kotlin-acyclic:publishToMavenLocal
./gradlew -PkotlinVersion=2.3.10 :kotlin-acyclic-plugin:publishToMavenLocal
./gradlew :kotlin-acyclic-gradle-plugin:publishToMavenLocal
```

If the downstream project also needs IDE support artifacts, publish the IntelliJ plugin separately through its own packaging flow.

For local consumer wiring examples, the Gradle plugin tests are a useful reference:

- [`AcyclicGradlePluginTest.kt`](../gradle-plugin/src/test/kotlin/one/wabbit/acyclic/gradle/AcyclicGradlePluginTest.kt)

## Composite-Build Consumer Testing

Before publication, downstream Gradle repos should use both forms of composite-build wiring:

```kotlin
pluginManagement {
    includeBuild("../kotlin-acyclic")
}

includeBuild("../kotlin-acyclic")
```

Both are required:

- `pluginManagement.includeBuild(...)` resolves the Gradle plugin ID
- root `includeBuild(...)` lets Gradle substitute the annotations and compiler-plugin artifacts

This distinction is easy to forget and is worth keeping explicit in local consumer tests.

For a local downstream setup, a practical flow is:

1. use both `pluginManagement.includeBuild(...)` and root `includeBuild(...)`
2. publish the annotations library to Maven Local when the consumer needs normal dependency resolution
3. publish the Kotlin-line-specific compiler plugin variant needed by that consumer
4. use the same `-PkotlinVersion=...` line locally that the downstream build expects

## Versioning Model

Base version:

- `projectVersion` in [`gradle.properties`](../gradle.properties)

Kotlin matrix:

- `supportedKotlinVersions` in [`gradle.properties`](../gradle.properties)

Published versions:

- annotations library publishes exactly `projectVersion`
- Gradle plugin publishes exactly `projectVersion`
- compiler plugin publishes `<projectVersion>-kotlin-<kotlinVersion>`

The Kotlin suffix matters because compiler-plugin binaries are tied to the Kotlin compiler APIs they were built against.

## Release And Snapshot Workflows

GitHub workflows:

- [`.github/workflows/release-publish.yml`](../.github/workflows/release-publish.yml)
- [`.github/workflows/snapshot-publish.yml`](../.github/workflows/snapshot-publish.yml)

Release/snapshot behavior is driven from repo configuration rather than hardcoded per compiler line. The workflow fanout reads the Kotlin matrix from `supportedKotlinVersions`.

## Where To Change What

### Change rule semantics

Start in:

- [`compiler-plugin/src/main/kotlin/one/wabbit/acyclic/AcyclicFileAnalysis.kt`](../compiler-plugin/src/main/kotlin/one/wabbit/acyclic/AcyclicFileAnalysis.kt)
- [`compiler-plugin/src/main/kotlin/one/wabbit/acyclic/AcyclicDeclarationGraph.kt`](../compiler-plugin/src/main/kotlin/one/wabbit/acyclic/AcyclicDeclarationGraph.kt)
- [`compiler-plugin/src/main/kotlin/one/wabbit/acyclic/AcyclicDependencyGraph.kt`](../compiler-plugin/src/main/kotlin/one/wabbit/acyclic/AcyclicDependencyGraph.kt)
- [`compiler-plugin/src/test/kotlin/one/wabbit/acyclic/CompilerIntegrationTest.kt`](../compiler-plugin/src/test/kotlin/one/wabbit/acyclic/CompilerIntegrationTest.kt)

The compiler integration tests compile real Kotlin snippets through the actual compiler pipeline. They are the main confidence source for semantics changes.

### Change source-level controls

Start in:

- [`library/src/commonMain/kotlin/one/wabbit/acyclic/`](../library/src/commonMain/kotlin/one/wabbit/acyclic/)

Be careful with:

- annotation targets
- annotation retention
- precedence wording

These choices directly affect compiler behavior and public user docs.

### Change Gradle wiring

Start in:

- [`gradle-plugin/src/main/kotlin/one/wabbit/acyclic/gradle/AcyclicGradlePlugin.kt`](../gradle-plugin/src/main/kotlin/one/wabbit/acyclic/gradle/AcyclicGradlePlugin.kt)
- [`gradle-plugin/src/main/kotlin/one/wabbit/acyclic/gradle/AcyclicCompilerPluginVersioning.kt`](../gradle-plugin/src/main/kotlin/one/wabbit/acyclic/gradle/AcyclicCompilerPluginVersioning.kt)
- Gradle plugin unit/functional tests under [`gradle-plugin/src/test/kotlin/`](../gradle-plugin/src/test/kotlin/)

### Change IntelliJ behavior

Start in:

- [`ij-plugin/src/main/kotlin/one/wabbit/acyclic/idea/AcyclicCompilerPluginDetector.kt`](../ij-plugin/src/main/kotlin/one/wabbit/acyclic/idea/AcyclicCompilerPluginDetector.kt)
- [`ij-plugin/src/main/kotlin/one/wabbit/acyclic/idea/AcyclicIdeSupportCoordinator.kt`](../ij-plugin/src/main/kotlin/one/wabbit/acyclic/idea/AcyclicIdeSupportCoordinator.kt)
- [`ij-plugin/src/main/kotlin/one/wabbit/acyclic/idea/AcyclicIdeSupportActivity.kt`](../ij-plugin/src/main/kotlin/one/wabbit/acyclic/idea/AcyclicIdeSupportActivity.kt)

## Documentation And Backlog

The most useful repo docs during development are:

- [user-guide.md](./user-guide.md)
- [ARCHITECTURE.md](./ARCHITECTURE.md)
- [`compiler-plugin/GOAL.md`](../compiler-plugin/GOAL.md)
- [`compiler-plugin/WALKTHROUGH.md`](../compiler-plugin/WALKTHROUGH.md)
- [`compiler-plugin/PLAN.md`](../compiler-plugin/PLAN.md)

## Suggested Reading Order

If you are new to the codebase, this order usually works well:

1. [`README.md`](../README.md)
2. [user-guide.md](./user-guide.md)
3. [ARCHITECTURE.md](./ARCHITECTURE.md)
4. [`compiler-plugin/README.md`](../compiler-plugin/README.md)
5. [`compiler-plugin/PLAN.md`](../compiler-plugin/PLAN.md)
