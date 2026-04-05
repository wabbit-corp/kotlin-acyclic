package one.wabbit.acyclic.gradle

import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import javax.inject.Inject

/**
 * Build-level enforcement modes shared by compilation-unit and declaration checks.
 */
enum class AcyclicEnforcementMode(
    internal val cliValue: String,
) {
    DISABLED("disabled"),
    OPT_IN("opt-in"),
    ENABLED("enabled"),
}

/**
 * Source-order policies for declaration dependencies.
 */
enum class AcyclicDeclarationOrderMode(
    internal val cliValue: String,
) {
    NONE("none"),
    TOP_DOWN("top-down"),
    BOTTOM_UP("bottom-up"),
}

/**
 * Gradle DSL exposed as `acyclic {}`.
 *
 * These properties define the module-level defaults that are forwarded to the compiler plugin for
 * every Kotlin compilation in the project.
 */
abstract class AcyclicGradleExtension
    @Inject
    constructor(
        objects: ObjectFactory,
    ) {
        /**
         * Controls file-level cycle checking.
         */
        val compilationUnits: Property<AcyclicEnforcementMode> =
            objects.property(AcyclicEnforcementMode::class.java)
                .convention(AcyclicEnforcementMode.OPT_IN)

        /**
         * Controls declaration-level cycle checking.
         */
        val declarations: Property<AcyclicEnforcementMode> =
            objects.property(AcyclicEnforcementMode::class.java)
                .convention(AcyclicEnforcementMode.DISABLED)

        /**
         * Controls source-order enforcement for declaration dependencies.
         */
        val declarationOrder: Property<AcyclicDeclarationOrderMode> =
            objects.property(AcyclicDeclarationOrderMode::class.java)
                .convention(AcyclicDeclarationOrderMode.NONE)
    }
