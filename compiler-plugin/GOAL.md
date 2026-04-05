# Goal

`kotlin-acyclic-plugin` is intended to enforce explicit, structural acyclicity constraints on Kotlin code.

The project is not only about preventing cycles between files. It is also intended to prevent recursive definition structure within files and to force a clear dependency order between definitions.

## Primary objective

The plugin should make Kotlin codebases easier to understand by rejecting hidden recursive structure unless that recursion is explicitly allowed.

In practice, that means enforcing three distinct properties:
- compilation-unit acyclicity
- declaration acyclicity
- declaration order

## Compilation-unit acyclicity

Kotlin compilation units, in practice `.kt` files, should form a DAG.

This must hold regardless of package boundaries. If `foo/A.kt` depends on definitions in `foo/B.kt` and `foo/B.kt` depends back on definitions in `foo/A.kt`, that is a cycle and should be rejected.

This is file-level acyclicity, not package-level acyclicity.

## Declaration acyclicity

Top-level declarations within a compilation unit should also form a DAG.

This includes cycles:
- between values
- between types
- from types to values
- from values to types

Mutual recursion is not considered acceptable by default merely because the declarations are in the same file or in the same lexical region.

## Declaration order

Declaration dependencies should also respect source order.

The intended effect is that definitions read in a single direction rather than requiring arbitrary jumping around the file.

One order policy should be enforced consistently:
- top-down order, where declarations may depend only on later declarations, or
- bottom-up order, where declarations may depend only on earlier declarations

This is a stricter policy layered on top of declaration acyclicity. Declaration acyclicity says the declaration graph must be a DAG. Declaration order says the DAG must also align with source order in a chosen direction.

The plugin should make this policy explicit rather than leaving declaration order informal.

## Scoping is not dependency

Lexical containment alone must not count as a dependency edge.

In particular, these should be allowed:
- `sealed interface Foo { class Boo : Foo }`
- `class Foo { fun foo(): Foo }`

The reason is that:
- containing a declaration is not the same as depending on that declaration
- naming the containing type from within its own scoped body is not the kind of recursion this project is trying to forbid

This rule exists to distinguish genuine semantic dependency from mere scope structure.

## Annotation control

The system should support both opt-in and opt-out control.

That control should exist at two levels:
- file level
- individual declaration level

It should also be possible to control:
- compilation-unit acyclicity independently
- declaration acyclicity independently
- declaration-order enforcement independently

This allows teams to:
- enable strictness incrementally
- carve out narrow exceptions
- make intentional cycles explicit

## Configuration surface

The enforcement model should be easy to configure from build tooling as well as from source.

Configuration should exist at these levels:
- Gradle plugin extension / DSL
- compiler plugin options
- file annotations
- declaration annotations

The intended precedence is:
- build-level defaults establish the normal policy
- compiler options can set or override those defaults for a compilation
- file annotations can override the default order policy or enable/disable checks locally
- declaration annotations can carve out narrow local exceptions

In particular, it should be possible to configure:
- whether compilation-unit acyclicity is enabled
- whether declaration acyclicity is enabled
- whether declaration-order enforcement is enabled
- which declaration order is the default: top-down or bottom-up
- whether a given file overrides the default order

## Explicit exceptions

Cycles or order violations should be allowed only when explicitly authorized by annotation.

The default stance should be:
- recursion is forbidden
- exceptions must be local and visible in source

## Non-goals

The project is not trying to ban every form of self-reference in Kotlin.

In particular, it is not a goal to treat lexical scoping, nesting, or nominal self-reference as dependency edges when they do not create real recursive definition structure.

## Design constraints

- Distinguish clearly between compilation-unit acyclicity, declaration acyclicity, and declaration order.
- Treat semantic dependency as the thing being constrained, not package membership or lexical containment.
- Keep exceptions explicit and narrow.
- Make the enforcement policy easy to control from Gradle and compiler options.
- Allow source-level overrides, but keep them visible and local.
- Prefer simple, explainable rules over clever but opaque analyses.
- Produce diagnostics that make the violated dependency structure obvious.
