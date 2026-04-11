# Troubleshooting

This page maps common compiler-plugin failures to the rule that caused them and the usual fix.

## The Plugin Reports A Declaration Cycle

Cause:

- two or more tracked declarations in the same file depend on each other
- a member initializer, delegate, default value, or local declaration creates a dependency attributed to the enclosing tracked declaration

Fix:

- split the cycle into an explicit data structure or separate abstraction
- move cross-file recursion to separate files and rely on compilation-unit policy if that is the intended boundary
- use `@AllowSelfRecursion` or `@AllowMutualRecursion` only when the recursion is intentional

Mutual-recursion opt-out works only when every declaration in the cycle opts out.

## The Plugin Reports A File Cycle

Cause:

- two or more Kotlin source files have resolved semantic dependencies that form a cycle

Fix:

- move shared contracts into a third file
- invert one dependency through an interface or constructor parameter
- use `@file:AllowCompilationUnitCycles` only when every file in the cycle is meant to participate

## Declaration Order Fails Unexpectedly

Cause:

- `TOP_DOWN` allows earlier declarations to depend on later declarations
- `BOTTOM_UP` allows later declarations to depend on earlier declarations

Fix:

- reorder the declarations
- change `acyclic.declarationOrder`
- use `@Acyclic(order = NONE)` on the narrowest declaration where order is genuinely irrelevant

If the edge is already part of a declaration cycle, the cycle diagnostic takes precedence and the redundant order diagnostic is suppressed.

## An Opt-Out Did Not Work

Cause:

- opt-outs are all-participants rules
- applying an opt-out to only one declaration or one file in a cycle is insufficient

Fix:

- annotate every cycle participant
- or restructure the cycle so the exemption is no longer necessary

## No Diagnostics Are Reported

Cause:

- `declarations` defaults to `DISABLED`
- `compilationUnits` defaults to `OPT_IN`
- `declarationOrder` defaults to `NONE`
- the Gradle plugin may not be applied to the Kotlin target you are compiling

Fix:

```kotlin
acyclic {
    compilationUnits.set(AcyclicEnforcementMode.ENABLED)
    declarations.set(AcyclicEnforcementMode.ENABLED)
    declarationOrder.set(AcyclicDeclarationOrderMode.TOP_DOWN)
}
```

For compilation-unit `OPT_IN`, add:

```kotlin
@file:one.wabbit.acyclic.Acyclic
```

## Gradle Cannot Resolve The Compiler Plugin

Cause:

- the Kotlin Gradle plugin version does not match a published `kotlin-acyclic-plugin:<baseVersion>-kotlin-<kotlinVersion>` artifact
- local composite builds include the Gradle plugin but not the library/compiler artifacts

Fix:

- use one of the supported Kotlin lines listed in [`../gradle.properties`](../gradle.properties)
- for local development, include the build in both `pluginManagement` and normal dependency resolution:

```kotlin
pluginManagement {
    includeBuild("../kotlin-acyclic")
}

includeBuild("../kotlin-acyclic")
```
