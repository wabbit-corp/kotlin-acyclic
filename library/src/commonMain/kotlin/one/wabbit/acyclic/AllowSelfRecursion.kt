// SPDX-License-Identifier: AGPL-3.0-or-later

package one.wabbit.acyclic

/**
 * Explicitly permits direct self-recursion for the annotated declaration or file.
 *
 * This annotation is intentionally narrow:
 *
 * - it applies only to self-recursive edges
 * - it does not permit mutual recursion between multiple declarations
 * - it does not affect compilation-unit cycle checking
 *
 * When used as a file annotation, it acts as a file-local default for tracked declarations in that
 * file. When used on one declaration, it applies only to that declaration.
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
