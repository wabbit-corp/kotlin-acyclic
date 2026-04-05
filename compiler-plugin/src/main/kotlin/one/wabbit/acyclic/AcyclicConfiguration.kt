package one.wabbit.acyclic

import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.CompilerConfigurationKey

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
