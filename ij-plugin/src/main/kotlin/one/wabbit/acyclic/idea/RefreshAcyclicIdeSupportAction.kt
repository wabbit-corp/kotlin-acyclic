package one.wabbit.acyclic.idea

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction

class RefreshAcyclicIdeSupportAction : DumbAwareAction(
    "Refresh Acyclic IDE Support",
    "Re-scan Kotlin compiler arguments and enable acyclic IDE support for this project session",
    null,
) {
    override fun update(event: AnActionEvent) {
        event.presentation.isEnabledAndVisible = event.project != null
    }

    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        AcyclicIdeSupportCoordinator.enableIfNeeded(
            project = project,
            userInitiated = true,
        )
    }
}

