# Plan

This file tracks the implementation roadmap for `kotlin-acyclic-plugin`.

It complements `GOAL.md`:
- `GOAL.md` defines the intended semantics and design constraints.
- `PLAN.md` tracks the concrete follow-up work, investigations, and delivery order.

This plan is intentionally biased toward:
- correctness before feature expansion
- regression tests before semantic changes
- explicit product decisions instead of accidental behavior

## Working Rules

- [ ] Add or tighten regression tests before changing analysis semantics.
- [ ] Prefer proving a behavior with focused compiler integration tests over relying on FIR intuition.
- [ ] Keep build-level defaults, source annotations, and diagnostics aligned whenever semantics move.
- [ ] Update README/KDoc/WALKTHROUGH whenever a user-visible boundary or override rule changes.

## Phase 1: Semantic Correctness

### 1. Compilation-unit analysis

- [x] Keep compilation-unit dependency collection semantic rather than import-string based.
- [x] Continue validating cross-file edges through resolved references, qualifiers, generic type arguments, and function types.
- [x] Keep file-cycle exemptions narrow:
  - [x] a cycle is exempt only when every participating file opts out
  - [x] diagnostics still appear for non-exempt participants
  - [ ] Keep duplicate/stale file-cycle reporting under control as graphs evolve during one FIR session.
- [ ] Expand real-project regressions for same-package cycles, indirection through type positions, and order-independent reporting.

### 2. Declaration analysis

- [x] Stabilize declaration-cycle detection for:
  - [x] top-level declarations
  - [x] class members
  - [x] typealias declarations
  - [x] type-to-value and value-to-type recursion
- [x] Keep class construction semantics complete:
  - [x] `init {}` blocks
  - [x] member property initializers
  - [x] member delegates
  - [x] secondary constructor bodies
  - [x] constructor delegation
- [x] Keep scoping-only patterns legal:
  - [x] sealed nested implementations
  - [x] return types referencing the enclosing type
  - [x] constructor calls to the enclosing class
  - [x] lexical containment without semantic dependency
- [x] Investigate local tracked-declaration cut points explicitly.
  - [x] Confirm with focused tests and, if needed, FIR-level probes whether local `fun`, local `val`, and local classes are folded into the enclosing declaration dependency set or only appear to work because of current visitation behavior.
  - [x] Add tests that isolate the cut-point rule itself instead of only asserting end-to-end errors.
  - [x] Pin the proven behavior with stronger regressions:
    - [x] uninvoked local function bodies still contribute enclosing declaration dependencies
    - [x] unused local class member bodies still contribute enclosing declaration dependencies
    - [x] local declarations themselves remain outside declaration-node scope
  - [x] Record the conclusion from the investigation: no traversal change is currently needed because the existing analyzer already preserves enclosing dependencies through the tested local-declaration shapes.
- [ ] Decide and document the declaration-graph scope.
  - [x] Decide whether declaration analysis is intentionally same-file-only.
  - [x] If same-file-only is the product boundary, say so plainly in README, KDoc, and any wording that currently reads broader than the implementation.
  - [ ] If module-wide declaration analysis is desired later, sketch the required redesign first:
    - [ ] cross-file node accumulation
    - [ ] module-wide graph lifetime
    - [ ] exemption semantics across files
    - [ ] diagnostic attachment strategy
  - [x] Add focused regressions for cross-file declaration recursion so the chosen boundary is test-pinned.

### 3. Declaration order

- [x] Keep `TOP_DOWN` and `BOTTOM_UP` evaluation correct per source declaration, not per file.
- [x] Keep file-level and declaration-level order overrides working together:
  - [x] absent override
  - [x] explicit concrete override
  - [x] explicit `DEFAULT` reset
- [x] Re-evaluate whether order diagnostics should be emitted for edges inside declaration SCCs that already produce cycle diagnostics.
  - [x] Decide whether dual reporting is useful signal or redundant noise.
  - [x] If suppression is preferred, suppress order diagnostics for edges internal to any reported cycle, not only allowed mutual-recursion components.
  - [x] Add regressions for whichever policy is chosen.
- [x] Keep diagnostic wording precise enough that users can tell whether they hit:
  - [x] a cycle
  - [x] an order violation
  - [x] both

## Phase 2: Control Surface And API Consistency

### 4. Build/tool configuration

- [x] Keep Gradle plugin defaults and compiler option defaults aligned.
- [x] Keep the Gradle DSL, CLI values, and internal configuration parsing in sync.
- [x] Fail fast on unsupported Kotlin compiler lines instead of silently guessing compatibility.
- [x] Keep compiler-plugin artifact version negotiation explicit and test-covered.

### 5. Annotation semantics

- [ ] Keep file-level and declaration-level opt-in/opt-out narrow and explicit.
- [x] Keep escape hatches semantically strict:
  - [x] self recursion only for explicit self-recursion
  - [x] mutual recursion only when every declaration in the SCC opts out
  - [x] compilation-unit cycle allowance only when every file in the cycle opts out
- [x] Verify every public annotation target is supported by the checker implementation.
- [x] Keep public docs aligned with actual precedence:
  - [x] Gradle defaults
  - [x] compiler options
  - [x] file annotations
  - [x] declaration annotations

### 6. Vocabulary cleanup

- [ ] Reduce duplicated mode/order vocabulary across modules.
  - [ ] Inventory `AcyclicOrder`, `AcyclicDeclarationOrder`, `AcyclicDeclarationOrderMode`, and `AcyclicOrderSelection`.
  - [ ] Inventory the corresponding enforcement-mode types.
  - [ ] Decide whether to unify around shared string-backed models or generate translation glue.
  - [ ] Add drift tests so a new enum case cannot land in one layer without failing another layer’s parser or mapper.

## Phase 3: Documentation And Reviewer Support

### 7. User-facing docs

- [x] Keep the root README current for Maven Central consumers.
- [x] Keep the compiler-plugin README honest about current boundaries and supported Kotlin lines.
- [x] Keep the Gradle-plugin README aligned with actual installation and version negotiation behavior.
- [x] Keep the library README aligned with the real annotation semantics and targets.
- [x] Add worked examples for:
  - [x] legal scoping
  - [x] illegal declaration recursion
  - [x] illegal file cycles
  - [x] order violations
  - [x] explicit opt-outs

### 8. API docs and review docs

- [x] Keep Dokka/KDoc strong for the public compiler entry points and the review-relevant internal model.
- [x] Keep `WALKTHROUGH.md` aligned with the current repo/module layout.
- [x] Document the final answer for:
  - [x] local declaration handling
  - [x] declaration graph scope
  - [x] order-diagnostic policy in cyclic graphs

## Phase 4: IntelliJ Integration

### 9. Detection hardening

- [x] Keep compiler-plugin path detection strict enough to avoid unrelated jars.
- [x] Harden Gradle and version-catalog detection further.
  - [x] Strip TOML `#` comments before matching `.versions.toml` entries.
  - [x] Add regressions for commented version-catalog plugin lines and commented artifact coordinates.
  - [ ] Support common Groovy Gradle forms:
    - [x] `id 'one.wabbit.acyclic'`
    - [x] `apply plugin: 'one.wabbit.acyclic'`
    - [x] `classpath 'one.wabbit:kotlin-acyclic-gradle-plugin:...'`
    - [x] map-notation dependency declarations
  - [x] Keep false positives low for comments, doc snippets, and random string literals.

### 10. Activation lifecycle

- [ ] Keep startup detection working for already-imported trusted projects.
- [x] Re-run detection after project trust transitions.
- [x] Re-run detection after Gradle/Kotlin import completes so post-startup compiler configuration is picked up automatically.
- [x] Keep the manual refresh action as a fallback, not the primary recovery path.
- [x] Keep activation notifications truthful:
  - [x] already enabled
  - [x] enabled now
  - [x] failed to enable
- [x] Add integration-style tests around activation timing and failure paths.

### 11. Future IDE features

- [ ] Add on-the-fly diagnostics before a build runs.
- [ ] Add quick fixes or intentions for common allow-annotation workflows.
- [ ] Add IDE-native graph visualization for declaration/file dependencies.
- [ ] Add inspections that match the compiler plugin’s semantics closely enough to avoid contradictory IDE/build feedback.

## Phase 5: Validation And Release Confidence

### 12. Test suite quality

- [x] Keep graph-level tests strong for:
  - [x] SCC detection
  - [x] cycle rendering
  - [x] order-direction rules
  - [x] deep non-recursive graphs that would previously risk stack overflow
- [ ] Keep compiler integration tests strong for semantic FIR behavior:
  - [x] types
  - [x] expressions
  - [x] constructor delegation
  - [x] local declarations
  - [x] nested classes
  - [x] typealiases
  - [x] overrides and exemptions
- [ ] Use failures from real projects to drive new regressions before changing analysis rules.

### 13. Real-project proving

- [ ] Continue testing against a rotating sample of local `../` repositories.
- [ ] Track which failures revealed:
  - [ ] genuine plugin bugs
  - [ ] intentional policy mismatches
  - [ ] docs gaps
  - [ ] IDE integration gaps
- [ ] Prefer shipping only semantics that have survived at least one real-project proving round.

## Current Priorities
