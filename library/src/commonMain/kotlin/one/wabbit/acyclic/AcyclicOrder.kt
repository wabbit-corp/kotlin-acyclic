// SPDX-License-Identifier: AGPL-3.0-or-later

package one.wabbit.acyclic

/**
 * Declaration-order policies used by [Acyclic].
 *
 * The effective order policy is resolved in layers:
 *
 * 1. the build-level default from Gradle or direct compiler-plugin options
 * 2. an optional file-level override from `@file:Acyclic`
 * 3. an optional declaration-level override from `@Acyclic`
 *
 * [DEFAULT] is meaningful only on `@Acyclic(order = ...)`: it means "reset back to the build-level
 * default" rather than "inherit the file-level override".
 */
public enum class AcyclicOrder {
    /**
     * Use the build-level declaration-order default.
     *
     * On a declaration annotation, this resets the declaration back to the module default even when
     * the file uses `@file:Acyclic(order = ...)`. On a file annotation, this leaves the file
     * without its own order override.
     */
    DEFAULT,

    /**
     * Disable declaration-order checks for the annotated scope.
     */
    NONE,

    /**
     * Apply top-down ordering.
     *
     * Earlier declarations may depend on later declarations, but later declarations may not depend
     * on earlier ones.
     */
    TOP_DOWN,

    /**
     * Apply bottom-up ordering.
     *
     * Later declarations may depend on earlier declarations, but earlier declarations may not
     * depend on later ones.
     */
    BOTTOM_UP,
}
