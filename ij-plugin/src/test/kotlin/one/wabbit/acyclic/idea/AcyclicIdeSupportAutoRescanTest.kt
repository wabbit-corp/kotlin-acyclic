// SPDX-License-Identifier: LicenseRef-Wabbit-Public-Test-License-1.1

package one.wabbit.acyclic.idea

import com.intellij.openapi.project.Project
import java.lang.reflect.Proxy
import kotlin.test.Test
import kotlin.test.assertEquals

class AcyclicIdeSupportAutoRescanTest {
    @Test
    fun `install triggers rescans for import finish and roots changed`() {
        val project = fakeProject("demo")
        var importFinished: (() -> Unit)? = null
        var rootsChanged: (() -> Unit)? = null
        var projectTrusted: ((Project) -> Unit)? = null
        var rescans = 0

        AcyclicIdeSupportAutoRescan.install(
            project = project,
            requestRescan = { rescans += 1 },
            subscribeImportFinished = { callback -> importFinished = callback },
            subscribeRootsChanged = { callback -> rootsChanged = callback },
            subscribeProjectTrusted = { callback -> projectTrusted = callback },
        )

        importFinished!!.invoke()
        rootsChanged!!.invoke()

        assertEquals(2, rescans)
        val onProjectTrusted = requireNotNull(projectTrusted)
        onProjectTrusted.invoke(fakeProject("other"))
        assertEquals(2, rescans)
        onProjectTrusted.invoke(project)
        assertEquals(3, rescans)
    }

    private fun fakeProject(name: String): Project =
        Proxy.newProxyInstance(
            javaClass.classLoader,
            arrayOf(Project::class.java),
        ) { _, method, _ ->
            when (method.name) {
                "getName" -> name
                "toString" -> "fakeProject($name)"
                else -> unsupported(method.name)
            }
        } as Project

    private fun unsupported(methodName: String): Nothing =
        throw UnsupportedOperationException("Unexpected proxy call to $methodName")
}
