package one.wabbit.acyclic

/**
 * Opts a file or declaration into acyclicity checking.
 *
 * When declaration checking runs in `OPT_IN` mode, this annotation marks the annotated declaration,
 * or every declaration in the annotated file, as participating in declaration-cycle checks.
 *
 * The optional [order] argument can override the module-level declaration-order policy for the
 * annotated file or declaration. When used as a file annotation, that order becomes the default
 * for tracked declarations in the file unless a declaration-level annotation replaces it or resets
 * it with [AcyclicOrder.DEFAULT].
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
