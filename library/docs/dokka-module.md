# Module kotlin-acyclic

Source-retained annotations and enums for expressing structural acyclicity policy in Kotlin source.

This module is the public source-level API for the `kotlin-acyclic` project family. It contains no
compiler implementation by itself. Instead, it lets source code:

- opt files or declarations into analysis
- override declaration-order policy
- carve out narrow, explicit recursion exceptions

## Main Controls

- [Acyclic][one.wabbit.acyclic.Acyclic]
  Opts files or declarations into checking and can override declaration-order policy.
- [AllowCompilationUnitCycles][one.wabbit.acyclic.AllowCompilationUnitCycles]
  Permits a file-level cycle only when every file in the cycle opts out.
- [AllowSelfRecursion][one.wabbit.acyclic.AllowSelfRecursion]
  Permits direct self-recursion for the annotated declaration or file.
- [AllowMutualRecursion][one.wabbit.acyclic.AllowMutualRecursion]
  Permits a declaration cycle only when every declaration in the cycle opts out.
- [AcyclicOrder][one.wabbit.acyclic.AcyclicOrder]
  Describes declaration-order policies such as `TOP_DOWN` and `BOTTOM_UP`.

## How These Controls Compose

Within one compilation, source annotations refine build-level defaults supplied by the Gradle plugin
or direct compiler-plugin options.

Effective precedence is:

1. build-level defaults
2. file annotations
3. declaration annotations
4. declaration-level order overrides

For declaration order specifically:

- the build config establishes the module default
- `@file:Acyclic(order = ...)` can override that default for tracked declarations in one file
- `@Acyclic(order = DEFAULT)` resets one declaration back to the module default

## Current Declaration Scope

Declaration analysis is intentionally file-local today.

- tracked declaration nodes include top-level classes, functions, properties, and typealiases
- the same tracked kinds nested inside classes also participate
- local declarations are not separate declaration nodes
- dependencies discovered inside local declarations are attributed to the enclosing tracked declaration

## Retention And Runtime Behavior

All annotations use source retention because they exist only to guide compile-time analysis. They do
not need to remain in runtime-visible metadata.
