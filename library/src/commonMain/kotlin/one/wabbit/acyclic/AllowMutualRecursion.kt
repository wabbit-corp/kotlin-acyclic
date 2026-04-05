package one.wabbit.acyclic

/**
 * Explicitly permits a declaration or file to participate in a mutual-recursion component.
 *
 * A mutual-recursion cycle is only exempt when every declaration in the cycle opts out with this
 * annotation.
 */
@Target(
    AnnotationTarget.FILE,
    AnnotationTarget.CLASS,
    AnnotationTarget.FUNCTION,
    AnnotationTarget.PROPERTY,
    AnnotationTarget.TYPEALIAS,
)
@Retention(AnnotationRetention.SOURCE)
public annotation class AllowMutualRecursion
