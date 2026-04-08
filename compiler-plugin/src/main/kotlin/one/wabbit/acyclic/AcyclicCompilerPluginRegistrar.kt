// SPDX-License-Identifier: AGPL-3.0-or-later

@file:OptIn(org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi::class)

package one.wabbit.acyclic

import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.extensions.FirExtensionRegistrar
import org.jetbrains.kotlin.fir.extensions.FirExtensionRegistrarAdapter

@OptIn(ExperimentalCompilerApi::class)
/**
 * Compiler-plugin entry point for `one.wabbit.acyclic`.
 *
 * The registrar advertises K2/FIR support and installs the FIR checker extension that performs
 * all acyclicity analysis for the current compilation:
 *
 * - compilation-unit cycle detection
 * - declaration cycle detection
 * - declaration source-order enforcement
 *
 * In normal builds this class is loaded through the companion Gradle plugin. Direct compiler
 * integration is still possible by passing the plugin jar with `-Xplugin` and supplying the
 * `plugin:one.wabbit.acyclic:*` options parsed by [AcyclicCommandLineProcessor].
 *
 * The installed FIR extension interprets compiler-plugin options as build-level defaults, then
 * refines them with source annotations from `one.wabbit:kotlin-acyclic` on a file-by-file and
 * declaration-by-declaration basis.
 */
class AcyclicCompilerPluginRegistrar : CompilerPluginRegistrar() {
    override val pluginId: String = ACYCLIC_PLUGIN_ID
    override val supportsK2: Boolean = true

    override fun ExtensionStorage.registerExtensions(configuration: CompilerConfiguration) {
        FirExtensionRegistrarAdapter.registerExtension(
            AcyclicFirExtensionRegistrar(configuration.toAcyclicConfiguration()),
        )
    }
}

private class AcyclicFirExtensionRegistrar(
    private val configuration: AcyclicConfiguration,
) : FirExtensionRegistrar() {
    override fun ExtensionRegistrarContext.configurePlugin() {
        +{ session: FirSession ->
            AcyclicCheckersExtension(session, configuration)
        }
    }
}
