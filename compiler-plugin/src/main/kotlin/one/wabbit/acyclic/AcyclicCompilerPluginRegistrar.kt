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
 * Registers the FIR checker extension used by the acyclicity compiler plugin.
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
