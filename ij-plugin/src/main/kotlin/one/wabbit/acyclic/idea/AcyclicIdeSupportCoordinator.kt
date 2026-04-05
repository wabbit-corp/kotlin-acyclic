package one.wabbit.acyclic.idea

import com.intellij.ide.trustedProjects.TrustedProjects
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry

internal data class AcyclicIdeSupportResult(
    val scan: AcyclicCompilerPluginScan,
    val projectTrusted: Boolean,
    val registryAlreadyEnabledForExternalPlugins: Boolean,
    val registryUpdated: Boolean,
)

internal object AcyclicIdeSupportCoordinator {
    private val logger = Logger.getInstance(AcyclicIdeSupportCoordinator::class.java)

    fun enableIfNeeded(project: Project, userInitiated: Boolean): AcyclicIdeSupportResult {
        val scan = AcyclicCompilerPluginDetector.scan(project)
        val trusted = TrustedProjects.isProjectTrusted(project)
        val registryValue = Registry.get(EXTERNAL_K2_COMPILER_PLUGINS_REGISTRY_KEY)
        val registryAllowsOnlyBundledPlugins = registryValue.asBoolean()
        if (!scan.hasMatches) {
            if (userInitiated) {
                notify(
                    project = project,
                    type = NotificationType.INFORMATION,
                    title = "Acyclic IDE support",
                    content =
                        "No imported Kotlin compiler arguments or Gradle build files reference kotlin-acyclic-plugin or one.wabbit.acyclic.",
                )
            }
            return AcyclicIdeSupportResult(
                scan = scan,
                projectTrusted = trusted,
                registryAlreadyEnabledForExternalPlugins = !registryAllowsOnlyBundledPlugins,
                registryUpdated = false,
            )
        }

        if (!trusted) {
            notify(
                project = project,
                type = NotificationType.WARNING,
                title = "Acyclic IDE support is waiting for trust",
                content =
                    "kotlin-acyclic-plugin was detected, but IntelliJ will not load external compiler plugins until the project is trusted.",
            )
            return AcyclicIdeSupportResult(
                scan = scan,
                projectTrusted = false,
                registryAlreadyEnabledForExternalPlugins = !registryAllowsOnlyBundledPlugins,
                registryUpdated = false,
            )
        }

        var registryUpdated = false
        if (registryAllowsOnlyBundledPlugins) {
            logger.info(
                "Temporarily enabling non-bundled K2 compiler plugins for project ${project.name}",
            )
            registryValue.setValue(false, project)
            registryUpdated = true
        }

        if (registryUpdated || userInitiated) {
            notify(
                project = project,
                type = NotificationType.INFORMATION,
                title = "Acyclic IDE support is active",
                content = buildEnabledMessage(scan, registryUpdated),
            )
        }

        return AcyclicIdeSupportResult(
            scan = scan,
            projectTrusted = true,
            registryAlreadyEnabledForExternalPlugins = !registryAllowsOnlyBundledPlugins,
            registryUpdated = registryUpdated,
        )
    }

    internal fun buildEnabledMessage(
        scan: AcyclicCompilerPluginScan,
        registryUpdated: Boolean,
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
            if (registryUpdated) {
                "Enabled all non-bundled K2 compiler plugins for this project session."
            } else {
                "All non-bundled K2 compiler plugins were already enabled for this project session."
            }
        val ownerSummary = owners.joinToString(", ")
        return "$prefix Detected kotlin-acyclic-plugin in $ownerSummary."
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
}
