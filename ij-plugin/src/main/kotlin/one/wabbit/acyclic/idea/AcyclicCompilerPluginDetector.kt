// SPDX-License-Identifier: AGPL-3.0-or-later

package one.wabbit.acyclic.idea

import one.wabbit.ijplugin.common.CompilerPluginIdeSupportDescriptor
import one.wabbit.ijplugin.common.ConfiguredCompilerPluginDetectorSupport
import one.wabbit.ijplugin.common.ConfiguredCompilerPluginIdeSupport

internal const val ACYCLIC_COMPILER_PLUGIN_MARKER = "kotlin-acyclic-plugin"
internal const val ACYCLIC_GRADLE_PLUGIN_ID = "one.wabbit.acyclic"

internal val ACYCLIC_PLUGIN_SUPPORT =
    ConfiguredCompilerPluginIdeSupport(
        descriptor =
            CompilerPluginIdeSupportDescriptor(
                loggerCategory = AcyclicIdeSupportCoordinator::class.java,
                notificationGroupId = "AcyclicIdeSupport",
                supportDisplayName = "Acyclic",
                supportDisplayNameLowercase = "acyclic",
                compilerPluginMarker = ACYCLIC_COMPILER_PLUGIN_MARKER,
                compilerPluginDisplayName = "kotlin-acyclic-plugin",
                gradlePluginId = ACYCLIC_GRADLE_PLUGIN_ID,
                externalPluginDisplayName = "kotlin-acyclic",
                analysisRestartReason = "Acyclic IDE support activation",
                enablementLogMessage = { _ ->
                    "Temporarily enabling non-bundled K2 compiler plugins for the current project session"
                },
                waitingForGradleImportTitle = "Acyclic IDE support is waiting for Gradle import",
                enabledNowPrefix = "Enabled all non-bundled K2 compiler plugins for this project session.",
                alreadyEnabledPrefix =
                    "All non-bundled K2 compiler plugins were already enabled for this project session.",
                gradleImportDetectedName = "kotlin-acyclic Gradle plugin",
            )
    )

internal object AcyclicCompilerPluginDetector :
    ConfiguredCompilerPluginDetectorSupport(ACYCLIC_PLUGIN_SUPPORT)
