package one.wabbit.acyclic

/**
 * Explicitly permits direct self-recursion for the annotated declaration or file.
 */
@Target(
    AnnotationTarget.FILE,
    AnnotationTarget.CLASS,
    AnnotationTarget.FUNCTION,
    AnnotationTarget.PROPERTY,
    AnnotationTarget.TYPEALIAS,
)
@Retention(AnnotationRetention.SOURCE)
public annotation class AllowSelfRecursion
