// SPDX-License-Identifier: AGPL-3.0-or-later

package one.wabbit.acyclic.gradle

import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import javax.inject.Inject

/**
 * Build-level enforcement modes shared by compilation-unit and declaration checks.
 *
 * These values define the module default before file annotations and declaration annotations refine
 * the policy inside source code.
 */
enum class AcyclicEnforcementMode(
    internal val cliValue: String,
) {
    /**
     * Disable the corresponding rule family for the module.
     *
     * Source annotations still compile normally, but they do not activate the disabled rule family.
     */
    DISABLED("disabled"),

    /**
     * Require source code to opt in explicitly with annotations such as `@Acyclic`.
     *
     * This is the most conservative setting when a team wants the rule family available without
     * making it the default everywhere.
     */
    OPT_IN("opt-in"),

    /**
     * Enable the corresponding rule family by default for the module.
     *
     * Source-level opt-out annotations such as `@file:AllowCompilationUnitCycles` and
     * `@AllowMutualRecursion` may still carve out narrow exceptions where supported.
     */
    ENABLED("enabled"),
}

/**
 * Source-order policies for declaration dependencies.
 *
 * These values correspond to the public library enum [one.wabbit.acyclic.AcyclicOrder], but this
 * Gradle-facing type only models build-level defaults. Source annotations may override the file or
 * declaration policy later.
 */
enum class AcyclicDeclarationOrderMode(
    internal val cliValue: String,
) {
    /**
     * Do not enforce declaration source order.
     */
    NONE("none"),

    /**
     * Allow earlier declarations to depend on later declarations.
     */
    TOP_DOWN("top-down"),

    /**
     * Allow later declarations to depend on earlier declarations.
     */
    BOTTOM_UP("bottom-up"),
}

/**
 * Gradle DSL exposed as `acyclic {}`.
 *
 * This extension defines the build-level defaults forwarded to the compiler plugin for every Kotlin
 * compilation in the project.
 *
 * Typical usage:
 *
 * ```kotlin
 * acyclic {
 *     compilationUnits.set(AcyclicEnforcementMode.OPT_IN)
 *     declarations.set(AcyclicEnforcementMode.ENABLED)
 *     declarationOrder.set(AcyclicDeclarationOrderMode.TOP_DOWN)
 * }
 * ```
 *
 * Source annotations from `one.wabbit:kotlin-acyclic` refine these defaults later:
 *
 * - file annotations can opt files in, allow file-level cycles, or set a file-local order default
 * - declaration annotations can opt individual tracked declarations in or grant narrow recursion exceptions
 * - declaration-level `@Acyclic(order = DEFAULT)` resets one declaration back to the module-level default
 *
 * Effective precedence is:
 *
 * 1. `acyclic {}` module defaults
 * 2. file annotations
 * 3. declaration annotations
 * 4. declaration-level order overrides
 */
abstract class AcyclicGradleExtension
    @Inject
    constructor(
        objects: ObjectFactory,
    ) {
        /**
         * Module-level default for compilation-unit cycle checking.
         *
         * This setting governs semantic cycles between Kotlin source files.
         */
        val compilationUnits: Property<AcyclicEnforcementMode> =
            objects.property(AcyclicEnforcementMode::class.java)
                .convention(AcyclicEnforcementMode.OPT_IN)

        /**
         * Module-level default for declaration-level cycle checking.
         *
         * Declaration analysis is currently file-local and applies to top-level tracked declarations
         * and tracked declarations nested inside classes.
         */
        val declarations: Property<AcyclicEnforcementMode> =
            objects.property(AcyclicEnforcementMode::class.java)
                .convention(AcyclicEnforcementMode.DISABLED)

        /**
         * Module-level default for source-order enforcement of declaration dependencies.
         *
         * File annotations may override this default for tracked declarations in one file, and
         * declaration-level `@Acyclic(order = DEFAULT)` may reset an individual declaration back
         * to this value.
         */
        val declarationOrder: Property<AcyclicDeclarationOrderMode> =
            objects.property(AcyclicDeclarationOrderMode::class.java)
                .convention(AcyclicDeclarationOrderMode.NONE)
    }
