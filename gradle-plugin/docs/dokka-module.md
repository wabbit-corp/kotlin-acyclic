# Module kotlin-acyclic-gradle-plugin

Typed Gradle integration for the `one.wabbit.acyclic` compiler plugin.

This module exposes the `one.wabbit.acyclic` Gradle plugin, registers the `acyclic {}` extension,
forwards typed build configuration into compiler-plugin options, and resolves the Kotlin-matched
compiler-plugin artifact automatically.

## Public API

The main public entry points are:

- [AcyclicGradlePlugin][one.wabbit.acyclic.gradle.AcyclicGradlePlugin]
  Gradle plugin entry point.
- [AcyclicGradleExtension][one.wabbit.acyclic.gradle.AcyclicGradleExtension]
  Typed `acyclic {}` DSL.
- [AcyclicEnforcementMode][one.wabbit.acyclic.gradle.AcyclicEnforcementMode]
  Build-level defaults for compilation-unit and declaration checking.
- [AcyclicDeclarationOrderMode][one.wabbit.acyclic.gradle.AcyclicDeclarationOrderMode]
  Build-level default for declaration source-order enforcement.

## What The DSL Owns

The Gradle DSL defines module-level defaults for:

- compilation-unit checking
- declaration checking
- declaration-order policy

The built-in defaults are:

- `compilationUnits = OPT_IN`
- `declarations = DISABLED`
- `declarationOrder = NONE`

Those defaults are forwarded to the compiler plugin as compilation options.

Source annotations from `one.wabbit:kotlin-acyclic` then refine them in source:

- file annotations can opt a file in, allow a file-level cycle, or override declaration order for the file
- declaration annotations can opt one tracked declaration in, grant recursion exemptions, or reset order with `@Acyclic(order = DEFAULT)`

## Version Negotiation

The Gradle plugin resolves the compiler plugin artifact as:

- `one.wabbit:kotlin-acyclic-plugin:<baseVersion>-kotlin-<kotlinVersion>`

`<kotlinVersion>` is taken from the applied Kotlin Gradle plugin, so consumer builds do not need to
pick the Kotlin-suffixed compiler artifact manually.

If no published compiler-plugin variant exists for the consumer's Kotlin version, resolution fails
fast instead of silently guessing compatibility.

## Relationship To The Other Modules

Most consumers use this module together with:

- `one.wabbit:kotlin-acyclic` for the public source annotations
- the Kotlin-line-specific compiler plugin artifact resolved automatically by this module
