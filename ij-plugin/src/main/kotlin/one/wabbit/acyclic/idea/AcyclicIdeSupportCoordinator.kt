// SPDX-License-Identifier: AGPL-3.0-or-later

package one.wabbit.acyclic.idea

import com.intellij.ide.trustedProjects.TrustedProjects
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import java.util.MissingResourceException

internal data class AcyclicIdeSupportResult(
    val scan: AcyclicCompilerPluginScan,
    val projectTrusted: Boolean,
    val registryAlreadyEnabledForExternalPlugins: Boolean,
    val registryUpdated: Boolean,
    val activationState: AcyclicIdeSupportActivationState,
)

internal enum class AcyclicIdeSupportActivationState {
    NOT_NEEDED,
    WAITING_FOR_TRUST,
    ALREADY_ENABLED,
    ENABLED_NOW,
    FAILED_TO_ENABLE,
}

internal object AcyclicIdeSupportCoordinator {
    private val logger = Logger.getInstance(AcyclicIdeSupportCoordinator::class.java)

    fun enableIfNeeded(project: Project, userInitiated: Boolean): AcyclicIdeSupportResult {
        val scan = AcyclicCompilerPluginDetector.scan(project)
        val trusted = TrustedProjects.isProjectTrusted(project)
        val registryAllowsOnlyBundledPlugins = Registry.`is`(EXTERNAL_K2_COMPILER_PLUGINS_REGISTRY_KEY, false)
        return enableIfNeeded(
            scan = scan,
            projectTrusted = trusted,
            userInitiated = userInitiated,
            registryAllowsOnlyBundledPlugins = registryAllowsOnlyBundledPlugins,
            enableExternalPluginsForProjectSession = {
                Registry.get(EXTERNAL_K2_COMPILER_PLUGINS_REGISTRY_KEY).setValue(false, project)
            },
            notify = { type, title, content ->
                notify(
                    project = project,
                    type = type,
                    title = title,
                    content = content,
                )
            },
        )
    }

    internal fun enableIfNeeded(
        scan: AcyclicCompilerPluginScan,
        projectTrusted: Boolean,
        userInitiated: Boolean,
        registryAllowsOnlyBundledPlugins: Boolean,
        enableExternalPluginsForProjectSession: () -> Unit,
        notify: (NotificationType, String, String) -> Unit,
    ): AcyclicIdeSupportResult {
        if (!scan.hasMatches) {
            if (userInitiated) {
                notify(
                    NotificationType.INFORMATION,
                    "Acyclic IDE support",
                        "No imported Kotlin compiler arguments or Gradle build files reference kotlin-acyclic-plugin or one.wabbit.acyclic.",
                )
            }
            return AcyclicIdeSupportResult(
                scan = scan,
                projectTrusted = projectTrusted,
                registryAlreadyEnabledForExternalPlugins = !registryAllowsOnlyBundledPlugins,
                registryUpdated = false,
                activationState = AcyclicIdeSupportActivationState.NOT_NEEDED,
            )
        }

        if (!projectTrusted) {
            notify(
                NotificationType.WARNING,
                "Acyclic IDE support is waiting for trust",
                    "kotlin-acyclic-plugin was detected, but IntelliJ will not load external compiler plugins until the project is trusted.",
            )
            return AcyclicIdeSupportResult(
                scan = scan,
                projectTrusted = false,
                registryAlreadyEnabledForExternalPlugins = !registryAllowsOnlyBundledPlugins,
                registryUpdated = false,
                activationState = AcyclicIdeSupportActivationState.WAITING_FOR_TRUST,
            )
        }

        val activationState =
            if (!registryAllowsOnlyBundledPlugins) {
                AcyclicIdeSupportActivationState.ALREADY_ENABLED
            } else {
                logger.info(
                    "Temporarily enabling non-bundled K2 compiler plugins for the current project session",
                )
                if (enableExternalPluginsForProjectSessionSafely(enableExternalPluginsForProjectSession)) {
                    AcyclicIdeSupportActivationState.ENABLED_NOW
                } else {
                    AcyclicIdeSupportActivationState.FAILED_TO_ENABLE
                }
            }
        val registryUpdated = activationState == AcyclicIdeSupportActivationState.ENABLED_NOW

        when {
            activationState == AcyclicIdeSupportActivationState.ENABLED_NOW ||
                (activationState == AcyclicIdeSupportActivationState.ALREADY_ENABLED && userInitiated) ->
                notify(
                    NotificationType.INFORMATION,
                    "Acyclic IDE support is active",
                    buildEnabledMessage(scan, activationState),
                )
            activationState == AcyclicIdeSupportActivationState.FAILED_TO_ENABLE && userInitiated ->
                notify(
                    NotificationType.WARNING,
                    "Acyclic IDE support could not be enabled",
                    buildFailedEnablementMessage(scan),
                )
        }

        return AcyclicIdeSupportResult(
            scan = scan,
            projectTrusted = true,
            registryAlreadyEnabledForExternalPlugins = activationState == AcyclicIdeSupportActivationState.ALREADY_ENABLED,
            registryUpdated = registryUpdated,
            activationState = activationState,
        )
    }

    internal fun buildEnabledMessage(
        scan: AcyclicCompilerPluginScan,
        activationState: AcyclicIdeSupportActivationState,
    ): String {
        val owners =
            buildList {
                scan.projectLevelMatch?.let { add("project settings") }
                addAll(scan.moduleMatches.map { match -> "module ${match.ownerName}" })
                if (scan.gradleBuildFiles.isNotEmpty()) {
                    add("Gradle build files")
                }
            }
        val prefix =
            when (activationState) {
                AcyclicIdeSupportActivationState.ENABLED_NOW ->
                    "Enabled all non-bundled K2 compiler plugins for this project session."
                AcyclicIdeSupportActivationState.ALREADY_ENABLED ->
                    "All non-bundled K2 compiler plugins were already enabled for this project session."
                else ->
                    error("buildEnabledMessage only supports successful activation states")
            }
        val ownerSummary = owners.joinToString(", ")
        return "$prefix Detected kotlin-acyclic-plugin in $ownerSummary."
    }

    internal fun buildFailedEnablementMessage(scan: AcyclicCompilerPluginScan): String {
        val owners =
            buildList {
                scan.projectLevelMatch?.let { add("project settings") }
                addAll(scan.moduleMatches.map { match -> "module ${match.ownerName}" })
                if (scan.gradleBuildFiles.isNotEmpty()) {
                    add("Gradle build files")
                }
            }.joinToString(", ")
        return "Detected kotlin-acyclic-plugin in $owners, but IntelliJ could not enable all non-bundled K2 compiler plugins for this project session."
    }

    private fun notify(
        project: Project,
        type: NotificationType,
        title: String,
        content: String,
    ) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("AcyclicIdeSupport")
            .createNotification(title, content, type)
            .notify(project)
    }

    private fun enableExternalPluginsForProjectSessionSafely(action: () -> Unit): Boolean =
        try {
            action()
            true
        } catch (error: MissingResourceException) {
            logger.warn(
                "IntelliJ registry key $EXTERNAL_K2_COMPILER_PLUGINS_REGISTRY_KEY is unavailable; skipping kotlin-acyclic external compiler-plugin enablement.",
                error,
            )
            false
        }
}
