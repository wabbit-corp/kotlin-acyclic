// SPDX-License-Identifier: AGPL-3.0-or-later

@file:OptIn(org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi::class)

package one.wabbit.acyclic

import org.jetbrains.kotlin.compiler.plugin.AbstractCliOption
import org.jetbrains.kotlin.compiler.plugin.CliOption
import org.jetbrains.kotlin.compiler.plugin.CliOptionProcessingException
import org.jetbrains.kotlin.compiler.plugin.CommandLineProcessor
import org.jetbrains.kotlin.config.CompilerConfiguration

/**
 * Command-line option parser for the `one.wabbit.acyclic` compiler plugin.
 *
 * The processor translates raw compiler-plugin options into the [CompilerConfiguration] entries
 * consumed by [AcyclicCompilerPluginRegistrar].
 *
 * It accepts three module-level controls:
 *
 * - `compilationUnits=disabled|opt-in|enabled`
 * - `declarations=disabled|opt-in|enabled`
 * - `declarationOrder=none|top-down|bottom-up`
 *
 * These are the same values exposed through the Gradle `acyclic {}` DSL. Most consumers should
 * configure the plugin through Gradle, but the direct CLI path is useful for:
 *
 * - debugging compiler integration directly
 * - compiler integration tests
 * - non-Gradle build tooling
 * - confirming the exact raw options forwarded by the Gradle plugin
 */
class AcyclicCommandLineProcessor : CommandLineProcessor {
    private val compilationUnitsOption =
        CliOption(
            optionName = "compilationUnits",
            valueDescription = "<disabled|opt-in|enabled>",
            description = "Controls compilation-unit acyclicity enforcement.",
            required = false,
            allowMultipleOccurrences = true,
        )

    private val declarationsOption =
        CliOption(
            optionName = "declarations",
            valueDescription = "<disabled|opt-in|enabled>",
            description = "Controls declaration-level acyclicity enforcement.",
            required = false,
            allowMultipleOccurrences = true,
        )

    private val declarationOrderOption =
        CliOption(
            optionName = "declarationOrder",
            valueDescription = "<none|top-down|bottom-up>",
            description = "Controls declaration source-order enforcement.",
            required = false,
            allowMultipleOccurrences = true,
        )

    override val pluginId: String = ACYCLIC_PLUGIN_ID

    override val pluginOptions: Collection<AbstractCliOption> =
        listOf(
            compilationUnitsOption,
            declarationsOption,
            declarationOrderOption,
        )

    override fun processOption(
        option: AbstractCliOption,
        value: String,
        configuration: CompilerConfiguration,
    ) {
        when (option.optionName) {
            compilationUnitsOption.optionName ->
                configuration.put(
                    AcyclicConfigurationKeys.COMPILATION_UNIT_MODE,
                    parseMode(option.optionName, value),
                )

            declarationsOption.optionName ->
                configuration.put(
                    AcyclicConfigurationKeys.DECLARATION_MODE,
                    parseMode(option.optionName, value),
                )

            declarationOrderOption.optionName ->
                configuration.put(
                    AcyclicConfigurationKeys.DECLARATION_ORDER,
                    parseOrder(value),
                )

            else -> throw CliOptionProcessingException("Unknown option ${option.optionName}")
        }
    }

    private fun parseMode(
        optionName: String,
        value: String,
    ): AcyclicMode =
        try {
            AcyclicMode.parse(value)
        } catch (error: IllegalArgumentException) {
            throw CliOptionProcessingException("Invalid value '$value' for option '$optionName'")
        }

    private fun parseOrder(value: String): AcyclicDeclarationOrder =
        try {
            AcyclicDeclarationOrder.parse(value)
        } catch (error: IllegalArgumentException) {
            throw CliOptionProcessingException("Invalid value '$value' for option 'declarationOrder'")
        }
}
