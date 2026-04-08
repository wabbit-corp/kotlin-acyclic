// SPDX-License-Identifier: AGPL-3.0-or-later

package one.wabbit.acyclic

/**
 * Explicitly permits a file to participate in a reported compilation-unit cycle.
 *
 * This annotation applies only at file scope because compilation-unit analysis operates on whole
 * Kotlin source files rather than individual declarations.
 *
 * Important rule: a file cycle is exempt only when every file in the strongly connected component
 * opts out with this annotation. Annotating only one participant does not suppress the cycle
 * diagnostic for the component.
 *
 * This annotation does not affect declaration-level analysis.
 */
@Target(AnnotationTarget.FILE)
@Retention(AnnotationRetention.SOURCE)
public annotation class AllowCompilationUnitCycles
