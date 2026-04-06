package one.wabbit.acyclic

/**
 * Opts a file or declaration into `kotlin-acyclic` analysis.
 *
 * This annotation is the main source-level control surface for the library.
 *
 * File target behavior:
 *
 * - with build-level compilation-unit checking in `OPT_IN` mode, `@file:Acyclic` opts the file
 *   into file-cycle analysis
 * - with build-level declaration checking in `OPT_IN` mode, `@file:Acyclic` opts tracked
 *   declarations in the file into declaration analysis
 * - `@file:Acyclic(order = ...)` sets the default declaration-order policy for tracked
 *   declarations in that file
 *
 * Declaration target behavior:
 *
 * - with build-level declaration checking in `OPT_IN` mode, `@Acyclic` opts the annotated
 *   declaration into declaration analysis
 * - `@Acyclic(order = ...)` can replace the file-level order rule for one declaration
 * - `@Acyclic(order = [AcyclicOrder.DEFAULT])` resets one declaration back to the build-level
 *   order policy, even when the file uses its own order override
 *
 * Declaration analysis is intentionally file-local today. Tracked declaration nodes currently
 * include top-level classes, functions, properties, and typealiases, plus the same tracked kinds
 * nested inside classes. Local declarations inside function bodies, accessors, and initializer
 * logic are not modeled as separate declaration nodes, but their resolved dependencies are still
 * attributed to the enclosing tracked declaration.
 *
 * @property order declaration-order policy for the annotated scope. On a file annotation,
 * [AcyclicOrder.DEFAULT] means "leave the file on the build-level default order policy" rather
 * than creating a file-local override.
 */
@Target(
    AnnotationTarget.FILE,
    AnnotationTarget.CLASS,
    AnnotationTarget.FUNCTION,
    AnnotationTarget.PROPERTY,
    AnnotationTarget.TYPEALIAS,
)
@Retention(AnnotationRetention.SOURCE)
public annotation class Acyclic(
    val order: AcyclicOrder = AcyclicOrder.DEFAULT,
)
