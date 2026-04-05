package one.wabbit.acyclic.idea

import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity

class AcyclicIdeSupportActivity : ProjectActivity {
    override suspend fun execute(project: Project) {
        AcyclicIdeSupportCoordinator.enableIfNeeded(
            project = project,
            userInitiated = false,
        )
    }
}

