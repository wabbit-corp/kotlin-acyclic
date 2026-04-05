# Plan

This file tracks the implementation plan for `kotlin-acyclic-plugin`.

It complements `GOAL.md`:
- `GOAL.md` describes the intended semantics and design constraints
- `PLAN.md` describes the delivery roadmap

## Core Compiler Plugin

- [ ] Stabilize compilation-unit acyclicity on real projects.
- [ ] Stabilize declaration acyclicity on real projects.
- [ ] Stabilize declaration-order checking in both `TOP_DOWN` and `BOTTOM_UP` modes.
- [ ] Tighten and document the exact scoping exemptions that do not count as dependency.
- [ ] Keep diagnostics precise, readable, and attached to useful source locations.
- [ ] Expand regression coverage around real-world patterns found in local repositories.

## Control Surface

- [ ] Keep Gradle plugin defaults and compiler option defaults aligned.
- [ ] Keep file-level and declaration-level annotation overrides narrow and explicit.
- [ ] Document precedence clearly across Gradle defaults, compiler options, file annotations, and declaration annotations.
- [ ] Verify that each escape hatch is covered by focused tests.

## Documentation

- [ ] Keep `README.md` current for normal users of the compiler plugin.
- [ ] Keep Dokka/KDoc coverage strong for public and review-relevant internal APIs.
- [ ] Keep `WALKTHROUGH.md` useful as a manual review guide for the whole stack.
- [ ] Add more worked examples showing legal scoping, illegal recursion, and explicit opt-outs.

## IDE Integration

- [ ] Add red squiggles before a build runs.
- [ ] Add quick fixes / intentions for adding allow annotations.
- [ ] Add IDE-specific visualization of dependency/order graphs.
- [ ] Add inspections that match IntelliJ’s own on-the-fly analysis model.

## Validation

- [ ] Keep graph-level tests strong for SCC detection and order rules.
- [ ] Keep compiler integration tests strong for semantic FIR-based behavior.
- [ ] Continue testing against a rotating sample of local `../` projects.
- [ ] Use failures from real projects to drive new regression tests before changing analysis rules.
