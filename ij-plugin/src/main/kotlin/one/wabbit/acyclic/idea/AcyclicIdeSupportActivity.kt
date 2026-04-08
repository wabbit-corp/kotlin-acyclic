// SPDX-License-Identifier: AGPL-3.0-or-later

package one.wabbit.acyclic.idea

import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity

class AcyclicIdeSupportActivity : ProjectActivity {
    override suspend fun execute(project: Project) {
        fun requestRescan() {
            AcyclicIdeSupportCoordinator.enableIfNeeded(
                project = project,
                userInitiated = false,
            )
        }

        requestRescan()
        AcyclicIdeSupportAutoRescan.install(project, ::requestRescan)
    }
}
