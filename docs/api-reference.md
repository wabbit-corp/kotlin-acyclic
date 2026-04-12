# API Reference

This page is the stable map for the public surface. The exhaustive API reference is generated from source with Dokka so it stays tied to the exact project version.

Published API docs:

- `https://wabbit-corp.github.io/kotlin-acyclic/`

Generate local API docs from the repository root:

```bash
./gradlew :kotlin-acyclic:dokkaGeneratePublicationHtml
./gradlew :kotlin-acyclic-gradle-plugin:dokkaGeneratePublicationHtml
./gradlew :kotlin-acyclic-plugin:dokkaGeneratePublicationHtml
./gradlew :kotlin-acyclic-ij-plugin:dokkaGeneratePublicationHtml
```

## Public Annotation API

The annotation API lives in `one.wabbit:kotlin-acyclic`.

- `@Acyclic` opts a file or declaration into checks and can set declaration-order policy.
- `@AllowCompilationUnitCycles` permits a file-level cycle only when every file in that cycle opts out.
- `@AllowSelfRecursion` permits direct self-recursion for the annotated file or declaration.
- `@AllowMutualRecursion` permits a declaration cycle only when every declaration in that cycle opts out.
- `AcyclicOrder` defines declaration-order modes used by `@Acyclic`.

All annotations are source-retained. They are consumed by the compiler plugin and do not affect runtime metadata.

## Gradle DSL

The Gradle DSL is exposed by plugin id `one.wabbit.acyclic`.

```kotlin
acyclic {
    compilationUnits.set(AcyclicEnforcementMode.OPT_IN)
    declarations.set(AcyclicEnforcementMode.DISABLED)
    declarationOrder.set(AcyclicDeclarationOrderMode.NONE)
}
```

The main DSL types are:

- `AcyclicEnforcementMode`: `DISABLED`, `OPT_IN`, `ENABLED`
- `AcyclicDeclarationOrderMode`: `NONE`, `TOP_DOWN`, `BOTTOM_UP`

The Gradle plugin resolves the compiler-plugin artifact variant that matches the applied Kotlin Gradle plugin version.
It does not add the annotations library automatically; builds should declare
`one.wabbit:kotlin-acyclic` explicitly where source annotations are used.

## Compiler Plugin Options

Direct compiler-plugin users can pass:

```text
-P plugin:one.wabbit.acyclic:compilationUnits=disabled|opt-in|enabled
-P plugin:one.wabbit.acyclic:declarations=disabled|opt-in|enabled
-P plugin:one.wabbit.acyclic:declarationOrder=none|top-down|bottom-up
```

If source uses `one.wabbit.acyclic.*`, the annotation library must still be on the compilation classpath.
