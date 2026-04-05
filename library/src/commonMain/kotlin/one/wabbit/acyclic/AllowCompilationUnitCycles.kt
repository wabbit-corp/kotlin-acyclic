package one.wabbit.acyclic

/**
 * Explicitly permits a file to participate in a compilation-unit cycle.
 *
 * A compilation-unit cycle is only exempt when every file in the reported cycle opts out with this
 * annotation.
 */
@Target(AnnotationTarget.FILE)
@Retention(AnnotationRetention.SOURCE)
public annotation class AllowCompilationUnitCycles
