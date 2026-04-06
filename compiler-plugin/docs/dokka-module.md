# Module kotlin-acyclic-plugin

K2/FIR compiler plugin for enforcing structural acyclicity in Kotlin source.

`kotlin-acyclic-plugin` analyzes resolved FIR rather than raw syntax. That lets it report
acyclicity errors from semantic dependencies such as resolved references, supertypes, constructor
delegation, initializer logic, generic type arguments, and function types.

## Rule Families

The plugin exposes three related checks:

- compilation-unit acyclicity
  Detects cycles between `.kt` files in the same compilation.
- declaration acyclicity
  Detects cycles between tracked declarations in the same file.
- declaration order
  Requires the declaration dependency graph to respect either top-down or bottom-up source order.

## Public Entry Points

The public compiler-plugin entry points are:

- [AcyclicCommandLineProcessor][one.wabbit.acyclic.AcyclicCommandLineProcessor]
  Parses raw `plugin:one.wabbit.acyclic:*` options into compiler configuration.
- [AcyclicCompilerPluginRegistrar][one.wabbit.acyclic.AcyclicCompilerPluginRegistrar]
  Registers the FIR checker extension used during K2 compilation.

Most builds should apply the companion Gradle plugin instead of invoking these entry points
directly. The Gradle plugin resolves the Kotlin-matched compiler-plugin artifact automatically and
forwards the typed `acyclic {}` DSL as compiler-plugin options.

## Compiler Options

The compiler plugin accepts three module-level options:

- `compilationUnits=disabled|opt-in|enabled`
- `declarations=disabled|opt-in|enabled`
- `declarationOrder=none|top-down|bottom-up`

These values become the defaults for the current module. Source annotations in
`one.wabbit:kotlin-acyclic` can opt files or declarations in, override declaration-order policy, or
allow narrow recursion exemptions.

## Analysis Boundaries

The current implementation makes a few intentional scope choices:

- compilation-unit analysis operates across the whole compilation
- declaration analysis is file-local
- tracked declaration nodes include top-level tracked declarations and tracked declarations nested inside classes
- local declarations are folded into the enclosing tracked declaration instead of becoming separate nodes
- lexical containment is distinguished from semantic dependency for shapes such as self return types and enclosing-type references

## Effective Precedence

The compiler plugin resolves control state in this order:

1. compiler-plugin options define the build-level defaults for the compilation
2. file annotations refine those defaults for one file
3. declaration annotations refine them again for one tracked declaration

For declaration order:

- the module default comes from `declarationOrder`
- `@file:Acyclic(order = ...)` overrides that default inside one file
- `@Acyclic(order = DEFAULT)` resets one declaration back to the module default

If an edge is already part of a reported declaration cycle, the cycle diagnostic wins and the
redundant declaration-order diagnostic for that same edge is suppressed.

## Artifact Layout

The compiler plugin is published as a Kotlin-line-specific artifact:

- `one.wabbit:kotlin-acyclic-plugin:<baseVersion>-kotlin-<kotlinVersion>`

That versioning keeps the compiler-plugin binary aligned with the Kotlin compiler API it was built
against. The annotations library and Gradle plugin keep the plain project version.

## Typical Usage

Most consumers should not wire this module directly. Use the companion Gradle plugin unless you are:

- integrating with a non-Gradle build
- debugging compiler-plugin behavior
- writing compiler integration tests
- inspecting the raw plugin API surface
