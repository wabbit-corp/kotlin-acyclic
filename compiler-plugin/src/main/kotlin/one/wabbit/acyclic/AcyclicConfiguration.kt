// SPDX-License-Identifier: AGPL-3.0-or-later

package one.wabbit.acyclic

import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.CompilerConfigurationKey

/**
 * Build-level enforcement mode for compilation-unit and declaration analysis.
 *
 * `OPT_IN` and `ENABLED` are interpreted relative to source annotations such as
 * `@Acyclic`, `@AllowCompilationUnitCycles`, and `@AllowMutualRecursion`.
 *
 * In the effective precedence chain, this is the build-level default. File annotations and then
 * declaration annotations may refine the behavior locally for one file or one tracked declaration.
 */
internal enum class AcyclicMode(
    val cliValue: String,
) {
    DISABLED("disabled"),
    OPT_IN("opt-in"),
    ENABLED("enabled");

    companion object {
        fun parse(value: String): AcyclicMode =
            entries.firstOrNull { it.cliValue == value }
                ?: throw IllegalArgumentException(
                    "Unknown acyclic mode '$value'. Expected one of: ${entries.joinToString { it.cliValue }}",
                )
    }
}

/**
 * Build-level source-order policy for declaration dependencies.
 *
 * This setting is the module default. Source annotations may replace it for an entire file or for
 * a single declaration. `@Acyclic(order = DEFAULT)` on a declaration resets back to this value,
 * even when the file sets its own order override.
 */
internal enum class AcyclicDeclarationOrder(
    val cliValue: String,
) {
    NONE("none"),
    TOP_DOWN("top-down"),
    BOTTOM_UP("bottom-up");

    companion object {
        fun parse(value: String): AcyclicDeclarationOrder =
            entries.firstOrNull { it.cliValue == value }
                ?: throw IllegalArgumentException(
                    "Unknown declaration order '$value'. Expected one of: ${entries.joinToString { it.cliValue }}",
                )
    }
}

/**
 * Effective compiler-plugin configuration for one compilation.
 *
 * Values originate from [AcyclicCommandLineProcessor] and are interpreted by the FIR checker
 * extension registered by [AcyclicCompilerPluginRegistrar].
 *
 * These values are the build-level defaults for the compilation. The FIR analysis then resolves the
 * final policy per file and per declaration by applying source annotations on top of this
 * configuration.
 */
internal data class AcyclicConfiguration(
    val compilationUnitMode: AcyclicMode = AcyclicMode.OPT_IN,
    val declarationMode: AcyclicMode = AcyclicMode.DISABLED,
    val declarationOrder: AcyclicDeclarationOrder = AcyclicDeclarationOrder.NONE,
)

internal object AcyclicConfigurationKeys {
    val COMPILATION_UNIT_MODE: CompilerConfigurationKey<AcyclicMode> =
        CompilerConfigurationKey.create("acyclic compilation unit mode")
    val DECLARATION_MODE: CompilerConfigurationKey<AcyclicMode> =
        CompilerConfigurationKey.create("acyclic declaration mode")
    val DECLARATION_ORDER: CompilerConfigurationKey<AcyclicDeclarationOrder> =
        CompilerConfigurationKey.create("acyclic declaration order")
}

internal fun CompilerConfiguration.toAcyclicConfiguration(): AcyclicConfiguration =
    AcyclicConfiguration(
        compilationUnitMode =
            get(AcyclicConfigurationKeys.COMPILATION_UNIT_MODE)
                ?: AcyclicConfiguration().compilationUnitMode,
        declarationMode =
            get(AcyclicConfigurationKeys.DECLARATION_MODE)
                ?: AcyclicConfiguration().declarationMode,
        declarationOrder =
            get(AcyclicConfigurationKeys.DECLARATION_ORDER)
                ?: AcyclicConfiguration().declarationOrder,
    )

internal fun AcyclicMode.isEnabled(
    explicitOptIn: Boolean,
    explicitOptOut: Boolean,
): Boolean =
    when (this) {
        AcyclicMode.DISABLED -> false
        AcyclicMode.OPT_IN -> explicitOptIn
        AcyclicMode.ENABLED -> !explicitOptOut
    }
