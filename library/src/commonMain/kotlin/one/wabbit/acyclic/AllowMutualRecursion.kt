// SPDX-License-Identifier: AGPL-3.0-or-later

package one.wabbit.acyclic

/**
 * Explicitly permits a declaration or file to participate in a declaration cycle.
 *
 * Important rule: a declaration cycle is exempt only when every declaration in the reported cycle
 * opts out with this annotation. Annotating only one participant does not suppress the cycle
 * diagnostic for the component.
 *
 * When used as a file annotation, it acts as a file-local default for tracked declarations in that
 * file. When used on one declaration, it applies only to that declaration.
 *
 * This annotation is broader than [AllowSelfRecursion]: it covers multi-declaration strongly
 * connected components as well as self-cycles.
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
