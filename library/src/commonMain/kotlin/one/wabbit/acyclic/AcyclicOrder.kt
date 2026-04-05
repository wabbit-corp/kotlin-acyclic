package one.wabbit.acyclic

/**
 * Source-order policies that can be selected by [Acyclic].
 */
public enum class AcyclicOrder {
    /**
     * Use the build-level default order policy.
     */
    DEFAULT,

    /**
     * Disable declaration-order checks for the annotated scope.
     */
    NONE,

    /**
     * Require declarations to depend only on later declarations.
     */
    TOP_DOWN,

    /**
     * Require declarations to depend only on earlier declarations.
     */
    BOTTOM_UP,
}
